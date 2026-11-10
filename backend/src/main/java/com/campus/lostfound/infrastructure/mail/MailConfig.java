package com.campus.lostfound.infrastructure.mail;

import com.campus.lostfound.config.AppProperties;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;

/** 按 app.mail.mode 选择邮件发送方式。 */
@Configuration
public class MailConfig {

    @Bean
    EmailSender emailSender(AppProperties properties, Clock clock, ObjectProvider<JavaMailSender> smtp,
            @Value("${app.mail.from:}") String from) {
        return switch (properties.mail().mode()) {
            case DEV_FILE -> new DevFileEmailSender(Path.of(properties.mail().devMailDir()), clock);
            case SMTP -> new SmtpEmailSender(smtp.getIfAvailable(), from);
        };
    }
}
