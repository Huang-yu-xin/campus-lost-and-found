package com.campus.lostfound.modules.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campus.lostfound.config.SecurityConfig;
import com.campus.lostfound.modules.account.service.AccountService;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
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
 * 举报全流程（连接本地库，每个测试回滚）：提交、我的举报、管理员列表/详情、
 * 受限会话读取与审计、KEEP/TAKE_DOWN/REJECT_PENDING_CLAIM 处置。
 */
@SpringBootTest(properties = "app.admin.account=")
@AutoConfigureMockMvc
@Transactional
@EnabledIf("com.campus.lostfound.TestDatabase#configured")
class ReportIntegrationTest {

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
    private MockHttpSession adminSession;
    private MockHttpSession studentSession;

    @BeforeEach
    void setUp() throws Exception {
        publisherId = createUserAndLogin("发布人", publisherSession = new MockHttpSession());
        applicantId = createUserAndLogin("申请人", applicantSession = new MockHttpSession());
        outsiderId = createUserAndLogin("路人", outsiderSession = new MockHttpSession());
        createUserAndLogin("管理员", adminSession = new MockHttpSession());
        createStudentAndLogin("普通学生", studentSession = new MockHttpSession());
    }

    @Test
    void participantCanReportConversationAndAdminReadsItWithAudit() throws Exception {
        long itemId = createItem(publisherId, "PUBLISHED");
        long claimId = createClaim(itemId, publisherId, applicantId, "PENDING");
        jdbcTemplate.update("""
                INSERT INTO claim_messages (claim_id, sender_id, content) VALUES (?, ?, ?)""",
                claimId, applicantId, "这是我的钱包，可以约时间拿吗");

        long reportId = createReport(applicantSession, itemId, claimId, null, "对方要求我先转账再归还")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.claimId").value(claimId))
                .andReturn().getResponse().getContentAsString().transform(this::reportIdOf);

        mockMvc.perform(get("/api/v1/me/reports").session(applicantSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(reportId));

        mockMvc.perform(get("/api/v1/admin/reports").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(reportId));
        mockMvc.perform(get("/api/v1/admin/reports/" + reportId).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reportId));

        mockMvc.perform(get("/api/v1/admin/reports/" + reportId + "/conversation").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CLAIM"))
                .andExpect(jsonPath("$.data.contextId").value(claimId))
                .andExpect(jsonPath("$.data.evidenceText").value("钱包内侧有一张公交卡和一张照片"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].content").value("这是我的钱包，可以约时间拿吗"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report_access_logs WHERE report_id=?", Long.class, reportId)).isEqualTo(1L);

        // 已处理举报的会话不能再看
        resolve(adminSession, reportId, 1, "KEEP", "证据不足，保留现状")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.action").value("KEEP"));
        mockMvc.perform(get("/api/v1/admin/reports/" + reportId + "/conversation").session(adminSession))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_STATE_CONFLICT"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report_access_logs WHERE report_id=?", Long.class, reportId)).isEqualTo(1L);
    }

    @Test
    void unrelatedUserCannotReportPrivateClaimAndAnonymousCannotUseAdminApi() throws Exception {
        long itemId = createItem(publisherId, "PUBLISHED");
        long claimId = createClaim(itemId, publisherId, applicantId, "PENDING");

        createReport(outsiderSession, itemId, claimId, null, "我也要举报这个认领")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPORT_CONTEXT_INVALID"));

        // 普通举报只针对公开物品
        long offShelfItem = createItem(publisherId, "OFF_SHELF");
        createReport(applicantSession, offShelfItem, null, null, "举报一个看不到的物品")
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/reports")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/reports").session(studentSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void takeDownActionClosesClaimsAndRevokesContactPermission() throws Exception {
        long itemId = createItem(publisherId, "PUBLISHED");
        long claimId = createClaim(itemId, publisherId, applicantId, "CONFIRMED");
        jdbcTemplate.update("UPDATE items SET accepted_claim_id=? WHERE id=?", claimId, itemId);
        jdbcTemplate.update("""
                INSERT INTO claim_contact_permissions (claim_id, grantor_id, phone_visible, email_visible)
                VALUES (?, ?, 1, 1)""", claimId, applicantId);

        long reportId = reportId(createReport(applicantSession, itemId, claimId, null,
                "对方在会话里索要额外费用").andReturn().getResponse().getContentAsString());

        resolve(adminSession, reportId, 1, "TAKE_DOWN", "核实存在违规索费，下架处理")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("TAKE_DOWN"));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM items WHERE id=?", String.class, itemId))
                .isEqualTo("OFF_SHELF");
        Map<String, Object> claim = jdbcTemplate.queryForMap("SELECT status, reason FROM claims WHERE id=?", claimId);
        assertThat(claim).containsEntry("status", "CLOSED").containsEntry("reason", "核实存在违规索费，下架处理");
        Map<String, Object> permission = jdbcTemplate.queryForMap(
                "SELECT phone_visible, email_visible, version FROM claim_contact_permissions WHERE claim_id=?",
                claimId);
        assertThat(permission).containsEntry("phone_visible", 0).containsEntry("email_visible", 0);
        assertThat(((Number) permission.get("version")).longValue()).isEqualTo(2L);
    }

    @Test
    void rejectPendingClaimActionOnlyTouchesRelatedPendingClaim() throws Exception {
        long itemId = createItem(publisherId, "PUBLISHED");
        long claimId = createClaim(itemId, publisherId, applicantId, "PENDING");
        long reportId = reportId(createReport(publisherSession, itemId, claimId, null,
                "申请人描述与事实不符").andReturn().getResponse().getContentAsString());

        resolve(adminSession, reportId, 1, "REJECT_PENDING_CLAIM", "凭证与物品特征不一致")
                .andExpect(status().isOk());
        Map<String, Object> claim = jdbcTemplate.queryForMap("SELECT status, reason FROM claims WHERE id=?", claimId);
        assertThat(claim).containsEntry("status", "REJECTED").containsEntry("reason", "凭证与物品特征不一致");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM reports WHERE id=?", String.class, reportId))
                .isEqualTo("RESOLVED");

        // 已驳回申请不能再次用举报操作回退
        long secondReport = reportId(createReport(publisherSession, itemId, claimId, null,
                "再举报一次").andReturn().getResponse().getContentAsString());
        resolve(adminSession, secondReport, 1, "REJECT_PENDING_CLAIM", "重复处置")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_STATE_CONFLICT"));
    }

    private long createUserAndLogin(String nickname, MockHttpSession session) throws Exception {
        String account = "it-" + Long.toString(System.nanoTime(), 36);
        long userId = accountService.createAdmin(account, PASSWORD, account + "@example.com", nickname);
        postJson(session, "/api/v1/auth/login", Map.of("account", account, "password", PASSWORD))
                .andExpect(status().isOk());
        return userId;
    }

    /** 造一个真实 STUDENT 角色的账号：先建账号，再绑定模拟名册并改角色。 */
    private long createStudentAndLogin(String nickname, MockHttpSession session) throws Exception {
        String suffix = Long.toString(System.nanoTime(), 36);
        String account = "it-" + suffix;
        long userId = accountService.createAdmin(account, PASSWORD, account + "@example.com", nickname);
        jdbcTemplate.update("""
                INSERT INTO mock_students (student_no, campus_card_no, real_name)
                VALUES (?, ?, '测试学生')""", "S" + suffix, "C" + suffix);
        long studentId = lastInsertId();
        jdbcTemplate.update("UPDATE users SET role='STUDENT', student_record_id=? WHERE id=?",
                studentId, userId);
        postJson(session, "/api/v1/auth/login", Map.of("account", account, "password", PASSWORD))
                .andExpect(status().isOk());
        return userId;
    }

    private long createItem(long publisher, String status) {
        Timestamp publishedAt = "PUBLISHED".equals(status) ? new Timestamp(System.currentTimeMillis()) : null;
        jdbcTemplate.update("""
                INSERT INTO items (publisher_id, type, category, location, occurred_date,
                                   title, description, status, published_at, closure_reason, closed_at)
                VALUES (?, 'FOUND', 'DOCUMENT', '图书馆', '2026-11-01', '黑色钱包',
                        '在图书馆二楼捡到一个黑色钱包，内有校园卡', ?, ?, ?, ?)""",
                publisher, status, publishedAt,
                "OFF_SHELF".equals(status) ? "VIOLATION" : null,
                "OFF_SHELF".equals(status) ? new Timestamp(System.currentTimeMillis()) : null);
        return lastInsertId();
    }

    private long createClaim(long itemId, long publisher, long applicant, String status) {
        jdbcTemplate.update("""
                INSERT INTO claims (item_id, publisher_id, applicant_id, description, status, confirmed_at)
                VALUES (?, ?, ?, '钱包内侧有一张公交卡和一张照片', ?, ?)""",
                itemId, publisher, applicant, status,
                "CONFIRMED".equals(status) ? new Timestamp(System.currentTimeMillis()) : null);
        return lastInsertId();
    }

    private long lastInsertId() {
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0 : id;
    }

    private ResultActions createReport(MockHttpSession session, long itemId, Long claimId, Long clueId,
            String reason) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("itemId", itemId);
        body.put("claimId", claimId);
        body.put("clueId", clueId);
        body.put("reason", reason);
        return postJson(session, "/api/v1/reports", body);
    }

    private ResultActions resolve(MockHttpSession session, long reportId, long expectedVersion,
            String action, String reason) throws Exception {
        return postJson(session, "/api/v1/admin/reports/" + reportId + "/resolve",
                Map.of("expectedVersion", expectedVersion, "action", action, "reason", reason));
    }

    private long reportIdOf(String body) {
        return reportId(body);
    }

    private long reportId(String body) {
        Number id = JsonPath.read(body, "$.data.id");
        return id.longValue();
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
