package com.campus.lostfound.modules.account.controller;

import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.modules.account.dto.CodeSentData;
import com.campus.lostfound.modules.account.dto.LoginRequest;
import com.campus.lostfound.modules.account.dto.Me;
import com.campus.lostfound.modules.account.dto.RegisterRequest;
import com.campus.lostfound.modules.account.dto.SendCodeRequest;
import com.campus.lostfound.modules.account.service.AuthService;
import com.campus.lostfound.modules.account.service.CodeSendLimiter;
import com.campus.lostfound.modules.account.service.EmailAddresses;
import com.campus.lostfound.modules.account.service.RegistrationService;
import com.campus.lostfound.modules.account.service.VerificationCodeService;
import com.campus.lostfound.modules.account.service.VerificationPurpose;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-01 注册与 FR-02 登录、退出。都是写请求，需携带 X-CSRF-TOKEN；
 * 登录、退出成功后前端须重新获取 CSRF 令牌。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final VerificationCodeService verificationCodeService;
    private final RegistrationService registrationService;
    private final CodeSendLimiter codeSendLimiter;

    public AuthController(AuthService authService, VerificationCodeService verificationCodeService,
            RegistrationService registrationService, CodeSendLimiter codeSendLimiter) {
        this.authService = authService;
        this.verificationCodeService = verificationCodeService;
        this.registrationService = registrationService;
        this.codeSendLimiter = codeSendLimiter;
    }

    /** 发送注册验证码。邮箱是否已注册在注册时才判断，这里不暴露。 */
    @PostMapping("/email-codes")
    public ApiResponse<CodeSentData> sendRegistrationCode(@Valid @RequestBody SendCodeRequest request,
            HttpServletRequest httpRequest) {
        codeSendLimiter.acquire(httpRequest.getRemoteAddr());
        return ApiResponse.ok(verificationCodeService.send(VerificationPurpose.REGISTER, null,
                EmailAddresses.normalize(request.email())));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Me> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(registrationService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<Me> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        return ApiResponse.ok(authService.login(request, httpRequest, httpResponse));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout(Authentication authentication, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        authService.logout(authentication, httpRequest, httpResponse);
        return ApiResponse.ok();
    }
}
