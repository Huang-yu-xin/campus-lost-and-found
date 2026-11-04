package com.campus.lostfound.modules.account.service;

import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.modules.account.dto.LoginRequest;
import com.campus.lostfound.modules.account.dto.Me;
import com.campus.lostfound.modules.account.security.AccountUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;

/**
 * JSON 登录与退出。登录不能只调用 SecurityContextHolder.setContext：
 * 还要轮换 Session ID、更新 CSRF 令牌，并把 SecurityContext 保存到会话，后续请求才算已登录。
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository contextRepository;
    private final LoginAttemptLimiter attemptLimiter;
    private final CurrentUserService currentUserService;
    private final LogoutHandler[] logoutHandlers;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public AuthService(AuthenticationManager authenticationManager, SessionAuthenticationStrategy sessionStrategy,
            SecurityContextRepository contextRepository, CsrfTokenRepository csrfTokenRepository,
            LoginAttemptLimiter attemptLimiter, CurrentUserService currentUserService) {
        this.authenticationManager = authenticationManager;
        this.sessionStrategy = sessionStrategy;
        this.contextRepository = contextRepository;
        this.attemptLimiter = attemptLimiter;
        this.currentUserService = currentUserService;

        SecurityContextLogoutHandler contextLogout = new SecurityContextLogoutHandler();
        contextLogout.setSecurityContextRepository(contextRepository);
        this.logoutHandlers = new LogoutHandler[] {
                new CsrfLogoutHandler(csrfTokenRepository),
                contextLogout,
                new CookieClearingLogoutHandler("JSESSIONID"),
        };
    }

    public Me login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String account = request.account();
        attemptLimiter.checkAllowed(account);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(account, request.password()));
        } catch (BadCredentialsException ex) {
            attemptLimiter.recordFailure(account);
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        } catch (AuthenticationServiceException ex) {
            // 查询用户时数据库等内部错误，按 500 处理而不是“未登录”
            throw new IllegalStateException("认证服务异常", ex);
        }
        attemptLimiter.reset(account);

        sessionStrategy.onAuthentication(authentication, httpRequest, httpResponse);
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        contextRepository.saveContext(context, httpRequest, httpResponse);

        AccountUserDetails user = (AccountUserDetails) authentication.getPrincipal();
        return currentUserService.getMe(user.userId());
    }

    /** 清除 CSRF 令牌、使会话失效、清空 SecurityContext，并让浏览器删除会话 Cookie。 */
    public void logout(Authentication authentication, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        for (LogoutHandler handler : logoutHandlers) {
            handler.logout(httpRequest, httpResponse, authentication);
        }
    }
}
