package com.campus.lostfound.common.error;

import com.campus.lostfound.common.api.ApiErrorResponse;
import com.campus.lostfound.common.api.FieldError;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * 把 Controller 及 Spring MVC 抛出的异常统一转为合同错误格式。
 * Security 过滤器层的 401/403 由 SecurityErrorHandler 处理，两者输出格式相同。
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        return respond(code.status(), code, ex.getMessage(), ex.getErrors());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return respond(ErrorCode.VALIDATION_ERROR, errors);
    }

    /** 交还给 Security 的 ExceptionTranslationFilter，由它区分未登录 401 与无权限 403。 */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void rethrowSecurityException(RuntimeException ex) {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("未预期异常", ex);
        return respond(ErrorCode.INTERNAL_ERROR, List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.add(new FieldError(e.getField(), e.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> errors.add(new FieldError(e.getObjectName(), e.getDefaultMessage())));
        return respond(ErrorCode.VALIDATION_ERROR, errors);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String field = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errors.add(new FieldError(field, error.getDefaultMessage()));
            }
        }
        return respond(ErrorCode.VALIDATION_ERROR, errors);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = List.of();
        if (ex.getCause() instanceof UnrecognizedPropertyException unknown) {
            errors = List.of(new FieldError(unknown.getPropertyName(), "不允许提交该字段"));
        }
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return respond(code.status(), code, "请求体格式不正确", errors);
    }

    /** 其余 Spring MVC 内置异常（404、405、413、415 等）按 HTTP 状态映射到合同错误码。 */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = switch (statusCode.value()) {
            case 400 -> ErrorCode.VALIDATION_ERROR;
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 413 -> ErrorCode.IMAGE_TOO_LARGE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> statusCode.is4xxClientError() ? ErrorCode.VALIDATION_ERROR : ErrorCode.INTERNAL_ERROR;
        };
        if (statusCode.is5xxServerError()) {
            log.error("请求处理失败", ex);
        }
        return ResponseEntity.status(statusCode).headers(headers)
                .body(ApiErrorResponse.of(code.name(), code.defaultMessage(), List.of()));
    }

    private static ResponseEntity<Object> respond(ErrorCode code, List<FieldError> errors) {
        return respond(code.status(), code, code.defaultMessage(), errors);
    }

    private static ResponseEntity<Object> respond(HttpStatusCode status, ErrorCode code, String message,
            List<FieldError> errors) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code.name(), message, errors));
    }
}
