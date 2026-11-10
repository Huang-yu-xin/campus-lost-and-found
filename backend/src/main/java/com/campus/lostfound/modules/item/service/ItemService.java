package com.campus.lostfound.modules.item.service;

import com.campus.lostfound.common.api.FieldError;
import com.campus.lostfound.common.api.PageData;
import com.campus.lostfound.common.api.Pages;
import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.modules.item.dto.CreateItemRequest;
import com.campus.lostfound.modules.item.dto.ItemSearch;
import com.campus.lostfound.modules.item.dto.EditItemRequest;
import com.campus.lostfound.modules.item.dto.OwnItemSearch;
import com.campus.lostfound.modules.item.dto.ItemViews.Image;
import com.campus.lostfound.modules.item.dto.ItemViews.ItemRecord;
import com.campus.lostfound.modules.item.dto.ItemViews.PublicItem;
import com.campus.lostfound.modules.item.dto.ItemViews.Publisher;
import com.campus.lostfound.modules.item.entity.NewItem;
import com.campus.lostfound.modules.item.mapper.ItemMapper;
import com.campus.lostfound.modules.item.mapper.ItemMapper.Submission;
import com.campus.lostfound.modules.item.mapper.ItemRow;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ItemService {
    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern SUBMISSION_KEY = Pattern.compile("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    // 提醒用户移除常见明文联系方式，剩余内容仍需人工审核。
    private static final Pattern CONTACT = Pattern.compile(
            "(?i)(?<![0-9])1[3-9][0-9]{9}(?![0-9])"
            + "|[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}"
            + "|(?:微信|wechat|wx|qq)\\s*[:：]?\\s*[a-z0-9_-]{5,}");

    private final ItemMapper mapper;
    private final Clock clock;
    private final ObjectMapper json;
    private final ImageService images;

    public ItemService(ItemMapper mapper, Clock clock, ObjectMapper json, ImageService images) {
        this.mapper = mapper;
        this.clock = clock;
        this.json = json;
        this.images = images;
    }

    @Transactional
    public ItemRecord create(long publisherId, CreateItemRequest request, String submissionKey) {
        if (submissionKey != null && !SUBMISSION_KEY.matcher(submissionKey).matches()) {
            throw invalid("Idempotency-Key", "提交编号必须是小写 UUID");
        }
        validateContent(request);

        NewItem item = new NewItem(publisherId, request);
        long id;
        if (submissionKey == null) {
            mapper.insert(item);
            id = item.getId();
        } else {
            String hash = submissionHash(request);
            mapper.insertOnce(item, submissionKey, hash);
            Submission stored = mapper.findSubmission(publisherId, submissionKey);
            if (!hash.equals(stored.submissionHash())) {
                throw new BusinessException(ErrorCode.ITEM_SUBMISSION_CONFLICT);
            }
            id = stored.itemId();
        }
        ItemRow row = mapper.lockOwn(id, publisherId);
        if (row == null) {
            throw new IllegalStateException("无法读取刚发布的物品");
        }
        // 重试已编辑/下架的旧提交只读取当前记录，不能把旧图片重新绑定回来。
        if (row.version() == 1) images.replace(id, publisherId, request.imageIds());
        return record(row);
    }

    private String submissionHash(CreateItemRequest request) {
        // 固定字段顺序，使用规范化后的请求。缺省图片与空数组等价。
        List<?> fields = List.of(request.type().name(), request.category().name(), request.location(),
                request.occurredDate().toString(), request.title(), request.description(),
                request.imageIds() == null ? List.of() : request.imageIds());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(fields));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    @Transactional(readOnly = true)
    public PageData<PublicItem> listPublished(ItemSearch search) {
        if (search.dateFrom() != null && search.dateTo() != null && search.dateTo().isBefore(search.dateFrom())) {
            throw invalid("dateTo", "结束日期不能早于开始日期");
        }
        long offset = (long) (search.page() - 1) * search.pageSize();
        long total = mapper.countPublished(search);
        List<ItemRow> rows = offset >= total ? List.of() : mapper.listPublished(search, offset);
        var pictures = images.forItems(rows.stream().map(ItemRow::id).toList());
        List<PublicItem> items = rows.stream()
                .map(row -> publicItem(row, pictures.getOrDefault(row.id(), List.of()))).toList();
        return new PageData<>(items, search.page(), search.pageSize(), total);
    }

    @Transactional(readOnly = true)
    public PublicItem getPublished(long id) {
        ItemRow row = mapper.findPublished(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return publicItem(row, pictures(row.id()));
    }

    private static PublicItem publicItem(ItemRow row, List<Image> pictures) {
        return new PublicItem(Long.toString(row.id()), row.type(), row.category(), row.location(),
                row.occurredDate(), row.title(), row.description(), pictures, publisher(row),
                instant(row.publishedAt()), instant(row.createdAt()), row.hasConfirmedClaim());
    }

    @Transactional(readOnly = true)
    public ItemRecord getOwn(long id, long userId) {
        ItemRow row = mapper.findOwn(id, userId);
        if (row == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return record(row);
    }

    @Transactional(readOnly = true)
    public PageData<ItemRecord> listOwn(long userId, OwnItemSearch search) {
        long offset = (long) (search.page() - 1) * search.pageSize();
        long total = mapper.countOwn(userId, search);
        var rows = offset >= total ? List.<ItemRow>of() : mapper.listOwn(userId, search, offset);
        var pictures = images.forItems(rows.stream().map(ItemRow::id).toList());
        return new PageData<>(rows.stream().map(row -> record(row, pictures.getOrDefault(row.id(), List.of())))
                .toList(), search.page(), search.pageSize(), total);
    }

    /** 管理员审核、认领参与者等场景读取完整记录；调用方须先完成权限判断。 */
    @Transactional(readOnly = true)
    public ItemRecord getRecord(long id) {
        ItemRow row = mapper.findById(id);
        if (row == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return record(row);
    }

    /** 管理端按状态分页，createdAt、id 升序。 */
    @Transactional(readOnly = true)
    public PageData<ItemRecord> listByStatus(String status, int page, int pageSize) {
        int offset = Pages.offset(page, pageSize);
        long total = mapper.countByStatus(status);
        var rows = offset >= total ? List.<ItemRow>of() : mapper.listByStatus(status, pageSize, offset);
        var pictures = images.forItems(rows.stream().map(ItemRow::id).toList());
        return new PageData<>(rows.stream().map(row -> record(row, pictures.getOrDefault(row.id(), List.of())))
                .toList(), page, pageSize, total);
    }

    @Transactional
    public ItemRecord edit(long id, long userId, EditItemRequest patch) {
        ItemRow row = lockForChange(id, userId, patch.expectedVersion());
        if (mapper.hasBlockingHistory(id)) {
            throw new BusinessException(ErrorCode.ITEM_STATE_CONFLICT, "存在未全部驳回的认领或线索历史，不能修改");
        }
        var request = new CreateItemRequest(CreateItemRequest.ItemType.valueOf(row.type()),
                patch.category() == null ? CreateItemRequest.ItemCategory.valueOf(row.category()) : patch.category(),
                patch.location() == null ? row.location() : patch.location(),
                patch.occurredDate() == null ? row.occurredDate() : patch.occurredDate(),
                patch.title() == null ? row.title() : patch.title(),
                patch.description() == null ? row.description() : patch.description(), patch.imageIds());
        validateContent(request);
        if (mapper.edit(id, row.version(), request) != 1) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        if (patch.imageIds() != null) images.replace(id, userId, patch.imageIds());
        return record(mapper.findOwn(id, userId));
    }

    @Transactional
    public ItemRecord withdraw(long id, long userId, long expectedVersion) {
        ItemRow row = lockForChange(id, userId, expectedVersion);
        if (mapper.hasConfirmedClaim(id)) throw new BusinessException(ErrorCode.ITEM_ALREADY_CLAIMED);
        if (mapper.withdraw(id, row.version()) != 1) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        mapper.closePendingClaims(id);
        return record(mapper.findOwn(id, userId));
    }

    private ItemRow lockForChange(long id, long userId, long expectedVersion) {
        ItemRow row = mapper.lockOwn(id, userId);
        if (row == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (row.version() != expectedVersion) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        if (!List.of("PENDING_REVIEW", "RETURNED", "PUBLISHED").contains(row.status())) {
            throw new BusinessException(ErrorCode.ITEM_STATE_CONFLICT);
        }
        return row;
    }

    private void validateContent(CreateItemRequest request) {
        if (request.occurredDate().isAfter(LocalDate.now(clock.withZone(CAMPUS_ZONE)))) {
            throw invalid("occurredDate", "发生日期不能晚于今天");
        }
        checkContact("title", request.title());
        checkContact("location", request.location());
        checkContact("description", request.description());
    }

    private List<Image> pictures(long id) {
        return images.forItems(List.of(id)).getOrDefault(id, List.of());
    }

    private ItemRecord record(ItemRow row) { return record(row, pictures(row.id())); }

    private static ItemRecord record(ItemRow row, List<Image> pictures) {
        return new ItemRecord(Long.toString(row.id()), row.type(), row.category(), row.location(),
                row.occurredDate(), row.title(), row.description(), pictures, publisher(row),
                instant(row.publishedAt()), instant(row.createdAt()), row.hasConfirmedClaim(),
                row.status(), row.version(), row.reviewReason(), row.closureReason(), instant(row.updatedAt()));
    }

    private static Publisher publisher(ItemRow row) {
        return new Publisher(Long.toString(row.publisherId()), row.nickname());
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static void checkContact(String field, String value) {
        if (CONTACT.matcher(value).find()) {
            throw invalid(field, "请移除手机号、邮箱或社交账号等联系方式");
        }
    }

    private static BusinessException invalid(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message, List.of(new FieldError(field, message)));
    }
}
