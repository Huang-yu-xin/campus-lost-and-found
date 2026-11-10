package com.campus.lostfound.modules.claim.controller;

import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.ContactPermissionRequest;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.ContactPermissionView;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.CounterpartyContactView;
import com.campus.lostfound.modules.claim.service.ContactPermissionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 联系方式授权接口（FR-06）：查询自己、设置/撤回、查询对方获准字段。 */
@RestController
@RequestMapping("/api/v1/claims/{claimId}")
public class ContactPermissionController {

    private final ContactPermissionService service;

    public ContactPermissionController(ContactPermissionService service) {
        this.service = service;
    }

    @GetMapping("/contact-permission")
    public ApiResponse<ContactPermissionView> mine(@PathVariable("claimId") long claimId,
                                                   Authentication authentication) {
        return ApiResponse.ok(service.getMine(userId(authentication), claimId));
    }

    @PutMapping("/contact-permission")
    public ApiResponse<ContactPermissionView> update(
            @PathVariable("claimId") long claimId,
            @RequestBody ContactPermissionRequest request,
            Authentication authentication) {
        return ApiResponse.ok(service.setMine(userId(authentication), claimId, request));
    }

    @GetMapping("/counterparty-contact")
    public ApiResponse<CounterpartyContactView> counterparty(@PathVariable("claimId") long claimId,
                                                             Authentication authentication) {
        return ApiResponse.ok(service.getCounterparty(userId(authentication), claimId));
    }

    private static long userId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}
