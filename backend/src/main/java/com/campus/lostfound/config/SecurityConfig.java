package com.campus.lostfound.config;

import com.campus.lostfound.common.security.SecurityErrorHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import tools.jackson.databind.ObjectMapper;

/**
 * 服务端 Session + CSRF。默认所有接口需要登录；
 * 合同中的公共 GET 接口由各模块负责人在开发时逐条加入 permitAll，不整体放开。
 * 登录、退出、改密使会话失效等由账号模块在此基础上补充。
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    public static final String CSRF_HEADER = "X-CSRF-TOKEN";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
            @Value("${app.dev-login-enabled:false}") boolean devLoginEnabled) throws Exception {
        SecurityErrorHandler errorHandler = new SecurityErrorHandler(objectMapper);
        HttpSessionCsrfTokenRepository csrfTokenRepository = new HttpSessionCsrfTokenRepository();
        csrfTokenRepository.setHeaderName(CSRF_HEADER);

        http
                .csrf(csrf -> {
                    csrf.csrfTokenRepository(csrfTokenRepository);
                    // 临时：开发期登录接口不校验 CSRF，关闭开关后此分支不执行
                    if (devLoginEnabled) {
                        csrf.ignoringRequestMatchers("/api/v1/dev/**");
                    }
                })
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll();
                    // 临时：开发期登录放行，关闭开关后此分支不执行
                    if (devLoginEnabled) {
                        auth.requestMatchers("/api/v1/dev/**").permitAll();
                    }
                    // springdoc 仅在 dev 配置中开启，其他环境这些路径不存在
                    auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    // 接口调试页：只是静态外壳，数据仍由 /v3/api-docs 提供，不含任何业务数据
                    auth.requestMatchers("/api.html", "/favicon.ico").permitAll();
                    auth.requestMatchers("/error").permitAll();
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                // 纯 JSON 接口：不做登录跳转、不缓存请求、不使用默认表单和 Basic 认证
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
