package com.campus.lostfound.modules.account.service;

import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * 登录失败限流：同一账号连续失败达到次数后锁定一段时间，到期自动解锁，不永久锁号。
 * 记录保存在内存中，重启后清空；第一版只部署单个后端实例，足够使用。
 */
@Component
public class LoginAttemptLimiter {

    private record Attempts(int failures, Instant lockedUntil) {
    }

    private final ConcurrentMap<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration lockDuration;
    private final Clock clock;

    public LoginAttemptLimiter(AppProperties properties, Clock clock) {
        this.maxFailures = properties.security().loginMaxFailures();
        this.lockDuration = properties.security().loginLockDuration();
        this.clock = clock;
    }

    /** 账号处于锁定期时抛出 429。 */
    public void checkAllowed(String account) {
        Attempts current = attempts.get(account);
        if (current == null || current.lockedUntil() == null) {
            return;
        }
        Instant now = clock.instant();
        if (now.isBefore(current.lockedUntil())) {
            long minutes = Math.max(1, (Duration.between(now, current.lockedUntil()).toSeconds() + 59) / 60);
            throw new BusinessException(ErrorCode.RATE_LIMITED,
                    "登录失败次数过多，请 " + minutes + " 分钟后再试");
        }
        // 锁定已到期，重新计数
        attempts.remove(account, current);
    }

    public void recordFailure(String account) {
        attempts.compute(account, (key, current) -> {
            int failures = (current == null ? 0 : current.failures()) + 1;
            Instant lockedUntil = failures >= maxFailures ? clock.instant().plus(lockDuration) : null;
            return new Attempts(failures, lockedUntil);
        });
    }

    public void reset(String account) {
        attempts.remove(account);
    }
}
