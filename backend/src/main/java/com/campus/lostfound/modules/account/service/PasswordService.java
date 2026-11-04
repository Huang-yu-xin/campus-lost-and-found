package com.campus.lostfound.modules.account.service;

import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.modules.account.dto.ChangePasswordRequest;
import com.campus.lostfound.modules.account.mapper.ProfileRow;
import com.campus.lostfound.modules.account.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-03 修改密码。原密码正确后更新哈希并递增认证版本：该用户所有旧会话（包括当前这个）都失效，需重新登录。
 * 原密码连续输错与登录共用失败限流，防止借已登录的会话猜原密码。
 */
@Service
public class PasswordService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter attemptLimiter;

    public PasswordService(UserMapper userMapper, PasswordEncoder passwordEncoder, LoginAttemptLimiter attemptLimiter) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.attemptLimiter = attemptLimiter;
    }

    @Transactional
    public void changePassword(long userId, ChangePasswordRequest request) {
        String limiterKey = "change-password:" + userId;
        attemptLimiter.checkAllowed(limiterKey);

        ProfileRow current = userMapper.findProfileForUpdate(userId);
        if (current == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        if (!passwordEncoder.matches(request.oldPassword(), current.passwordHash())) {
            attemptLimiter.recordFailure(limiterKey);
            throw new BusinessException(ErrorCode.OLD_PASSWORD_INCORRECT);
        }
        attemptLimiter.reset(limiterKey);
        if (request.oldPassword().equals(request.newPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "新密码不能与原密码相同");
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(request.newPassword()));
    }
}
