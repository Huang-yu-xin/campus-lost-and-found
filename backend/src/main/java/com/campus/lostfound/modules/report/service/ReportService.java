package com.campus.lostfound.modules.report.service;

import com.campus.lostfound.common.api.PageData;
import com.campus.lostfound.common.api.Pages;
import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.common.sql.Rows;
import com.campus.lostfound.common.web.RequestIdFilter;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.MessageView;
import com.campus.lostfound.modules.claim.service.ClaimMessageService;
import com.campus.lostfound.modules.moderation.service.ModerationService;
import com.campus.lostfound.modules.item.service.ItemStateLocks;
import com.campus.lostfound.modules.report.dto.ReportDtos.CreateReportRequest;
import com.campus.lostfound.modules.report.dto.ReportDtos.ReportConversation;
import com.campus.lostfound.modules.report.dto.ReportDtos.ReportView;
import com.campus.lostfound.modules.report.dto.ReportDtos.ResolveReportRequest;
import com.campus.lostfound.modules.report.mapper.ReportMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 举报（FR-22、FR-27，选做）。举报可绑定一个认领或线索上下文，提交人必须是该会话参与者；
 * 管理员只能经待处理举报的 conversation 接口查看会话，并留下访问审计。
 */
@Service
public class ReportService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RESOLVED = "RESOLVED";

    private final ReportMapper mapper;
    private final ModerationService moderationService;
    private final ItemStateLocks stateLocks;

    public ReportService(ReportMapper mapper, ModerationService moderationService, ItemStateLocks stateLocks) {
        this.mapper = mapper;
        this.moderationService = moderationService;
        this.stateLocks = stateLocks;
    }

    /** 提交举报：普通举报只针对公开物品；关联会话举报允许物品已归档但须属于该会话。 */
    @Transactional
    public ReportView create(long userId, CreateReportRequest request) {
        if (request == null || request.itemId() == null) {
            throw new BusinessException(ErrorCode.REPORT_CONTEXT_INVALID, "举报必须指定物品");
        }
        String reason = validateReason(request.reason(), "举报理由");
        if (request.claimId() != null && request.clueId() != null) {
            throw new BusinessException(ErrorCode.REPORT_CONTEXT_INVALID, "认领与线索上下文只能选择一个");
        }
        // 格式已由 @Pattern 校验，这里只做类型转换
        long itemId = Long.parseLong(request.itemId());
        Long claimId = request.claimId() == null ? null : Long.valueOf(request.claimId());
        Long clueId = request.clueId() == null ? null : Long.valueOf(request.clueId());
        Map<String, Object> item = mapper.findItem(itemId);
        if (item == null) {
            throw new BusinessException(ErrorCode.REPORT_CONTEXT_INVALID, "被举报物品不存在");
        }

        if (claimId != null) {
            Map<String, Object> claim = mapper.findClaim(claimId);
            if (claim == null || Rows.num(claim, "item_id") != itemId) {
                throw new BusinessException(ErrorCode.REPORT_CONTEXT_INVALID, "认领不属于该物品");
            }
            long applicant = Rows.num(claim, "applicant_id");
            long publisher = Rows.num(claim, "publisher_id");
            if (userId != applicant && userId != publisher) {
                throw new BusinessException(ErrorCode.REPORT_CONTEXT_INVALID, "只能举报自己参与的认领会话");
            }
        } else if (clueId != null) {
            Map<String, Object> clue = mapper.findClue(clueId);
            if (clue == null || Rows.num(clue, "lost_item_id") != itemId) {
                throw new BusinessException(ErrorCode.REPORT_CONTEXT_INVALID, "线索不属于该物品");
            }
            long provider = Rows.num(clue, "provider_id");
            long publisher = Rows.num(clue, "publisher_id");
            if (userId != provider && userId != publisher) {
                throw new BusinessException(ErrorCode.REPORT_CONTEXT_INVALID, "只能举报自己参与的线索会话");
            }
        } else if (!"PUBLISHED".equals(Rows.str(item, "status"))) {
            throw new BusinessException(ErrorCode.REPORT_CONTEXT_INVALID, "只能举报当前公开可见的信息");
        }

        Map<String, Object> row = new HashMap<>();
        row.put("reporterId", userId);
        row.put("itemId", itemId);
        row.put("claimId", claimId);
        row.put("clueId", clueId);
        row.put("reason", reason);
        mapper.insertReport(row);
        long reportId = Rows.num(row, "id");
        writeLog(userId, reportId, "CREATE_REPORT", null, STATUS_PENDING);
        return toView(mapper.findReport(reportId));
    }

    /** 我提交的举报，只含自己的记录与处理结论。 */
    public PageData<ReportView> listMine(long userId, int page, int pageSize) {
        int normalizedPage = Pages.page(page);
        int normalizedSize = Pages.pageSize(pageSize);
        long total = mapper.countMyReports(userId);
        List<ReportView> items = new ArrayList<>();
        for (Map<String, Object> row : mapper.listMyReports(
                userId, normalizedSize, Pages.offset(normalizedPage, normalizedSize))) {
            items.add(toView(row));
        }
        return new PageData<>(items, normalizedPage, normalizedSize, total);
    }

    /** 管理员举报列表，默认待处理，按提交时间正序。 */
    public PageData<ReportView> listAdmin(String status, int page, int pageSize) {
        String normalizedStatus = status == null || status.isBlank()
                ? STATUS_PENDING
                : status.trim().toUpperCase();
        if (!STATUS_PENDING.equals(normalizedStatus) && !STATUS_RESOLVED.equals(normalizedStatus)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "status 只支持 PENDING / RESOLVED");
        }
        int normalizedPage = Pages.page(page);
        int normalizedSize = Pages.pageSize(pageSize);
        long total = mapper.countByStatus(normalizedStatus);
        List<ReportView> items = new ArrayList<>();
        for (Map<String, Object> row : mapper.listByStatus(
                normalizedStatus, normalizedSize, Pages.offset(normalizedPage, normalizedSize))) {
            items.add(toView(row));
        }
        return new PageData<>(items, normalizedPage, normalizedSize, total);
    }

    public ReportView getAdmin(long reportId) {
        return toView(requireReport(reportId));
    }

    /**
     * 管理员经待处理举报读取关联会话：无上下文 404，已处理 409，并记录访问审计。
     * 只返回凭证正文与留言，不返回联系方式。
     */
    @Transactional
    public ReportConversation conversation(long adminId, long reportId, int page, int pageSize) {
        Map<String, Object> report = requireReport(reportId);
        if (!STATUS_PENDING.equals(Rows.str(report, "status"))) {
            throw new BusinessException(ErrorCode.REPORT_STATE_CONFLICT);
        }
        Long claimId = Rows.longOrNull(report, "claim_id");
        Long clueId = Rows.longOrNull(report, "clue_id");
        int normalizedPage = Pages.page(page);
        int normalizedSize = Pages.pageSize(pageSize);

        String kind;
        long contextId;
        String evidenceText;
        long total;
        List<Map<String, Object>> rows;
        if (claimId != null) {
            Map<String, Object> claim = mapper.findClaim(claimId);
            if (claim == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "举报关联的会话不存在");
            }
            kind = "CLAIM";
            contextId = claimId;
            evidenceText = Rows.str(claim, "description");
            total = mapper.countClaimMessages(claimId);
            rows = mapper.listClaimMessages(
                    claimId, normalizedSize, Pages.offset(normalizedPage, normalizedSize));
        } else if (clueId != null) {
            Map<String, Object> clue = mapper.findClue(clueId);
            if (clue == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "举报关联的会话不存在");
            }
            kind = "CLUE";
            contextId = clueId;
            evidenceText = Rows.str(clue, "content");
            total = mapper.countClueMessages(clueId);
            rows = mapper.listClueMessages(
                    clueId, normalizedSize, Pages.offset(normalizedPage, normalizedSize));
        } else {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "该举报没有关联会话");
        }

        Map<String, Object> accessLog = new HashMap<>();
        accessLog.put("reportId", reportId);
        accessLog.put("adminId", adminId);
        accessLog.put("requestId", RequestIdFilter.current());
        mapper.insertAccessLog(accessLog);

        List<MessageView> items = rows.stream().map(ClaimMessageService::toView).toList();
        return new ReportConversation(kind, Long.toString(contextId), evidenceText, items,
                normalizedPage, normalizedSize, total);
    }

    /** 处理举报：业务处置与举报结论同一事务；任一步失败全部回滚。 */
    @Transactional
    public ReportView resolve(long adminId, long reportId, ResolveReportRequest request) {
        if (request == null || request.action() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "处置动作必填");
        }
        String action = request.action().trim().toUpperCase();
        if (!"KEEP".equals(action) && !"TAKE_DOWN".equals(action)
                && !"REJECT_PENDING_CLAIM".equals(action)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "处置动作不合法");
        }
        String reason = validateReason(request.reason(), "处理结论");
        if (request.expectedVersion() == null || request.expectedVersion() < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "expectedVersion 不合法");
        }

        Map<String, Object> report = requireReport(reportId);
        if (!STATUS_PENDING.equals(Rows.str(report, "status"))) {
            throw new BusinessException(ErrorCode.REPORT_STATE_CONFLICT);
        }
        if (Rows.num(report, "version") != request.expectedVersion()) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        long itemId = Rows.num(report, "item_id");
        Map<String, Object> lockedItem = stateLocks.lockItem(itemId);

        switch (action) {
            case "KEEP" -> {
                // 仅写结论，不改动物品或认领
            }
            case "TAKE_DOWN" -> moderationService.takeDown(adminId, itemId, Rows.num(lockedItem, "version"), reason);
            case "REJECT_PENDING_CLAIM" -> {
                Long claimId = Rows.longOrNull(report, "claim_id");
                if (claimId == null) {
                    throw new BusinessException(ErrorCode.REPORT_CONTEXT_INVALID, "举报未关联认领申请");
                }
                Map<String, Object> claim = stateLocks.lockClaim(claimId);
                if (claim == null) {
                    throw new BusinessException(ErrorCode.REPORT_CONTEXT_INVALID, "举报关联的认领不存在");
                }
                if (!"PENDING".equals(Rows.str(claim, "status"))) {
                    throw new BusinessException(ErrorCode.CLAIM_STATE_CONFLICT);
                }
                if (mapper.rejectPendingClaim(claimId, reason) == 0) {
                    throw new BusinessException(ErrorCode.CLAIM_STATE_CONFLICT);
                }
            }
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "处置动作不合法");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("reportId", reportId);
        params.put("action", action);
        params.put("resolution", reason);
        params.put("resolverId", adminId);
        params.put("expectedVersion", request.expectedVersion());
        if (mapper.resolveReport(params) == 0) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        writeLog(adminId, reportId, "RESOLVE_REPORT", STATUS_PENDING, STATUS_RESOLVED);
        return toView(mapper.findReport(reportId));
    }

    // ---------------- 内部工具 ----------------

    private Map<String, Object> requireReport(long reportId) {
        Map<String, Object> report = mapper.findReport(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "举报不存在");
        }
        return report;
    }

    private static String validateReason(String raw, String label) {
        String text = raw == null ? "" : raw.trim();
        int length = text.codePointCount(0, text.length());
        if (length < 1 || length > 500) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, label + "需为 1~500 字");
        }
        return text;
    }

    private void writeLog(long actorId, long reportId, String action, String before, String after) {
        Map<String, Object> log = new HashMap<>();
        log.put("actorId", actorId);
        log.put("entityId", reportId);
        log.put("action", action);
        log.put("beforeStatus", before);
        log.put("afterStatus", after);
        log.put("requestId", RequestIdFilter.current());
        mapper.insertOperationLog(log);
    }

    private static ReportView toView(Map<String, Object> row) {
        return new ReportView(
                Rows.id(row, "id"),
                Rows.id(row, "item_id"),
                Rows.idOrNull(row, "claim_id"),
                Rows.idOrNull(row, "clue_id"),
                Rows.str(row, "reason"),
                Rows.str(row, "status"),
                Rows.str(row, "action"),
                Rows.str(row, "resolution"),
                Rows.num(row, "version"),
                Rows.instant(row, "created_at"),
                Rows.instant(row, "resolved_at"));
    }
}
