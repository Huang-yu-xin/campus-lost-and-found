package com.campus.lostfound.modules.account.controller;

import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.modules.account.dto.LoginRequest;
import com.campus.lostfound.modules.account.dto.Me;
import com.campus.lostfound.modules.account.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR-02 登录与退出。两者都是写请求，需携带 X-CSRF-TOKEN；成功后前端须重新获取 CSRF 令牌。 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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
