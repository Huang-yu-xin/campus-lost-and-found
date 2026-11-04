-- 仅隔离验证实例使用，禁止作为实际账号初始化脚本。
-- password_hash 是不可用于登录的占位内容，不是已知密码的哈希。
INSERT INTO users(id,student_record_id,role,nickname,email,phone,password_hash) VALUES
 (11,1,'STUDENT','发布测试用户','owner@example.invalid','+0000000011','!VALIDATION_ONLY_NOT_A_LOGIN_HASH!'),
 (12,2,'STUDENT','申请测试用户甲','applicant1@example.invalid','+0000000012','!VALIDATION_ONLY_NOT_A_LOGIN_HASH!'),
 (13,3,'STUDENT','申请测试用户乙','applicant2@example.invalid','+0000000013','!VALIDATION_ONLY_NOT_A_LOGIN_HASH!'),
 (14,4,'STUDENT','旁观测试用户','observer@example.invalid',NULL,'!VALIDATION_ONLY_NOT_A_LOGIN_HASH!'),
 (15,NULL,'ADMIN','测试管理员','admin@example.invalid',NULL,'!VALIDATION_ONLY_NOT_A_LOGIN_HASH!');
INSERT INTO login_identifiers(identifier,user_id) VALUES
 ('20260001',11),('90000001',11),('20260002',12),('90000002',12),
 ('20260003',13),('90000003',13),('20260004',14),('90000004',14),('admin-demo',15);
INSERT INTO items(id,publisher_id,type,category,location,occurred_date,title,description,status,published_at) VALUES
 (101,11,'FOUND','DAILY','图书馆', '2026-11-01','测试招领一','用于隔离数据库验证的虚拟黑色水杯','PUBLISHED',UTC_TIMESTAMP(6)),
 (102,11,'LOST','BOOK','教学楼', '2026-11-01','测试寻物一','用于隔离数据库验证的虚拟蓝色笔记本','PUBLISHED',UTC_TIMESTAMP(6)),
 (103,11,'FOUND','DAILY','食堂', '2026-11-01','测试招领二','用于授权与交接并发验证的虚拟物品','PUBLISHED',UTC_TIMESTAMP(6)),
 (104,11,'FOUND','DAILY','操场', '2026-11-01','测试招领三','用于举报关联检查的另一件虚拟物品','PUBLISHED',UTC_TIMESTAMP(6)),
 (105,11,'FOUND','DAILY','图书馆', '2026-11-01','并发认领物品','用于同时确认两个认领人的虚拟物品','PUBLISHED',UTC_TIMESTAMP(6)),
 (106,11,'LOST','BOOK','教学楼', '2026-11-01','并发线索物品','用于线索提交与归档并发验证的物品','PUBLISHED',UTC_TIMESTAMP(6));
INSERT INTO claims(id,item_id,publisher_id,applicant_id,description) VALUES
 (201,101,11,12,'测试特征为杯底存在一个明显三角形标记'),
 (202,101,11,13,'另一位测试申请人提供的物品特征说明'),
 (203,103,11,12,'用于并发授权验证的测试物品特征说明'),
 (204,104,11,12,'用于举报上下文校验的测试物品特征说明'),
 (205,105,11,12,'并发确认申请甲提供的完整物品特征说明'),
 (206,105,11,13,'并发确认申请乙提供的完整物品特征说明');
INSERT INTO clues(id,lost_item_id,publisher_id,provider_id,content) VALUES
 (301,102,11,12,'测试线索表示物品可能在图书馆三楼服务台'),
 (302,102,11,13,'另一条测试线索表示可以在教学楼值班室询问');
INSERT INTO images(id,uploader_id,item_id,position,storage_key,media_type,size_bytes) VALUES
 (401,11,NULL,NULL,'test/unbound-owner.png','image/png',512),
 (402,11,101,1,'test/bound-owner.png','image/png',512),
 (403,12,NULL,NULL,'test/unbound-applicant.png','image/png',512);
INSERT INTO reports(id,reporter_id,item_id,claim_id,reason) VALUES
 (601,12,101,201,'这是一条隔离验证使用的测试举报理由');
