package com.campus.lostfound.modules.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campus.lostfound.config.SecurityConfig;
import com.campus.lostfound.infrastructure.mail.EmailSender;
import com.campus.lostfound.modules.account.service.AccountService;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 本人资料、修改邮箱、修改密码与授权撤回（连接本地库，每个测试回滚）。
 * 对应用例 CT-updateProfile-01、CT-sendChangeEmailCode-01、CT-changeEmail-01、CT-changePassword-01、IT-CONTACT-06。
 */
@SpringBootTest(properties = "app.admin.account=")
@AutoConfigureMockMvc
@Transactional
@EnabledIf("com.campus.lostfound.TestDatabase#configured")
class ProfileIntegrationTest {

    private static final String PASSWORD = "Passw0rd123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailSender emailSender;

    private String account;
    private String email;
    private long userId;
    private MockHttpSession session;

    @BeforeEach
    void createAndLogin() throws Exception {
        account = uniqueName();
        email = account + "@example.com";
        userId = accountService.createAdmin(account, PASSWORD, email, "原昵称");
        session = new MockHttpSession();
        login(session, account, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void patchChangesOnlyGivenFieldsAndChecksVersion() throws Exception {
        patchMe(Map.of("expectedVersion", 1, "nickname", "  新昵称  ", "phone", "138 0000 0000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.phone").value("138 0000 0000"))
                .andExpect(jsonPath("$.data.version").value(2));

        // 版本过期
        patchMe(Map.of("expectedVersion", 1, "nickname", "再改")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // 只传 phone: null 表示清空手机号，昵称不变
        Map<String, Object> clearPhone = new HashMap<>();
        clearPhone.put("expectedVersion", 2);
        clearPhone.put("phone", null);
        patchMe(clearPhone).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value(nullValue()))
                .andExpect(jsonPath("$.data.nickname").value("新昵称"));

        // 至少要改一项；身份字段不能改
        patchMe(Map.of("expectedVersion", 3)).andExpect(status().isBadRequest());
        patchMe(Map.of("expectedVersion", 3, "studentId", "20269999")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("studentId"));
    }

    @Test
    void changingPhoneRevokesOnlyPhoneAuthorization() throws Exception {
        long claimId = createAuthorizationForCurrentUser();

        patchMe(Map.of("expectedVersion", 1, "nickname", "只改昵称")).andExpect(status().isOk());
        assertThat(permission(claimId)).containsEntry("phone_visible", 1).containsEntry("email_visible", 1);

        patchMe(Map.of("expectedVersion", 2, "phone", "13900000000")).andExpect(status().isOk());
        assertThat(permission(claimId))
                .containsEntry("phone_visible", 0)
                .containsEntry("email_visible", 1)
                .containsEntry("version", 2L);
    }

    @Test
    void changeEmailNeedsItsOwnCodeAndRevokesEmailAuthorization() throws Exception {
        long claimId = createAuthorizationForCurrentUser();
        String newEmail = uniqueName() + "@example.com";

        postJson(session, "/api/v1/me/email-codes", Map.of("email", email.toUpperCase()))
                .andExpect(status().isBadRequest());
        postJson(session, "/api/v1/me/email-codes", Map.of("email", newEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300));
        String changeCode = lastCode(newEmail, "修改邮箱");

        // 注册用途的验证码不能用来改邮箱
        postJson(new MockHttpSession(), "/api/v1/auth/email-codes", Map.of("email", newEmail))
                .andExpect(status().isOk());
        String registerCode = lastCode(newEmail, "注册");
        if (!registerCode.equals(changeCode)) {
            putEmail(newEmail, registerCode).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"));
        }

        putEmail(newEmail, changeCode).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(newEmail))
                .andExpect(jsonPath("$.data.version").value(2));
        assertThat(permission(claimId)).containsEntry("email_visible", 0).containsEntry("phone_visible", 1);
    }

    @Test
    void changePasswordLogsOutEverySessionIncludingCurrent() throws Exception {
        MockHttpSession otherBrowser = new MockHttpSession();
        login(otherBrowser, account, PASSWORD).andExpect(status().isOk());

        putPassword("Wrong0password", "NewPassw0rd").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OLD_PASSWORD_INCORRECT"));
        putPassword(PASSWORD, PASSWORD).andExpect(status().isBadRequest());

        putPassword(PASSWORD, "NewPassw0rd").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me").session(session)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me").session(otherBrowser)).andExpect(status().isUnauthorized());
        login(new MockHttpSession(), account, PASSWORD).andExpect(status().isUnauthorized());
        login(new MockHttpSession(), account, "NewPassw0rd").andExpect(status().isOk());
    }

    /** 造一条“当前用户在某认领中授权了手机号和邮箱”的记录，返回认领 id。 */
    private long createAuthorizationForCurrentUser() {
        long publisherId = accountService.createAdmin(uniqueName(), PASSWORD, uniqueName() + "@example.com", "发布人");
        jdbcTemplate.update("""
                INSERT INTO items (publisher_id, type, category, location, occurred_date, title, description)
                VALUES (?, 'FOUND', 'DOCUMENT', '图书馆', '2026-11-01', '校园卡', '在图书馆二楼捡到一张校园卡')""",
                publisherId);
        long itemId = lastInsertId();
        jdbcTemplate.update("""
                INSERT INTO claims (item_id, publisher_id, applicant_id, description)
                VALUES (?, ?, ?, '这是我的校园卡，卡套是蓝色的')""", itemId, publisherId, userId);
        long claimId = lastInsertId();
        jdbcTemplate.update("""
                INSERT INTO claim_contact_permissions (claim_id, grantor_id, phone_visible, email_visible)
                VALUES (?, ?, 1, 1)""", claimId, userId);
        return claimId;
    }

    private long lastInsertId() {
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0 : id;
    }

    private Map<String, Object> permission(long claimId) {
        return jdbcTemplate.queryForMap("""
                SELECT phone_visible, email_visible, version FROM claim_contact_permissions
                WHERE claim_id = ? AND grantor_id = ?""", claimId, userId);
    }

    private static String uniqueName() {
        return "it-" + Long.toString(System.nanoTime(), 36);
    }

    private String lastCode(String to, String purposeLabel) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailSender, atLeastOnce()).sendVerificationCode(eq(to), eq(purposeLabel), code.capture(),
                any(Duration.class));
        return code.getValue();
    }

    private ResultActions patchMe(Map<String, Object> body) throws Exception {
        return send(session, patch("/api/v1/me"), body);
    }

    private ResultActions putEmail(String newEmail, String code) throws Exception {
        return send(session, put("/api/v1/me/email"),
                Map.of("expectedVersion", 1, "email", newEmail, "emailCode", code));
    }

    private ResultActions putPassword(String oldPassword, String newPassword) throws Exception {
        return send(session, put("/api/v1/me/password"),
                Map.of("oldPassword", oldPassword, "newPassword", newPassword));
    }

    private ResultActions login(MockHttpSession target, String loginAccount, String password) throws Exception {
        return postJson(target, "/api/v1/auth/login", Map.of("account", loginAccount, "password", password));
    }

    private ResultActions postJson(MockHttpSession target, String url, Object body) throws Exception {
        return send(target, post(url), body);
    }

    private ResultActions send(MockHttpSession target, MockHttpServletRequestBuilder builder, Object body)
            throws Exception {
        String token = JsonPath.read(mockMvc.perform(get("/api/v1/auth/csrf").session(target))
                .andReturn().getResponse().getContentAsString(), "$.data.token");
        return mockMvc.perform(builder.session(target)
                .header(SecurityConfig.CSRF_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }
}
