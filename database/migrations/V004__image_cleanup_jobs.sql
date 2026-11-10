-- 在更新应用前执行一次；持久化文件清理任务，进程重启或文件删除失败后可重试。
CREATE TABLE image_cleanup_jobs (
  storage_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (storage_key),
  KEY idx_image_cleanup_created (created_at, storage_key)
) ENGINE=InnoDB COMMENT='已解除图片引用后的待删除文件';

CREATE INDEX idx_images_unbound_age ON images (item_id, created_at, id);
