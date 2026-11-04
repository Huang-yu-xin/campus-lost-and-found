package com.campus.lostfound.modules.account.mapper;

/** 修改本人资料时加锁读取的字段。 */
public record ProfileRow(Long id, String nickname, String email, String phone, String passwordHash, Long version) {
}
