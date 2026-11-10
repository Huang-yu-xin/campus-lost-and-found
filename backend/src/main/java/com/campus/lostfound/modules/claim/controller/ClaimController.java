package com.campus.lostfound.modules.claim.controller;

import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.ClaimDetailView;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.ClaimView;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.CreateClaimRequest;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.RejectClaimRequest;
import com.campus.lostfound.modules.claim.service.ClaimService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 认领模块接口（FR-15 提交、FR-16 确认、认领私有详情）。 */
@RestController
@RequestMapping("/api/v1")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    /** FR-15：对已发布的招领信息提交认领申请。 */
    @PostMapping("/items/{itemId}/claims")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ClaimView> create(@PathVariable("itemId") long itemId,
                                         @Valid @RequestBody CreateClaimRequest request,
                                         Authentication authentication) {
        return ApiResponse.ok(
                claimService.createClaim(currentUserId(authentication), itemId, request.description()));
    }

    /** 发布者视角：我发布的信息收到的认领申请（扩展接口，待写入接口文档）。 */
    @GetMapping("/claims/received")
    public ApiResponse<java.util.List<java.util.Map<String, Object>>> received(
            Authentication authentication) {
        return ApiResponse.ok(claimService.receivedClaims(currentUserId(authentication)));
    }

    /** 认领私有详情：仅申请人与发布人可见。 */
    @GetMapping("/claims/{claimId}")
    public ApiResponse<ClaimDetailView> detail(@PathVariable("claimId") long claimId,
                                               Authentication authentication) {
        return ApiResponse.ok(claimService.getClaim(currentUserId(authentication), claimId));
    }

    /** FR-16：发布者确认其中一条申请，其余申请不可再被确认。 */
    @PostMapping("/claims/{claimId}/confirm")
    public ApiResponse<ClaimView> confirm(@PathVariable("claimId") long claimId,
                                          Authentication authentication) {
        return ApiResponse.ok(claimService.confirmClaim(currentUserId(authentication), claimId));
    }

    /** FR-17：发布者驳回认领申请，原因必填。 */
    @PostMapping("/claims/{claimId}/reject")
    public ApiResponse<ClaimView> reject(@PathVariable("claimId") long claimId,
                                         @Valid @RequestBody RejectClaimRequest request,
                                         Authentication authentication) {
        return ApiResponse.ok(claimService.rejectClaim(
                currentUserId(authentication), claimId, request.reason()));
    }

    /** FR-18：发布者确认线下交接完成，物品归档。 */
    @PostMapping("/claims/{claimId}/complete")
    public ApiResponse<ClaimView> complete(@PathVariable("claimId") long claimId,
                                           Authentication authentication) {
        return ApiResponse.ok(claimService.completeClaim(currentUserId(authentication), claimId));
    }

    /** 会话中以用户 ID 字符串作为 principal，取当前用户主键。 */
    private static long currentUserId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}
