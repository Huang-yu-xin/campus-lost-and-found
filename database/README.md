# 校园失物招领数据库文件

目标：MySQL 8.0.34。设计文档见 [数据库设计](../docs/数据库设计.md)，字段字典见 [数据库字段字典](../docs/数据库字段字典.md)。

## 文件顺序

1. `000_create_database.sql`：创建项目库。
2. `migrations/V001__core_schema.sql`：核心 13 张表。
3. `migrations/V002__optional_reports.sql`：举报的 2 张表。甲乙丙合并版本已接入举报功能，本地完整运行需要执行。
4. `migrations/V003__item_submission_key.sql`：发布防重，已有开发库也需要执行一次。只依赖 V001。
5. `seeds/S001__mock_students.sql`：仅虚拟名册，不创建账号。

初始化脚本用于空项目库，各迁移只执行一次。已执行 V001 的库可单独运行尚未执行的 V003。脚本不含 DROP/TRUNCATE，也不关闭外键检查。执行出错时停下核对，MySQL DDL 会隐式提交。

当前没有引入 Flyway；V001/V002 是有序版本文件命名，不代表会自动迁移。团队记录已执行版本，后续修改使用新迁移，不反复编辑已投入使用的 V001。未来接入自动迁移工具时另行配置。

## 手动初始化示例

以下仅为后续实际建库操作说明，本次未在现有服务执行。先在项目根目录打开终端，使用具备项目建库权限的本地账户进入 mysql 客户端，让客户端交互提示密码；不要把密码写入命令行或仓库。

在 MySQL 客户端内执行：

```sql
SOURCE database/000_create_database.sql;
USE campus_lost_found;
SOURCE database/migrations/V001__core_schema.sql;
-- 合并版本的举报功能需要这两张表：
SOURCE database/migrations/V002__optional_reports.sql;
SOURCE database/migrations/V003__item_submission_key.sql;
SOURCE database/seeds/S001__mock_students.sql;
```

应用使用项目专用账号，不使用 root。迁移账号和应用账号分开；应用权限仅覆盖业务所需数据访问，不授予修改系统库、建表或删除表权限。三个人各自使用本地数据库，通过相同迁移保持结构一致。

测试名册可通过真实注册流程产生学生账号；管理员由后续受控初始化工具生成密码哈希并注册登录标识。脚本不提供通用明文管理员密码。

## 隔离验证

`tools/validate_database.py` 只使用 Python 标准库与本机 MySQL 二进制。它创建全新的系统临时目录，用 `--no-defaults` 防止读取现有 MySQL 配置，仅绑定 127.0.0.1 随机端口，导入独立测试数据并验证，最后关闭该临时进程。

可在 PowerShell 7 中运行（python 换成可用解释器路径）：

```text
python database/tools/validate_database.py --mysql-home D:/MySQL/mysql-8.0.34-winx64
```

报告写到 `docs/数据库设计验证结果.json`，真实字段元数据写到 `database/validation_metadata.json`，字段字典写到 `docs/数据库字段字典.md`。临时目录保留日志与虚拟数据，脚本不递归删除目录。

`tests/fixtures.sql` 只能用于隔离验证，密码字段是不能登录的占位内容，不要导入正式开发账号库。没有任何真实个人资料。

## 开发统一约定

- 连接使用 UTF-8、UTC 和严格 SQL 模式，不能靠非严格模式截断超长输入。
- 时间按 UTC 存储，业务发生日期按北京时间判断；私有数据查询必须带归属条件。
- 密码、HMAC 密钥、数据库凭据不提交 Git。
- 数据库字段 snake_case，对外 DTO camelCase；生成列不出现在 INSERT/UPDATE 字段列表中。
- 联系方式只存 users，授权表不保存副本；终态读取检查业务状态。
- 每次修改表结构同步数据字典、接口模型、追溯和相关测试。
