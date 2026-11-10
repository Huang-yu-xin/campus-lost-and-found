# 校园失物招领系统

三人软件工程课程项目，使用 Java 21、Spring Boot、MySQL 和 Vue 3。

请从 [开发实施手册](docs/开发实施手册.md) 开始：它包含已确认需求、三人分工、基础框架搭建、模块开发、联调验收和个人课程材料安排。

配套文件：

- [三人开发任务清单](docs/三人开发任务清单.csv)
- [技术配置基线](docs/技术配置基线.md)
- [接口文档](docs/接口文档.md) 与 [OpenAPI](docs/openapi.yaml)
- [数据库设计](docs/数据库设计.md) 与 [迁移说明](database/README.md)

平时各自在本地开发和测试，需要检查三个人的功能能否接起来时，再使用下面的服务器环境。本地代码能使用哪些功能，以当前分支为准；GitHub 上的提交不会自动更新服务器。

## 服务器联调

服务器是三个人共用的测试环境。大家打开同一个网址，用自己的账号操作，使用的是同一套系统、同一个数据库，不需要在自己电脑上启动项目。

### 1. 访问和登录

网站地址：<https://lch4id8sf57x1tv9-18080.sh03-webservice.gpuhome.cc:8443>

| 使用者 | 账号 | 说明 |
| --- | --- | --- |
| 甲 | `20260001` | 学生账号 |
| 乙 | `20260002` | 学生账号 |
| 丙 | `20260003` | 学生账号 |
| 管理员 | `admin` | 供负责管理端的成员使用 |

密码由维护人单独提供，不放进 README。以上账号已经创建，直接登录即可，不用重新注册。测试时填写虚拟信息。

关闭自己的电脑或 SSH 窗口，不会影响网站。服务器 zc4 必须保持开机；服务器关机后，所有人都无法访问。重新开机后如果网站没有恢复，联系维护人启动服务。

### 2. 当前可以测试的功能

截至 2026 年 11 月 10 日本次部署，服务器使用乙分支的 `ce6959f`，并包含首页居中样式修复，已经包含甲、乙的以下功能：

- 注册、登录、退出、修改个人资料、邮箱和密码。
- 发布失物或招领信息，上传图片。
- 浏览、搜索公开物品。
- 查看“我的发布”，修改信息、下架信息。
- 检查不同账号之间是否会看到或修改别人的私人记录。

丙的审核、认领功能目前还没有部署。管理员能够登录，不代表审核页面已经可用。当前暂时不能测试完整的“发布 → 审核 → 认领”流程。后续更新服务器时，维护人同步更新这里的版本和功能范围。

新发布的信息会先进入“待审核”，只有审核通过后才会出现在公开列表。发布成功后搜不到，先到“我的发布”检查状态，不要直接当成发布失败。

服务器目前使用模拟邮件，不会向真实邮箱发送验证码。已有账号登录不需要验证码；确实要测试注册或修改邮箱时，由维护人从服务器的模拟收件箱取出验证码。

### 3. 三个人怎么一起测试

先按下面的顺序走一遍。每次操作都记住物品编号，方便后续检查和反馈问题。

1. **甲登录并发布一条信息。** 标题可以填“联调测试：蓝色水杯”，地点填“图书馆”，描述写清楚物品外观，日期选择今天或过去的日期。上传一张 JPG 或 PNG 图片；每条最多三张，每张不超过 2 MB。
2. **甲打开“我的发布”。** 应该能看到刚提交的信息、图片和“待审核”状态，刷新页面后记录仍然存在。
3. **乙、丙检查账号隔离。** 分别登录自己的账号，在各自的“我的发布”中不应该出现甲的记录。把甲的编辑页面地址复制过去，也不应该允许读取和修改。
4. **甲修改信息。** 修改标题、替换或移除图片，提交后刷新“我的发布”，检查内容是否正确保存，状态仍为待审核。
5. **甲下架信息。** 确认下架后，应显示“已下架”，不能再继续编辑这条记录。
6. **检查退出和重新登录。** 退出后不能继续进行发布、修改等操作；重新登录后，自己的记录仍然可以查看。

