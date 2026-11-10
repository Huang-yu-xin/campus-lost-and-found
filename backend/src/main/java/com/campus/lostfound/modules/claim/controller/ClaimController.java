package com.campus.lostfound.modules.claim.controller;

import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.common.api.PageData;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.ClaimDetailView;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.ClaimView;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.CreateClaimRequest;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.MessageView;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.RejectClaimRequest;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.SendMessageRequest;
import com.campus.lostfound.modules.claim.service.ClaimMessageService;
import com.campus.lostfound.modules.claim.service.ClaimService;
import com.campus.lostfound.modules.item.dto.VersionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 认领模块接口（FR-15 提交、FR-16 确认、FR-19 留言、认领私有详情）。 */
@RestController
@RequestMapping("/api/v1")
public class ClaimController {

    private final ClaimService claimService;
    private final ClaimMessageService messageService;

    public ClaimController(ClaimService claimService, ClaimMessageService messageService) {
        this.claimService = claimService;
        this.messageService = messageService;
    }

    /** FR-15：对已发布的招领信息提交认领申请。 */
    @PostMapping("/items/{itemId}/claims")
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ClaimView> create(@PathVariable("itemId") long itemId,
                                         @Valid @RequestBody CreateClaimRequest request,
                                         Authentication authentication) {
        return ApiResponse.ok(
                claimService.createClaim(currentUserId(authentication), itemId, request.description()));
    }

    /** 我提交的认领申请，只含本人记录。 */
    @GetMapping("/me/claims")
    public ApiResponse<PageData<ClaimView>> mine(Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(claimService.personal(currentUserId(authentication), false,
                status, null, page, pageSize));
    }

    /** 我发布的信息收到的认领申请；itemId 只能缩小范围。 */
    @GetMapping("/me/received-claims")
    public ApiResponse<PageData<ClaimView>> receivedPage(Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long itemId) {
        return ApiResponse.ok(claimService.personal(currentUserId(authentication), true,
                status, itemId, page, pageSize));
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
                                          @Valid @RequestBody VersionRequest request,
                                          Authentication authentication) {
        return ApiResponse.ok(claimService.confirmClaim(currentUserId(authentication), claimId, request.expectedVersion()));
    }

    /** FR-17：发布者驳回认领申请，原因必填。 */
    @PostMapping("/claims/{claimId}/reject")
    public ApiResponse<ClaimView> reject(@PathVariable("claimId") long claimId,
                                         @Valid @RequestBody RejectClaimRequest request,
                                         Authentication authentication) {
        return ApiResponse.ok(claimService.rejectClaim(
                currentUserId(authentication), claimId, request.expectedVersion(), request.reason()));
    }

    /** FR-18：发布者确认线下交接完成，物品归档。 */
    @PostMapping("/claims/{claimId}/complete")
    public ApiResponse<ClaimView> complete(@PathVariable("claimId") long claimId,
                                           @Valid @RequestBody VersionRequest request,
                                           Authentication authentication) {
        return ApiResponse.ok(claimService.completeClaim(currentUserId(authentication), claimId, request.expectedVersion()));
    }

    /** FR-19：查询认领会话留言，双方可见，终态仍可查询。 */
    @GetMapping("/claims/{claimId}/messages")
    public ApiResponse<PageData<MessageView>> messages(
            @PathVariable("claimId") long claimId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            Authentication authentication) {
        return ApiResponse.ok(messageService.list(currentUserId(authentication), claimId, page, pageSize));
    }

    /** FR-19：发送认领留言，申请后可聊，终态只读。 */
    @PostMapping("/claims/{claimId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MessageView> sendMessage(@PathVariable("claimId") long claimId,
                                                @Valid @RequestBody SendMessageRequest request,
                                                Authentication authentication) {
        return ApiResponse.ok(messageService.send(
                currentUserId(authentication), claimId, request.content()));
    }

    /** 会话中以用户 ID 字符串作为 principal，取当前用户主键。 */
    private static long currentUserId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}
