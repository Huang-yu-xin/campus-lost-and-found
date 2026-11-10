package com.campus.lostfound.common.api;

import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;

/** 列表接口的分页参数校验：page 从 1 开始，pageSize 上限 50。 */
public final class Pages {

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;

    private Pages() {
    }

    public static int page(int page) {
        if (page < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "page 必须大于等于 1");
        }
        return page;
    }

    public static int pageSize(int pageSize) {
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "pageSize 必须在 1~50 之间");
        }
        return pageSize;
    }

    public static int offset(int page, int pageSize) {
        return (page - 1) * pageSize;
    }
}
