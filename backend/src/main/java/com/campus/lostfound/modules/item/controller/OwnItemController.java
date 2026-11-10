package com.campus.lostfound.modules.item.controller;

import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.common.api.PageData;
import com.campus.lostfound.modules.account.security.AccountUserDetails;
import com.campus.lostfound.modules.item.dto.ItemViews.ItemRecord;
import com.campus.lostfound.modules.item.dto.OwnItemSearch;
import com.campus.lostfound.modules.item.service.ItemService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 为乙的编辑/下架提供本人入口；与甲联调时统一个人中心入口，避免重复映射。 */
@RestController
@RequestMapping("/api/v1/me/items")
@PreAuthorize("hasRole('STUDENT')")
public class OwnItemController {
    private final ItemService service;
    public OwnItemController(ItemService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PageData<ItemRecord>> list(@AuthenticationPrincipal AccountUserDetails user,
            @Valid @ModelAttribute OwnItemSearch search) {
        return ApiResponse.ok(service.listOwn(user.userId(), search));
    }

    @GetMapping("/{itemId}")
    public ApiResponse<ItemRecord> detail(@AuthenticationPrincipal AccountUserDetails user,
            @PathVariable long itemId) {
        return ApiResponse.ok(service.getOwn(itemId, user.userId()));
    }
}
