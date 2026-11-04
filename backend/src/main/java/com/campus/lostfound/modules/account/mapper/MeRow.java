package com.campus.lostfound.modules.account.mapper;

/** 本人资料：users 连接名册得到身份字段；管理员没有名册记录，身份字段为 null。 */
public record MeRow(Long id, String studentNo, String realName, String campusCardNo, String nickname, String email,
        String phone, String role, Long version) {
}
