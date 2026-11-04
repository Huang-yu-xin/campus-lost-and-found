package com.campus.lostfound.modules.account.dto;

import com.campus.lostfound.modules.account.mapper.MeRow;

/** 当前用户资料（MeResponse.data）。ID 以字符串对外；管理员的身份字段为 null。 */
public record Me(String id, String studentId, String realName, String campusCardNo, String nickname, String email,
        String phone, String role, long version) {

    public static Me from(MeRow row) {
        return new Me(String.valueOf(row.id()), row.studentNo(), row.realName(), row.campusCardNo(),
                row.nickname(), row.email(), row.phone(), row.role(), row.version());
    }
}
