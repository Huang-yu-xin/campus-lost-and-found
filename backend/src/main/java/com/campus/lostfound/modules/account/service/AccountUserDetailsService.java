package com.campus.lostfound.modules.account.service;

import com.campus.lostfound.modules.account.mapper.UserAuthRow;
import com.campus.lostfound.modules.account.mapper.UserMapper;
import com.campus.lostfound.modules.account.security.AccountUserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** 按登录标识（学号、校园卡号或管理员账号）查用户。邮箱不能作为登录账号。 */
@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    public AccountUserDetailsService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public AccountUserDetails loadUserByUsername(String account) {
        UserAuthRow row = userMapper.findAuthByIdentifier(account);
        if (row == null) {
            // 对外统一表现为账号或密码错误，不暴露账号是否存在
            throw new UsernameNotFoundException("login identifier not found");
        }
        return new AccountUserDetails(row.id(), row.role(), row.authVersion(), row.passwordHash());
    }
}
