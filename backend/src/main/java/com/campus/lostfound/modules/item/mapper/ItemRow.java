package com.campus.lostfound.modules.item.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ItemRow(long id, long publisherId, String nickname, String type, String category, String location,
        LocalDate occurredDate, String title, String description, String status, long version,
        String reviewReason, String closureReason, LocalDateTime publishedAt, LocalDateTime createdAt,
        LocalDateTime updatedAt, boolean hasConfirmedClaim) {}
