package com.campus.lostfound.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DevFileEmailSenderTest {

    @Test
    void writesMarkedSimulatedMailIntoInbox(@TempDir Path tempDir) throws Exception {
        Path inbox = tempDir.resolve("dev-mail");
        Clock clock = Clock.fixed(Instant.parse("2026-11-04T08:00:00Z"), ZoneOffset.UTC);
        new DevFileEmailSender(inbox, clock).sendVerificationCode("a@example.com", "注册", "123456",
                Duration.ofMinutes(5));

        List<Path> files;
        try (Stream<Path> stream = Files.list(inbox)) {
            files = stream.toList();
        }
        assertThat(files).hasSize(1);
        assertThat(files.get(0).getFileName().toString()).isEqualTo("20261104-080000-000_a@example.com.txt");
        String content = Files.readString(files.get(0), StandardCharsets.UTF_8);
        assertThat(content).contains("【模拟发送】", "注册", "123456", "5 分钟");
    }
}
