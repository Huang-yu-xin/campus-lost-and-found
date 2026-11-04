package com.campus.lostfound.modules.account.security;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 登录后保存在 Session 中的当前用户。只放身份判断需要的字段，不放联系方式等私有资料。
 * 认证完成后密码哈希会被清除。
 */
public final class AccountUserDetails implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long userId;
    private final String role;
    private final long authVersion;
    private String passwordHash;

    public AccountUserDetails(long userId, String role, long authVersion, String passwordHash) {
        this.userId = userId;
        this.role = role;
        this.authVersion = authVersion;
        this.passwordHash = passwordHash;
    }

    public long userId() {
        return userId;
    }

    public String role() {
        return role;
    }

    /** 登录时的认证版本；改密后数据库中的值递增，与此不一致的会话即失效。 */
    public long authVersion() {
        return authVersion;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return String.valueOf(userId);
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
