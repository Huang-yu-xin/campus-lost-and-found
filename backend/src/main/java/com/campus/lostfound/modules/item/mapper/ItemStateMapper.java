package com.campus.lostfound.modules.item.mapper;

import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 与发布、编辑、下架使用同一物品行锁，不联表锁定用户行。 */
@Mapper
public interface ItemStateMapper {
    @Select("SELECT item_id FROM claims WHERE id=#{claimId}")
    Long findClaimItem(@Param("claimId") long claimId);

    @Select("SELECT id, publisher_id, type, status, version, accepted_claim_id "
            + "FROM items WHERE id=#{itemId} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Map<String, Object> lockItem(@Param("itemId") long itemId);

    @Select("SELECT id, item_id, publisher_id, applicant_id, status, version "
            + "FROM claims WHERE id=#{claimId} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Map<String, Object> lockClaim(@Param("claimId") long claimId);
}
