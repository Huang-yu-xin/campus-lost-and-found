package com.campus.lostfound.modules.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campus.lostfound.config.SecurityConfig;
import com.campus.lostfound.modules.account.service.AccountService;
import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.Map;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 认领留言与联系方式授权的真实链路（连接本地库，每个测试回滚）。
 * 覆盖 FR-19 留言、FR-06 授权/撤回/失效，以及确认、交接的状态联动。
 */
@SpringBootTest(properties = "app.admin.account=")
@AutoConfigureMockMvc
@Transactional
@EnabledIf("com.campus.lostfound.TestDatabase#configured")
class ClaimConversationIntegrationTest {

    private static final String PASSWORD = "Passw0rd123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private long publisherId;
    private long applicantId;
    private long outsiderId;
    private MockHttpSession publisherSession;
    private MockHttpSession applicantSession;
    private MockHttpSession outsiderSession;

    @BeforeEach
    void setUp() throws Exception {
        publisherId = createUserAndLogin("发布人", publisherSession = new MockHttpSession());
        applicantId = createUserAndLogin("申请人", applicantSession = new MockHttpSession());
        outsiderId = createUserAndLogin("路人", outsiderSession = new MockHttpSession());
    }

    @Test
    void bothPartiesCanChatWhilePendingButOutsidersCannotRead() throws Exception {
        long itemId = createItem(publisherId, "PUBLISHED");
        long claimId = createClaim(itemId, publisherId, applicantId, "PENDING");

        sendMessage(applicantSession, claimId, "你好，这个钱包是我丢的，里面有一张校园卡")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sender.id").value(applicantId));

        mockMvc.perform(get("/api/v1/claims/{id}/messages", claimId).session(publisherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].content").value("你好，这个钱包是我丢的，里面有一张校园卡"));

        // 无关人员统一 404，不暴露会话存在
        mockMvc.perform(get("/api/v1/claims/{id}/messages", claimId).session(outsiderSession))
                .andExpect(status().isNotFound());
        sendMessage(outsiderSession, claimId, "我也想看看这个钱包").andExpect(status().isNotFound());

        // 留言不得夹带联系方式
        sendMessage(applicantSession, claimId, "打我电话 13800138000 联系")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 终态保留历史但禁止新增
        jdbcTemplate.update("UPDATE claims SET status='COMPLETED', confirmed_at=NOW(6), "
                + "completed_at=NOW(6) WHERE id=?", claimId);
        sendMessage(applicantSession, claimId, "交接完成后再发一条")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONVERSATION_CLOSED"));
        mockMvc.perform(get("/api/v1/claims/{id}/messages", claimId).session(applicantSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void onlyConfirmedClaimExposesAuthorizedContactAndCompletionRevokesIt() throws Exception {
        long itemId = createItem(publisherId, "PUBLISHED");
        long claimId = createClaim(itemId, publisherId, applicantId, "PENDING");
        jdbcTemplate.update("UPDATE users SET phone='13900001111' WHERE id=?", applicantId);
        jdbcTemplate.update("UPDATE users SET phone='13800002222' WHERE id=?", publisherId);

        // PENDING 阶段不能设置，也不能查询对方
        setPermission(applicantSession, claimId, 0, true, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTACT_NOT_ACTIVE"));

        confirmClaimDirectly(itemId, claimId);

        // 首次设置：申请人授权手机号和邮箱
        setPermission(applicantSession, claimId, 0, true, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phoneVisible").value(true))
                .andExpect(jsonPath("$.data.effective").value(true))
                .andExpect(jsonPath("$.data.version").value(1));

        // 发布人可以查看申请人已授权的手机号；申请人此时看不到未授权的发布人
        Map<String, Object> contact = readData(mockMvc.perform(
                get("/api/v1/claims/{id}/counterparty-contact", claimId).session(publisherSession))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(contact).containsEntry("phone", "13900001111");

        // 申请人查看发布人：对方尚未授权，403
        mockMvc.perform(get("/api/v1/claims/{id}/counterparty-contact", claimId).session(applicantSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONTACT_NOT_AUTHORIZED"));

        // 申请人可以看到自己刚授权的设置，但接口不返回真实号码
        mockMvc.perform(get("/api/v1/claims/{id}/contact-permission", claimId).session(applicantSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phoneVisible").value(true))
                .andExpect(jsonPath("$.data.version").value(1));

        // 撤回后立即失效，旧版本号不能再写
        setPermission(applicantSession, claimId, 1, false, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));
        mockMvc.perform(get("/api/v1/claims/{id}/counterparty-contact", claimId).session(publisherSession))
                .andExpect(status().isForbidden());
        setPermission(applicantSession, claimId, 1, true, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // 重新授权后完成交接：授权失效且版本递增
        setPermission(applicantSession, claimId, 2, true, true).andExpect(status().isOk());
        setPermission(publisherSession, claimId, 0, true, false).andExpect(status().isOk());
        postJson(publisherSession, "/api/v1/claims/" + claimId + "/complete", Map.of("expectedVersion", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/claims/{id}/counterparty-contact", claimId).session(publisherSession))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTACT_NOT_ACTIVE"));
        Map<String, Object> permission = jdbcTemplate.queryForMap(
                "SELECT phone_visible, email_visible, version FROM claim_contact_permissions "
                        + "WHERE claim_id=? AND grantor_id=?", claimId, applicantId);
        assertThat(permission).containsEntry("phone_visible", 0).containsEntry("email_visible", 0);
        assertThat(((Number) permission.get("version")).longValue()).isEqualTo(4L);
    }

    @Test
    void confirmingOneClaimClosesOtherPendingClaims() throws Exception {
        long itemId = createItem(publisherId, "PUBLISHED");
        long firstClaim = createClaim(itemId, publisherId, applicantId, "PENDING");
        long secondClaim = createClaim(itemId, publisherId, outsiderId, "PENDING");

        postJson(publisherSession, "/api/v1/claims/" + firstClaim + "/confirm", Map.of("expectedVersion", 1))
                .andExpect(status().isOk());
        assertThat(claimStatus(secondClaim)).isEqualTo("CLOSED");
        assertThat(claimReason(secondClaim)).isEqualTo("OTHER_ACCEPTED");
    }

    private long createUserAndLogin(String nickname, MockHttpSession session) throws Exception {
        String account = "it-" + Long.toString(System.nanoTime(), 36);
        String email = account + "@example.com";
        long userId = accountService.createAdmin(account, PASSWORD, email, nickname);
        postJson(session, "/api/v1/auth/login", Map.of("account", account, "password", PASSWORD))
                .andExpect(status().isOk());
        return userId;
    }

    private long createItem(long publisher, String status) {
        jdbcTemplate.update("""
                INSERT INTO items (publisher_id, type, category, location, occurred_date,
                                   title, description, status, published_at)
                VALUES (?, 'FOUND', 'DOCUMENT', '图书馆', '2026-11-01', '黑色钱包',
                        '在图书馆二楼捡到一个黑色钱包，内有校园卡', ?, NOW(6))""", publisher, status);
        return lastInsertId();
    }

    private long createClaim(long itemId, long publisher, long applicant, String status) {
        jdbcTemplate.update("""
                INSERT INTO claims (item_id, publisher_id, applicant_id, description, status, confirmed_at)
                VALUES (?, ?, ?, '钱包内侧有一张公交卡和一张照片', ?, ?)""",
                itemId, publisher, applicant, status, "CONFIRMED".equals(status) ? new java.sql.Timestamp(System.currentTimeMillis()) : null);
        return lastInsertId();
    }

    private void confirmClaimDirectly(long itemId, long claimId) {
        jdbcTemplate.update("UPDATE claims SET status='CONFIRMED', confirmed_at=NOW(6) WHERE id=?", claimId);
        jdbcTemplate.update("UPDATE items SET accepted_claim_id=? WHERE id=?", claimId, itemId);
    }

    private long lastInsertId() {
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0 : id;
    }

    private String claimStatus(long claimId) {
        return jdbcTemplate.queryForObject("SELECT status FROM claims WHERE id=?", String.class, claimId);
    }

    private String claimReason(long claimId) {
        return jdbcTemplate.queryForObject("SELECT reason FROM claims WHERE id=?", String.class, claimId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readData(String body) {
        Map<String, Object> root = objectMapper.readValue(body, Map.class);
        return (Map<String, Object>) root.get("data");
    }

    private ResultActions sendMessage(MockHttpSession session, long claimId, String content) throws Exception {
        return postJson(session, "/api/v1/claims/" + claimId + "/messages", Map.of("content", content));
    }

    private ResultActions setPermission(MockHttpSession session, long claimId, long expectedVersion,
            boolean phone, boolean email) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("expectedVersion", expectedVersion);
        body.put("phoneVisible", phone);
        body.put("emailVisible", email);
        return send(session, put("/api/v1/claims/" + claimId + "/contact-permission"), body);
    }

    private ResultActions postJson(MockHttpSession session, String url, Object body) throws Exception {
        return send(session, post(url), body);
    }

    private ResultActions send(MockHttpSession session, MockHttpServletRequestBuilder builder, Object body)
            throws Exception {
        String token = JsonPath.read(mockMvc.perform(get("/api/v1/auth/csrf").session(session))
                .andReturn().getResponse().getContentAsString(), "$.data.token");
        return mockMvc.perform(builder.session(session)
                .header(SecurityConfig.CSRF_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }
}
