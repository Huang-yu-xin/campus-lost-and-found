package com.campus.lostfound.modules.account.dto;

/** 验证码已发送：有效期与再次发送前需等待的秒数。 */
public record CodeSentData(long expiresInSeconds, long retryAfterSeconds) {
}