需要同时登录不同账号时，可以分别使用 Edge 和 Chrome，或使用不同的浏览器配置文件。同一个浏览器里的普通标签页通常共用登录状态，不能当成两个独立用户。

可以按下面的格式记录测试结果：

| 测试人 | 操作 | 物品编号 | 预期结果 | 实际结果 | 截图或报错 |
| --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  |

### 4. 平时在本地怎么测试

日常开发时，各自在自己的电脑上启动前端、后端和本地数据库，打开 <http://localhost:5173>。具体启动步骤见下文“拉代码后在自己电脑上跑起来”。

| 修改内容 | 怎么查看效果 |
| --- | --- |
| Vue 页面、CSS、前端交互 | 保存后在本地网页查看，开发服务器会自动更新页面 |
| Java 后端接口 | 重启本地后端，再在网页中操作 |
| 数据库结构 | 在本地数据库执行新增迁移，再测试对应功能 |

每改一点代码，不需要构建和上传服务器。先在本地把自己的功能测通，需要和其他人的模块一起检查时，再统一更新服务器。本地与服务器的账号、物品和数据库内容相互独立。

### 5. 提交代码后怎么更新网站

目前没有配置自动部署。提交或推送到 GitHub 后，网站仍然运行原来的版本。

更新按以下顺序进行：

1. 三个人先把需要联调的代码合并到约定的集成分支，明确要部署的提交编号。
2. 在本地执行测试和构建，确认需要的功能都包含在这个版本中。
3. 由维护人备份服务器数据，把后端 JAR 和前端构建文件上传到新的版本目录。
4. 有新增数据库迁移时，先核对并执行尚未执行的迁移；不要重复建库或重复执行已有迁移。
5. 切换服务器版本、重启后端，再通过网站检查登录、发布和本人记录等主要流程。

日常代码更新保留服务器已有的账号、物品和图片，不通过清空数据库来更新。后端重启后，大家可能需要重新登录。部署前要确认目标分支包含所需的甲、乙、丙代码，不能只凭分支名判断功能是否齐全。

### 6. 遇到问题先看这里

| 现象 | 先做什么 |
| --- | --- |
| 页面看起来还是旧版 | 按 `Ctrl + F5` 强制刷新 |
| 提示登录失效 | 重新登录；可能刚更新过后端，或账号修改过密码 |
| 发布成功，但公开列表查不到 | 到“我的发布”查看是否仍为待审核 |
| 找不到审核或认领入口 | 查看上面的功能范围，确认对应模块是否已经部署 |
| 网站完全打不开 | 确认 zc4 是否开机，再联系维护人检查服务 |
| 服务器与自己本地的表现不同 | 先核对两边运行的代码版本，以及操作的账号和物品是否相同 |

反馈问题时，提供使用的账号、操作步骤、物品编号、预期结果、实际结果和报错截图，不需要发送密码。例如：“用乙账号打开甲的编辑页面，修改标题后竟然保存成功，物品编号为 ××。”这样其他人可以按同样步骤复现和排查。

## 拉代码后在自己电脑上跑起来

每个人用自己电脑上的数据库，按下面 6 步操作一次即可。命令以 PowerShell 7（`pwsh.exe`）为例。

### 1. 准备软件

- JDK 21（不能用 17；电脑默认是别的版本也没关系，见第 4 步）
- MySQL 8.0
- Node.js 24（24.12 以上；或 22.18 以上），自带 npm
- Git
- 编辑器二选一：IntelliJ IDEA，或 VS Code + “Extension Pack for Java”和“Vue - Official”两个扩展。VS Code 里不要同时装 Oracle 的 Java 扩展，会和这套冲突

### 2. 下载代码

```powershell
git clone https://github.com/Huang-yu-xin/campus-lost-and-found.git
cd campus-lost-and-found
```

### 3. 建本地数据库

在**仓库根目录**登录 MySQL，按提示输入 root 密码（把路径换成你的 MySQL 安装位置）：

```powershell
& 'D:\MySQL\mysql-8.0.34-winx64\bin\mysql.exe' -u root -p --default-character-set=utf8mb4
```

进入 `mysql>` 后先执行：

```sql
SHOW DATABASES LIKE 'campus_lost_found';
```

