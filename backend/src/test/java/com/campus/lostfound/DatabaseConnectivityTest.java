package com.campus.lostfound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 本机数据库连通性检查：启动完整应用并执行 SELECT 1，确认会话时区和严格模式。
 * 需要先按 database/README.md 初始化本地库，并通过 backend/config/application.yml
 * 或 DB_* / APP_* 环境变量提供配置；两者都没有时跳过，避免没有数据库的机器上 test 失败。
 */
@SpringBootTest
@EnabledIf("databaseConfigured")
class DatabaseConnectivityTest {

    static boolean databaseConfigured() {
        String username = System.getenv("DB_USERNAME");
        return Files.exists(Path.of("config", "application.yml")) || (username != null && !username.isBlank());
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void connectsWithUtcSessionAndStrictMode() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT 1, DATABASE(), @@session.time_zone, @@session.sql_mode")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
            assertThat(rs.getString(2)).isEqualTo("campus_lost_found");
            assertThat(rs.getString(3)).isEqualTo("+00:00");
            assertThat(rs.getString(4)).contains("STRICT_TRANS_TABLES");
        }
    }
}
