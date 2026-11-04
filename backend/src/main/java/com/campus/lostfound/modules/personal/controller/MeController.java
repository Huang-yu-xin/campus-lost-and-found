package com.campus.lostfound.modules.personal.controller;

import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.modules.account.dto.Me;
import com.campus.lostfound.modules.account.security.AccountUserDetails;
import com.campus.lostfound.modules.account.service.CurrentUserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR-06 本人资料。只读取当前会话对应的用户，不接受 userId 参数。 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final CurrentUserService currentUserService;

    public MeController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ApiResponse<Me> me(@AuthenticationPrincipal AccountUserDetails user) {
        return ApiResponse.ok(currentUserService.getMe(user.userId()));
    }
}
