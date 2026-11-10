package com.campus.lostfound.modules.item;

import static org.assertj.core.api.Assertions.assertThat;
import com.campus.lostfound.config.AppProperties;
import com.campus.lostfound.modules.item.service.ImageCleanupService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties={"app.admin.account=", "app.storage.cleanup-enabled=false"})
@EnabledIf("com.campus.lostfound.TestDatabase#configured")
class ImageCleanupIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AppProperties properties;
    @Autowired PlatformTransactionManager manager;
    @Autowired ImageCleanupService cleanup;
    long owner, item;
    Path directory;
    List<String> keys = new ArrayList<>();
    @BeforeEach void setup() throws Exception {
        directory = Path.of(properties.storage().uploadDir()); Files.createDirectories(directory);
        String email = UUID.randomUUID()+"@example.test";
        jdbc.update("INSERT INTO users(role,nickname,email,password_hash) VALUES('ADMIN','图片清理测试',?,'unused')", email);
        owner = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        jdbc.update("INSERT INTO items(publisher_id,type,category,location,occurred_date,title,description) VALUES(?,'FOUND','OTHER','图书馆','2026-11-01','测试物品','图片清理测试所需的完整物品描述')", owner);
        item = jdbc.queryForObject("SELECT id FROM items WHERE publisher_id=?", Long.class, owner);
    }
    @AfterEach void teardown() throws Exception {
        jdbc.update("DELETE FROM images WHERE uploader_id=?", owner);
        jdbc.update("DELETE FROM items WHERE id=?", item);
        jdbc.update("DELETE FROM users WHERE id=?", owner);
        for (String key: keys) { jdbc.update("DELETE FROM image_cleanup_jobs WHERE storage_key=?", key); Files.deleteIfExists(directory.resolve(key)); }
    }
    long image(boolean old, boolean bound) throws Exception {
        String key = UUID.randomUUID()+".png"; keys.add(key); Files.write(directory.resolve(key), new byte[]{1});
        jdbc.update("INSERT INTO images(uploader_id,item_id,position,storage_key,media_type,size_bytes,created_at) VALUES(?,?,?,?,'image/png',1,?)",
                owner, bound?item:null, bound?1:null, key, LocalDateTime.now(ZoneOffset.UTC).minusHours(old?25:1));
        return jdbc.queryForObject("SELECT id FROM images WHERE storage_key=?", Long.class, key);
    }
    @Test void onlyExpiredUnboundImagesAreRemoved() throws Exception {
        long expired = image(true,false), recent = image(false,false), bound = image(true,true);
        cleanup.cleanup();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM images WHERE id=?", Integer.class, expired)).isZero();
        assertThat(Files.exists(directory.resolve(keys.get(0)))).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM images WHERE id IN (?,?)", Integer.class, recent,bound)).isEqualTo(2);
        assertThat(Files.exists(directory.resolve(keys.get(1)))).isTrue();
        assertThat(Files.exists(directory.resolve(keys.get(2)))).isTrue();
    }
    @Test void failedDeletionIsRetriedByANewWorker() throws Exception {
        image(true,false); Path file = directory.resolve(keys.getFirst());
        Files.delete(file); Files.createDirectory(file); Files.write(file.resolve("busy"),new byte[]{1});
        cleanup.cleanup();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM image_cleanup_jobs WHERE storage_key=?", Integer.class,keys.getFirst())).isEqualTo(1);
        Files.delete(file.resolve("busy"));
        new ImageCleanupService(jdbc,manager,properties,Clock.systemUTC(),24).cleanup();
        assertThat(Files.exists(file)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM image_cleanup_jobs WHERE storage_key=?", Integer.class,keys.getFirst())).isZero();
    }
    @Test void concurrentlyBindingImageIsSkippedThenPreserved() throws Exception {
        long id = image(true,false);
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var writer = executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT id FROM images WHERE id=? FOR UPDATE",Long.class,id);
                locked.countDown();
                try { if (!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new RuntimeException(ex); }
                jdbc.update("UPDATE images SET item_id=?,position=1 WHERE id=?",item,id);
            }));
            try {assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue(); cleanup.cleanup();}
            finally {release.countDown();}
            writer.get(10,TimeUnit.SECONDS);
        }
        cleanup.cleanup();
        assertThat(jdbc.queryForObject("SELECT item_id FROM images WHERE id=?",Long.class,id)).isEqualTo(item);
        assertThat(Files.exists(directory.resolve(keys.getFirst()))).isTrue();
    }
}
