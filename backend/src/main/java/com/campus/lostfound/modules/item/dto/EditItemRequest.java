package com.campus.lostfound.modules.item.dto;

import com.campus.lostfound.common.validation.CodePointLength;
import com.campus.lostfound.modules.item.dto.CreateItemRequest.ItemCategory;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** 普通字段绑定区分 PATCH 缺省与显式 null；缺省不修改，显式 null 拒绝。 */
public final class EditItemRequest {
    @NotNull @Positive public Long expectedVersion;
    @JsonSetter(nulls = Nulls.FAIL) public ItemCategory category;
    @JsonSetter(nulls = Nulls.FAIL) @CodePointLength(min = 1, max = 50) public String location;
    @JsonSetter(nulls = Nulls.FAIL) public LocalDate occurredDate;
    @JsonSetter(nulls = Nulls.FAIL) @CodePointLength(min = 1, max = 50) public String title;
    @JsonSetter(nulls = Nulls.FAIL) @CodePointLength(min = 10, max = 500) public String description;
    @JsonSetter(nulls = Nulls.FAIL) @Size(max = 3)
    public List<@NotNull @Pattern(regexp = "[1-9][0-9]{0,18}") String> imageIds;

    public Long expectedVersion() { return expectedVersion; }
    public ItemCategory category() { return category; }
    public String location() { return location; }
    public LocalDate occurredDate() { return occurredDate; }
    public String title() { return title; }
    public String description() { return description; }
    public List<String> imageIds() { return imageIds; }

    @AssertTrue(message = "至少修改一项内容")
    public boolean isChanged() {
        return category != null || location != null || occurredDate != null || title != null
                || description != null || imageIds != null;
    }

    @Override public String toString() { return "EditItemRequest[expectedVersion=" + expectedVersion + "]"; }
}
