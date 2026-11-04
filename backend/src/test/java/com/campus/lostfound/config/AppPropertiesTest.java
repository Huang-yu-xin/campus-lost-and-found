package com.campus.lostfound.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 缺少关键配置时必须启动失败，而不是带着空值运行。 */
class AppPropertiesTest {

    private static final String VALID_KEY = "k".repeat(32);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AppConfig.class);

    @Test
    void bindsCompleteConfiguration() {
        runner.withPropertyValues(
                        "app.storage.upload-dir=runtime/uploads",
                        "app.verification.hmac-key=" + VALID_KEY,
                        "app.mail.mode=dev-file",
                        "app.mail.dev-mail-dir=runtime/dev-mail")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AppProperties properties = context.getBean(AppProperties.class);
                    assertThat(properties.mail().mode()).isEqualTo(AppProperties.MailMode.DEV_FILE);
                    assertThat(properties.storage().uploadDir()).isEqualTo("runtime/uploads");
                    // 未配置时使用确认过的默认限流：5 次失败锁 15 分钟；不配置管理员
                    assertThat(properties.security().loginMaxFailures()).isEqualTo(5);
                    assertThat(properties.security().loginLockDuration()).hasMinutes(15);
                    assertThat(properties.admin() == null || !properties.admin().enabled()).isTrue();
                });
    }

    @Test
    void bindsAdminWithDefaultNickname() {
        runner.withPropertyValues(validBase("app.admin.account=admin", "app.admin.password=Admin12345",
                        "app.admin.email=admin@example.com"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AppProperties.Admin admin = context.getBean(AppProperties.class).admin();
                    assertThat(admin.enabled()).isTrue();
                    assertThat(admin.nickname()).isEqualTo("管理员");
                });
    }

    @Test
    void adminPasswordMustContainLettersAndDigits() {
        runner.withPropertyValues(validBase("app.admin.account=admin", "app.admin.password=onlyletters",
                        "app.admin.email=admin@example.com"))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void adminRequiresEmail() {
        runner.withPropertyValues(validBase("app.admin.account=admin", "app.admin.password=Admin12345"))
                .run(context -> assertThat(context).hasFailed());
    }

    private static String[] validBase(String... extra) {
        String[] base = {
                "app.storage.upload-dir=runtime/uploads",
                "app.verification.hmac-key=" + VALID_KEY,
                "app.mail.mode=dev-file",
                "app.mail.dev-mail-dir=runtime/dev-mail",
        };
        String[] all = new String[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return all;
    }

    @Test
    void failsWhenUploadDirIsBlank() {
        runner.withPropertyValues(
                        "app.storage.upload-dir=",
                        "app.verification.hmac-key=" + VALID_KEY,
                        "app.mail.mode=dev-file",
                        "app.mail.dev-mail-dir=runtime/dev-mail")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenHmacKeyIsTooShort() {
        runner.withPropertyValues(
                        "app.storage.upload-dir=runtime/uploads",
                        "app.verification.hmac-key=short",
                        "app.mail.mode=dev-file",
                        "app.mail.dev-mail-dir=runtime/dev-mail")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void devFileModeRequiresDevMailDir() {
        runner.withPropertyValues(
                        "app.storage.upload-dir=runtime/uploads",
                        "app.verification.hmac-key=" + VALID_KEY,
                        "app.mail.mode=dev-file")
                .run(context -> assertThat(context).hasFailed());
    }
}
