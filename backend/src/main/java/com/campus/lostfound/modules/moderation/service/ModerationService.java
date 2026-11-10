package com.campus.lostfound.modules.moderation.service;

import com.campus.lostfound.common.api.PageData;
import com.campus.lostfound.common.api.Pages;
import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.common.sql.Rows;
import com.campus.lostfound.common.web.RequestIdFilter;
import com.campus.lostfound.modules.item.dto.ItemViews.ItemRecord;
import com.campus.lostfound.modules.item.service.ItemService;
import com.campus.lostfound.modules.item.service.ItemStateLocks;
import com.campus.lostfound.modules.moderation.dto.ReviewRecord;
import com.campus.lostfound.modules.moderation.mapper.ModerationMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理端审核业务规则（FR-20 通过/退回、FR-21 下架）。 */
@Service
public class ModerationService {

    private static final Set<String> LISTABLE = Set.of("PENDING_REVIEW", "RETURNED", "PUBLISHED");

    private final ModerationMapper mapper;
    private final ItemStateLocks stateLocks;
    private final ItemService items;

    public ModerationService(ModerationMapper mapper, ItemStateLocks stateLocks, ItemService items) {
        this.mapper = mapper;
        this.stateLocks = stateLocks;
        this.items = items;
    }

    /** 管理列表包括待审核、已退回及可直接下架的已发布信息，默认待审核，createdAt、id 升序。 */
    public PageData<ItemRecord> list(String status, int page, int pageSize) {
        String s = status == null || status.isBlank() ? "PENDING_REVIEW" : status.trim().toUpperCase();
        if (!LISTABLE.contains(s)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "status 只支持 PENDING_REVIEW / RETURNED / PUBLISHED");
        }
        return items.listByStatus(s, page, pageSize);
    }

    public ItemRecord detail(long itemId) {
        return items.getRecord(itemId);
    }

    /** 审核处置记录，createdAt、id 降序。 */
    @Transactional(readOnly = true)
    public PageData<ReviewRecord> reviewRecords(long itemId, int page, int pageSize) {
        int offset = Pages.offset(page, pageSize);
        if (mapper.findVersion(itemId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "信息不存在");
        }
        long total = mapper.countReviewRecords(itemId);
        List<Map<String, Object>> rows = total == 0 ? List.of() : mapper.listReviewRecords(itemId, pageSize, offset);
        return new PageData<>(rows.stream().map(ModerationService::toRecord).toList(), page, pageSize, total);
    }

    @Transactional
    public ItemRecord approve(long operatorId, long itemId, long expectedVersion) {
        requirePending(itemId, expectedVersion);
        if (mapper.approve(itemId) == 0) {
            throw new BusinessException(ErrorCode.ITEM_STATE_CONFLICT);
        }
        record(operatorId, itemId, "APPROVE", null);
        log(operatorId, itemId, "APPROVE_ITEM", "PENDING_REVIEW", "PUBLISHED");
        return items.getRecord(itemId);
    }

    @Transactional
    public ItemRecord returnItem(long operatorId, long itemId, long expectedVersion, String reason) {
        String text = reason(reason, "退回原因必填且不超过 500 字");
        requirePending(itemId, expectedVersion);
        if (mapper.returnItem(itemId, text) == 0) {
            throw new BusinessException(ErrorCode.ITEM_STATE_CONFLICT);
        }
        record(operatorId, itemId, "RETURN", text);
        log(operatorId, itemId, "RETURN_ITEM", "PENDING_REVIEW", "RETURNED");
        return items.getRecord(itemId);
    }

    @Transactional
    public ItemRecord takeDown(long operatorId, long itemId, long expectedVersion, String reason) {
        String text = reason(reason, "处置原因必填且不超过 500 字");
        requireVersion(stateLocks.lockItem(itemId), expectedVersion);
        if (mapper.takeDown(itemId) == 0) {
            throw new BusinessException(ErrorCode.ITEM_STATE_CONFLICT, "只有已发布的信息可以下架");
        }
        // 违规下架同步关闭进行中的认领并让联系方式授权失效
        mapper.closeClaimsForItem(itemId, text);
        mapper.revokePermissionsForItem(itemId);
        record(operatorId, itemId, "TAKE_DOWN", text);
        log(operatorId, itemId, "TAKE_DOWN_ITEM", "PUBLISHED", "OFF_SHELF");
        return items.getRecord(itemId);
    }

    // ---------------- 内部 ----------------

    private void requirePending(long itemId, long expectedVersion) {
        Map<String, Object> item = stateLocks.lockItem(itemId);
        requireVersion(item, expectedVersion);
        if (!"PENDING_REVIEW".equals(Rows.str(item, "status"))) {
            throw new BusinessException(ErrorCode.ITEM_STATE_CONFLICT, "当前状态不是待审核");
        }
    }

    private static void requireVersion(Map<String, Object> item, long expectedVersion) {
        if (Rows.num(item, "version") != expectedVersion) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
    }

    private static String reason(String reason, String message) {
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty() || text.length() > 500) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return text;
    }

    /** 审核记录保存处置后的物品版本，便于核对每次审核对应的是哪一版内容。 */
    private void record(long operatorId, long itemId, String action, String reason) {
        mapper.insertReviewRecord(itemId, operatorId, action, reason, mapper.findVersion(itemId));
    }

    private void log(long actorId, long itemId, String action, String before, String after) {
        Map<String, Object> log = new HashMap<>();
        log.put("actorId", actorId);
        log.put("entityId", itemId);
        log.put("action", action);
        log.put("beforeStatus", before);
        log.put("afterStatus", after);
        log.put("requestId", RequestIdFilter.current());
        mapper.insertOperationLog(log);
    }

    private static ReviewRecord toRecord(Map<String, Object> row) {
        return new ReviewRecord(Rows.id(row, "id"), Rows.id(row, "item_id"), Rows.str(row, "action"),
                Rows.str(row, "reason"), Rows.id(row, "operator_id"), Rows.instant(row, "created_at"));
    }
}
