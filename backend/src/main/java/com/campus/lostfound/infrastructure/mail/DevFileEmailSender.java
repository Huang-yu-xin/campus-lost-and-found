package com.campus.lostfound.infrastructure.mail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模拟发送：把验证码邮件写成文本文件放进本机开发收件箱（app.mail.dev-mail-dir），不真实发出。
 * 只用于虚拟数据和演示，页面上需标注“模拟发送”。该目录被 Git 忽略，也不对外开放。
 */
public class DevFileEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(DevFileEmailSender.class);
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private final Path inbox;
    private final Clock clock;

    public DevFileEmailSender(Path inbox, Clock clock) {
        this.inbox = inbox;
        this.clock = clock;
    }

    @Override
    public void sendVerificationCode(String to, String purposeLabel, String code, Duration validFor) {
        Instant now = clock.instant();
        String content = """
                【模拟发送】本邮件未真实发出，仅供开发和演示使用。
                收件人：%s
                用途：%s
                验证码：%s
                有效期：%d 分钟
                生成时间（UTC）：%s
                """.formatted(to, purposeLabel, code, validFor.toMinutes(), now);
        String fileName = FILE_TIME.format(now) + "_" + to.replaceAll("[^A-Za-z0-9@._-]", "_") + ".txt";
        try {
            Files.createDirectories(inbox);
            Files.writeString(inbox.resolve(fileName), content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("写入开发收件箱失败", ex);
        }
        // 日志不记录收件邮箱和验证码
        log.info("模拟验证码邮件已写入开发收件箱 {}", inbox.toAbsolutePath());
    }
}
