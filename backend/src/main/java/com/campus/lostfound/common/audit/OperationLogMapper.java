package com.campus.lostfound.common.audit;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** operation_logs 只追加，不提供修改和删除。 */
@Mapper
public interface OperationLogMapper {

    @Insert("INSERT INTO operation_logs "
            + "(actor_id, entity_type, entity_id, action, before_status, after_status, request_id) "
            + "VALUES (#{actorId}, #{entityType}, #{entityId}, #{action}, #{beforeStatus}, "
            + "#{afterStatus}, #{requestId})")
    int insert(@Param("actorId") Long actorId, @Param("entityType") String entityType,
               @Param("entityId") long entityId, @Param("action") String action,
               @Param("beforeStatus") String beforeStatus, @Param("afterStatus") String afterStatus,
               @Param("requestId") String requestId);
}
