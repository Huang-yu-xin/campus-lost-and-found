package com.campus.lostfound.modules.item.service;

import com.campus.lostfound.common.api.FieldError;
import com.campus.lostfound.common.api.PageData;
import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.modules.item.dto.CreateItemRequest;
import com.campus.lostfound.modules.item.dto.ItemSearch;
import com.campus.lostfound.modules.item.dto.ItemViews.ItemRecord;
import com.campus.lostfound.modules.item.dto.ItemViews.PublicItem;
import com.campus.lostfound.modules.item.dto.ItemViews.Publisher;
import com.campus.lostfound.modules.item.entity.NewItem;
import com.campus.lostfound.modules.item.mapper.ItemMapper;
import com.campus.lostfound.modules.item.mapper.ItemRow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemService {
    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");
    // 提醒用户移除常见明文联系方式，剩余内容仍需人工审核。
    private static final Pattern CONTACT = Pattern.compile(
            "(?i)(?<![0-9])1[3-9][0-9]{9}(?![0-9])"
            + "|[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}"
            + "|(?:微信|wechat|wx|qq)\\s*[:：]?\\s*[a-z0-9_-]{5,}");

    private final ItemMapper mapper;
    private final Clock clock;

    public ItemService(ItemMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public ItemRecord create(long publisherId, CreateItemRequest request) {
        if (request.occurredDate().isAfter(LocalDate.now(clock.withZone(CAMPUS_ZONE)))) {
            throw invalid("occurredDate", "发生日期不能晚于今天");
        }
        if (request.imageIds() != null && !request.imageIds().isEmpty()) {
            throw invalid("imageIds", "当前仅支持无图发布");
        }
        checkContact("title", request.title());
        checkContact("location", request.location());
        checkContact("description", request.description());

        NewItem item = new NewItem(publisherId, request);
        mapper.insert(item);
        ItemRow row = mapper.findOwn(item.getId(), publisherId);
        if (row == null) {
            throw new IllegalStateException("无法读取刚发布的物品");
        }
        return new ItemRecord(Long.toString(row.id()), row.type(), row.category(), row.location(),
                row.occurredDate(), row.title(), row.description(), List.of(), publisher(row),
                instant(row.publishedAt()), instant(row.createdAt()), row.hasConfirmedClaim(),
                row.status(), row.version(), row.reviewReason(), row.closureReason(), instant(row.updatedAt()));
    }

    @Transactional(readOnly = true)
    public PageData<PublicItem> listPublished(ItemSearch search) {
        if (search.dateFrom() != null && search.dateTo() != null && search.dateTo().isBefore(search.dateFrom())) {
            throw invalid("dateTo", "结束日期不能早于开始日期");
        }
        long offset = (long) (search.page() - 1) * search.pageSize();
        long total = mapper.countPublished(search);
        List<PublicItem> items = offset >= total ? List.of()
                : mapper.listPublished(search, offset).stream().map(ItemService::publicItem).toList();
        return new PageData<>(items, search.page(), search.pageSize(), total);
    }

    public PublicItem getPublished(long id) {
        ItemRow row = mapper.findPublished(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return publicItem(row);
    }

    private static PublicItem publicItem(ItemRow row) {
        return new PublicItem(Long.toString(row.id()), row.type(), row.category(), row.location(),
                row.occurredDate(), row.title(), row.description(), List.of(), publisher(row),
                instant(row.publishedAt()), instant(row.createdAt()), row.hasConfirmedClaim());
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
