package com.campus.lostfound.modules.account.mapper;

import com.campus.lostfound.modules.account.entity.NewUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    /** 按登录标识（学号、卡号或管理员账号）查找认证信息；不存在返回 null。 */
    UserAuthRow findAuthByIdentifier(@Param("identifier") String identifier);

    /** 当前认证版本；用户不存在返回 null。 */
    Long findAuthVersion(@Param("id") long id);

    MeRow findMe(@Param("id") long id);

    /** 认证版本加 1，使该用户已有的全部会话失效（改密时使用）。返回受影响行数。 */
    int incrementAuthVersion(@Param("id") long id);

    void insert(NewUser user);
}
