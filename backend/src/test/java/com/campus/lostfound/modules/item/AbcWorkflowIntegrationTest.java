package com.campus.lostfound.modules.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.config.SecurityConfig;
import com.campus.lostfound.modules.account.service.AccountService;
import com.campus.lostfound.modules.claim.service.ClaimService;
import com.campus.lostfound.modules.item.dto.EditItemRequest;
import com.campus.lostfound.modules.item.service.ItemService;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 使用真实登录会话验证甲的资料、乙的物品与丙的审核认领之间的联动。 */
@SpringBootTest(properties = "app.admin.account=")
@AutoConfigureMockMvc
@Transactional
@EnabledIf("com.campus.lostfound.TestDatabase#configured")
class AbcWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired AccountService accounts;
    @Autowired ItemService items;
    @Autowired ClaimService claims;
    @Autowired com.campus.lostfound.modules.clue.ClueService clues;
    long owner, applicant, other, admin;
    MockHttpSession ownerSession, applicantSession, otherSession, adminSession;

    @BeforeEach void fixtures() throws Exception {
        ownerSession = new MockHttpSession();
        applicantSession = new MockHttpSession();
        otherSession = new MockHttpSession();
        adminSession = new MockHttpSession();
        owner = actor(true, ownerSession);
        applicant = actor(true, applicantSession);
        other = actor(true, otherSession);
        admin = actor(false, adminSession);
    }

    @Test void personalClaimsArePagedFilteredAndCannotEscapeOwnership() throws Exception {
        String item = publish();
        approve(item);
        long first = apply(applicantSession, item);
        long second = apply(otherSession, item);
        mvc.perform(get("/api/v1/me/claims").session(applicantSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(first));
        mvc.perform(get("/api/v1/me/received-claims").param("pageSize", "1").session(ownerSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(second));
        mvc.perform(get("/api/v1/me/received-claims").param("pageSize", "1").param("page", "2").session(ownerSession))
                .andExpect(jsonPath("$.data.items[0].id").value(first));
        mvc.perform(get("/api/v1/me/received-claims").param("itemId", item).session(otherSession))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/me/claims").param("status", "REJECTED").session(applicantSession))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/me/claims").param("status", "INVALID").session(applicantSession))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/me/claims").param("page", "2147483647").session(applicantSession))
                .andExpect(status().isBadRequest());
    }

    @Test void lostCluesArePrivatePagedAndBecomeReadOnlyAfterFound() throws Exception {
        String item = publish("LOST");
        send(applicantSession, post("/api/v1/items/" + item + "/clues"), Map.of("content", "我在图书馆服务台看到相似物品"))
                .andExpect(status().isConflict());
        approve(item);
        send(ownerSession, post("/api/v1/items/" + item + "/clues"), Map.of("content", "我在图书馆服务台看到相似物品"))
                .andExpect(status().isForbidden());
        String result = send(applicantSession, post("/api/v1/items/" + item + "/clues"),
                Map.of("content", "我在图书馆服务台看到相似物品"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String clue = JsonPath.read(result, "$.data.id");
        send(applicantSession, post("/api/v1/items/" + item + "/clues"), Map.of("content", "我又补充了一条相同物品的线索"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLUE_ALREADY_EXISTS"));
        mvc.perform(get("/api/v1/clues/" + clue).session(otherSession)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/clues/" + clue).session(adminSession)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/me/clues").session(applicantSession))
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].id").value(clue));
        mvc.perform(get("/api/v1/me/received-clues").param("itemId", item).session(otherSession))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/items/" + item + "/clues").session(applicantSession)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/items/" + item + "/clues").session(ownerSession))
                .andExpect(jsonPath("$.data.total").value(1));
        send(otherSession, post("/api/v1/clues/" + clue + "/messages"), Map.of("content", "越权消息"))
                .andExpect(status().isNotFound());
        for (int i = 0; i < 12; i++) {
            send(i % 2 == 0 ? applicantSession : ownerSession, post("/api/v1/clues/" + clue + "/messages"),
                    Map.of("content", "分页留言 " + i)).andExpect(status().isCreated());
        }
        mvc.perform(get("/api/v1/clues/" + clue + "/messages").param("page", "2").session(ownerSession))
                .andExpect(jsonPath("$.data.total").value(12)).andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].content").value("分页留言 10"));
        send(ownerSession, post("/api/v1/items/" + item + "/resolve"), Map.of("expectedVersion", 1))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        send(ownerSession, post("/api/v1/items/" + item + "/resolve"), Map.of("expectedVersion", 2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.closureReason").value("FOUND"));
        mvc.perform(get("/api/v1/items/" + item)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/clues/" + clue).session(applicantSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.item.status").value("ARCHIVED"));
        send(applicantSession, post("/api/v1/clues/" + clue + "/messages"), Map.of("content", "归档后消息"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONVERSATION_CLOSED"));
        send(otherSession, post("/api/v1/items/" + item + "/clues"), Map.of("content", "归档后再提供一条完整线索"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/clues/" + clue + "/messages").session(applicantSession))
                .andExpect(jsonPath("$.data.total").value(12));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void sendingClueRacingArchiveCannotLeaveMessagesAfterClosure() throws Exception {
        try {
            String item = publish("LOST"); approve(item);
            var clue = clues.create(applicant, Long.parseLong(item), "图书馆服务台有相同描述的物品");
            List<String> outcomes = race(
                    () -> clues.send(applicant, Long.parseLong(clue.id()), "服务台营业时间请向工作人员确认"),
                    () -> clues.resolve(owner, Long.parseLong(item), 2));
            assertThat(outcomes).contains("OK").allMatch(s -> s.equals("OK") || s.equals("CONVERSATION_CLOSED"));
            assertThat(jdbc.queryForObject("SELECT status FROM items WHERE id=?", String.class, item)).isEqualTo("ARCHIVED");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> clues.send(applicant, Long.parseLong(clue.id()), "结束后消息"))
                    .isInstanceOf(BusinessException.class).extracting(e -> ((BusinessException)e).getErrorCode())
                    .isEqualTo(com.campus.lostfound.common.error.ErrorCode.CONVERSATION_CLOSED);
        } finally {cleanupCommittedFixtures();}
    }

    @Test void publicationClaimContactChangeAndHandoverStayConsistent() throws Exception {
        String item = publish();
        approve(item).andExpect(jsonPath("$.data.version").value(2));
        mvc.perform(get("/api/v1/items/{id}", item)).andExpect(status().isOk());
        send(ownerSession, patch("/api/v1/items/" + item), Map.of("expectedVersion", 1, "title", "旧页面修改"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        long claim = apply(applicantSession, item);
        send(ownerSession, post("/api/v1/claims/" + claim + "/confirm"), Map.of())
                .andExpect(status().isBadRequest());
        send(ownerSession, post("/api/v1/claims/" + claim + "/confirm"), Map.of("expectedVersion", 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2));
        send(ownerSession, post("/api/v1/items/" + item + "/withdraw"), Map.of("expectedVersion", 3))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ITEM_ALREADY_CLAIMED"));
        send(applicantSession, patch("/api/v1/me"), Map.of("expectedVersion", 1, "phone", "13900001111"))
                .andExpect(status().isOk());
        send(applicantSession, put("/api/v1/claims/" + claim + "/contact-permission"),
                Map.of("expectedVersion", 0, "phoneVisible", true, "emailVisible", true))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/claims/{id}/counterparty-contact", claim).session(ownerSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.phone").value("13900001111"));
        send(applicantSession, patch("/api/v1/me"), Map.of("expectedVersion", 2, "phone", "13900002222"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/claims/{id}/counterparty-contact", claim).session(ownerSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.phone").doesNotExist())
                .andExpect(jsonPath("$.data.email").exists());
        mvc.perform(get("/api/v1/claims/{id}", claim).session(otherSession)).andExpect(status().isNotFound());
        send(ownerSession, post("/api/v1/claims/" + claim + "/complete"), Map.of("expectedVersion", 1))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        send(ownerSession, post("/api/v1/claims/" + claim + "/complete"), Map.of("expectedVersion", 2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        mvc.perform(get("/api/v1/items/{id}", item)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/claims/{id}", claim).session(applicantSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.item.status").value("ARCHIVED"));
        mvc.perform(get("/api/v1/claims/{id}/counterparty-contact", claim).session(ownerSession))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT version FROM items WHERE id=?", Long.class, item)).isEqualTo(4);
    }

    @Test void reviewReturnAndResubmissionAdvanceVersionsAndAuditTheActualVersion() throws Exception {
        String item = publish();
        send(adminSession, post("/api/v1/admin/items/" + item + "/return"), Map.of("expectedVersion", 1, "reason", "请补充描述"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2));
        mvc.perform(get("/api/v1/me/items/{id}", item).session(ownerSession))
                .andExpect(jsonPath("$.data.reviewReason").value("请补充描述"));
        send(ownerSession, patch("/api/v1/items/" + item), Map.of("expectedVersion", 2, "title", "已补充的测试物品"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(3));
        send(adminSession, post("/api/v1/admin/items/" + item + "/approve"), Map.of("expectedVersion", 2))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        approve(item).andExpect(jsonPath("$.data.version").value(4));
        assertThat(jdbc.queryForList("SELECT item_version FROM review_records WHERE item_id=? ORDER BY id", Long.class, item))
                .containsExactly(2L, 4L);
    }

    @Test void studentAuthorizationDoesNotRegressToTemporaryLogin() throws Exception {
        String item = publish();
        send(ownerSession, post("/api/v1/admin/items/" + item + "/approve"), Map.of("expectedVersion", 1))
                .andExpect(status().isForbidden());
        approve(item);
        send(adminSession, post("/api/v1/items/" + item + "/claims"), Map.of("description", "管理员不能作为学生申请认领"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/dev/login").param("userId", Long.toString(owner)).session(ownerSession))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void editRacingClaimCreationCannotBothSucceed() throws Exception {
        try {
            String item = publish();
            approve(item);
            EditItemRequest patch = new EditItemRequest();
            patch.expectedVersion = 2L;
            patch.title = "并发修改的测试物品";
            List<String> outcomes = race(
                    () -> items.edit(Long.parseLong(item), owner, patch),
                    () -> claims.createClaim(applicant, Long.parseLong(item), "这是并发认领使用的完整特征描述"));
            assertThat(outcomes).containsExactlyInAnyOrder("OK", "ITEM_STATE_CONFLICT");
            long count = jdbc.queryForObject("SELECT COUNT(*) FROM claims WHERE item_id=?", Long.class, item);
            String state = jdbc.queryForObject("SELECT status FROM items WHERE id=?", String.class, item);
            assertThat(count == 1 ? state.equals("PUBLISHED") : state.equals("PENDING_REVIEW")).isTrue();
        } finally { cleanupCommittedFixtures(); }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentConfirmationsSelectOneWinnerAndCloseTheOther() throws Exception {
        try {
            String item = publish();
            approve(item);
            long first = apply(applicantSession, item), second = apply(otherSession, item);
            assertThat(race(() -> claims.confirmClaim(owner, first, 1), () -> claims.confirmClaim(owner, second, 1)))
                    .containsExactlyInAnyOrder("OK", "VERSION_CONFLICT");
            assertThat(jdbc.queryForList("SELECT status FROM claims WHERE item_id=? ORDER BY status", String.class, item))
                    .containsExactly("CLOSED", "CONFIRMED");
        } finally { cleanupCommittedFixtures(); }
    }

    private List<String> race(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> outcome(start, first));
            var b = pool.submit(() -> outcome(start, second));
            start.countDown();
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
    }

    private String outcome(CountDownLatch start, Callable<?> operation) throws Exception {
        start.await();
        try { operation.call(); return "OK"; }
        catch (BusinessException ex) { return ex.getErrorCode().name(); }
    }

    private long actor(boolean student, MockHttpSession session) throws Exception {
        String tag = "abc-" + UUID.randomUUID().toString().substring(0, 18);
        long id = accounts.createAdmin(tag, "AbcTest2026!", tag + "@example.test", "联调测试");
        if (student) {
            jdbc.update("INSERT INTO mock_students(student_no,campus_card_no,real_name) VALUES (?,?,'测试学生')", tag, tag);
            Long record = jdbc.queryForObject("SELECT id FROM mock_students WHERE student_no=?", Long.class, tag);
            jdbc.update("UPDATE users SET role='STUDENT',student_record_id=? WHERE id=?", record, id);
        }
        send(session, post("/api/v1/auth/login"), Map.of("account", tag, "password", "AbcTest2026!"))
                .andExpect(status().isOk());
        return id;
    }

    private String publish() throws Exception {
        return publish("FOUND");
    }
    private String publish(String type) throws Exception {
        String response = send(ownerSession, post("/api/v1/items"), Map.of(
                "type", type, "category", "OTHER", "location", "图书馆", "occurredDate", "2026-11-01",
                "title", "甲乙丙联调测试物品", "description", "这是用于甲乙丙跨模块测试的虚构物品信息"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }

    private ResultActions approve(String id) throws Exception {
        long version = jdbc.queryForObject("SELECT version FROM items WHERE id=?", Long.class, id);
        return send(adminSession, post("/api/v1/admin/items/" + id + "/approve"), Map.of("expectedVersion", version))
                .andExpect(status().isOk());
    }

    private long apply(MockHttpSession session, String item) throws Exception {
        String response = send(session, post("/api/v1/items/" + item + "/claims"),
                Map.of("description", "这是认领所需的完整物品特征描述信息"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.id")).longValue();
    }

    private ResultActions send(MockHttpSession session, MockHttpServletRequestBuilder request, Object body) throws Exception {
        String token = JsonPath.read(mvc.perform(get("/api/v1/auth/csrf").session(session))
                .andReturn().getResponse().getContentAsString(), "$.data.token");
        return mvc.perform(request.session(session).header(SecurityConfig.CSRF_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private void cleanupCommittedFixtures() {
        jdbc.update("DELETE m FROM clue_messages m JOIN clues c ON c.id=m.clue_id WHERE c.publisher_id=?", owner);
        jdbc.update("DELETE FROM clues WHERE publisher_id=?", owner);
        jdbc.update("UPDATE items SET accepted_claim_id=NULL WHERE publisher_id=?", owner);
        jdbc.update("DELETE p FROM claim_contact_permissions p JOIN claims c ON c.id=p.claim_id WHERE c.publisher_id=?", owner);
        jdbc.update("DELETE m FROM claim_messages m JOIN claims c ON c.id=m.claim_id WHERE c.publisher_id=?", owner);
        jdbc.update("DELETE r FROM review_records r JOIN items i ON i.id=r.item_id WHERE i.publisher_id=?", owner);
        jdbc.update("DELETE FROM claims WHERE publisher_id=?", owner);
        jdbc.update("DELETE FROM items WHERE publisher_id=?", owner);
        for (long id : new long[]{owner, applicant, other, admin}) {
            Long record = jdbc.queryForObject("SELECT student_record_id FROM users WHERE id=?", Long.class, id);
            jdbc.update("DELETE FROM operation_logs WHERE actor_id=?", id);
            jdbc.update("DELETE FROM login_identifiers WHERE user_id=?", id);
            jdbc.update("DELETE FROM users WHERE id=?", id);
            if (record != null) jdbc.update("DELETE FROM mock_students WHERE id=?", record);
        }
    }
}
