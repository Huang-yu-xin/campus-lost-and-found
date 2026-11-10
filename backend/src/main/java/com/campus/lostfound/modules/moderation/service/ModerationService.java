package com.campus.lostfound.modules.moderation.service;

import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.common.web.RequestIdFilter;
import com.campus.lostfound.common.sql.Rows;
import com.campus.lostfound.modules.moderation.mapper.ModerationMapper;
import com.campus.lostfound.modules.item.service.ItemStateLocks;
import com.campus.lostfound.modules.item.service.ImageService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理端审核业务规则（FR-20 通过/退回、FR-21 下架）。 */
@Service
public class ModerationService {

    private final ModerationMapper mapper;
    private final ItemStateLocks stateLocks;
    private final ImageService images;

    public ModerationService(ModerationMapper mapper, ItemStateLocks stateLocks, ImageService images) {
        this.mapper = mapper;
        this.stateLocks = stateLocks;
        this.images = images;
    }

    /** 管理列表包括待审核、已退回及可直接下架的已发布信息。 */
    public List<Map<String, Object>> list(String status) {
        String s = status == null || status.isBlank() ? "PENDING_REVIEW" : status.trim().toUpperCase();
        if (!java.util.Set.of("PENDING_REVIEW", "RETURNED", "PUBLISHED").contains(s)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "status 只支持 PENDING_REVIEW / RETURNED / PUBLISHED");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> r : mapper.listByStatus(s)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.get("id"));
            row.put("title", r.get("title"));
            row.put("type", r.get("type"));
            row.put("category", r.get("category"));
            row.put("location", r.get("location"));
            row.put("status", r.get("status"));
            row.put("version", r.get("version"));
            row.put("reviewReason", r.get("review_reason"));
            row.put("publisherNickname", r.get("publisher_nickname"));
            row.put("createdAt", r.get("created_at"));
            result.add(row);
        }
        return result;
    }

    public Map<String, Object> detail(long itemId) {
        Map<String, Object> r = mapper.findItem(itemId);
        if (r == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "信息不存在");
        }
        Map<String, Object> row = new LinkedHashMap<>(r);
        row.put("reviewRecords", reviewRecords(itemId));
        row.put("images", images.forItems(List.of(itemId)).getOrDefault(itemId, List.of()));
        return row;
    }

    public List<Map<String, Object>> reviewRecords(long itemId) {
        return mapper.listReviewRecords(itemId);
    }

    @Transactional
    public Map<String, Object> approve(long operatorId, long itemId, long expectedVersion) {
        Map<String, Object> item = requirePending(itemId, expectedVersion);
        if (mapper.approve(itemId) == 0) {
            throw new BusinessException(ErrorCode.ITEM_STATE_CONFLICT);
        }
        record(operatorId, itemId, "APPROVE", null);
        log(operatorId, itemId, "APPROVE_ITEM", "PENDING_REVIEW", "PUBLISHED");
        return detail(itemId);
    }

    @Transactional
    public Map<String, Object> returnItem(long operatorId, long itemId, long expectedVersion, String reason) {
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty() || text.length() > 500) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "退回原因必填且不超过 500 字");
        }
        Map<String, Object> item = requirePending(itemId, expectedVersion);
        if (mapper.returnItem(itemId, text) == 0) {
            throw new BusinessException(ErrorCode.ITEM_STATE_CONFLICT);
        }
        record(operatorId, itemId, "RETURN", text);
        logText(operatorId, itemId, "RETURN_ITEM", "PENDING_REVIEW", "RETURNED", text);
        return detail(itemId);
    }

    @Transactional
    public Map<String, Object> takeDown(long operatorId, long itemId, long expectedVersion, String reason) {
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty() || text.length() > 500) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "处置原因必填且不超过 500 字");
        }
        Map<String, Object> locked = stateLocks.lockItem(itemId);
        requireVersion(locked, expectedVersion);
        Map<String, Object> item = mapper.findItem(itemId);
        if (item == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "信息不存在");
        }
        if (mapper.takeDown(itemId) == 0) {
            throw new BusinessException(ErrorCode.ITEM_STATE_CONFLICT, "只有已发布的信息可以下架");
        }
        // 违规下架同步关闭进行中的认领并让联系方式授权失效
        mapper.closeClaimsForItem(itemId, text);
        mapper.revokePermissionsForItem(itemId);
        record(operatorId, itemId, "TAKE_DOWN", text);
        logText(operatorId, itemId, "TAKE_DOWN_ITEM", "PUBLISHED", "OFF_SHELF", text);
        return detail(itemId);
    }

    // ---------------- 内部 ----------------

    private Map<String, Object> requirePending(long itemId, long expectedVersion) {
        Map<String, Object> item = stateLocks.lockItem(itemId);
        requireVersion(item, expectedVersion);
        if (item == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "信息不存在");
        }
        if (!"PENDING_REVIEW".equals(String.valueOf(item.get("status")))) {
            throw new BusinessException(ErrorCode.ITEM_STATE_CONFLICT, "当前状态不是待审核");
        }
        return item;
    }

    private static void requireVersion(Map<String, Object> item, long expectedVersion) {
        if (Rows.num(item, "version") != expectedVersion) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
    }

    private void record(long operatorId, long itemId, String action, String reason) {
        Map<String, Object> rec = new HashMap<>();
        rec.put("itemId", itemId);
        rec.put("operatorId", operatorId);
        rec.put("action", action);
        rec.put("reason", reason);
        rec.put("itemVersion", mapper.findItem(itemId).get("version"));
        mapper.insertReviewRecord(rec);
    }

    private void log(long actorId, long itemId, String action, String before, String after) {
        logText(actorId, itemId, action, before, after, null);
    }

    private void logText(long actorId, long itemId, String action, String before, String after,
                         String reason) {
        Map<String, Object> log = new HashMap<>();
        log.put("actorId", actorId);
        log.put("entityId", itemId);
        log.put("action", reason == null ? action : action);
        log.put("beforeStatus", before);
        log.put("afterStatus", after);
        log.put("requestId", RequestIdFilter.current());
        mapper.insertOperationLog(log);
    }
}
