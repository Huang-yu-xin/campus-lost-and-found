package com.campus.lostfound.modules.moderation.dto;

import java.time.Instant;

/** 审核处置记录，字段与 openapi.yaml 的 ReviewRecord 对齐。 */
public record ReviewRecord(String id, String itemId, String action, String reason, String operatorId,
        Instant createdAt) {
}
