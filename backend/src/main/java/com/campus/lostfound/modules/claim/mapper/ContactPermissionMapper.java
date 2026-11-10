package com.campus.lostfound.modules.claim.mapper;

import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 联系方式授权的数据访问：只存开关，不复制手机号或邮箱。 */
@Mapper
public interface ContactPermissionMapper {

    @Select("SELECT claim_id, grantor_id, phone_visible, email_visible, version, updated_at "
            + "FROM claim_contact_permissions WHERE claim_id = #{claimId} AND grantor_id = #{grantorId}")
    Map<String, Object> find(@Param("claimId") long claimId, @Param("grantorId") long grantorId);

    @Insert("INSERT INTO claim_contact_permissions "
            + "(claim_id, grantor_id, phone_visible, email_visible, version) "
            + "VALUES (#{claimId}, #{grantorId}, #{phoneVisible}, #{emailVisible}, 1)")
    int insert(Map<String, Object> row);

    /** 乐观锁更新；expectedVersion=0 时走插入分支。 */
    @Update("UPDATE claim_contact_permissions SET phone_visible=#{phoneVisible}, "
            + "email_visible=#{emailVisible}, version=version+1 "
            + "WHERE claim_id=#{claimId} AND grantor_id=#{grantorId} AND version=#{expectedVersion}")
    int updateVersioned(Map<String, Object> row);

    /** 交接完成或认领关闭时清空该认领下双方授权并递增版本。 */
    @Update("UPDATE claim_contact_permissions SET phone_visible=0, email_visible=0, "
            + "version=version+1 WHERE claim_id=#{claimId}")
    int revokeAllForClaim(@Param("claimId") long claimId);

    @Update("UPDATE claim_contact_permissions SET phone_visible=0, version=version+1 "
            + "WHERE grantor_id=#{grantorId} AND phone_visible=1")
    int revokePhoneForGrantor(@Param("grantorId") long grantorId);

    @Update("UPDATE claim_contact_permissions SET email_visible=0, version=version+1 "
            + "WHERE grantor_id=#{grantorId} AND email_visible=1")
    int revokeEmailForGrantor(@Param("grantorId") long grantorId);

    /** 授权查询时读取授权人当前的联系方式。 */
    @Select("SELECT id, phone, email FROM users WHERE id = #{userId}")
    Map<String, Object> findUserContact(@Param("userId") long userId);
}
