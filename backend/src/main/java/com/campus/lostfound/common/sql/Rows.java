package com.campus.lostfound.common.sql;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/** 读取 MyBatis 以 Map 返回的行时的类型安全取值工具。 */
public final class Rows {

    private Rows() {
    }

    public static String str(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static long num(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    public static Long longOrNull(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : null;
    }

    /** 对外编号按合同以字符串输出，避免前端把大整数转成 Number 时丢失精度。 */
    public static String id(Map<String, Object> row, String key) {
        return Long.toString(num(row, key));
    }

    public static String idOrNull(Map<String, Object> row, String key) {
        Long value = longOrNull(row, key);
        return value == null ? null : value.toString();
    }

    /** 数据库会话固定为 UTC，DATETIME 按 UTC 解释后输出带时区的时间。 */
    public static Instant instant(Map<String, Object> row, String key) {
        LocalDateTime value = time(row, key);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    public static boolean bool(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.TRUE.equals(value);
    }

    public static LocalDateTime time(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().atStartOfDay();
        }
        return null;
    }
}
