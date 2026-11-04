package com.campus.lostfound.modules.account.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CodeSendLimiterTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-04T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void limitsEachSourceWithinWindowAndRecoversAfterIt() {
        MutableClock clock = new MutableClock();
        AppProperties.Verification verification = new AppProperties.Verification(
                "k".repeat(32), "k1", Duration.ofMinutes(5), Duration.ofSeconds(60), 5, 3, Duration.ofHours(1));
        CodeSendLimiter limiter = new CodeSendLimiter(
                new AppProperties(null, verification, null, null, null), clock);

        for (int i = 0; i < 3; i++) {
            limiter.acquire("10.0.0.1");
        }
        assertThatThrownBy(() -> limiter.acquire("10.0.0.1")).isInstanceOf(BusinessException.class);
        assertThatCode(() -> limiter.acquire("10.0.0.2")).doesNotThrowAnyException();

        clock.now = clock.now.plus(Duration.ofHours(1));
        assertThatCode(() -> limiter.acquire("10.0.0.1")).doesNotThrowAnyException();
    }
}
