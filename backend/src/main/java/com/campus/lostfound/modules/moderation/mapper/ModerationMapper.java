package com.campus.lostfound.modules.moderation.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 管理端审核数据访问（FR-20）。所有状态变更都带原状态条件；物品列表与详情复用 ItemService。 */
@Mapper
public interface ModerationMapper {

    @Select("SELECT version FROM items WHERE id = #{itemId}")
    Long findVersion(@Param("itemId") long itemId);

    @Update("UPDATE items SET status='PUBLISHED', review_reason=NULL, published_at=NOW(6), version=version+1 "
            + "WHERE id=#{itemId} AND status='PENDING_REVIEW'")
    int approve(@Param("itemId") long itemId);

    @Update("UPDATE items SET status='RETURNED', review_reason=#{reason}, version=version+1 "
            + "WHERE id=#{itemId} AND status='PENDING_REVIEW'")
    int returnItem(@Param("itemId") long itemId, @Param("reason") String reason);

    @Update("UPDATE items SET status='OFF_SHELF', closure_reason='VIOLATION', "
            + "closed_at=NOW(6), version=version+1 WHERE id=#{itemId} AND status='PUBLISHED'")
    int takeDown(@Param("itemId") long itemId);

    /** 违规下架时关闭该物品仍在进行中的认领申请。 */
    @Update("UPDATE claims SET status='CLOSED', reason=#{reason}, version=version+1 "
            + "WHERE item_id=#{itemId} AND status IN ('PENDING','CONFIRMED')")
    int closeClaimsForItem(@Param("itemId") long itemId, @Param("reason") String reason);

    /** 违规下架时清空该物品所有认领的授权并递增版本。 */
    @Update("UPDATE claim_contact_permissions p JOIN claims c ON c.id = p.claim_id "
            + "SET p.phone_visible=0, p.email_visible=0, p.version=p.version+1 "
            + "WHERE c.item_id=#{itemId}")
    int revokePermissionsForItem(@Param("itemId") long itemId);

    @Select("SELECT COUNT(*) FROM review_records WHERE item_id = #{itemId}")
    long countReviewRecords(@Param("itemId") long itemId);

    @Select("SELECT id, item_id, operator_id, action, reason, created_at FROM review_records "
            + "WHERE item_id = #{itemId} ORDER BY created_at DESC, id DESC "
            + "LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> listReviewRecords(@Param("itemId") long itemId,
                                                @Param("limit") int limit,
                                                @Param("offset") int offset);

    @Insert("INSERT INTO review_records (item_id, operator_id, action, reason, item_version) "
            + "VALUES (#{itemId}, #{operatorId}, #{action}, #{reason}, #{itemVersion})")
    int insertReviewRecord(@Param("itemId") long itemId, @Param("operatorId") long operatorId,
                           @Param("action") String action, @Param("reason") String reason,
                           @Param("itemVersion") long itemVersion);
}
