-- 举报选做功能。仅在 V001 成功后执行；不改变既有业务数据。
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone = '+00:00';

CREATE TABLE reports (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '举报 ID',
  reporter_id BIGINT NOT NULL COMMENT '举报人',
  item_id BIGINT NOT NULL COMMENT '被举报物品',
  claim_id BIGINT NULL COMMENT '关联认领，可为空；与 clue_id 互斥',
  clue_id BIGINT NULL COMMENT '关联线索，可为空；与 claim_id 互斥',
  reason VARCHAR(500) NOT NULL COMMENT '举报理由，仅举报人与处理者可看',
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / RESOLVED',
  action VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'KEEP / TAKE_DOWN / REJECT_PENDING_CLAIM',
  resolution VARCHAR(500) NULL COMMENT '处理结论',
  resolver_id BIGINT NULL COMMENT '处理管理员',
  resolved_at DATETIME(6) NULL COMMENT 'UTC 处理完成时间',
  version BIGINT NOT NULL DEFAULT 1 COMMENT '举报乐观锁版本',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 举报时间',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'UTC 更新时间',
  PRIMARY KEY (id),
  KEY idx_report_owner (reporter_id,created_at DESC,id DESC),
  KEY idx_report_pending (status,created_at,id),
  KEY idx_report_item_claim (item_id,claim_id),
  KEY idx_report_item_clue (item_id,clue_id),
  KEY idx_report_resolver (resolver_id),
  CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_report_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_report_claim FOREIGN KEY (item_id,claim_id) REFERENCES claims(item_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_report_clue FOREIGN KEY (item_id,clue_id) REFERENCES clues(lost_item_id,id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_report_resolver FOREIGN KEY (resolver_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_report_context CHECK (claim_id IS NULL OR clue_id IS NULL),
  CONSTRAINT ck_report_reason CHECK (CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 500),
  CONSTRAINT ck_report_state CHECK (
    (status='PENDING' AND action IS NULL AND resolution IS NULL AND resolver_id IS NULL AND resolved_at IS NULL)
    OR (status='RESOLVED' AND action IS NOT NULL AND action IN ('KEEP','TAKE_DOWN','REJECT_PENDING_CLAIM') AND resolution IS NOT NULL AND CHAR_LENGTH(TRIM(resolution))>=1 AND resolver_id IS NOT NULL AND resolved_at IS NOT NULL)
  ),
  CONSTRAINT ck_report_reject_context CHECK (action IS NULL OR action<>'REJECT_PENDING_CLAIM' OR claim_id IS NOT NULL),
  CONSTRAINT ck_report_version CHECK (version>=1)
) ENGINE=InnoDB COMMENT='选做举报；上下文所属物品由复合外键保障，参与者权限由服务保障';

CREATE TABLE report_access_logs (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '举报会话读取审计 ID',
  report_id BIGINT NOT NULL COMMENT '允许读取的举报上下文，举报关联创建后不可变',
  admin_id BIGINT NOT NULL COMMENT '读取者，管理员角色由服务校验',
  request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求追踪编号',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 读取时间',
  PRIMARY KEY (id),
  KEY idx_report_access (report_id,created_at,id),
  KEY idx_report_access_admin (admin_id,created_at,id),
  CONSTRAINT fk_report_access_report FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_report_access_admin FOREIGN KEY (admin_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='选做：管理员经待处理举报读取会话的审计；不复制凭证与留言';
