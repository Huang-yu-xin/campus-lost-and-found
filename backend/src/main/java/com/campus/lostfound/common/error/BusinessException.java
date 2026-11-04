package com.campus.lostfound.common.error;

import com.campus.lostfound.common.api.FieldError;
import java.util.List;

/** 业务规则失败，由 GlobalExceptionHandler 转为合同格式的错误响应。 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<FieldError> errors;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), List.of());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public BusinessException(ErrorCode errorCode, String message, List<FieldError> errors) {
        super(message);
        this.errorCode = errorCode;
        this.errors = List.copyOf(errors);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<FieldError> getErrors() {
        return errors;
    }
}
