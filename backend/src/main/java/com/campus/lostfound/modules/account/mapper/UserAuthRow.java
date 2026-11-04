package com.campus.lostfound.modules.account.mapper;

/** 登录认证需要的用户字段。 */
public record UserAuthRow(Long id, String role, String passwordHash, Long authVersion) {
}
