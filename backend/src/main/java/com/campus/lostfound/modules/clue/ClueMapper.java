package com.campus.lostfound.modules.clue;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ClueMapper {
    String SELECT = "SELECT c.*, p.nickname AS provider_nickname, u.nickname AS publisher_nickname, "
            + "i.status AS item_status FROM clues c JOIN users p ON p.id=c.provider_id "
            + "JOIN users u ON u.id=c.publisher_id JOIN items i ON i.id=c.lost_item_id ";
    String FILTER = " WHERE ((#{received}=TRUE AND c.publisher_id=#{userId}) OR "
            + "(#{received}=FALSE AND c.provider_id=#{userId})) "
            + "AND (#{itemId} IS NULL OR c.lost_item_id=#{itemId})";

    @Select(SELECT + "WHERE c.id=#{id}")
    Map<String, Object> find(long id);

    @Select("SELECT COUNT(*) FROM clues c" + FILTER)
    long count(@Param("userId") long userId, @Param("received") boolean received, @Param("itemId") Long itemId);

    @Select(SELECT + FILTER + " ORDER BY c.created_at DESC,c.id DESC LIMIT #{size} OFFSET #{offset}")
    List<Map<String, Object>> list(@Param("userId") long userId, @Param("received") boolean received,
            @Param("itemId") Long itemId, @Param("size") int size, @Param("offset") int offset);

    @Insert("INSERT INTO clues(lost_item_id,publisher_id,provider_id,content) "
            + "VALUES(#{itemId},#{publisherId},#{providerId},#{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String, Object> row);

    @Select("SELECT COUNT(*) FROM clue_messages WHERE clue_id=#{id}")
    long countMessages(long id);

    String MESSAGE = "SELECT m.*,u.nickname AS sender_nickname FROM clue_messages m "
            + "JOIN users u ON u.id=m.sender_id ";
    @Select(MESSAGE + "WHERE m.clue_id=#{id} ORDER BY m.created_at,m.id LIMIT #{size} OFFSET #{offset}")
    List<Map<String, Object>> messages(@Param("id") long id, @Param("size") int size, @Param("offset") int offset);

    @Insert("INSERT INTO clue_messages(clue_id,sender_id,content) VALUES(#{clueId},#{senderId},#{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertMessage(Map<String, Object> row);

    @Select(MESSAGE + "WHERE m.id=#{id}")
    Map<String, Object> message(long id);

    @Update("UPDATE items SET status='ARCHIVED',closure_reason='FOUND',closed_at=UTC_TIMESTAMP(6),"
            + "version=version+1 WHERE id=#{id} AND type='LOST' AND status='PUBLISHED' AND version=#{version}")
    int resolve(@Param("id") long id, @Param("version") long version);
}
