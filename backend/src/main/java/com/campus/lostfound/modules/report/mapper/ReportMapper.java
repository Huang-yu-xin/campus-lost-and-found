package com.campus.lostfound.modules.report.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 举报数据访问：提交、查询、受限会话读取审计与处置。 */
@Mapper
public interface ReportMapper {

    @Select("SELECT id, publisher_id, type, status FROM items WHERE id = #{itemId}")
    Map<String, Object> findItem(@Param("itemId") long itemId);

    @Select("SELECT id, item_id, publisher_id, applicant_id, status, description "
            + "FROM claims WHERE id = #{claimId}")
    Map<String, Object> findClaim(@Param("claimId") long claimId);

    @Select("SELECT id, lost_item_id, publisher_id, provider_id, content "
            + "FROM clues WHERE id = #{clueId}")
    Map<String, Object> findClue(@Param("clueId") long clueId);

    @Insert("INSERT INTO reports (reporter_id, item_id, claim_id, clue_id, reason) "
            + "VALUES (#{reporterId}, #{itemId}, #{claimId}, #{clueId}, #{reason})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertReport(Map<String, Object> report);

    @Select("SELECT r.id, r.reporter_id, r.item_id, r.claim_id, r.clue_id, r.reason, r.status, "
            + "r.action, r.resolution, r.resolver_id, r.version, r.created_at, r.resolved_at "
            + "FROM reports r WHERE r.id = #{reportId}")
    Map<String, Object> findReport(@Param("reportId") long reportId);

    @Select("SELECT COUNT(*) FROM reports WHERE reporter_id = #{reporterId}")
    long countMyReports(@Param("reporterId") long reporterId);

    @Select("SELECT r.id, r.item_id, r.claim_id, r.clue_id, r.reason, r.status, r.action, "
            + "r.resolution, r.version, r.created_at, r.resolved_at FROM reports r "
            + "WHERE r.reporter_id = #{reporterId} ORDER BY r.created_at DESC, r.id DESC "
            + "LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> listMyReports(@Param("reporterId") long reporterId,
                                            @Param("limit") int limit,
                                            @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM reports WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    @Select("SELECT r.id, r.reporter_id, r.item_id, r.claim_id, r.clue_id, r.reason, r.status, "
            + "r.action, r.resolution, r.resolver_id, r.version, r.created_at, r.resolved_at "
            + "FROM reports r WHERE r.status = #{status} "
            + "ORDER BY r.created_at ASC, r.id ASC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> listByStatus(@Param("status") String status,
                                           @Param("limit") int limit,
                                           @Param("offset") int offset);

    /** 处置：只有 PENDING 且版本一致才能写入结论。 */
    @Update("UPDATE reports SET status='RESOLVED', action=#{action}, resolution=#{resolution}, "
            + "resolver_id=#{resolverId}, resolved_at=NOW(6), version=version+1 "
            + "WHERE id=#{reportId} AND status='PENDING' AND version=#{expectedVersion}")
    int resolveReport(Map<String, Object> params);

    @Select("SELECT COUNT(*) FROM claim_messages WHERE claim_id = #{claimId}")
    long countClaimMessages(@Param("claimId") long claimId);

    @Select("SELECT m.id, m.sender_id, m.content, m.created_at, u.nickname AS sender_nickname "
            + "FROM claim_messages m JOIN users u ON u.id = m.sender_id "
            + "WHERE m.claim_id = #{claimId} ORDER BY m.created_at ASC, m.id ASC "
            + "LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> listClaimMessages(@Param("claimId") long claimId,
                                                @Param("limit") int limit,
                                                @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM clue_messages WHERE clue_id = #{clueId}")
    long countClueMessages(@Param("clueId") long clueId);

    @Select("SELECT m.id, m.sender_id, m.content, m.created_at, u.nickname AS sender_nickname "
            + "FROM clue_messages m JOIN users u ON u.id = m.sender_id "
            + "WHERE m.clue_id = #{clueId} ORDER BY m.created_at ASC, m.id ASC "
            + "LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> listClueMessages(@Param("clueId") long clueId,
                                               @Param("limit") int limit,
                                               @Param("offset") int offset);

    /** 驳回与举报直接关联且仍为 PENDING 的认领申请。 */
    @Update("UPDATE claims SET status='REJECTED', reason=#{reason}, version=version+1 "
            + "WHERE id=#{claimId} AND status='PENDING'")
    int rejectPendingClaim(@Param("claimId") long claimId, @Param("reason") String reason);

    @Insert("INSERT INTO report_access_logs (report_id, admin_id, request_id) "
            + "VALUES (#{reportId}, #{adminId}, #{requestId})")
    int insertAccessLog(Map<String, Object> log);
}
