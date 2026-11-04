package com.campus.lostfound.modules.account.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MockStudentMapper {

    /** 学号、姓名、校园卡号三项都与一条启用的名册记录一致时返回其 id，否则返回 null。 */
    Long findEnabledMatch(@Param("studentNo") String studentNo, @Param("realName") String realName,
            @Param("campusCardNo") String campusCardNo);
}
