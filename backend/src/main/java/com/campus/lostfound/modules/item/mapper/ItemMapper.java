package com.campus.lostfound.modules.item.mapper;

import com.campus.lostfound.modules.item.dto.ItemSearch;
import com.campus.lostfound.modules.item.entity.NewItem;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ItemMapper {
    void insert(NewItem item);

    ItemRow findOwn(@Param("id") long id, @Param("publisherId") long publisherId);

    ItemRow findPublished(@Param("id") long id);

    long countPublished(@Param("search") ItemSearch search);

    List<ItemRow> listPublished(@Param("search") ItemSearch search, @Param("offset") long offset);
}
