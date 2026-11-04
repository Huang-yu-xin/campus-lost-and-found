package com.campus.lostfound.modules.personal.controller;

import com.campus.lostfound.application.ProfileWorkflowService;
import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.modules.account.dto.ChangeEmailRequest;
import com.campus.lostfound.modules.account.dto.ChangePasswordRequest;
import com.campus.lostfound.modules.account.dto.CodeSentData;
import com.campus.lostfound.modules.account.dto.Me;
import com.campus.lostfound.modules.account.dto.ProfilePatchRequest;
import com.campus.lostfound.modules.account.dto.SendCodeRequest;
import com.campus.lostfound.modules.account.security.AccountUserDetails;
import com.campus.lostfound.modules.account.service.AuthService;
import com.campus.lostfound.modules.account.service.CodeSendLimiter;
import com.campus.lostfound.modules.account.service.CurrentUserService;
import com.campus.lostfound.modules.account.service.EmailAddresses;
import com.campus.lostfound.modules.account.service.PasswordService;
import com.campus.lostfound.modules.account.service.VerificationCodeService;
import com.campus.lostfound.modules.account.service.VerificationPurpose;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-03 修改密码、FR-06 本人资料。只操作当前会话对应的用户，不接受 userId 参数。
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final CurrentUserService currentUserService;
    private final ProfileWorkflowService profileWorkflowService;
    private final VerificationCodeService verificationCodeService;
    private final CodeSendLimiter codeSendLimiter;
    private final PasswordService passwordService;
    private final AuthService authService;

    public MeController(CurrentUserService currentUserService, ProfileWorkflowService profileWorkflowService,
            VerificationCodeService verificationCodeService, CodeSendLimiter codeSendLimiter,
            PasswordService passwordService, AuthService authService) {
        this.currentUserService = currentUserService;
        this.profileWorkflowService = profileWorkflowService;
        this.verificationCodeService = verificationCodeService;
        this.codeSendLimiter = codeSendLimiter;
        this.passwordService = passwordService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<Me> me(@AuthenticationPrincipal AccountUserDetails user) {
        return ApiResponse.ok(currentUserService.getMe(user.userId()));
    }

    /** 修改昵称和手机号；改了手机号会撤回已给出的手机号授权。 */
    @PatchMapping
    public ApiResponse<Me> updateProfile(@AuthenticationPrincipal AccountUserDetails user,
            @Valid @RequestBody ProfilePatchRequest request) {
        return ApiResponse.ok(profileWorkflowService.updateProfile(user.userId(), request));
    }

    /** 发送修改邮箱验证码：绑定当前用户和新邮箱，不能用于注册。 */
    @PostMapping("/email-codes")
    public ApiResponse<CodeSentData> sendChangeEmailCode(@AuthenticationPrincipal AccountUserDetails user,
            @Valid @RequestBody SendCodeRequest request, HttpServletRequest httpRequest) {
        String email = EmailAddresses.normalize(request.email());
        if (email.equals(currentUserService.getMe(user.userId()).email())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "新邮箱与当前邮箱相同");
        }
        codeSendLimiter.acquire(httpRequest.getRemoteAddr());
        return ApiResponse.ok(verificationCodeService.send(VerificationPurpose.CHANGE_EMAIL, user.userId(), email));
    }

    /** 确认修改邮箱；成功后撤回已给出的邮箱授权。 */
    @PutMapping("/email")
    public ApiResponse<Me> changeEmail(@AuthenticationPrincipal AccountUserDetails user,
            @Valid @RequestBody ChangeEmailRequest request) {
        return ApiResponse.ok(profileWorkflowService.changeEmail(user.userId(), request));
    }

    /** 修改密码。成功后该用户所有会话失效，当前会话也立即退出，需要重新登录。 */
    @PutMapping("/password")
    public ApiResponse<Map<String, Object>> changePassword(@AuthenticationPrincipal AccountUserDetails user,
            @Valid @RequestBody ChangePasswordRequest request, Authentication authentication,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        passwordService.changePassword(user.userId(), request);
        authService.logout(authentication, httpRequest, httpResponse);
        return ApiResponse.ok();
    }
}
