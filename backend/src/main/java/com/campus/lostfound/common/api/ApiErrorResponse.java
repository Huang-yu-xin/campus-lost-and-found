package com.campus.lostfound.common.api;

import com.campus.lostfound.common.web.RequestIdFilter;
import java.util.List;

/**
 * 失败响应：data 固定为 null，errors 为字段错误数组（无字段错误时为 []）。
 */
public record ApiErrorResponse(String code, String message, Object data, String requestId, List<FieldError> errors) {

    public static ApiErrorResponse of(String code, String message, List<FieldError> errors) {
        return new ApiErrorResponse(code, message, null, RequestIdFilter.current(),
                errors == null ? List.of() : List.copyOf(errors));
    }
}
