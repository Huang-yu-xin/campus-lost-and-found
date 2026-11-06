package com.campus.lostfound.modules.item.dto;

import com.campus.lostfound.common.validation.CodePointLength;
import com.campus.lostfound.modules.item.dto.CreateItemRequest.ItemCategory;
import com.campus.lostfound.modules.item.dto.CreateItemRequest.ItemType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record ItemSearch(
        @Min(1) Integer page,
        @Min(10) @Max(10) Integer pageSize,
        ItemType type,
        ItemCategory category,
        @CodePointLength(min = 0, max = 50) String location,
        @CodePointLength(min = 0, max = 100) String keyword,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

    public ItemSearch {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 10 : pageSize;
        location = location == null || location.isBlank() ? null : location.strip();
        keyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
    }
}
