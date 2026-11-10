package com.campus.lostfound.modules.claim.mapper;

import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 认领模块数据访问。SQL 全部参数化，状态迁移一律带原状态条件。 */
@Mapper
public interface ClaimMapper {

    @Select("SELECT id, publisher_id, type, status, accepted_claim_id "
            + "FROM items WHERE id = #{itemId}")
    Map<String, Object> findItem(@Param("itemId") long itemId);

    /** 同一用户对同一物品只能有一个申请（含驳回/关闭历史）。 */
    @Select("SELECT id, status FROM claims WHERE item_id = #{itemId} AND applicant_id = #{applicantId}")
    Map<String, Object> findApplicantClaim(@Param("itemId") long itemId,
                                           @Param("applicantId") long applicantId);

    @Select("SELECT id FROM claims WHERE item_id = #{itemId} "
            + "AND status IN ('CONFIRMED','COMPLETED') LIMIT 1")
    Map<String, Object> findWinner(@Param("itemId") long itemId);

    @Insert("INSERT INTO claims (item_id, publisher_id, applicant_id, description, status) "
            + "VALUES (#{itemId}, #{publisherId}, #{applicantId}, #{description}, 'PENDING')")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertClaim(Map<String, Object> claim);

    @Select("SELECT c.id, c.item_id, c.publisher_id, c.applicant_id, c.description, "
            + "c.status, c.reason, c.version, c.created_at, c.updated_at, "
            + "ua.nickname AS applicant_nickname, up.nickname AS publisher_nickname "
            + "FROM claims c "
            + "JOIN users ua ON ua.id = c.applicant_id "
            + "JOIN users up ON up.id = c.publisher_id "
            + "WHERE c.id = #{claimId}")
    Map<String, Object> findClaim(@Param("claimId") long claimId);

    @Select("SELECT id, type, category, location, title, description, status, "
            + "occurred_date, published_at, created_at FROM items WHERE id = #{itemId}")
    Map<String, Object> findItemBrief(@Param("itemId") long itemId);

    /** 确认认领：条件更新，rowcount=0 表示状态已被他人改动。 */
    @Update("UPDATE claims SET status='CONFIRMED', confirmed_at=NOW(6) "
            + "WHERE id=#{claimId} AND status='PENDING'")
    int markConfirmed(@Param("claimId") long claimId);

    /** 物品记录当前选中的申请；复合外键保证与 item_id 一致。 */
    @Update("UPDATE items SET accepted_claim_id=#{claimId} "
            + "WHERE id=#{itemId} AND status='PUBLISHED' AND accepted_claim_id IS NULL")
    int bindAcceptedClaim(@Param("itemId") long itemId, @Param("claimId") long claimId);

    /** 驳回：条件更新，只有 PENDING 才能被驳回。 */
    @Update("UPDATE claims SET status='REJECTED', reason=#{reason} "
            + "WHERE id=#{claimId} AND status='PENDING'")
    int markRejected(@Param("claimId") long claimId, @Param("reason") String reason);

    /** 交接完成：只有 CONFIRMED 才能被标记完成。 */
    @Update("UPDATE claims SET status='COMPLETED', completed_at=NOW(6) "
            + "WHERE id=#{claimId} AND status='CONFIRMED'")
    int markCompleted(@Param("claimId") long claimId);

    /** 归档物品：必须仍处于已发布且选中该申请。 */
    @Update("UPDATE items SET status='ARCHIVED', closure_reason='HANDOVER', closed_at=NOW(6) "
            + "WHERE id=#{itemId} AND status='PUBLISHED' AND accepted_claim_id=#{claimId}")
    int archiveItemForClaim(@Param("itemId") long itemId, @Param("claimId") long claimId);

    /** 发布者视角：自己发布的信息收到的全部认领申请。 */
    @Select("SELECT c.id, c.item_id, c.publisher_id, c.applicant_id, c.description, "
            + "c.status, c.reason, c.version, c.created_at, c.updated_at, "
            + "ua.nickname AS applicant_nickname, up.nickname AS publisher_nickname, "
            + "i.title AS item_title, i.status AS item_status "
            + "FROM claims c "
            + "JOIN users ua ON ua.id = c.applicant_id "
            + "JOIN users up ON up.id = c.publisher_id "
            + "JOIN items i ON i.id = c.item_id "
            + "WHERE c.publisher_id = #{userId} ORDER BY c.created_at DESC, c.id DESC")
    java.util.List<Map<String, Object>> listReceivedClaims(@Param("userId") long userId);

    @Insert("INSERT INTO operation_logs "
            + "(actor_id, entity_type, entity_id, action, before_status, after_status, request_id) "
            + "VALUES (#{actorId}, #{entityType}, #{entityId}, #{action}, "
            + "#{beforeStatus}, #{afterStatus}, #{requestId})")
    int insertOperationLog(Map<String, Object> log);
}
