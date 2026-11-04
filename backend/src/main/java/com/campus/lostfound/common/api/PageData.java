package com.campus.lostfound.common.api;

import java.util.List;

/** 列表数据：page 从 1 开始；超出末页时 items 为空数组。 */
public record PageData<T>(List<T> items, int page, int pageSize, long total) {

    public PageData {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
