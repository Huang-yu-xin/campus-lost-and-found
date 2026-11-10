package com.campus.lostfound.modules.item.service;

import com.campus.lostfound.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 先在事务中移除过期且未绑定的图片，再按持久化任务删文件，失败留待重试。 */
@Service
public class ImageCleanupService {
    private static final Logger log = LoggerFactory.getLogger(ImageCleanupService.class);
    private static final Pattern KEY = Pattern.compile("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.(?:png|jpg)");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Path directory;
    private final Clock clock;
    private final Duration retention;

    public ImageCleanupService(JdbcTemplate jdbc, PlatformTransactionManager manager, AppProperties properties,
            Clock clock, @Value("${app.storage.unbound-retention-hours:24}") long hours) {
        if (hours < 1 || hours > 8760) throw new IllegalArgumentException("图片保留时长需为 1～8760 小时");
        this.jdbc = jdbc; this.clock = clock; this.retention = Duration.ofHours(hours);
        this.directory = Path.of(properties.storage().uploadDir()).toAbsolutePath().normalize();
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 每轮最多处理 100 条；绑定事务持有的行跳过，已绑定图片永不进入清理队列。 */
    public int cleanup() {
        transaction.executeWithoutResult(ignored -> {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT id,storage_key FROM images WHERE item_id IS NULL AND created_at < ?
                    ORDER BY created_at,id LIMIT 100 FOR UPDATE SKIP LOCKED
                    """, Timestamp.from(clock.instant().minus(retention)));
            for (var row : rows) {
                String key = row.get("storage_key").toString();
                if (!KEY.matcher(key).matches()) { log.warn("跳过存储键异常的图片记录 {}", row.get("id")); continue; }
                jdbc.update("INSERT IGNORE INTO image_cleanup_jobs(storage_key) VALUES (?)", key);
                jdbc.update("DELETE FROM images WHERE id=? AND item_id IS NULL", row.get("id"));
            }
        });
        // 上面的事务已提交；此处文件删除失败不会回滚出一条指向缺失文件的图片记录。
        int deleted = 0;
        for (String key : jdbc.queryForList("SELECT storage_key FROM image_cleanup_jobs ORDER BY created_at,storage_key LIMIT 100", String.class)) {
            if (!KEY.matcher(key).matches()) { log.warn("跳过存储键异常的图片清理任务"); continue; }
            Path file = directory.resolve(key).normalize();
            if (!directory.equals(file.getParent())) continue;
            try {
                Files.deleteIfExists(file);
                jdbc.update("DELETE FROM image_cleanup_jobs WHERE storage_key=?", key);
                deleted++;
            } catch (IOException ex) { log.warn("图片文件清理失败，将在下一轮重试", ex); }
        }
        return deleted;
    }
}
