package com.campus.lostfound.modules.item.controller;

import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.common.api.PageData;
import com.campus.lostfound.modules.account.security.AccountUserDetails;
import com.campus.lostfound.modules.item.dto.CreateItemRequest;
import com.campus.lostfound.modules.item.dto.ItemSearch;
import com.campus.lostfound.modules.item.dto.ItemViews.ItemRecord;
import com.campus.lostfound.modules.item.dto.ItemViews.PublicItem;
import com.campus.lostfound.modules.item.service.ItemService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/items")
public class ItemController {
    private final ItemService service;

    public ItemController(ItemService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ItemRecord> create(@AuthenticationPrincipal AccountUserDetails user,
            @Valid @RequestBody CreateItemRequest request) {
        return ApiResponse.ok(service.create(user.userId(), request));
    }

    @GetMapping
    public ApiResponse<PageData<PublicItem>> list(@Valid @ModelAttribute ItemSearch search) {
        return ApiResponse.ok(service.listPublished(search));
    }

    @GetMapping("/{itemId}")
    public ApiResponse<PublicItem> detail(@PathVariable @Positive long itemId) {
        return ApiResponse.ok(service.getPublished(itemId));
    }
}
