package com.campus.lostfound.modules.account.mapper;

import java.time.Instant;

/** email_verification_codes 的一行。codeMac 是验证码的 HMAC 摘要，不是明文。 */
public record EmailCodeRow(Long id, byte[] codeMac, String keyId, Instant sentAt, Instant expiresAt,
        Instant consumedAt, Integer failedAttempts) {
}
