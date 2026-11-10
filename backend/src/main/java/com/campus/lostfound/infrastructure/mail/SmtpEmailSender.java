package com.campus.lostfound.infrastructure.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import java.time.Duration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/** 通过配置的 SMTP 服务发送纯文本验证码；验证码不写入应用日志。 */
public final class SmtpEmailSender implements EmailSender {
    private final JavaMailSender sender;
    private final InternetAddress from;
    public SmtpEmailSender(JavaMailSender sender, String from) {
        if (sender == null) throw new IllegalStateException("SMTP 模式需要配置 spring.mail.host");
        this.sender = sender;
        try {
            if (from == null || from.isBlank() || from.contains("\r") || from.contains("\n"))
                throw new MessagingException("missing from");
            this.from = new InternetAddress(from, true); this.from.validate();
        } catch (MessagingException ex) { throw new IllegalStateException("SMTP 模式需要配置有效的 app.mail.from 发件邮箱"); }
    }
    @Override public void sendVerificationCode(String to, String purposeLabel, String code, Duration validFor) {
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(new InternetAddress(to, true));
            helper.setSubject("校园失物招领：" + purposeLabel + "验证码");
            helper.setText("你的" + purposeLabel + "验证码是：" + code + "\n有效期 "
                    + validFor.toMinutes() + " 分钟。请勿向他人提供验证码。\n如非本人操作，请忽略此邮件。", false);
            sender.send(message);
        } catch (MessagingException ex) { throw new IllegalStateException("邮件格式错误，发送失败", ex); }
    }
}
