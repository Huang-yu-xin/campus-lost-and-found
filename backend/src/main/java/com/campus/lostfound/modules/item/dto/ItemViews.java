package com.campus.lostfound.modules.item.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ItemViews {
    private ItemViews() {}

    public record Publisher(String id, String nickname) {}

    public record Image(String id, String url, String mediaType, int sizeBytes) {}

    public record PublicItem(String id, String type, String category, String location, LocalDate occurredDate,
            String title, String description, List<Image> images, Publisher publisher,
            Instant publishedAt, Instant createdAt, boolean hasConfirmedClaim) {}

    public record ItemRecord(String id, String type, String category, String location, LocalDate occurredDate,
            String title, String description, List<Image> images, Publisher publisher,
            Instant publishedAt, Instant createdAt, boolean hasConfirmedClaim,
            String status, long version, String reviewReason, String closureReason, Instant updatedAt) {}
}
