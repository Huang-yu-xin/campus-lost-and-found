package com.campus.lostfound;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 连接真实本地库的测试的启用条件：有 backend/config/application.yml，或设置了 DB_USERNAME。
 * 两者都没有时这些测试跳过，没装数据库的机器上 mvnw test 也能通过。
 * 用法：@EnabledIf("com.campus.lostfound.TestDatabase#configured")
 */
public final class TestDatabase {

    private TestDatabase() {
    }

    public static boolean configured() {
        String username = System.getenv("DB_USERNAME");
        return Files.exists(Path.of("config", "application.yml")) || (username != null && !username.isBlank());
    }
}
