package com.campus.lostfound.config;

import com.campus.lostfound.modules.item.service.ImageCleanupService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="app.storage.cleanup-enabled", havingValue="true", matchIfMissing=true)
public class ImageCleanupSchedule {
    private final ImageCleanupService cleanup;
    public ImageCleanupSchedule(ImageCleanupService cleanup) { this.cleanup = cleanup; }

    @Scheduled(fixedDelayString="${app.storage.cleanup-delay-ms:3600000}",
            initialDelayString="${app.storage.cleanup-initial-delay-ms:60000}")
    public void cleanup() { cleanup.cleanup(); }
}
