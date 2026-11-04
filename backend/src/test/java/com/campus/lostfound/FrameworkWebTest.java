package com.campus.lostfound;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.common.web.RequestIdFilter;
import com.campus.lostfound.config.SecurityConfig;
import com.campus.lostfound.modules.account.controller.CsrfController;
import com.campus.lostfound.modules.account.mapper.UserMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 验证基础框架的公共行为：统一响应、requestId、CSRF、未登录与错误格式。 */
@WebMvcTest(controllers = CsrfController.class)
@Import({SecurityConfig.class, FrameworkWebTest.ProbeController.class})
class FrameworkWebTest {

    @Autowired
    private MockMvc mockMvc;

    /** SecurityConfig 的认证版本过滤器依赖它；本测试用的模拟用户不是账号模块的主体，不会被调用。 */
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void anonymousCanGetCsrfTokenInSuccessEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER, startsWith("req-")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.data.token", not(emptyString())))
                .andExpect(jsonPath("$.requestId", startsWith("req-")))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void tokenFromCsrfEndpointIsAcceptedOnSameSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf").session(session)).andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");

        mockMvc.perform(post("/api/v1/probe/echo").session(session).with(user("student"))
                        .header(SecurityConfig.CSRF_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"校园卡\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("校园卡"));
    }

    @Test
    void anonymousPrivateRequestReturns401Json() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.message").value("请先登录"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.requestId", startsWith("req-")))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void writeWithoutCsrfTokenReturns403CsrfInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/probe/echo").with(user("student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"校园卡\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void beanValidationFailureListsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/probe/echo").with(user("student")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }

    @Test
    void unknownJsonFieldIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/probe/echo").with(user("student")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"校园卡\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("role"));
    }

    @Test
    void businessExceptionUsesItsErrorCodeStatus() throws Exception {
        mockMvc.perform(get("/api/v1/probe/conflict").with(user("student")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_STATE_CONFLICT"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void unknownPathForLoggedInUserReturns404Json() throws Exception {
        mockMvc.perform(get("/api/v1/no-such-path").with(user("student")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    /** 仅测试使用的探针接口，不属于业务合同。 */
    @RestController
    @RequestMapping("/api/v1/probe")
    static class ProbeController {

        record EchoRequest(@NotBlank String title) {
        }

        @PostMapping("/echo")
        ApiResponse<EchoRequest> echo(@Valid @RequestBody EchoRequest request) {
            return ApiResponse.ok(request);
        }

        @GetMapping("/conflict")
        ApiResponse<Void> conflict() {
            throw new BusinessException(ErrorCode.ITEM_STATE_CONFLICT);
        }
    }
}
