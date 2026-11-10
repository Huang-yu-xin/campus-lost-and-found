package com.campus.lostfound.modules.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campus.lostfound.modules.account.security.AccountUserDetails;
import com.campus.lostfound.modules.item.dto.CreateItemRequest;
import com.campus.lostfound.modules.item.dto.CreateItemRequest.ItemCategory;
import com.campus.lostfound.modules.item.dto.CreateItemRequest.ItemType;
import com.campus.lostfound.modules.item.service.ItemService;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.annotation.Propagation;
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
    @Autowired private ItemService itemService;

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

    @Test
    void sameSubmissionReturnsOriginalIdAndRejectsChangedContent() throws Exception {
        String key = UUID.randomUUID().toString();
        String description = "发现一个带有标识的黑色物品";
        publishWithKey("invalid", "合法标题", description)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));
        String response = publishWithKey(key, "合法标题", description)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(response, "$.data.id");

        publishWithKey(key, " 合法标题 ", description)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(id));
        publishWithKey(key, "修改后的标题", description)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_SUBMISSION_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM items WHERE publisher_id = ?",
                Long.class, student.userId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT title FROM items WHERE id = ?", String.class, id))
                .isEqualTo("合法标题");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRetriesWriteOnlyOneItem() throws Exception {
        long publisherId = student.userId();
        Long studentRecord = jdbc.queryForObject("SELECT student_record_id FROM users WHERE id = ?",
                Long.class, publisherId);
        try (var workers = Executors.newFixedThreadPool(2)) {
            String key = UUID.randomUUID().toString();
            var request = new CreateItemRequest(ItemType.FOUND, ItemCategory.OTHER, "图书馆",
                    LocalDate.of(2026, 10, 1), "并发提交测试", "发现一个带有标识的黑色物品", null);
            var start = new CountDownLatch(1);
            var first = workers.submit(() -> {
                start.await();
                return itemService.create(publisherId, request, key);
            });
            var second = workers.submit(() -> {
                start.await();
                return itemService.create(publisherId, request, key);
            });
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS).id()).isEqualTo(second.get(15, TimeUnit.SECONDS).id());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM items WHERE publisher_id = ?",
                    Long.class, publisherId)).isEqualTo(1L);
        } finally {
            jdbc.update("DELETE FROM items WHERE publisher_id = ?", publisherId);
            jdbc.update("DELETE FROM users WHERE id = ?", publisherId);
            jdbc.update("DELETE FROM mock_students WHERE id = ?", studentRecord);
        }
    }

    @Test
    void searchesPercentAndUnderscoreAsLiteralCharacters() throws Exception {
        String marker = "搜索" + UUID.randomUUID().toString().substring(0, 8);
        String percentItem = addPublishedItem(marker + "百分号", marker + "% 标签", "2026-10-01");
        String underscoreItem = addPublishedItem(marker + "_标签", "测试物品的普通描述信息", "2026-10-01");
        addPublishedItem(marker + "A标签", "测试物品的普通描述信息", "2026-10-01");

        mvc.perform(get("/api/v1/items").param("keyword", marker + "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(percentItem));
        mvc.perform(get("/api/v1/items").param("keyword", marker + "_"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(underscoreItem));
    }

    @Test
    void dateRangeIncludesBothBoundariesAndRejectsReversedDates() throws Exception {
        String marker = "日期" + UUID.randomUUID().toString().substring(0, 8);
        addPublishedItem(marker + "起点之前", "测试物品的普通描述信息", "2026-10-01");
        String first = addPublishedItem(marker + "起点", "测试物品的普通描述信息", "2026-10-02");
        String last = addPublishedItem(marker + "终点", "测试物品的普通描述信息", "2026-10-03");
        addPublishedItem(marker + "终点之后", "测试物品的普通描述信息", "2026-10-04");

        mvc.perform(get("/api/v1/items").param("keyword", marker)
                        .param("dateFrom", "2026-10-02").param("dateTo", "2026-10-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(last))
                .andExpect(jsonPath("$.data.items[1].id").value(first));
        mvc.perform(get("/api/v1/items").param("dateFrom", "2026-10-03").param("dateTo", "2026-10-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dateTo"));
    }

    @Test
    void paginationKeepsAStableOrderWhenPublicationTimesMatch() throws Exception {
        String marker = "分页" + UUID.randomUUID().toString().substring(0, 8);
        String[] ids = new String[11];
        for (int index = 0; index < ids.length; index++) {
            ids[index] = addPublishedItem(marker + index, "测试物品的普通描述信息", "2026-10-01");
        }

        mvc.perform(get("/api/v1/items").param("keyword", marker).param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(11))
                .andExpect(jsonPath("$.data.items.length()").value(10))
                .andExpect(jsonPath("$.data.items[0].id").value(ids[10]))
                .andExpect(jsonPath("$.data.items[9].id").value(ids[1]));
        mvc.perform(get("/api/v1/items").param("keyword", marker).param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(ids[0]));
        mvc.perform(get("/api/v1/items").param("keyword", marker).param("page", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(11))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    private String addPublishedItem(String title, String description, String date) {
        jdbc.update("""
                INSERT INTO items (publisher_id, type, category, location, occurred_date, title, description,
                                   status, published_at)
                VALUES (?, 'FOUND', 'OTHER', '图书馆', ?, ?, ?, 'PUBLISHED', '2026-10-05 09:00:00')
                """, student.userId(), date, title, description);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", String.class);
    }

    private ResultActions publish(String title, String description) throws Exception {
        return mvc.perform(post("/api/v1/items").with(user(student)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(title, description)));
    }

    private ResultActions publishWithKey(String key, String title, String description) throws Exception {
        return mvc.perform(post("/api/v1/items").with(user(student)).with(csrf())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body(title, description)));
    }

    private String body(String title, String description) {
        return json.writeValueAsString(Map.of("type", "FOUND", "category", "OTHER", "title", title,
                "description", description, "location", "图书馆",
                "occurredDate", LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()));
    }
}
