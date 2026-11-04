package com.campus.lostfound.modules.account.mapper;

import com.campus.lostfound.modules.account.entity.NewUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    /** 按登录标识（学号、卡号或管理员账号）查找认证信息；不存在返回 null。 */
    UserAuthRow findAuthByIdentifier(@Param("identifier") String identifier);

    /** 当前认证版本；用户不存在返回 null。 */
    Long findAuthVersion(@Param("id") long id);

    MeRow findMe(@Param("id") long id);

    /** 认证版本加 1，使该用户已有的全部会话失效（改密时使用）。返回受影响行数。 */
    int incrementAuthVersion(@Param("id") long id);

    /** 锁定自己的用户行后读取资料；必须在事务中调用。按约定先锁用户行，再锁授权等其他行。 */
    ProfileRow findProfileForUpdate(@Param("id") long id);

    /** 只在版本一致时更新并递增版本；返回受影响行数，0 表示版本冲突。 */
    int updateProfile(@Param("id") long id, @Param("nickname") String nickname, @Param("phone") String phone,
            @Param("expectedVersion") long expectedVersion);

    int updateEmail(@Param("id") long id, @Param("email") String email,
            @Param("expectedVersion") long expectedVersion);

    /** 更新密码哈希并递增认证版本，使该用户全部旧会话失效。 */
    int updatePassword(@Param("id") long id, @Param("passwordHash") String passwordHash);

    void insert(NewUser user);

    boolean existsByStudentRecordId(@Param("studentRecordId") long studentRecordId);

    /** 邮箱列不区分大小写比较，传入规范化后的邮箱。 */
    boolean existsByEmail(@Param("email") String email);
}
