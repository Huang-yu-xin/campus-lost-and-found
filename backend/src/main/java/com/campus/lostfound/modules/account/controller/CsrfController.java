package com.campus.lostfound.modules.account.controller;

import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.modules.account.dto.CsrfData;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** SUP-01：匿名也可获取与当前会话绑定的 CSRF 令牌。登录、退出、改密后前端需重新获取。 */
@RestController
@RequestMapping("/api/v1/auth")
public class CsrfController {

    @GetMapping("/csrf")
    public ApiResponse<CsrfData> csrf(CsrfToken csrfToken) {
        return ApiResponse.ok(new CsrfData(csrfToken.getHeaderName(), csrfToken.getToken()));
    }
}
