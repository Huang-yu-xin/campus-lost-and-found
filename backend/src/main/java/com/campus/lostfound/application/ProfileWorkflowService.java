package com.campus.lostfound.application;

import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.modules.account.dto.ChangeEmailRequest;
import com.campus.lostfound.modules.account.dto.Me;
import com.campus.lostfound.modules.account.dto.ProfilePatchRequest;
import com.campus.lostfound.modules.account.mapper.ProfileRow;
import com.campus.lostfound.modules.account.mapper.UserMapper;
import com.campus.lostfound.modules.account.service.CurrentUserService;
import com.campus.lostfound.modules.account.service.EmailAddresses;
import com.campus.lostfound.modules.account.service.EmailCodeRejectedException;
import com.campus.lostfound.modules.account.service.VerificationCodeService;
import com.campus.lostfound.modules.account.service.VerificationPurpose;
import com.campus.lostfound.modules.claim.service.ContactField;
import com.campus.lostfound.modules.claim.service.ContactPermissionService;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 改资料 + 撤授权（FR-06，甲与丙协作）。放在应用层协调，避免账号模块和认领模块互相依赖。
 * 联系方式（手机号、邮箱）一旦改变，就在同一事务中撤回该字段的全部既有授权，防止对方静默看到新值。
 * 加锁顺序：先锁自己的用户行，再改授权，与项目约定的“用户 → 物品 → 认领 → 授权”一致。
 */
@Service
public class ProfileWorkflowService {

    private final UserMapper userMapper;
    private final VerificationCodeService verificationCodeService;
    private final ContactPermissionService contactPermissionService;
    private final CurrentUserService currentUserService;

    public ProfileWorkflowService(UserMapper userMapper, VerificationCodeService verificationCodeService,
            ContactPermissionService contactPermissionService, CurrentUserService currentUserService) {
        this.userMapper = userMapper;
        this.verificationCodeService = verificationCodeService;
        this.contactPermissionService = contactPermissionService;
        this.currentUserService = currentUserService;
    }

    /** 修改昵称和手机号。学号、姓名、校园卡、角色不可改；邮箱走独立的验证码流程。 */
    @Transactional
    public Me updateProfile(long userId, ProfilePatchRequest request) {
        ProfileRow current = lockAndCheckVersion(userId, request.getExpectedVersion());
        String nickname = request.hasNickname() ? request.getNickname() : current.nickname();
        String phone = request.hasPhone() ? request.getPhone() : current.phone();

        if (userMapper.updateProfile(userId, nickname, phone, request.getExpectedVersion()) != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        if (!Objects.equals(phone, current.phone())) {
            contactPermissionService.revokeAllForGrantor(userId, ContactField.PHONE);
        }
        return currentUserService.getMe(userId);
    }

    /** 确认修改邮箱：验证码须绑定当前用户和新邮箱；成功后撤回全部邮箱授权。 */
    @Transactional(noRollbackFor = EmailCodeRejectedException.class)
    public Me changeEmail(long userId, ChangeEmailRequest request) {
        ProfileRow current = lockAndCheckVersion(userId, request.expectedVersion());
        String email = EmailAddresses.normalize(request.email());
        if (email.equals(current.email())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "新邮箱与当前邮箱相同");
        }
        long codeId = verificationCodeService.verify(VerificationPurpose.CHANGE_EMAIL, userId, email,
                request.emailCode());
        if (userMapper.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_BOUND);
        }
        try {
            if (userMapper.updateEmail(userId, email, request.expectedVersion()) != 1) {
                throw new BusinessException(ErrorCode.VERSION_CONFLICT);
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_BOUND);
        }
        contactPermissionService.revokeAllForGrantor(userId, ContactField.EMAIL);
        verificationCodeService.markConsumed(codeId);
        return currentUserService.getMe(userId);
    }

    private ProfileRow lockAndCheckVersion(long userId, long expectedVersion) {
        ProfileRow current = userMapper.findProfileForUpdate(userId);
        if (current == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        if (current.version() != expectedVersion) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        return current;
    }
}
