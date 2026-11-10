package com.campus.lostfound.modules.item.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ImageMapper {
    void insert(NewImage image);
    ImageRow find(long id);
    ImageRow lock(long id);
    boolean canRead(@Param("id") long id, @Param("userId") Long userId, @Param("admin") boolean admin);
    List<ImageRow> forItems(@Param("ids") List<Long> ids);
    void detach(long itemId);
    void bind(@Param("id") long id, @Param("itemId") long itemId, @Param("position") int position);

    record ImageRow(long id, long uploaderId, Long itemId, String storageKey, String mediaType, int sizeBytes) {}

    final class NewImage {
        private Long id;
        private final long uploaderId;
        private final String storageKey;
        private final String mediaType;
        private final int sizeBytes;

        public NewImage(long uploaderId, String storageKey, String mediaType, int sizeBytes) {
            this.uploaderId = uploaderId;
            this.storageKey = storageKey;
            this.mediaType = mediaType;
            this.sizeBytes = sizeBytes;
        }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public long getUploaderId() { return uploaderId; }
        public String getStorageKey() { return storageKey; }
        public String getMediaType() { return mediaType; }
        public int getSizeBytes() { return sizeBytes; }
    }
}
