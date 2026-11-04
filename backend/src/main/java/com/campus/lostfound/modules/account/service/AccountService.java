package com.campus.lostfound.modules.account.service;

import com.campus.lostfound.modules.account.entity.NewUser;
import com.campus.lostfound.modules.account.mapper.LoginIdentifierMapper;
import com.campus.lostfound.modules.account.mapper.UserMapper;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 账号创建。用户与登录标识必须在同一事务中写入，任何唯一冲突整体回滚。 */
@Service
public class AccountService {

    private final UserMapper userMapper;
    private final LoginIdentifierMapper loginIdentifierMapper;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserMapper userMapper, LoginIdentifierMapper loginIdentifierMapper,
            PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.loginIdentifierMapper = loginIdentifierMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean identifierExists(String identifier) {
        return loginIdentifierMapper.exists(identifier);
    }

    /** 创建预置管理员，返回用户 id。管理员没有名册绑定，角色只能由受控初始化设定。 */
    @Transactional
    public long createAdmin(String account, String rawPassword, String email, String nickname) {
        NewUser user = NewUser.admin(nickname.strip(), normalizeEmail(email), passwordEncoder.encode(rawPassword));
        userMapper.insert(user);
        loginIdentifierMapper.insert(account, user.getId());
        return user.getId();
    }

    /** 邮箱去首尾空白后统一小写保存和判重。 */
    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
