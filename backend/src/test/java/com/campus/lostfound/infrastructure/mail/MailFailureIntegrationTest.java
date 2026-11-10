package com.campus.lostfound.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import com.campus.lostfound.modules.account.service.VerificationCodeService;
import com.campus.lostfound.modules.account.service.VerificationPurpose;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties={"app.admin.account=", "app.storage.cleanup-enabled=false"})
@EnabledIf("com.campus.lostfound.TestDatabase#configured")
class MailFailureIntegrationTest {
    @MockitoBean EmailSender sender;
    @Autowired VerificationCodeService verification;
    @Autowired JdbcTemplate jdbc;
    @Test void failedDeliveryRollsBackCodeAndDoesNotStartResendCooldown() {
        String email = UUID.randomUUID()+"@example.test";
        doThrow(new MailSendException("test rejection")).when(sender).sendVerificationCode(anyString(),anyString(),anyString(),any());
        for (int i=0;i<2;i++) {
            assertThatThrownBy(() -> verification.send(VerificationPurpose.REGISTER,null,email)).isInstanceOf(MailSendException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_verification_codes WHERE email=?", Integer.class,email)).isZero();
        }
    }
}
