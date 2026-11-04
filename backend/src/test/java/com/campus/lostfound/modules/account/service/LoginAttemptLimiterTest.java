package com.campus.lostfound.modules.account.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {

    /** 可手动拨动的时钟。 */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-04T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

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

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(
            new AppProperties(null, null, null, new AppProperties.Security(5, Duration.ofMinutes(15)), null),
            clock);

    @Test
    void locksAfterFiveFailuresAndUnlocksAfterFifteenMinutes() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("20260001");
        }
        assertThatCode(() -> limiter.checkAllowed("20260001")).doesNotThrowAnyException();

        limiter.recordFailure("20260001");
        assertThatThrownBy(() -> limiter.checkAllowed("20260001"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> org.assertj.core.api.Assertions.assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.RATE_LIMITED))
                .hasMessageContaining("15 分钟");

        clock.advance(Duration.ofMinutes(15));
        assertThatCode(() -> limiter.checkAllowed("20260001")).doesNotThrowAnyException();
    }

    @Test
    void successResetsCountAndAccountsAreIndependent() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("a");
        }
        limiter.reset("a");
        limiter.recordFailure("a");
        assertThatCode(() -> limiter.checkAllowed("a")).doesNotThrowAnyException();

        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("b");
        }
        assertThatCode(() -> limiter.checkAllowed("a")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.checkAllowed("b")).isInstanceOf(BusinessException.class);
    }
}
