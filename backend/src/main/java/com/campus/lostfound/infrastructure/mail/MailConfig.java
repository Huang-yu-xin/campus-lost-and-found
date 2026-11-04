package com.campus.lostfound.infrastructure.mail;

import com.campus.lostfound.config.AppProperties;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 按 app.mail.mode 选择邮件发送方式。 */
@Configuration
public class MailConfig {

    @Bean
    EmailSender emailSender(AppProperties properties, Clock clock) {
        return switch (properties.mail().mode()) {
            case DEV_FILE -> new DevFileEmailSender(Path.of(properties.mail().devMailDir()), clock);
            case SMTP -> throw new IllegalStateException(
                    "尚未实现真实 SMTP 发送，请把 app.mail.mode 设为 dev-file；需要真实发送时新增 EmailSender 的 SMTP 实现");
        };
    }
}
