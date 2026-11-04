package com.campus.lostfound.modules.account.service;

import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.modules.account.dto.Me;
import com.campus.lostfound.modules.account.dto.RegisterRequest;
import com.campus.lostfound.modules.account.entity.NewUser;
import com.campus.lostfound.modules.account.mapper.LoginIdentifierMapper;
import com.campus.lostfound.modules.account.mapper.UserMapper;
import java.security.SecureRandom;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-01 注册学生账号。在同一事务中：校验验证码 → 核对名册 → 写入用户和两个登录标识 → 消费验证码。
 * 任何一步失败都整体回滚，不留下半个账号；只有“验证码输错”的错误次数会保存下来。
 * 注册成功不自动登录。
 */
@Service
public class RegistrationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationCodeService verificationCodeService;
    private final RosterService rosterService;
    private final UserMapper userMapper;
    private final LoginIdentifierMapper loginIdentifierMapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;

    public RegistrationService(VerificationCodeService verificationCodeService, RosterService rosterService,
            UserMapper userMapper, LoginIdentifierMapper loginIdentifierMapper, PasswordEncoder passwordEncoder,
            CurrentUserService currentUserService) {
        this.verificationCodeService = verificationCodeService;
        this.rosterService = rosterService;
        this.userMapper = userMapper;
        this.loginIdentifierMapper = loginIdentifierMapper;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
    }

    @Transactional(noRollbackFor = EmailCodeRejectedException.class)
    public Me register(RegisterRequest request) {
        String email = EmailAddresses.normalize(request.email());
        long codeId = verificationCodeService.verify(VerificationPurpose.REGISTER, null, email, request.emailCode());

        long studentRecordId = rosterService
                .findMatchingStudent(request.studentId(), request.realName(), request.campusCardNo())
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTITY_MISMATCH));

        // 先查一遍给出明确的错误；并发时仍以数据库唯一约束为准
        if (userMapper.existsByStudentRecordId(studentRecordId)) {
            throw new BusinessException(ErrorCode.IDENTITY_ALREADY_BOUND);
        }
        if (userMapper.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_BOUND);
        }
        if (loginIdentifierMapper.exists(request.studentId()) || loginIdentifierMapper.exists(request.campusCardNo())) {
            throw new BusinessException(ErrorCode.ACCOUNT_IDENTIFIER_CONFLICT);
        }

        String nickname = request.nickname() != null ? request.nickname() : defaultNickname();
        NewUser user = NewUser.student(studentRecordId, nickname, email, passwordEncoder.encode(request.password()));
        try {
            userMapper.insert(user);
            loginIdentifierMapper.insert(request.studentId(), user.getId());
            if (!request.campusCardNo().equals(request.studentId())) {
                loginIdentifierMapper.insert(request.campusCardNo(), user.getId());
            }
        } catch (DuplicateKeyException ex) {
            throw duplicateToBusiness(ex);
        }

        verificationCodeService.markConsumed(codeId);
        return currentUserService.getMe(user.getId());
    }

    /** 默认昵称不包含姓名、学号等真实身份信息。 */
    private static String defaultNickname() {
        return "同学" + "%06d".formatted(RANDOM.nextInt(1_000_000));
    }

    private static BusinessException duplicateToBusiness(DuplicateKeyException ex) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (message.contains("uq_users_student")) {
            return new BusinessException(ErrorCode.IDENTITY_ALREADY_BOUND);
        }
        if (message.contains("uq_users_email")) {
            return new BusinessException(ErrorCode.EMAIL_ALREADY_BOUND);
        }
        return new BusinessException(ErrorCode.ACCOUNT_IDENTIFIER_CONFLICT);
    }
}
