package com.campus.lostfound.modules.item.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record OwnItemSearch(@Min(1) Integer page, @Min(1) @Max(50) Integer pageSize, ItemStatus status) {
    public OwnItemSearch {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 10 : pageSize;
    }
    public enum ItemStatus { PENDING_REVIEW, RETURNED, PUBLISHED, OFF_SHELF, ARCHIVED }
}
