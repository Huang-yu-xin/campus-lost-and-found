package com.campus.lostfound.modules.item.entity;

import com.campus.lostfound.modules.item.dto.CreateItemRequest;

public final class NewItem {
    private Long id;
    private final long publisherId;
    private final CreateItemRequest request;

    public NewItem(long publisherId, CreateItemRequest request) {
        this.publisherId = publisherId;
        this.request = request;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getPublisherId() { return publisherId; }
    public CreateItemRequest getRequest() { return request; }
}
