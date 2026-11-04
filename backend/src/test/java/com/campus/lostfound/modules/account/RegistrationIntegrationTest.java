package com.campus.lostfound.modules.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campus.lostfound.config.SecurityConfig;
import com.campus.lostfound.infrastructure.mail.EmailSender;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 发送验证码与注册（连接本地库，每个测试回滚）。邮件发送替换为模拟对象，直接取到生成的验证码。
 * 对应用例 CT-sendRegistrationCode-01、CT-register-01、IT-AUTH-02、IT-AUTH-03。
 */
@SpringBootTest(properties = "app.admin.account=")
@AutoConfigureMockMvc
@Transactional
@EnabledIf("com.campus.lostfound.TestDatabase#configured")
class RegistrationIntegrationTest {

    private static final String PASSWORD = "Passw0rd123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailSender emailSender;

    private MockHttpSession session;
    private List<Map<String, Object>> freeStudents;

    @BeforeEach
    void setUp() {
        session = new MockHttpSession();
        // 还没被注册、学号卡号也没被占用的名册记录；开发库里手动注册过的同学会被跳过
        freeStudents = jdbcTemplate.queryForList("""
                SELECT ms.student_no, ms.real_name, ms.campus_card_no FROM mock_students ms
                WHERE ms.enabled = 1
                  AND NOT EXISTS (SELECT 1 FROM users u WHERE u.student_record_id = ms.id)
                  AND NOT EXISTS (SELECT 1 FROM login_identifiers li
                                  WHERE li.identifier IN (ms.student_no, ms.campus_card_no))
                ORDER BY ms.id""");
        assumeTrue(!freeStudents.isEmpty(), "名册中没有未注册的学生，跳过注册测试");
    }

    @Test
    void sendCodeThenRegisterThenLoginWithStudentIdOrCardNo() throws Exception {
        Map<String, Object> student = freeStudents.get(0);
        String email = uniqueEmail();

        sendCode(email)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.data.retryAfterSeconds").value(60));
        String code = lastCodeSentTo(email);

        register(student, " " + email.toUpperCase() + " ", code, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.studentId").value(student.get("student_no")))
                .andExpect(jsonPath("$.data.realName").value(student.get("real_name")))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.nickname", Matchers.startsWith("同学")));

        // 注册不自动登录
        mockMvc.perform(get("/api/v1/me").session(session)).andExpect(status().isUnauthorized());

        login((String) student.get("student_no")).andExpect(status().isOk());
        login((String) student.get("campus_card_no")).andExpect(status().isOk());

        // 同一个验证码不能再用
        register(student, email, code, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_USED"));
    }

    @Test
    void wrongCodeFiveTimesVoidsTheCodeAndCreatesNoAccount() throws Exception {
        Map<String, Object> student = freeStudents.get(0);
        String email = uniqueEmail();
        sendCode(email).andExpect(status().isOk());
        String code = lastCodeSentTo(email);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 4; i >= 1; i--) {
            register(student, email, wrong, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"))
                    .andExpect(jsonPath("$.message").value("验证码错误，还可以再试 " + i + " 次"));
        }
        register(student, email, wrong, null).andExpect(status().isBadRequest());
        // 第 5 次错误后，正确的验证码也不能用了
        register(student, email, code, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"))
                .andExpect(jsonPath("$.message").value("验证码错误次数过多，请重新获取"));
        assertThat(userCountWithEmail(email)).isZero();
    }

    @Test
    void expiredCodeIsRejected() throws Exception {
        String email = uniqueEmail();
        sendCode(email).andExpect(status().isOk());
        String code = lastCodeSentTo(email);
        jdbcTemplate.update("UPDATE email_verification_codes SET expires_at = sent_at + INTERVAL 1 MICROSECOND"
                + " WHERE email = ?", email);

        register(freeStudents.get(0), email, code, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_EXPIRED"));
    }

    @Test
    void resendWithinSixtySecondsIsRateLimited() throws Exception {
        String email = uniqueEmail();
        sendCode(email).andExpect(status().isOk());
        sendCode(email)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void identityMismatchAndRoleFieldAreRejectedWithoutCreatingAccounts() throws Exception {
        Map<String, Object> student = freeStudents.get(0);
        String email = uniqueEmail();
        sendCode(email).andExpect(status().isOk());
        String code = lastCodeSentTo(email);

        Map<String, Object> wrongName = new HashMap<>(student);
        wrongName.put("real_name", "错误姓名");
        register(wrongName, email, code, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDENTITY_MISMATCH"));

        // IT-AUTH-03：注册请求不能指定角色
        register(student, email, code, Map.of("role", "ADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("role"));

        assertThat(userCountWithEmail(email)).isZero();
    }

    @Test
    void sameStudentOrSameEmailCannotRegisterTwice() throws Exception {
        assumeTrue(freeStudents.size() >= 2, "需要两条未注册的名册记录");
        Map<String, Object> first = freeStudents.get(0);
        Map<String, Object> second = freeStudents.get(1);
        String email = uniqueEmail();
        sendCode(email).andExpect(status().isOk());
        register(first, email, lastCodeSentTo(email), null).andExpect(status().isCreated());

        String otherEmail = uniqueEmail();
        sendCode(otherEmail).andExpect(status().isOk());
        register(first, otherEmail, lastCodeSentTo(otherEmail), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDENTITY_ALREADY_BOUND"));

        // 已注册邮箱也能收到验证码（不暴露是否注册），但注册时冲突
        sendCode(email.toUpperCase()).andExpect(status().isTooManyRequests());
        jdbcTemplate.update("UPDATE email_verification_codes SET sent_at = sent_at - INTERVAL 2 MINUTE"
                + " WHERE email = ?", email);
        sendCode(email).andExpect(status().isOk());
        register(second, email, lastCodeSentTo(email), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_BOUND"));
    }

    private String uniqueEmail() {
        return "it-" + Long.toString(System.nanoTime(), 36) + "@example.com";
    }

    private String lastCodeSentTo(String email) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailSender, atLeastOnce())
                .sendVerificationCode(eq(email), eq("注册"), code.capture(), any(Duration.class));
        return code.getValue();
    }

    private int userCountWithEmail(String email) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
        return count == null ? 0 : count;
    }

    private String csrfToken() throws Exception {
        String body = mockMvc.perform(get("/api/v1/auth/csrf").session(session))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.token");
    }

    private ResultActions sendCode(String email) throws Exception {
        return postJson("/api/v1/auth/email-codes", Map.of("email", email));
    }

    private ResultActions register(Map<String, Object> student, String email, String code,
            Map<String, Object> extra) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("studentId", student.get("student_no"));
        body.put("realName", student.get("real_name"));
        body.put("campusCardNo", student.get("campus_card_no"));
        body.put("email", email);
        body.put("emailCode", code);
        body.put("password", PASSWORD);
        if (extra != null) {
            body.putAll(extra);
        }
        return postJson("/api/v1/auth/register", body);
    }

    private ResultActions login(String account) throws Exception {
        return postJson("/api/v1/auth/login", Map.of("account", account, "password", PASSWORD));
    }

    private ResultActions postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).session(session)
                .header(SecurityConfig.CSRF_HEADER, csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }
}
