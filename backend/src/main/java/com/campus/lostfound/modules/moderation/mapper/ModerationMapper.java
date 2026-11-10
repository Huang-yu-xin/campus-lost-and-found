package com.campus.lostfound.modules.moderation.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 管理端审核数据访问（FR-20）。所有状态变更都带原状态条件。 */
@Mapper
public interface ModerationMapper {

    String COLUMNS = "i.id, i.publisher_id, i.type, i.category, i.location, i.title, "
            + "i.description, i.status, i.review_reason, i.published_at, i.created_at, "
            + "u.nickname AS publisher_nickname ";

    @Select("SELECT " + COLUMNS + "FROM items i JOIN users u ON u.id = i.publisher_id "
            + "WHERE i.status = #{status} ORDER BY i.created_at ASC, i.id ASC")
    List<Map<String, Object>> listByStatus(@Param("status") String status);

    @Select("SELECT " + COLUMNS + "FROM items i JOIN users u ON u.id = i.publisher_id "
            + "WHERE i.id = #{itemId}")
    Map<String, Object> findItem(@Param("itemId") long itemId);

    @Update("UPDATE items SET status='PUBLISHED', review_reason=NULL, published_at=NOW(6) "
            + "WHERE id=#{itemId} AND status='PENDING_REVIEW'")
    int approve(@Param("itemId") long itemId);

    @Update("UPDATE items SET status='RETURNED', review_reason=#{reason} "
            + "WHERE id=#{itemId} AND status='PENDING_REVIEW'")
    int returnItem(@Param("itemId") long itemId, @Param("reason") String reason);

    @Update("UPDATE items SET status='OFF_SHELF', closure_reason='VIOLATION', "
            + "closed_at=NOW(6) WHERE id=#{itemId} AND status='PUBLISHED'")
    int takeDown(@Param("itemId") long itemId);

    @Select("SELECT r.id, r.operator_id, r.action, r.reason, r.item_version, r.created_at, "
            + "u.nickname AS operator_nickname FROM review_records r "
            + "JOIN users u ON u.id = r.operator_id "
            + "WHERE r.item_id = #{itemId} ORDER BY r.created_at DESC, r.id DESC")
    List<Map<String, Object>> listReviewRecords(@Param("itemId") long itemId);

    @Insert("INSERT INTO review_records (item_id, operator_id, action, reason, item_version) "
            + "VALUES (#{itemId}, #{operatorId}, #{action}, #{reason}, #{itemVersion})")
    int insertReviewRecord(Map<String, Object> record);

    @Insert("INSERT INTO operation_logs "
            + "(actor_id, entity_type, entity_id, action, before_status, after_status, request_id) "
            + "VALUES (#{actorId}, 'ITEM', #{entityId}, #{action}, #{beforeStatus}, "
            + "#{afterStatus}, #{requestId})")
    int insertOperationLog(Map<String, Object> log);
}
