package com.campus.lostfound.modules.claim.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ContactPermissionMapper {

    /** 关闭授权人所有认领中对应字段的授权，并递增这些授权的版本；返回受影响行数。 */
    int revokeAllForGrantor(@Param("grantorId") long grantorId, @Param("field") String field);
}
