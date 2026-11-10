package com.campus.lostfound.modules.clue;

import com.campus.lostfound.common.api.PageData;
import com.campus.lostfound.common.api.Pages;
import com.campus.lostfound.common.audit.AuditService;
import com.campus.lostfound.common.audit.AuditService.Entity;
import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.common.sql.Rows;
import com.campus.lostfound.modules.item.dto.ItemViews.ItemRecord;
import com.campus.lostfound.modules.item.dto.ItemViews.Publisher;
import com.campus.lostfound.modules.item.service.ItemService;
import com.campus.lostfound.modules.item.service.ItemStateLocks;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 线索仅对提供者及发布人可见；所有写入与物品归档、下架共用同一行锁。 */
@Service
public class ClueService {
    private static final java.util.regex.Pattern CONTACT = java.util.regex.Pattern.compile(
            "(?i)(?<![0-9])1[3-9][0-9]{9}(?![0-9])|[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}"
            + "|(?:微信|wechat|wx|qq)\\s*[:：]?\\s*[a-z0-9_-]{5,}");
    public record Clue(String id, String lostItemId, Publisher provider, Publisher publisher,
            String content, Instant createdAt) {}
    public record Detail(Clue clue, ItemRecord item) {}
    public record Message(String id, Publisher sender, String content, Instant createdAt) {}

    private final ClueMapper mapper;
    private final ItemStateLocks locks;
    private final ItemService items;
    private final AuditService audit;

    public ClueService(ClueMapper mapper, ItemStateLocks locks, ItemService items, AuditService audit) {
        this.mapper = mapper; this.locks = locks; this.items = items; this.audit = audit;
    }

    @Transactional
    public Clue create(long user, long itemId, String content) {
        String text = text(content, 10, 500);
        var item = locks.lockItem(itemId);
        if (Rows.num(item, "publisher_id") == user) throw new BusinessException(ErrorCode.SELF_OPERATION_NOT_ALLOWED);
        requireLostPublished(item);
        var row = new HashMap<String, Object>();
        row.put("itemId", itemId); row.put("publisherId", item.get("publisher_id"));
        row.put("providerId", user); row.put("content", text);
        try { mapper.insert(row); }
        catch (DuplicateKeyException ex) { throw new BusinessException(ErrorCode.CLUE_ALREADY_EXISTS); }
        long id = Rows.num(row, "id");
        audit.record(user, Entity.CLUE, id, "CREATE_CLUE", null, null);
        return view(mapper.find(id));
    }

    @Transactional(readOnly = true)
    public Detail detail(long user, long id) {
        var row = participant(user, id);
        // 通过发布人读取完整记录，权限由上面的线索参与者校验收口。
        return new Detail(view(row), items.getOwn(Rows.num(row, "lost_item_id"), Rows.num(row, "publisher_id")));
    }

    @Transactional(readOnly = true)
    public PageData<Clue> list(long user, boolean received, Long itemId, int page, int size) {
        int offset = Pages.offset(page, size);
        if (itemId != null && itemId <= 0) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        return new PageData<>(mapper.list(user, received, itemId, size, offset).stream()
                .map(ClueService::view).toList(), page, size, mapper.count(user, received, itemId));
    }

    @Transactional(readOnly = true)
    public PageData<Clue> itemClues(long user, long itemId, int page, int size) {
        var item = items.getOwn(itemId, user);
        if (!"LOST".equals(item.type())) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        return list(user, true, itemId, page, size);
    }

    @Transactional(readOnly = true)
    public PageData<Message> messages(long user, long id, int page, int size) {
        participant(user, id);
        int offset = Pages.offset(page, size);
        return new PageData<>(mapper.messages(id, size, offset).stream().map(ClueService::message).toList(),
                page, size, mapper.countMessages(id));
    }

    @Transactional
    public Message send(long user, long id, String content) {
        var clue = participant(user, id);
        var item = locks.lockItem(Rows.num(clue, "lost_item_id"));
        if (!"PUBLISHED".equals(Rows.str(item, "status"))) throw new BusinessException(ErrorCode.CONVERSATION_CLOSED);
        var row = new HashMap<String, Object>();
        row.put("clueId", id); row.put("senderId", user); row.put("content", text(content, 1, 1000));
        mapper.insertMessage(row);
        return message(mapper.message(Rows.num(row, "id")));
    }

    @Transactional
    public ItemRecord resolve(long user, long itemId, long version) {
        var item = locks.lockItem(itemId);
        if (Rows.num(item, "publisher_id") != user) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (Rows.num(item, "version") != version) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        requireLostPublished(item);
        if (mapper.resolve(itemId, version) != 1) throw new BusinessException(ErrorCode.ITEM_STATE_CONFLICT);
        audit.record(user, Entity.ITEM, itemId, "RESOLVE_LOST_ITEM", "PUBLISHED", "ARCHIVED");
        return items.getOwn(itemId, user);
    }

    private Map<String, Object> participant(long user, long id) {
        var row = mapper.find(id);
        if (row == null || (Rows.num(row, "provider_id") != user && Rows.num(row, "publisher_id") != user))
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return row;
    }
    private static void requireLostPublished(Map<String, Object> item) {
        if (!"LOST".equals(Rows.str(item, "type"))) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "仅适用于寻物信息");
        if (!"PUBLISHED".equals(Rows.str(item, "status"))) throw new BusinessException(ErrorCode.ITEM_STATE_CONFLICT);
    }
    private static String text(String value, int min, int max) {
        String text = value == null ? "" : value.trim();
        int length = text.codePointCount(0, text.length());
        if (length < min || length > max) throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "内容需为 " + min + "～" + max + " 字");
        if (CONTACT.matcher(text).find()) throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "请勿在线索或留言中填写手机号、邮箱或社交账号");
        return text;
    }
    private static Publisher user(Map<String, Object> row, String prefix) {
        return new Publisher(Long.toString(Rows.num(row, prefix + "_id")), Rows.str(row, prefix + "_nickname"));
    }
    private static Instant time(Map<String, Object> row) { return Rows.time(row, "created_at").toInstant(ZoneOffset.UTC); }
    private static Clue view(Map<String, Object> row) {
        return new Clue(Long.toString(Rows.num(row, "id")), Long.toString(Rows.num(row, "lost_item_id")),
                user(row, "provider"), user(row, "publisher"), Rows.str(row, "content"), time(row));
    }
    private static Message message(Map<String, Object> row) {
        return new Message(Long.toString(Rows.num(row, "id")), user(row, "sender"), Rows.str(row, "content"), time(row));
    }
}
