package com.campus.lostfound.modules.account.service;

import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.config.AppProperties;
import com.campus.lostfound.infrastructure.mail.EmailSender;
import com.campus.lostfound.modules.account.dto.CodeSentData;
import com.campus.lostfound.modules.account.mapper.EmailCodeMapper;
import com.campus.lostfound.modules.account.mapper.EmailCodeRow;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 邮箱验证码：6 位数字，数据库只存 HMAC-SHA256 摘要。
 * 摘要输入包含用途、用户作用域、邮箱、发送时间和验证码，不同用途、不同邮箱的码不能互相使用。
 * 校验与消费分两步：业务写入成功后才标记已消费，业务失败时整体回滚，验证码仍可再用。
 */
@Service
public class VerificationCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final EmailCodeMapper emailCodeMapper;
    private final EmailSender emailSender;
    private final Clock clock;
    private final AppProperties.Verification config;
    private final SecretKeySpec hmacKey;

    public VerificationCodeService(EmailCodeMapper emailCodeMapper, EmailSender emailSender, Clock clock,
            AppProperties properties) {
        this.emailCodeMapper = emailCodeMapper;
        this.emailSender = emailSender;
        this.clock = clock;
        this.config = properties.verification();
        this.hmacKey = new SecretKeySpec(config.hmacKey().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    /**
     * 生成并发送验证码。同一作用域重发会覆盖旧码；距上次发送不足间隔时返回 429。
     * @param userId 修改邮箱时为当前用户，注册时为 null
     * @param email 已规范化的邮箱
     */
    @Transactional
    public CodeSentData send(VerificationPurpose purpose, Long userId, String email) {
        long subjectKey = subjectKey(userId);
        Instant now = now();
        EmailCodeRow existing = emailCodeMapper.findForUpdate(purpose.name(), email, subjectKey);
        if (existing != null) {
            Instant nextAllowed = existing.sentAt().plus(config.resendInterval());
            if (now.isBefore(nextAllowed)) {
                long seconds = Math.max(1, Duration.between(now, nextAllowed).toSeconds());
                throw new BusinessException(ErrorCode.RATE_LIMITED, "请 " + seconds + " 秒后再获取验证码");
            }
        }

        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        byte[] mac = mac(purpose, subjectKey, email, now, code);
        Instant expiresAt = now.plus(config.codeTtl());
        if (existing == null) {
            try {
                emailCodeMapper.insert(purpose.name(), userId, email, mac, config.keyId(), now, expiresAt);
            } catch (DuplicateKeyException | PessimisticLockingFailureException ex) {
                // 同一邮箱几乎同时发起了两次首次发送
                throw new BusinessException(ErrorCode.RATE_LIMITED, "验证码正在发送，请稍后再试");
            }
        } else {
            emailCodeMapper.resend(existing.id(), mac, config.keyId(), now, expiresAt);
        }
        // 发送失败时抛出异常，本次写入随事务回滚
        emailSender.sendVerificationCode(email, purpose.label(), code, config.codeTtl());
        return new CodeSentData(config.codeTtl().toSeconds(), config.resendInterval().toSeconds());
    }

    /**
     * 在调用方事务中校验验证码（加行锁），正确时返回记录 id。
     * 输错时错误次数加 1 并抛出 {@link EmailCodeRejectedException}；调用方事务需对该异常不回滚。
     */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = EmailCodeRejectedException.class)
    public long verify(VerificationPurpose purpose, Long userId, String email, String code) {
        long subjectKey = subjectKey(userId);
        EmailCodeRow row = emailCodeMapper.findForUpdate(purpose.name(), email, subjectKey);
        if (row == null) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_INVALID, "验证码错误，请先获取验证码");
        }
        if (row.consumedAt() != null) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_USED, "验证码已使用，请重新获取");
        }
        if (!now().isBefore(row.expiresAt())) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_EXPIRED, "验证码已过期，请重新获取");
        }
        if (row.failedAttempts() >= config.maxFailedAttempts()) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_INVALID, "验证码错误次数过多，请重新获取");
        }
        boolean matches = config.keyId().equals(row.keyId())
                && MessageDigest.isEqual(mac(purpose, subjectKey, email, row.sentAt(), code), row.codeMac());
        if (!matches) {
            emailCodeMapper.incrementFailedAttempts(row.id());
            int remaining = config.maxFailedAttempts() - row.failedAttempts() - 1;
            throw new EmailCodeRejectedException(remaining > 0
                    ? "验证码错误，还可以再试 " + remaining + " 次"
                    : "验证码错误次数过多，请重新获取");
        }
        return row.id();
    }

    /** 业务写入成功后调用，与业务写入在同一事务中提交。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void markConsumed(long codeId) {
        if (emailCodeMapper.markConsumed(codeId, now()) != 1) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_USED, "验证码已使用，请重新获取");
        }
    }

    private static long subjectKey(Long userId) {
        return userId == null ? 0 : userId;
    }

    /** 数据库 DATETIME(6) 精确到微秒，参与摘要计算的时间也截到微秒，读回后才能算出同样的摘要。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private byte[] mac(VerificationPurpose purpose, long subjectKey, String email, Instant sentAt, String code) {
        long sentAtMicros = ChronoUnit.MICROS.between(Instant.EPOCH, sentAt);
        String input = String.join("\n", purpose.name(), Long.toString(subjectKey), email,
                Long.toString(sentAtMicros), code);
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hmacKey);
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("无法计算验证码摘要", ex);
        }
    }
}
