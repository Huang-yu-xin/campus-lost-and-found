package com.campus.lostfound.common.error;

import org.springframework.http.HttpStatus;

/**
 * 接口合同中的稳定错误码及其 HTTP 状态。客户端按 code 判断，不依赖 message 文案。
 * 新增错误码时同步更新接口文档的错误码表和 openapi.yaml。
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "请求参数不合法"),
    IDENTITY_MISMATCH(HttpStatus.BAD_REQUEST, "身份信息与名册不匹配"),
    EMAIL_CODE_INVALID(HttpStatus.BAD_REQUEST, "验证码错误"),
    EMAIL_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "验证码已过期"),
    EMAIL_CODE_USED(HttpStatus.BAD_REQUEST, "验证码已使用"),
    OLD_PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST, "原密码错误"),
    CONTACT_FIELD_MISSING(HttpStatus.BAD_REQUEST, "尚未填写该联系方式"),
    REPORT_CONTEXT_INVALID(HttpStatus.BAD_REQUEST, "举报对象不合法"),

    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "请先登录"),
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, "账号或密码错误"),

    FORBIDDEN(HttpStatus.FORBIDDEN, "无权执行该操作"),
    CSRF_INVALID(HttpStatus.FORBIDDEN, "请求令牌无效，请刷新页面后重试"),
    SELF_OPERATION_NOT_ALLOWED(HttpStatus.FORBIDDEN, "不能对自己发布的信息执行该操作"),
    CONTACT_NOT_AUTHORIZED(HttpStatus.FORBIDDEN, "对方尚未授权查看联系方式"),

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),
    /** 框架层补充：合同错误码表暂未列出 405，待团队确认后写回接口文档。 */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "不支持该请求方法"),

    VERSION_CONFLICT(HttpStatus.CONFLICT, "数据已被修改，请刷新后重试"),
    IDENTITY_ALREADY_BOUND(HttpStatus.CONFLICT, "该身份已注册"),
    ACCOUNT_IDENTIFIER_CONFLICT(HttpStatus.CONFLICT, "登录标识冲突"),
    EMAIL_ALREADY_BOUND(HttpStatus.CONFLICT, "该邮箱已被使用"),
    ITEM_STATE_CONFLICT(HttpStatus.CONFLICT, "物品当前状态不允许该操作"),
    ITEM_SUBMISSION_CONFLICT(HttpStatus.CONFLICT, "同一提交编号已用于其他内容"),
    CLAIM_STATE_CONFLICT(HttpStatus.CONFLICT, "认领申请当前状态不允许该操作"),
    ITEM_ALREADY_CLAIMED(HttpStatus.CONFLICT, "该物品已有确认的认领人"),
    CLAIM_ALREADY_EXISTS(HttpStatus.CONFLICT, "已提交过认领申请"),
    CLUE_ALREADY_EXISTS(HttpStatus.CONFLICT, "已提交过线索"),
    CONTACT_NOT_ACTIVE(HttpStatus.CONFLICT, "当前认领阶段不允许查看联系方式"),
    CONVERSATION_CLOSED(HttpStatus.CONFLICT, "留言已关闭"),
    IMAGE_ALREADY_BOUND(HttpStatus.CONFLICT, "图片已绑定"),
    REPORT_STATE_CONFLICT(HttpStatus.CONFLICT, "举报当前状态不允许该操作"),

    IMAGE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "图片不能超过 2MB"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的文件或内容类型"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "操作过于频繁，请稍后再试"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
