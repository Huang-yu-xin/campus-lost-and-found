package com.campus.lostfound.config;

import com.campus.lostfound.common.security.SecurityErrorHandler;
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
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        SecurityErrorHandler errorHandler = new SecurityErrorHandler(objectMapper);
        HttpSessionCsrfTokenRepository csrfTokenRepository = new HttpSessionCsrfTokenRepository();
        csrfTokenRepository.setHeaderName(CSRF_HEADER);

        http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        // springdoc 仅在 dev 配置中开启，其他环境这些路径不存在
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
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
