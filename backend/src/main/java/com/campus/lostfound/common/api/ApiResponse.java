package com.campus.lostfound.common.api;

import com.campus.lostfound.common.web.RequestIdFilter;
import java.util.Map;

/**
 * 成功响应：{"code":"OK","message":"操作成功","data":{...},"requestId":"..."}。
 * 失败响应使用 {@link ApiErrorResponse}，不要用本类包装错误。
 */
public record ApiResponse<T>(String code, String message, T data, String requestId) {

    public static final String OK = "OK";
    private static final String DEFAULT_MESSAGE = "操作成功";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(OK, DEFAULT_MESSAGE, data, RequestIdFilter.current());
    }

    /** 无额外数据时 data 返回 {}。 */
    public static ApiResponse<Map<String, Object>> ok() {
        return ok(Map.of());
    }
}
