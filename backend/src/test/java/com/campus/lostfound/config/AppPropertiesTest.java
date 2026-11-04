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
                });
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
