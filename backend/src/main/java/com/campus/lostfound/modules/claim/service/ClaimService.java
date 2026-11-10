package com.campus.lostfound.modules.claim.service;

import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.common.web.RequestIdFilter;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.ClaimDetailView;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.ClaimView;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.ItemBrief;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.UserBrief;
import com.campus.lostfound.modules.claim.mapper.ClaimMapper;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认领模块业务规则。对应 FR-15（提交）、FR-16（确认）与认领详情查询。
 *
 * <p>并发保护分三层：应用层前置判断给出精确错误码；SQL 条件更新拦住同一记录的并发写；
 * {@code uq_claim_winner} 生成列唯一键兜底，保证一条物品最多一个「已确认」。</p>
 */
@Service
public class ClaimService {

    private final ClaimMapper mapper;

    public ClaimService(ClaimMapper mapper) {
        this.mapper = mapper;
    }

    /** FR-15 提交认领申请。 */
    @Transactional
    public ClaimView createClaim(long userId, long itemId, String description) {
        String text = description == null ? "" : description.trim();
        if (text.length() < 10 || text.length() > 500) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "物品特征说明需为 10~500 字");
        }
        Map<String, Object> item = mapper.findItem(itemId);
        if (item == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "物品不存在");
        }
        if (!"FOUND".equals(str(item, "type"))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "只能对招领信息提交认领申请");
        }
        if (!"PUBLISHED".equals(str(item, "status"))) {
            throw new BusinessException(ErrorCode.ITEM_STATE_CONFLICT, "该物品当前不可认领");
        }
        long publisherId = num(item, "publisher_id");
        if (publisherId == userId) {
            throw new BusinessException(ErrorCode.SELF_OPERATION_NOT_ALLOWED, "不能认领自己发布的信息");
        }
        if (mapper.findApplicantClaim(itemId, userId) != null) {
            throw new BusinessException(ErrorCode.CLAIM_ALREADY_EXISTS);
        }
        if (mapper.findWinner(itemId) != null) {
            throw new BusinessException(ErrorCode.ITEM_ALREADY_CLAIMED);
        }

        Map<String, Object> row = new HashMap<>();
        row.put("itemId", itemId);
        row.put("publisherId", publisherId);
        row.put("applicantId", userId);
        row.put("description", text);
        try {
            mapper.insertClaim(row);
        } catch (DuplicateKeyException e) {
            // uq_claim_once 或 uq_claim_winner 冲突，均为可预期的业务结果
            throw new BusinessException(ErrorCode.CLAIM_ALREADY_EXISTS);
        }
        long claimId = num(row, "id");
        writeLog(userId, "CLAIM", claimId, "CREATE_CLAIM", null, "PENDING");
        return toView(mapper.findClaim(claimId));
    }

    /** 认领私有详情：仅申请人、发布人可见；其他角色（含无上下文的管理员）一律 404。 */
    public ClaimDetailView getClaim(long userId, long claimId) {
        Map<String, Object> claim = mapper.findClaim(claimId);
        if (claim == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "认领申请不存在");
        }
        long applicantId = num(claim, "applicant_id");
        long publisherId = num(claim, "publisher_id");
        if (userId != applicantId && userId != publisherId) {
            // 不区分「不存在」与「无权查看」，避免探测
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "认领申请不存在");
        }
        Map<String, Object> item = mapper.findItemBrief(num(claim, "item_id"));
        return new ClaimDetailView(toView(claim), toItem(item));
    }

    /** FR-16 发布者确认认领。 */
    @Transactional
    public ClaimView confirmClaim(long userId, long claimId) {
        Map<String, Object> claim = mapper.findClaim(claimId);
        if (claim == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "认领申请不存在");
        }
        if (num(claim, "publisher_id") != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有信息发布者可以确认认领");
        }
        String before = str(claim, "status");
        if (!"PENDING".equals(before)) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CONFLICT);
        }
        if (mapper.findWinner(num(claim, "item_id")) != null) {
            throw new BusinessException(ErrorCode.ITEM_ALREADY_CLAIMED);
        }
        if (mapper.markConfirmed(claimId) == 0) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CONFLICT);
        }
        if (mapper.bindAcceptedClaim(num(claim, "item_id"), claimId) == 0) {
            throw new BusinessException(ErrorCode.ITEM_STATE_CONFLICT, "物品状态已变化，无法确认");
        }
        writeLog(userId, "CLAIM", claimId, "CONFIRM_CLAIM", before, "CONFIRMED");
        return toView(mapper.findClaim(claimId));
    }

    /** FR-17 发布者驳回认领，原因必填。 */
    @Transactional
    public ClaimView rejectClaim(long userId, long claimId, String reason) {
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty() || text.length() > 500) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "驳回原因必填且不超过 500 字");
        }
        Map<String, Object> claim = mapper.findClaim(claimId);
        if (claim == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "认领申请不存在");
        }
        if (num(claim, "publisher_id") != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有信息发布者可以驳回认领");
        }
        String before = str(claim, "status");
        if (!"PENDING".equals(before)) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CONFLICT);
        }
        if (mapper.markRejected(claimId, text) == 0) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CONFLICT);
        }
        writeLog(userId, "CLAIM", claimId, "REJECT_CLAIM", before, "REJECTED");
        return toView(mapper.findClaim(claimId));
    }

    /** FR-18 发布者确认交接完成：申请置完成、物品归档，两步同事务。 */
    @Transactional
    public ClaimView completeClaim(long userId, long claimId) {
        Map<String, Object> claim = mapper.findClaim(claimId);
        if (claim == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "认领申请不存在");
        }
        if (num(claim, "publisher_id") != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有信息发布者可以确认交接完成");
        }
        String before = str(claim, "status");
        if (!"CONFIRMED".equals(before)) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CONFLICT);
        }
        long itemId = num(claim, "item_id");
        if (mapper.markCompleted(claimId) == 0) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CONFLICT);
        }
        if (mapper.archiveItemForClaim(itemId, claimId) == 0) {
            // 物品状态已被改动，整笔回滚，不留「申请已完成、物品仍在列表」的半状态
            throw new BusinessException(ErrorCode.ITEM_STATE_CONFLICT, "物品状态已变化，无法归档");
        }
        writeLog(userId, "CLAIM", claimId, "COMPLETE_CLAIM", before, "COMPLETED");
        return toView(mapper.findClaim(claimId));
    }

    /** 发布者视角：我发布的信息收到的认领申请列表。 */
    public java.util.List<java.util.Map<String, Object>> receivedClaims(long userId) {
        java.util.List<java.util.Map<String, Object>> rows = mapper.listReceivedClaims(userId);
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (Map<String, Object> r : rows) {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", num(r, "id"));
            row.put("itemId", num(r, "item_id"));
            row.put("itemTitle", str(r, "item_title"));
            row.put("itemStatus", str(r, "item_status"));
            row.put("applicantNickname", str(r, "applicant_nickname"));
            row.put("status", str(r, "status"));
            row.put("reason", r.get("reason") == null ? null : str(r, "reason"));
            row.put("createdAt", time(r, "created_at"));
            result.add(row);
        }
        return result;
    }

    // ---------------- 内部工具 ----------------
    private void writeLog(long actorId, String entityType, long entityId, String action,
                          String before, String after) {
        Map<String, Object> log = new HashMap<>();
        log.put("actorId", actorId);
        log.put("entityType", entityType);
        log.put("entityId", entityId);
        log.put("action", action);
        log.put("beforeStatus", before);
        log.put("afterStatus", after);
        log.put("requestId", RequestIdFilter.current());
        mapper.insertOperationLog(log);
    }

    private static ClaimView toView(Map<String, Object> c) {
        return new ClaimView(
                num(c, "id"),
                num(c, "item_id"),
                new UserBrief(num(c, "applicant_id"), str(c, "applicant_nickname")),
                new UserBrief(num(c, "publisher_id"), str(c, "publisher_nickname")),
                str(c, "description"),
                str(c, "status"),
                c.get("reason") == null ? null : str(c, "reason"),
                c.get("version") == null ? 1L : num(c, "version"),
                time(c, "created_at"),
                time(c, "updated_at"));
    }

    private static ItemBrief toItem(Map<String, Object> i) {
        if (i == null) {
            return null;
        }
        return new ItemBrief(
                num(i, "id"), str(i, "type"), str(i, "category"), str(i, "location"),
                str(i, "title"), str(i, "description"), str(i, "status"),
                time(i, "occurred_date"), time(i, "published_at"), time(i, "created_at"));
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static long num(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static LocalDateTime time(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (v instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate().atStartOfDay();
        }
        return null;
    }
}