如果有结果，说明电脑上已经有同名的旧库，**先停下来**在群里问，不要继续执行，否则新旧表会混在一起。没有结果就接着执行（`你的密码` 换成你自己想设的，后面第 4 步要用）：

```sql
SOURCE database/000_create_database.sql;
USE campus_lost_found;
SOURCE database/migrations/V001__core_schema.sql;
SOURCE database/seeds/S001__mock_students.sql;

CREATE USER 'lostfound_app'@'localhost' IDENTIFIED BY '你的密码';
GRANT SELECT, INSERT, UPDATE, DELETE ON campus_lost_found.* TO 'lostfound_app'@'localhost';
CREATE USER 'lostfound_app'@'127.0.0.1' IDENTIFIED BY '你的密码';
GRANT SELECT, INSERT, UPDATE, DELETE ON campus_lost_found.* TO 'lostfound_app'@'127.0.0.1';

SHOW TABLES;
```

最后应看到 13 张表。中途任何一步报错就停下来，不要重复执行。`lostfound_app` 是后端专用账号，只能读写数据，不能改表结构；表结构要改时写新的迁移文件，详见 [迁移说明](database/README.md)。

### 4. 写本机配置

把 `backend/config/application.yml.example` 复制一份，改名为 `backend/config/application.yml`，然后改三处：

- `datasource` 下的 `password`：第 3 步设的密码
- `hmac-key`：在 PowerShell 运行下面这行，把输出的 64 位字符串粘进去
- `admin` 下的 `password`：自己定一个管理员密码（8 位以上，含字母和数字）。后端第一次启动时自动创建账号 `admin`，用它就能登录管理后台

```powershell
$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); -join ($b | % { $_.ToString('x2') })
```

`application.yml` 已被 Git 忽略，不会上传；不要把它发到群里。

### 5. 启动并确认

**VS Code**：打开仓库文件夹，等右下角 Java 项目加载完，按 F5 选“启动后端”。如果电脑默认 JDK 不是 21，在 VS Code 设置里搜 `java.configuration.runtimes`，添加 JDK 21 的路径。

**IDEA**：打开 `backend` 目录，Project SDK 选 JDK 21，运行 `CampusLostFoundApplication`。

**命令行**：先让当前终端使用 JDK 21，再在 `backend` 目录运行（路径换成你的 JDK 21 位置）：

```powershell
$env:JAVA_HOME = 'C:\你的\jdk-21'; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
cd backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

`mvnw test` 全部通过，且 `DatabaseConnectivityTest` 没有被跳过，说明数据库和配置都没问题。

启动后在浏览器打开 http://localhost:8080/api/v1/auth/csrf ，看到 `"code":"OK"` 就成功了。接口文档页面在 http://localhost:8080/swagger-ui.html 。

### 6. 启动前端

后端保持运行，另开一个终端：

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

`npm ci` 按 `package-lock.json` 安装和大家完全一样的依赖版本，第一次需要几分钟；以后只有 package-lock.json 变了才需要再运行。不要用 `npm install` 随手升级依赖，要加新依赖先在群里说。

浏览器打开 http://localhost:5173 （固定用 localhost，不要换成 127.0.0.1，否则登录状态对不上）。首页显示“后端连接正常”就说明前后端都通了。

提交前端代码前运行一遍检查，全部通过再提交：

```powershell
npm.cmd run type-check
npm.cmd run lint
npm.cmd run test:unit -- --run
npm.cmd run build
```

常见问题：

| 现象 | 原因 |
| --- | --- |
| 启动报 `Could not bind properties to 'AppProperties'` | `backend/config/application.yml` 不存在，或者 hmac-key 不足 32 位；也可能不是从 backend 目录启动的 |
| `Access denied for user 'lostfound_app'` | application.yml 里的密码和第 3 步设的不一致 |
| `Unknown database 'campus_lost_found'` | 第 3 步没做完 |
| 提示 8080 或 5173 端口被占用 | 后端或前端已经在运行了，先停掉旧的 |
| 前端首页显示“后端连接失败” | 后端没有启动，或启动失败；先看后端终端的报错 |
| 编译报 `Fatal error compiling`，后面跟“不支持发行版本 21”（中文系统上可能是乱码） | 当前终端用的不是 JDK 21 |
