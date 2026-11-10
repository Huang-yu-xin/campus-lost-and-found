package com.campus.lostfound.modules.claim.service;

import com.campus.lostfound.common.api.PageData;
import com.campus.lostfound.common.api.Pages;
import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.common.sql.Rows;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.MessageView;
import com.campus.lostfound.modules.claim.dto.ClaimDtos.UserBrief;
import com.campus.lostfound.modules.claim.mapper.ClaimMapper;
import com.campus.lostfound.modules.item.service.ItemStateLocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认领私有留言（FR-19）。只有申请双方能读写；
 * PENDING/CONFIRMED 且物品仍 PUBLISHED 时可发送，终态保留历史但拒绝新增。
 */
@Service
public class ClaimMessageService {

    private static final int MAX_CONTENT_CODE_POINTS = 1000;

    /** 首版用常见手机号/邮箱格式检测，提示走联系方式授权，不声称能识别所有变体。 */
    private static final Pattern CONTACT_PATTERN = Pattern.compile(
            "(?<!\\d)1[3-9]\\d{9}(?!\\d)|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private final ClaimMapper mapper;
    private final ItemStateLocks stateLocks;

    public ClaimMessageService(ClaimMapper mapper, ItemStateLocks stateLocks) {
        this.mapper = mapper;
        this.stateLocks = stateLocks;
    }

    /** 查询留言，createdAt、id 升序分页。 */
    public PageData<MessageView> list(long userId, long claimId, int page, int pageSize) {
        requireParticipant(userId, claimId);
        int normalizedPage = Pages.page(page);
        int normalizedSize = Pages.pageSize(pageSize);
        long total = mapper.countMessages(claimId);
        List<MessageView> items = new ArrayList<>();
        if (total > 0) {
            for (Map<String, Object> row : mapper.listMessages(
                    claimId, normalizedSize, Pages.offset(normalizedPage, normalizedSize))) {
                items.add(toView(row));
            }
        }
        return new PageData<>(items, normalizedPage, normalizedSize, total);
    }

    /** 发送留言；终态或物品已离开公开状态时拒绝发送。 */
    @Transactional
    public MessageView send(long userId, long claimId, String content) {
        Map<String, Object> claim = requireParticipant(userId, claimId, true);
        String status = Rows.str(claim, "status");
        if (!"PENDING".equals(status) && !"CONFIRMED".equals(status)) {
            throw new BusinessException(ErrorCode.CONVERSATION_CLOSED);
        }
        if (!"PUBLISHED".equals(Rows.str(claim, "item_status"))) {
            throw new BusinessException(ErrorCode.CONVERSATION_CLOSED);
        }

        String text = content == null ? "" : content.trim();
        int length = text.codePointCount(0, text.length());
        if (length < 1 || length > MAX_CONTENT_CODE_POINTS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "留言需为 1~1000 字");
        }
        if (CONTACT_PATTERN.matcher(text).find()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "留言不能包含手机号或邮箱，请在授权后通过联系方式查看");
        }

        Map<String, Object> row = new HashMap<>();
        row.put("claimId", claimId);
        row.put("senderId", userId);
        row.put("content", text);
        mapper.insertMessage(row);
        return toView(mapper.findMessage(Rows.num(row, "id")));
    }

    private Map<String, Object> requireParticipant(long userId, long claimId) {
        return requireParticipant(userId, claimId, false);
    }

    private Map<String, Object> requireParticipant(long userId, long claimId, boolean forWrite) {
        Map<String, Object> claim = forWrite ? stateLocks.lockClaim(claimId) : mapper.findClaimContext(claimId);
        if (claim == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "认领申请不存在");
        }
        long applicantId = Rows.num(claim, "applicant_id");
        long publisherId = Rows.num(claim, "publisher_id");
        if (userId != applicantId && userId != publisherId) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "认领申请不存在");
        }
        return claim;
    }

    /** 认领会话与举报受限读取共用同一留言视图。 */
    public static MessageView toView(Map<String, Object> row) {
        return new MessageView(
                Rows.id(row, "id"),
                new UserBrief(Rows.id(row, "sender_id"), Rows.str(row, "sender_nickname")),
                Rows.str(row, "content"),
                Rows.instant(row, "created_at"));
    }
}
