package com.campus.lostfound.modules.account.service;

import com.campus.lostfound.common.error.BusinessException;
import com.campus.lostfound.common.error.ErrorCode;
import com.campus.lostfound.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * 按来源（客户端 IP）限制验证码发送次数，防止同一来源换着邮箱大量发码。
 * 与“同一邮箱 60 秒内不能重发”叠加生效。记录在内存中，第一版单实例部署足够。
 * 注意：开发时经前端代理访问，所有请求的来源都是本机，次数是共用的。
 */
@Component
public class CodeSendLimiter {

    private final ConcurrentMap<String, Deque<Instant>> sends = new ConcurrentHashMap<>();
    private final int maxSends;
    private final Duration window;
    private final Clock clock;

    public CodeSendLimiter(AppProperties properties, Clock clock) {
        this.maxSends = properties.verification().maxSendsPerSource();
        this.window = properties.verification().sourceWindow();
        this.clock = clock;
    }

    /** 记一次发送；该来源在时间窗内已达上限时抛出 429，本次不计数。 */
    public void acquire(String source) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(window);
        sends.compute(source, (key, history) -> {
            Deque<Instant> recent = history == null ? new ArrayDeque<>() : history;
            while (!recent.isEmpty() && !recent.peekFirst().isAfter(windowStart)) {
                recent.pollFirst();
            }
            if (recent.size() >= maxSends) {
                throw new BusinessException(ErrorCode.RATE_LIMITED, "验证码发送过于频繁，请稍后再试");
            }
            recent.addLast(now);
            return recent;
        });
    }
}
