package com.campus.lostfound.modules.dev;

import com.campus.lostfound.common.api.ApiResponse;
import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【临时】开发期登录，仅用于认领/管理模块在账号模块完成前的联调。
 *
 * <p>三个约束保证它不会流入正式环境：
 * <ol>
 *   <li>只有 app.dev-login-enabled=true（仅 application-dev.yml 打开）才装配；</li>
 *   <li>路径固定 /api/v1/dev/**，与业务接口分离；</li>
 *   <li>不做任何密码校验，只按 userId 建立会话——因此绝不能在生产开启。</li>
 * </ol>
 * 成员甲的正式登录接口（/auth/login）就绪后，删除本包并在 SecurityConfig 去掉 dev 放行。</p>
 */
@RestController
@RequestMapping("/api/v1/dev")
@ConditionalOnProperty(name = "app.dev-login-enabled", havingValue = "true")
public class DevLoginController {

    private final DevUserMapper userMapper;
    private final SecurityContextRepository contextRepository =
            new HttpSessionSecurityContextRepository();

    public DevLoginController(DevUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestParam("userId") long userId,
                                                  HttpServletRequest request,
                                                  HttpServletResponse response) {
        Map<String, Object> user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        String role = String.valueOf(user.get("role"));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                String.valueOf(userId), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("role", role);
        data.put("nickname", String.valueOf(user.get("nickname")));
        return ApiResponse.ok(data);
    }
}
