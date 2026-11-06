package com.campus.lostfound.modules.item.dto;

import com.campus.lostfound.common.validation.CodePointLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record CreateItemRequest(
        @NotNull ItemType type,
        @NotNull ItemCategory category,
        @NotBlank @CodePointLength(min = 1, max = 50) String location,
        @NotNull LocalDate occurredDate,
        @NotBlank @CodePointLength(min = 1, max = 50) String title,
        @NotBlank @CodePointLength(min = 10, max = 500) String description,
        @Size(max = 3) List<@Pattern(regexp = "[1-9][0-9]{0,18}") String> imageIds) {

    public CreateItemRequest {
        location = location == null ? null : location.strip();
        title = title == null ? null : title.strip();
        description = description == null ? null : description.strip();
    }

    public enum ItemType { LOST, FOUND }

    public enum ItemCategory { DOCUMENT, ELECTRONICS, DAILY, BOOK, CLOTHING, OTHER }

    /** 避免请求日志记录尚未通过隐私校验的表单。 */
    @Override
    public String toString() {
        return "CreateItemRequest[type=" + type + ", category=" + category + "]";
    }
}
