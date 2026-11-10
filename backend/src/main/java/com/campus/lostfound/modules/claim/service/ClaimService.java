package com.campus.lostfound.modules.claim.service;

import static com.campus.lostfound.common.sql.Rows.num;
import static com.campus.lostfound.common.sql.Rows.str;

import com.campus.lostfound.common.api.PageData;
import com.campus.lostfound.common.api.Pages;
import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.common.sql.Rows;
import com.campus.lostfound.common.web.RequestIdFilter;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.ClaimDetailView;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.ClaimView;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.UserBrief;
import com.campus.lostfound.modules.claim.mapper.ClaimMapper;
import com.campus.lostfound.modules.item.service.ItemService;
import com.campus.lostfound.modules.item.service.ItemStateLocks;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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

    private static final Set<String> STATUSES = Set.of("PENDING", "CONFIRMED", "REJECTED", "COMPLETED", "CLOSED");

    private final ClaimMapper mapper;
    private final ContactPermissionService contactPermissionService;
    private final ItemStateLocks stateLocks;
    private final ItemService items;

    public ClaimService(ClaimMapper mapper, ContactPermissionService contactPermissionService,
            ItemStateLocks stateLocks, ItemService items) {
        this.mapper = mapper;
        this.contactPermissionService = contactPermissionService;
        this.stateLocks = stateLocks;
        this.items = items;
    }

    /** 我申请的 / 我收到的认领，createdAt、id 降序；itemId 只能缩小本人可见范围。 */
    @Transactional(readOnly = true)
    public PageData<ClaimView> personal(
            long userId, boolean received, String status, Long itemId, int page, int pageSize) {
        int offset = Pages.offset(page, pageSize);
        if ((status != null && !STATUSES.contains(status)) || (itemId != null && itemId <= 0)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        var rows = mapper.personalRows(userId, received, status, itemId, pageSize, offset);
        return new PageData<>(rows.stream().map(ClaimService::toView).toList(), page, pageSize,
                mapper.countPersonal(userId, received, status, itemId));
    }

    /** FR-15 提交认领申请。 */
    @Transactional
    public ClaimView createClaim(long userId, long itemId, String description) {
        String text = description == null ? "" : description.trim();
        if (text.length() < 10 || text.length() > 500) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "物品特征说明需为 10~500 字");
        }
        Map<String, Object> item = stateLocks.lockItem(itemId);
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
    @Transactional(readOnly = true)
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
        // 参与者校验已在上面完成，物品按合同返回完整 ItemRecord
        return new ClaimDetailView(toView(claim), items.getRecord(num(claim, "item_id")));
    }

    /** FR-16 发布者确认认领。 */
    @Transactional
    public ClaimView confirmClaim(long userId, long claimId, long expectedVersion) {
        Map<String, Object> claim = stateLocks.lockClaim(claimId);
        if (claim == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "认领申请不存在");
        }
        if (num(claim, "publisher_id") != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有信息发布者可以确认认领");
        }
        requireVersion(claim, expectedVersion);
        String before = str(claim, "status");
        if (!"PENDING".equals(before)) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CONFLICT);
        }
        if (!"PUBLISHED".equals(str(claim, "item_status"))) {
            throw new BusinessException(ErrorCode.ITEM_STATE_CONFLICT);
        }
        if (claim.get("item_accepted_claim_id") != null) {
            throw new BusinessException(ErrorCode.ITEM_ALREADY_CLAIMED);
        }
        if (mapper.markConfirmed(claimId) == 0) {
            throw new BusinessException(ErrorCode.CLAIM_STATE_CONFLICT);
        }
        if (mapper.bindAcceptedClaim(num(claim, "item_id"), claimId) == 0) {
            throw new BusinessException(ErrorCode.ITEM_STATE_CONFLICT, "物品状态已变化，无法确认");
        }
        // 合同要求：唯一认领人确定后，同物品其他 PENDING 申请关闭
        mapper.closeOtherPendingClaims(num(claim, "item_id"), claimId, "OTHER_ACCEPTED");
        writeLog(userId, "CLAIM", claimId, "CONFIRM_CLAIM", before, "CONFIRMED");
        return toView(mapper.findClaim(claimId));
    }

    /** FR-17 发布者驳回认领，原因必填。 */
    @Transactional
    public ClaimView rejectClaim(long userId, long claimId, long expectedVersion, String reason) {
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty() || text.length() > 500) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "驳回原因必填且不超过 500 字");
        }
        Map<String, Object> claim = stateLocks.lockClaim(claimId);
        if (claim == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "认领申请不存在");
        }
        if (num(claim, "publisher_id") != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有信息发布者可以驳回认领");
        }
        requireVersion(claim, expectedVersion);
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
    public ClaimView completeClaim(long userId, long claimId, long expectedVersion) {
        Map<String, Object> claim = stateLocks.lockClaim(claimId);
        if (claim == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "认领申请不存在");
        }
        if (num(claim, "publisher_id") != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有信息发布者可以确认交接完成");
        }
        requireVersion(claim, expectedVersion);
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
        // 交接完成后授权失效；即使旧页面仍持有开关，也必须按业务状态拒绝读取
        contactPermissionService.revokeAllForClaim(claimId);
        writeLog(userId, "CLAIM", claimId, "COMPLETE_CLAIM", before, "COMPLETED");
        return toView(mapper.findClaim(claimId));
    }

    // ---------------- 内部工具 ----------------
    private static void requireVersion(Map<String, Object> claim, long expectedVersion) {
        if (num(claim, "version") != expectedVersion) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
    }

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
                Rows.id(c, "id"),
                Rows.id(c, "item_id"),
                new UserBrief(Rows.id(c, "applicant_id"), str(c, "applicant_nickname")),
                new UserBrief(Rows.id(c, "publisher_id"), str(c, "publisher_nickname")),
                str(c, "description"),
                str(c, "status"),
                str(c, "reason"),
                c.get("version") == null ? 1L : num(c, "version"),
                Rows.instant(c, "created_at"),
                Rows.instant(c, "updated_at"));
    }
}
