package com.campus.lostfound.modules.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campus.lostfound.config.SecurityConfig;
import com.campus.lostfound.modules.account.mapper.UserMapper;
import com.campus.lostfound.modules.account.service.AccountService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登录、刷新、退出、改密失效与限流的真实链路（连接本地库）。
 * 每个测试在事务中运行并回滚，不在开发库留下账号。对应用例 CT-login-01、CT-logout-01、CT-getMe-01、IT-AUTH-01。
 */
@SpringBootTest(properties = "app.admin.account=")
@AutoConfigureMockMvc
@Transactional
@EnabledIf("com.campus.lostfound.TestDatabase#configured")
class AuthFlowIntegrationTest {

    private static final String PASSWORD = "Passw0rd123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    @Autowired
    private UserMapper userMapper;

    private String account;
    private String email;
    private long userId;
    private MockHttpSession session;

    @BeforeEach
    void createAccount() {
        String suffix = Long.toString(System.nanoTime(), 36);
        account = "it-" + suffix;
        email = "it-" + suffix + "@example.com";
        userId = accountService.createAdmin(account, PASSWORD, email, "测试管理员");
        session = new MockHttpSession();
    }

    @Test
    void loginRotatesSessionAndCsrfThenMeWorksUntilLogout() throws Exception {
        String anonymousToken = csrfToken();
        String anonymousSessionId = session.getId();

        login(anonymousToken, account, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(String.valueOf(userId)))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.studentId").value(nullValue()))
                .andExpect(jsonPath("$.data.nickname").value("测试管理员"));
        assertThat(session.getId()).as("登录后应轮换 Session ID").isNotEqualTo(anonymousSessionId);

        // 登录前的 CSRF 令牌已作废
        mockMvc.perform(post("/api/v1/auth/logout").session(session)
                        .header(SecurityConfig.CSRF_HEADER, anonymousToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        // 相当于刷新页面：同一会话再次读取 /me
        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));

        mockMvc.perform(post("/api/v1/auth/logout").session(session)
                        .header(SecurityConfig.CSRF_HEADER, csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        // 退出后旧会话不能再读私有接口
        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void wrongPasswordUnknownAccountAndEmailAllFailTheSameWay() throws Exception {
        login(csrfToken(), account, "Wrong0password").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
        login(csrfToken(), "it-no-such-user", PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
        // 邮箱不能作为登录账号
        login(csrfToken(), email, PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void fiveFailuresLockTheAccountEvenForTheRightPassword() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(csrfToken(), account, "Wrong0password").andExpect(status().isUnauthorized());
        }
        login(csrfToken(), account, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void authVersionChangeInvalidatesExistingSession() throws Exception {
        login(csrfToken(), account, PASSWORD).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me").session(session)).andExpect(status().isOk());

        // 模拟改密：认证版本递增（走 MyBatis，同一事务内的查询缓存会随之刷新）
        userMapper.incrementAuthVersion(userId);

        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void blankAccountIsAFieldError() throws Exception {
        login(csrfToken(), "", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("account"));
    }

    private String csrfToken() throws Exception {
        String body = mockMvc.perform(get("/api/v1/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.token");
    }

    private ResultActions login(String token, String loginAccount, String password) throws Exception {
        String body = "{\"account\":\"" + loginAccount + "\",\"password\":\"" + password + "\"}";
        return mockMvc.perform(post("/api/v1/auth/login").session(session)
                // 像浏览器带着 JSESSIONID Cookie 一样声明“请求的会话有效”，Security 才会轮换会话 ID
                .with(request -> {
                    request.setRequestedSessionId(session.getId());
                    request.setRequestedSessionIdValid(true);
                    return request;
                })
                .header(SecurityConfig.CSRF_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
