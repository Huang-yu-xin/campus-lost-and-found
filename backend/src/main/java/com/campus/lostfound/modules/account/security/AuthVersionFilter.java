package com.campus.lostfound.modules.account.security;

import com.campus.lostfound.modules.account.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 每个已登录请求都核对会话中的认证版本与 users.auth_version。
 * 改密后版本递增，所有旧会话（包括修改者自己的）在下一次请求时失效，按未登录处理。
 * 只注册在 Security 过滤器链中，不作为普通 Servlet 过滤器注册。
 */
public class AuthVersionFilter extends OncePerRequestFilter {

    private final UserMapper userMapper;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public AuthVersionFilter(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = contextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AccountUserDetails user) {
            Long current = userMapper.findAuthVersion(user.userId());
            if (current == null || current != user.authVersion()) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                contextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
