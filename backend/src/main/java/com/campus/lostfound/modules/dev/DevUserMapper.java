package com.campus.lostfound.modules.dev;

import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 开发期只读查询：临时登录需要确认用户是否存在及其角色。
 * 正式环境不会加载本类所在控制器（见 DevLoginController 的条件装配）。
 */
@Mapper
public interface DevUserMapper {

    @Select("SELECT id AS id, role AS role, nickname AS nickname FROM users WHERE id = #{id}")
    Map<String, Object> findById(@Param("id") long id);
}
