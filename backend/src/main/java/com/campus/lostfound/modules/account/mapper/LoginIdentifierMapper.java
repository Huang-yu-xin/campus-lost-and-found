package com.campus.lostfound.modules.account.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LoginIdentifierMapper {

    boolean exists(@Param("identifier") String identifier);

    /** 标识是主键：学号、卡号、管理员账号共用一个空间，重复时抛 DuplicateKeyException。 */
    void insert(@Param("identifier") String identifier, @Param("userId") long userId);
}
