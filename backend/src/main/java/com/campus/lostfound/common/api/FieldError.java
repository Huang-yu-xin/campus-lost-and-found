package com.campus.lostfound.common.api;

/** 字段错误：field 为字段路径，message 为可展示的说明。 */
public record FieldError(String field, String message) {
}
