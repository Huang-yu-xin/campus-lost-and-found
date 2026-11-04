package com.campus.lostfound.modules.account.mapper;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EmailCodeMapper {

    /** 按作用域（用途 + 邮箱 + 用户）加行锁读取；必须在事务中调用。不存在返回 null。 */
    EmailCodeRow findForUpdate(@Param("purpose") String purpose, @Param("email") String email,
            @Param("subjectKey") long subjectKey);

    void insert(@Param("purpose") String purpose, @Param("userId") Long userId, @Param("email") String email,
            @Param("codeMac") byte[] codeMac, @Param("keyId") String keyId, @Param("sentAt") Instant sentAt,
            @Param("expiresAt") Instant expiresAt);

    /** 重发：覆盖旧码，清空消费状态与错误次数。 */
    void resend(@Param("id") long id, @Param("codeMac") byte[] codeMac, @Param("keyId") String keyId,
            @Param("sentAt") Instant sentAt, @Param("expiresAt") Instant expiresAt);

    void incrementFailedAttempts(@Param("id") long id);

    /** 只消费尚未消费的码；返回受影响行数。 */
    int markConsumed(@Param("id") long id, @Param("consumedAt") Instant consumedAt);
}
