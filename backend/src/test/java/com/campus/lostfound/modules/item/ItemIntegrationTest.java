package com.campus.lostfound.modules.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campus.lostfound.modules.account.security.AccountUserDetails;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "app.admin.account=")
@AutoConfigureMockMvc
@Transactional
@EnabledIf("com.campus.lostfound.TestDatabase#configured")
class ItemIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private SqlSessionTemplate sqlSession;

    private AccountUserDetails student;

    @BeforeEach
    void createStudent() {
        String tag = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO mock_students (student_no, campus_card_no, real_name) VALUES (?, ?, '测试生')",
                tag, tag);
        jdbc.update("""
                INSERT INTO users (student_record_id, nickname, email, password_hash)
                SELECT id, '测试用户', ?, 'unused' FROM mock_students WHERE student_no = ?
                """, tag + "@example.test", tag);
        long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, tag + "@example.test");
        student = new AccountUserDetails(id, "STUDENT", 1, null);
    }

    @Test
    void pendingItemBecomesVisibleOnlyAfterPublication() throws Exception {
        String title = "边界".repeat(25);
        String response = publish(title, "发现一个带有标识的黑色物品")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.publisher.id").value(Long.toString(student.userId())))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(response, "$.data.id");

        mvc.perform(get("/api/v1/items/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/items").param("keyword", title))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());

        // 测试夹具模拟审核模块写入的状态，用来验证乙的公开读取边界。
        assertThat(jdbc.update("UPDATE items SET status='PUBLISHED', published_at=UTC_TIMESTAMP(6) WHERE id=?", id))
                .isEqualTo(1);
        // MockMvc 共享测试事务。原生 JDBC 更新后清掉本事务的 MyBatis 查询缓存。
        sqlSession.clearCache();

        mvc.perform(get("/api/v1/items/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.publisher.email").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.status").doesNotHaveJsonPath());
        mvc.perform(get("/api/v1/items").param("keyword", title).param("category", "OTHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(id));
        mvc.perform(get("/api/v1/items").param("keyword", title).param("category", "BOOK"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void rejectsOverlongTitlesAndContactDetailsWithoutWriting() throws Exception {
        publish("字".repeat(51), "发现一个带有标识的黑色物品")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        publish("合法标题", "请联系邮箱 demo@example.test 领取物品")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM items WHERE publisher_id = ?",
                Long.class, student.userId())).isZero();
    }

    @Test
    void anonymousUserCannotPublish() throws Exception {
        mvc.perform(post("/api/v1/items").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("合法标题", "发现一个带有标识的黑色物品")))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions publish(String title, String description) throws Exception {
        return mvc.perform(post("/api/v1/items").with(user(student)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(title, description)));
    }

    private String body(String title, String description) {
        return json.writeValueAsString(Map.of("type", "FOUND", "category", "OTHER", "title", title,
                "description", description, "location", "图书馆",
                "occurredDate", LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()));
    }
}
