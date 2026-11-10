package com.campus.lostfound.modules.item.mapper;

import com.campus.lostfound.modules.item.dto.ItemSearch;
import com.campus.lostfound.modules.item.dto.CreateItemRequest;
import com.campus.lostfound.modules.item.dto.OwnItemSearch;
import com.campus.lostfound.modules.item.entity.NewItem;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ItemMapper {
    void insert(NewItem item);

    void insertOnce(@Param("item") NewItem item, @Param("submissionKey") String submissionKey,
            @Param("submissionHash") String submissionHash);

    Submission findSubmission(@Param("publisherId") long publisherId,
            @Param("submissionKey") String submissionKey);

    record Submission(long itemId, String submissionHash) {}

    ItemRow findOwn(@Param("id") long id, @Param("publisherId") long publisherId);

    ItemRow findPublished(@Param("id") long id);

    /** 不限发布者和状态，只供已完成权限判断的调用方使用。 */
    ItemRow findById(@Param("id") long id);

    long countByStatus(@Param("status") String status);

    List<ItemRow> listByStatus(@Param("status") String status, @Param("pageSize") int pageSize,
            @Param("offset") long offset);

    long countPublished(@Param("search") ItemSearch search);

    List<ItemRow> listPublished(@Param("search") ItemSearch search, @Param("offset") long offset);

    Long lockOwnId(@Param("id") long id, @Param("publisherId") long publisherId);
    default ItemRow lockOwn(long id, long publisherId) {
        return lockOwnId(id, publisherId) == null ? null : findOwn(id, publisherId);
    }
    long countOwn(@Param("publisherId") long publisherId, @Param("search") OwnItemSearch search);
    List<ItemRow> listOwn(@Param("publisherId") long publisherId, @Param("search") OwnItemSearch search,
            @Param("offset") long offset);
    boolean hasBlockingHistory(long id);
    boolean hasConfirmedClaim(long id);
    int edit(@Param("id") long id, @Param("version") long version, @Param("request") CreateItemRequest request);
    int withdraw(@Param("id") long id, @Param("version") long version);
    void closePendingClaims(long id);
}
