package com.campus.lostfound.modules.account.service;

import com.campus.lostfound.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 受控初始化管理员：本机配置了 app.admin.account 时，启动后若该账号不存在就创建。
 * 已存在时不做任何修改（不会重置密码）。注册接口永远不能创建管理员。
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final AppProperties properties;
    private final AccountService accountService;

    public AdminAccountInitializer(AppProperties properties, AccountService accountService) {
        this.properties = properties;
        this.accountService = accountService;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Admin admin = properties.admin();
        if (admin == null || !admin.enabled()) {
            return;
        }
        if (accountService.identifierExists(admin.account())) {
            log.info("管理员账号 {} 已存在，跳过初始化", admin.account());
            return;
        }
        try {
            long userId = accountService.createAdmin(admin.account(), admin.password(), admin.email(),
                    admin.nickname());
            log.info("已创建管理员账号 {}（用户 ID {}）", admin.account(), userId);
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("创建管理员失败：账号或邮箱已被其他用户占用，请修改 app.admin 配置", ex);
        }
    }
}
