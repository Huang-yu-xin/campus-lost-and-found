-- 校园失物招领系统核心结构 v0.1；目标 MySQL 8.0.34。
-- 连接目标库后执行。只在空项目库执行一次；不要使用 --force 忽略错误。
-- DDL 会隐式提交，不能用事务回滚整份建表脚本。本文件不含 DROP/TRUNCATE。
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone = '+00:00';

CREATE TABLE mock_students (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模拟名册记录 ID',
  student_no VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '模拟学号，原样保存前导零',
  campus_card_no VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '模拟校园卡号',
  real_name VARCHAR(10) NOT NULL COMMENT '虚拟姓名，不导入真实学生信息',
  enabled TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否允许用于模拟注册',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uq_mock_student_no (student_no),
  UNIQUE KEY uq_mock_card_no (campus_card_no),
  CONSTRAINT ck_mock_name CHECK (CHAR_LENGTH(real_name) BETWEEN 2 AND 10),
  CONSTRAINT ck_mock_enabled CHECK (enabled IN (0,1))
) ENGINE=InnoDB COMMENT='仅开发演示使用的虚拟学生名册；身份字段只读';

CREATE TABLE users (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户 ID，对外转字符串',
  student_record_id BIGINT NULL COMMENT '学生绑定名册 ID；管理员为空',
  role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'STUDENT' COMMENT 'STUDENT 或 ADMIN',
  nickname VARCHAR(30) NOT NULL COMMENT '对外展示昵称',
  email VARCHAR(254) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT '规范化邮箱，项目按不区分大小写判重',
  phone VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '手机号，仅私有资料和有效授权查询可读',
  password_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '专用密码哈希，禁止明文或普通 SHA 摘要',
  auth_version BIGINT NOT NULL DEFAULT 1 COMMENT '密码变更递增；会话版本不匹配即失效',
  version BIGINT NOT NULL DEFAULT 1 COMMENT '个人资料乐观锁版本',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 创建时间',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'UTC 更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_student (student_record_id),
  UNIQUE KEY uq_users_email (email),
  CONSTRAINT fk_users_student FOREIGN KEY (student_record_id) REFERENCES mock_students(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_users_role CHECK ((role='STUDENT' AND student_record_id IS NOT NULL) OR (role='ADMIN' AND student_record_id IS NULL)),
  CONSTRAINT ck_users_nickname CHECK (CHAR_LENGTH(TRIM(nickname)) BETWEEN 1 AND 30),
  CONSTRAINT ck_users_versions CHECK (version>=1 AND auth_version>=1)
) ENGINE=InnoDB COMMENT='账号与私有资料；学生身份通过名册连接获取，不重复维护';

CREATE TABLE login_identifiers (
  identifier VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '统一登录标识空间：学号、卡号或预置管理员账号',
  user_id BIGINT NOT NULL COMMENT '标识唯一所属用户',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 创建时间',
  PRIMARY KEY (identifier),
  KEY idx_login_user (user_id),
  CONSTRAINT fk_login_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_login_identifier CHECK (CHAR_LENGTH(identifier) BETWEEN 1 AND 32)
) ENGINE=InnoDB COMMENT='避免某人学号与另一人卡号相同导致登录歧义；注册事务写入两个标识';

CREATE TABLE email_verification_codes (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '验证码记录 ID',
  purpose VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'REGISTER 或 CHANGE_EMAIL',
  user_id BIGINT NULL COMMENT '修改邮箱绑定用户，注册时为空',
  subject_key BIGINT GENERATED ALWAYS AS (IFNULL(user_id,0)) STORED COMMENT '生成唯一作用域，解决 NULL 唯一键不能去重问题',
  email VARCHAR(254) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT '接收验证码的规范化邮箱',
  code_mac BINARY(32) NOT NULL COMMENT '验证码及作用域的 HMAC-SHA256，密钥不在数据库',
  key_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '服务端 HMAC 密钥版本标识，不是密钥',
  sent_at DATETIME(6) NOT NULL COMMENT 'UTC 最近发送时间，重发覆盖旧码',
  expires_at DATETIME(6) NOT NULL COMMENT 'UTC 有效期截止',
  consumed_at DATETIME(6) NULL COMMENT 'UTC 成功消费时间',
  failed_attempts INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前码错误次数，阈值由服务配置',
  version BIGINT NOT NULL DEFAULT 1 COMMENT '重发或消费的并发版本',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 首次创建时间',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'UTC 更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uq_email_code_scope (purpose,email,subject_key),
  KEY idx_email_code_expiry (expires_at),
  KEY idx_email_code_user (user_id),
  CONSTRAINT fk_email_code_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_email_code_purpose CHECK ((purpose='REGISTER' AND user_id IS NULL) OR (purpose='CHANGE_EMAIL' AND user_id IS NOT NULL)),
  CONSTRAINT ck_email_code_time CHECK (expires_at>sent_at AND (consumed_at IS NULL OR consumed_at>=sent_at)),
  CONSTRAINT ck_email_code_version CHECK (version>=1)
) ENGINE=InnoDB COMMENT='验证码不明文存储；重发、验证与消费需服务层锁和事务';

CREATE TABLE items (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物品信息 ID',
  publisher_id BIGINT NOT NULL COMMENT '发布人，创建后不可变',
  type VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'LOST 寻物 / FOUND 招领',
  category VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '固定六类物品编码',
  location VARCHAR(50) NOT NULL COMMENT '地点描述',
  occurred_date DATE NOT NULL COMMENT '北京时间业务日期，不能晚于今天，由服务校验',
  title VARCHAR(50) NOT NULL COMMENT '公开标题',
  description VARCHAR(500) NOT NULL COMMENT '公开物品描述，不放联系方式',
  status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT '发布与审核状态',
  review_reason VARCHAR(500) NULL COMMENT '最近退回原因；只返回本人或管理员',
  accepted_claim_id BIGINT NULL COMMENT '当前选中的认领申请；同物品关系由复合外键约束',
  closure_reason VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'FOUND / HANDOVER / OWNER_WITHDRAWN / VIOLATION',
  published_at DATETIME(6) NULL COMMENT 'UTC 最近一次审核通过时间',
  closed_at DATETIME(6) NULL COMMENT 'UTC 下架或归档时间',
  version BIGINT NOT NULL DEFAULT 1 COMMENT '物品乐观锁版本',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 创建时间',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'UTC 更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uq_items_id_publisher (id,publisher_id),
  KEY idx_items_public (status,published_at DESC,id DESC),
  KEY idx_items_filter (status,type,category,occurred_date,id),
  KEY idx_items_owner (publisher_id,created_at DESC,id DESC),
  KEY idx_items_review (status,created_at,id),
  KEY idx_items_accepted (id,accepted_claim_id),
  CONSTRAINT fk_items_publisher FOREIGN KEY (publisher_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_items_type CHECK (type IN ('LOST','FOUND')),
  CONSTRAINT ck_items_category CHECK (category IN ('DOCUMENT','ELECTRONICS','DAILY','BOOK','CLOTHING','OTHER')),
  CONSTRAINT ck_items_text CHECK (CHAR_LENGTH(TRIM(location)) BETWEEN 1 AND 50 AND CHAR_LENGTH(TRIM(title)) BETWEEN 1 AND 50 AND CHAR_LENGTH(TRIM(description)) BETWEEN 10 AND 500),
  CONSTRAINT ck_items_status CHECK (status IN ('PENDING_REVIEW','RETURNED','PUBLISHED','OFF_SHELF','ARCHIVED')),
  CONSTRAINT ck_items_public_time CHECK (status<>'PUBLISHED' OR published_at IS NOT NULL),
  CONSTRAINT ck_items_return_reason CHECK (status<>'RETURNED' OR (review_reason IS NOT NULL AND CHAR_LENGTH(TRIM(review_reason))>=1)),
  CONSTRAINT ck_items_closure CHECK (
    (status IN ('PENDING_REVIEW','RETURNED','PUBLISHED') AND closure_reason IS NULL AND closed_at IS NULL)
    OR (status='OFF_SHELF' AND closure_reason IS NOT NULL AND closure_reason IN ('OWNER_WITHDRAWN','VIOLATION') AND closed_at IS NOT NULL)
    OR (status='ARCHIVED' AND closed_at IS NOT NULL AND closure_reason IS NOT NULL AND ((type='LOST' AND closure_reason='FOUND') OR (type='FOUND' AND closure_reason='HANDOVER')))
  ),
  CONSTRAINT ck_items_accepted_type CHECK (accepted_claim_id IS NULL OR type='FOUND'),
  CONSTRAINT ck_items_version CHECK (version>=1)
) ENGINE=InnoDB COMMENT='寻物与招领共用物品表；业务历史存在后编辑限制由服务执行';

CREATE TABLE images (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '上传图片 ID',
  uploader_id BIGINT NOT NULL COMMENT '上传者',
  item_id BIGINT NULL COMMENT '绑定物品；未绑定时为空',
  position TINYINT UNSIGNED NULL COMMENT '绑定后的槽位 1～3；未绑定为空',
  storage_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '服务端随机存储键，不接受客户端文件路径',
  media_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '真实检测的 MIME 类型',
  size_bytes INT UNSIGNED NOT NULL COMMENT '文件实际字节数',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 上传时间',
  PRIMARY KEY (id),
  UNIQUE KEY uq_images_storage (storage_key),
  UNIQUE KEY uq_images_slot (item_id,position),
  KEY idx_images_owner (uploader_id,created_at,id),
  KEY idx_images_item_owner (item_id,uploader_id),
  CONSTRAINT fk_images_uploader FOREIGN KEY (uploader_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_images_item_owner FOREIGN KEY (item_id,uploader_id) REFERENCES items(id,publisher_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_images_slot CHECK ((item_id IS NULL AND position IS NULL) OR (item_id IS NOT NULL AND position IS NOT NULL AND position BETWEEN 1 AND 3)),
  CONSTRAINT ck_images_size CHECK (size_bytes BETWEEN 1 AND 2097152),
  CONSTRAINT ck_images_type CHECK (media_type IN ('image/jpeg','image/png'))
) ENGINE=InnoDB COMMENT='上传记录及最多三张绑定；复合外键防止使用他人图片';

CREATE TABLE claims (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '认领申请 ID',
  item_id BIGINT NOT NULL COMMENT '招领物品 ID，类型由服务校验',
  publisher_id BIGINT NOT NULL COMMENT '冗余发布人，复合外键保证与物品一致',
  applicant_id BIGINT NOT NULL COMMENT '申请人',
  description VARCHAR(500) NOT NULL COMMENT '私有物品特征说明',
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / CONFIRMED / REJECTED / COMPLETED / CLOSED',
  reason VARCHAR(500) NULL COMMENT '驳回原因或系统关闭说明',
  confirmed_at DATETIME(6) NULL COMMENT 'UTC 确认时间',
  completed_at DATETIME(6) NULL COMMENT 'UTC 完成交接时间',
  version BIGINT NOT NULL DEFAULT 1 COMMENT '认领乐观锁版本',
  winner_item_id BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('CONFIRMED','COMPLETED') THEN item_id ELSE NULL END) STORED COMMENT '每物品最多一条确认/完成记录的数据库保护',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 申请时间',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'UTC 更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uq_claim_once (item_id,applicant_id),
  UNIQUE KEY uq_claim_winner (winner_item_id),
  UNIQUE KEY uq_claim_item_id (item_id,id),
  KEY idx_claim_owner (publisher_id,created_at DESC,id DESC),
  KEY idx_claim_applicant (applicant_id,created_at DESC,id DESC),
  KEY idx_claim_item_status (item_id,status,id),
  KEY idx_claim_item_owner (item_id,publisher_id),
  CONSTRAINT fk_claim_item_owner FOREIGN KEY (item_id,publisher_id) REFERENCES items(id,publisher_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_claim_applicant FOREIGN KEY (applicant_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_claim_not_self CHECK (applicant_id<>publisher_id),
  CONSTRAINT ck_claim_description CHECK (CHAR_LENGTH(TRIM(description)) BETWEEN 10 AND 500),
  CONSTRAINT ck_claim_status CHECK (status IN ('PENDING','CONFIRMED','REJECTED','COMPLETED','CLOSED')),
  CONSTRAINT ck_claim_reason CHECK (status NOT IN ('REJECTED','CLOSED') OR (reason IS NOT NULL AND CHAR_LENGTH(TRIM(reason))>=1)),
  CONSTRAINT ck_claim_times CHECK ((status NOT IN ('CONFIRMED','COMPLETED') OR confirmed_at IS NOT NULL) AND (status<>'COMPLETED' OR (completed_at IS NOT NULL AND completed_at>=confirmed_at))),
  CONSTRAINT ck_claim_version CHECK (version>=1)
) ENGINE=InnoDB COMMENT='认领私有申请；唯一获选约束不替代物品状态与事务校验';

ALTER TABLE items ADD CONSTRAINT fk_items_accepted_claim
  FOREIGN KEY (id,accepted_claim_id) REFERENCES claims(item_id,id)
  ON DELETE RESTRICT ON UPDATE RESTRICT;

CREATE TABLE clues (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '线索 ID',
  lost_item_id BIGINT NOT NULL COMMENT '寻物信息 ID，LOST 类型由服务校验',
  publisher_id BIGINT NOT NULL COMMENT '冗余发布人，复合外键保证一致',
  provider_id BIGINT NOT NULL COMMENT '线索提供者',
  content VARCHAR(500) NOT NULL COMMENT '私有文字线索',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 提交时间',
  PRIMARY KEY (id),
  UNIQUE KEY uq_clue_once (lost_item_id,provider_id),
  UNIQUE KEY uq_clue_item_id (lost_item_id,id),
  KEY idx_clue_owner (publisher_id,created_at DESC,id DESC),
  KEY idx_clue_provider (provider_id,created_at DESC,id DESC),
  KEY idx_clue_item_owner (lost_item_id,publisher_id),
  CONSTRAINT fk_clue_item_owner FOREIGN KEY (lost_item_id,publisher_id) REFERENCES items(id,publisher_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_clue_provider FOREIGN KEY (provider_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_clue_not_self CHECK (provider_id<>publisher_id),
  CONSTRAINT ck_clue_content CHECK (CHAR_LENGTH(TRIM(content)) BETWEEN 10 AND 500)
) ENGINE=InnoDB COMMENT='简化线索，无审核状态、图片、关联招领或已读字段';

CREATE TABLE claim_messages (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '认领留言 ID',
  claim_id BIGINT NOT NULL COMMENT '认领申请 ID',
  sender_id BIGINT NOT NULL COMMENT '发送人，须是申请双方之一，由服务校验',
  content VARCHAR(1000) NOT NULL COMMENT '私有纯文本，不在日志中复制',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 发送时间',
  PRIMARY KEY (id),
  KEY idx_claim_message_order (claim_id,created_at,id),
  KEY idx_claim_message_sender (sender_id),
  CONSTRAINT fk_claim_message_claim FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_claim_message_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_claim_message_content CHECK (CHAR_LENGTH(TRIM(content)) BETWEEN 1 AND 1000)
) ENGINE=InnoDB COMMENT='认领留言；跨行参与者与终态权限由服务校验';

CREATE TABLE clue_messages (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '线索留言 ID',
  clue_id BIGINT NOT NULL COMMENT '线索 ID',
  sender_id BIGINT NOT NULL COMMENT '发送人，须是线索双方之一，由服务校验',
  content VARCHAR(1000) NOT NULL COMMENT '私有纯文本',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 发送时间',
  PRIMARY KEY (id),
  KEY idx_clue_message_order (clue_id,created_at,id),
  KEY idx_clue_message_sender (sender_id),
  CONSTRAINT fk_clue_message_clue FOREIGN KEY (clue_id) REFERENCES clues(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_clue_message_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_clue_message_content CHECK (CHAR_LENGTH(TRIM(content)) BETWEEN 1 AND 1000)
) ENGINE=InnoDB COMMENT='线索留言；与认领分表以保留真实外键';

CREATE TABLE claim_contact_permissions (
  claim_id BIGINT NOT NULL COMMENT '授权所属认领',
  grantor_id BIGINT NOT NULL COMMENT '授权人；另一方从认领关系推导',
  phone_visible TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '自己的手机号是否授权',
  email_visible TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '自己的邮箱是否授权',
  version BIGINT NOT NULL DEFAULT 1 COMMENT '不存在时接口视作 0，首次写入为 1',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 首次设置时间',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'UTC 修改时间',
  PRIMARY KEY (claim_id,grantor_id),
  KEY idx_contact_grantor (grantor_id,claim_id),
  CONSTRAINT fk_contact_claim FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_contact_grantor FOREIGN KEY (grantor_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_contact_flags CHECK (phone_visible IN (0,1) AND email_visible IN (0,1)),
  CONSTRAINT ck_contact_version CHECK (version>=1)
) ENGINE=InnoDB COMMENT='仅存授权开关，不复制手机号/邮箱；有效性还取决于认领和物品状态';

CREATE TABLE review_records (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审核记录 ID',
  item_id BIGINT NOT NULL COMMENT '审核物品',
  operator_id BIGINT NOT NULL COMMENT '操作者，管理员角色由服务校验',
  action VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'APPROVE / RETURN / TAKE_DOWN',
  reason VARCHAR(500) NULL COMMENT '退回或违规下架原因；通过时可为空',
  item_version BIGINT NOT NULL COMMENT '该次操作完成后的物品版本',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 审核时间',
  PRIMARY KEY (id),
  KEY idx_review_item (item_id,created_at DESC,id DESC),
  KEY idx_review_operator (operator_id),
  CONSTRAINT fk_review_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_review_operator FOREIGN KEY (operator_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_review_action CHECK (action IN ('APPROVE','RETURN','TAKE_DOWN')),
  CONSTRAINT ck_review_reason CHECK (action='APPROVE' OR (reason IS NOT NULL AND CHAR_LENGTH(TRIM(reason))>=1)),
  CONSTRAINT ck_review_version CHECK (item_version>=1)
) ENGINE=InnoDB COMMENT='审核历史，只追加；禁止用最后一条覆盖此前审核';

CREATE TABLE operation_logs (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '操作审计 ID',
  actor_id BIGINT NULL COMMENT '操作者，系统操作为空',
  entity_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'USER / ITEM / CLAIM / CLUE / CONTACT_PERMISSION / REPORT',
  entity_id BIGINT NOT NULL COMMENT '目标对象 ID；通用审计引用由服务验证，无多态外键',
  action VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定操作编码',
  before_status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '变更前状态，不复制内容',
  after_status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '变更后状态',
  request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求追踪编号',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'UTC 操作时间',
  PRIMARY KEY (id),
  KEY idx_operation_entity (entity_type,entity_id,created_at,id),
  KEY idx_operation_actor (actor_id,created_at,id),
  KEY idx_operation_request (request_id),
  CONSTRAINT fk_operation_actor FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_operation_entity CHECK (entity_type IN ('USER','ITEM','CLAIM','CLUE','CONTACT_PERMISSION','REPORT'))
) ENGINE=InnoDB COMMENT='关键状态与授权审计，只追加，不记录密码验证码联系方式或留言正文';
