package com.campus.lostfound.modules.moderation.controller;

import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.common.api.PageData;
import com.campus.lostfound.modules.item.dto.ItemViews.ItemRecord;
import com.campus.lostfound.modules.item.dto.VersionRequest;
import com.campus.lostfound.modules.moderation.dto.ReviewRecord;
import com.campus.lostfound.modules.moderation.service.ModerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端接口（FR-20 审核、FR-21 下架）。全部要求 ADMIN 角色。 */
@RestController
@RequestMapping("/api/v1/admin/items")
@PreAuthorize("hasRole('ADMIN')")
public class ModerationController {

    private final ModerationService service;

    public ModerationController(ModerationService service) {
        this.service = service;
    }

    public record ReasonRequest(@NotNull @Positive Long expectedVersion,
            @NotBlank @Size(max = 500) String reason) {
    }

    /** 管理列表，默认只看 PENDING_REVIEW，分页。 */
    @GetMapping
    public ApiResponse<PageData<ItemRecord>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return ApiResponse.ok(service.list(status, page, pageSize));
    }

    @GetMapping("/{itemId}")
    public ApiResponse<ItemRecord> detail(@PathVariable("itemId") long itemId) {
        return ApiResponse.ok(service.detail(itemId));
    }

    @GetMapping("/{itemId}/review-records")
    public ApiResponse<PageData<ReviewRecord>> records(
            @PathVariable("itemId") long itemId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return ApiResponse.ok(service.reviewRecords(itemId, page, pageSize));
    }

    @PostMapping("/{itemId}/approve")
    public ApiResponse<ItemRecord> approve(@PathVariable("itemId") long itemId,
                                           @Valid @RequestBody VersionRequest request,
                                           Authentication authentication) {
        return ApiResponse.ok(service.approve(userId(authentication), itemId, request.expectedVersion()));
    }

    @PostMapping("/{itemId}/return")
    public ApiResponse<ItemRecord> returnItem(@PathVariable("itemId") long itemId,
                                              @Valid @RequestBody ReasonRequest body,
                                              Authentication authentication) {
        return ApiResponse.ok(service.returnItem(userId(authentication), itemId, body.expectedVersion(), body.reason()));
    }

    @PostMapping("/{itemId}/take-down")
    public ApiResponse<ItemRecord> takeDown(@PathVariable("itemId") long itemId,
                                            @Valid @RequestBody ReasonRequest body,
                                            Authentication authentication) {
        return ApiResponse.ok(service.takeDown(userId(authentication), itemId, body.expectedVersion(), body.reason()));
    }

    private static long userId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}
