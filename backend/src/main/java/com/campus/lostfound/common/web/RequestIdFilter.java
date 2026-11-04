package com.campus.lostfound.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为每个请求生成 requestId，写入日志 MDC 和响应头。
 * 排在 Security 过滤器之前，使认证、CSRF 失败的响应也能带上同一个编号。
 * 不采用客户端传入的编号，避免日志注入。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    private static final String UNAVAILABLE = "req-unavailable";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = "req-" + UUID.randomUUID();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** 当前请求线程的 requestId；不在请求线程内时返回占位值。 */
    public static String current() {
        String requestId = MDC.get(MDC_KEY);
        return requestId != null ? requestId : UNAVAILABLE;
    }
}
