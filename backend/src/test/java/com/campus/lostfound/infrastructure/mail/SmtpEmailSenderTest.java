package com.campus.lostfound.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpEmailSenderTest {
    @Test void sendsUtf8CodeThroughActualLocalSmtpProtocol() throws Exception {
        try (var smtp = new LocalSmtp(false)) {
            sender(smtp).sendVerificationCode("student@example.test", "修改邮箱", "123456", Duration.ofMinutes(5));
            var mail = new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(smtp.received.get(5, TimeUnit.SECONDS)));
            assertThat(mail.getFrom()[0].toString()).isEqualTo("campus@example.test");
            assertThat(mail.getAllRecipients()[0].toString()).isEqualTo("student@example.test");
            assertThat(mail.getSubject()).contains("修改邮箱");
            assertThat(mail.getContent().toString()).contains("123456", "5 分钟", "请勿向他人提供验证码");
        }
    }
    @Test void smtpRejectionIsPropagatedInsteadOfClaimingDelivery() throws Exception {
        try (var smtp = new LocalSmtp(true)) {
            assertThatThrownBy(() -> sender(smtp).sendVerificationCode("student@example.test", "注册", "654321", Duration.ofMinutes(5)))
                    .isInstanceOf(MailException.class);
        }
    }
    @Test void smtpRequiresConfiguredSenderAndValidFromAddress() {
        assertThatThrownBy(() -> new SmtpEmailSender(null,"campus@example.test")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SmtpEmailSender(new JavaMailSenderImpl(),"bad\r\nBcc:secret@example.test"))
                .isInstanceOf(IllegalStateException.class);
    }
    private SmtpEmailSender sender(LocalSmtp smtp) {
        var sender = new JavaMailSenderImpl(); sender.setHost("127.0.0.1"); sender.setPort(smtp.socket.getLocalPort());
        sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", "3000");
        sender.getJavaMailProperties().put("mail.smtp.timeout", "3000");
        sender.getJavaMailProperties().put("mail.smtp.writetimeout", "3000");
        return new SmtpEmailSender(sender,"campus@example.test");
    }
    /** 测试服务只监听回环地址，不联系外部邮箱。 */
    private static final class LocalSmtp implements AutoCloseable {
        final ServerSocket socket;
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final CompletableFuture<byte[]> received = new CompletableFuture<>();
        final Future<?> task;
        LocalSmtp(boolean reject) throws IOException {
            socket = new ServerSocket(0,1,InetAddress.getLoopbackAddress()); socket.setSoTimeout(5000);
            task = executor.submit(() -> {
                try (var client = socket.accept(); var in = new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.US_ASCII));
                        var out = new PrintWriter(new OutputStreamWriter(client.getOutputStream(),StandardCharsets.US_ASCII),true)) {
                    client.setSoTimeout(5000); reply(out,"220 local test mail");
                    String line;
                    while ((line=in.readLine()) != null) {
                        if (line.startsWith("EHLO") || line.startsWith("HELO")) reply(out,"250 localhost");
                        else if (line.startsWith("MAIL FROM")) reply(out,"250 OK");
                        else if (line.startsWith("RCPT TO")) reply(out,reject?"550 rejected by test":"250 OK");
                        else if (line.equals("DATA")) {
                            reply(out,"354 send mail"); var body = new ByteArrayOutputStream();
                            while ((line=in.readLine()) != null && !line.equals(".")) {
                                if (line.startsWith("..")) line=line.substring(1);
                                body.write((line+"\r\n").getBytes(StandardCharsets.US_ASCII));
                            }
                            received.complete(body.toByteArray()); reply(out,"250 queued");
                        } else if (line.equals("QUIT")) {reply(out,"221 bye"); break;}
                        else reply(out,"250 OK");
                    }
                } catch (IOException ex) {received.completeExceptionally(ex); throw new RuntimeException(ex);}
            });
        }
        private static void reply(PrintWriter out,String line) {out.print(line+"\r\n"); out.flush();}
        @Override public void close() throws Exception {
            try {task.get(6,TimeUnit.SECONDS);} finally {socket.close(); executor.shutdownNow();}
        }
    }
}
