# 单机 Docker Compose 部署

本目录面向单块 RK3568 板，把 PostgreSQL、Redis、MinIO、`yudao-server` 和前端 Nginx
部署在同一 Docker Compose 项目中。HSI collector 仍运行在宿主机 systemd，直接访问串口；
旧的后端/前端 systemd 与 Kiosk 单元方案已移除。开发用 Compose 仍保留在 `script/`。

## 1. 架构与端口

```text
浏览器/Kiosk
    │ HTTP WEB_PORT（默认 80）
    ▼
web (Nginx) ── 静态文件：/opt/yudao/releases/web/current
    │        ├─ /admin-api、/app-api、/infra/ws → yudao-server:48080
    │        └─ /minio/                        → minio:9000
    ▼
yudao-server (JDK 25) ── PostgreSQL:5432 / Redis:6379 / MinIO:9000

collector（宿主机 systemd）── /dev/ttyS4
                    └────── 127.0.0.1:5432（PostgreSQL 仅回环发布）
```

| 服务 | 镜像 | 宿主机端口 | 数据/产物目录 |
|---|---|---|---|
| postgres | `postgres:17-alpine` | `127.0.0.1:5432` | `/opt/yudao/data/postgres` |
| redis | `redis:7-alpine` | `127.0.0.1:6379` | `/opt/yudao/data/redis` |
| minio | `pgsty/minio:RELEASE.2026-08-04T00-00-00Z` | `127.0.0.1:9000/9001` | `/opt/yudao/data/minio` |
| minio-init | 同 MinIO | 一次性退出 | 幂等创建私有 bucket |
| yudao-server | `eclipse-temurin:25-jre` | 不发布 | `/opt/yudao/releases/backend` |
| web | `nginx:1.28-alpine` | `${WEB_PORT}:80` | `/opt/yudao/releases/web` |

所有数据均使用显式 bind mount，不用 named volume 隐藏目录。路径可在 `.env` 覆盖，
但覆盖后必须自行创建目录并处理属主。后端只在容器网络监听 48080，Nginx 是唯一 Web 入口。

PostgreSQL、Redis 与 MinIO 的宿主机端口都只绑定回环地址，开发机通过固定 SSH 隧道访问，
不对局域网暴露。以 Redis 为例（本地 `16379` → 板端 `127.0.0.1:6379`）：

```bash
ssh -N -L 16379:127.0.0.1:6379 cat@192.168.0.101
# 另开终端：默认使用 db 0，密码取板端 .env 的 REDIS_PASSWORD，不要写入仓库或命令历史
redis-cli -h 127.0.0.1 -p 16379
127.0.0.1:16379> AUTH <REDIS_PASSWORD>
```

隧道只打通到板端回环端口，Redis 仍要求 `--requirepass` 密码认证；不得改为 `0.0.0.0`
绑定、裸 `6379:6379` 或移除密码。

## 2. 前置条件

- Ubuntu 24.04，Docker 与 Compose V2；缺省时 `bootstrap-board.sh` 用 apt 安装
  `docker.io`、`docker-compose-v2`。
- 开发机具备 JDK 25、Maven、Node/pnpm；板端不执行 Maven/Node 构建。
- 所需镜像已拉取，或板端能访问镜像仓库。
- 时区使用 `Asia/Shanghai`；collector 用户属于 `dialout` 组。
- 默认目录为 `/opt/yudao`，数据库备份另存 `/opt/yudao/backups`。

`deploy/compose-single/.env.example` 只含占位值。复制为 `.env` 后必须修改所有
`CHANGE_ME`、数据库/Redis/MinIO 密码和 `MINIO_PUBLIC_DOMAIN`，文件权限设为 0600。
`.env` 与开发机 `deploy.env` 均已 gitignore，禁止提交真实凭据。

**已知风险：** Redis 密码会展开到 `docker-compose.yml` 的 `--requirepass` 启动参数，主机上的
Docker 管理员可通过 `docker inspect` 读取。本方案以 root、Docker 组与 0600 的 `.env`
属于同一受信任管理域为前提；必须限制 sudo/Docker 组成员，不要把 inspect 输出交给不受信任方。
Redis 与 PostgreSQL 一样只在宿主机回环地址发布端口（默认 `127.0.0.1:6379`，可用
`REDIS_PORT` 覆盖），不对局域网暴露，连接仍须通过 `--requirepass` 密码认证；回环发布
同样不能替代上述主机权限控制。

## 3. 构建产物

在源代码仓库根目录（或功能 worktree 根目录）执行：

```bash
mvn -B package -pl yudao-server -am -Dmaven.test.skip=true
cd yudao-ui/yudao-ui-admin-vben
pnpm install --frozen-lockfile
pnpm build:antdv-next
```

产物：

- `yudao-server/target/yudao-server.jar`；
- `yudao-ui/yudao-ui-admin-vben/apps/web-antdv-next/dist/`。

前端生产配置使用同源 `/admin-api`，验证码关闭，与 `.env` 的
`YUDAO_CAPTCHA_ENABLE=false` 对齐。Compose 已为后端设置 `TZ=Asia/Shanghai`；若在宿主机
直接启动裸 jar，必须显式执行 `java -Duser.timezone=Asia/Shanghai -jar yudao-server/target/yudao-server.jar`
（或保证系统 TZ 一致），避免全局 `LocalDateTime` 时间戳序列化受 JVM 默认时区影响。
最新值看板的三个时间字段已额外在服务端按 Asia/Shanghai 显式转为 epoch millis，不依赖该默认值。

## 4. 首次部署

### 4.1 上传部署文件和私有环境文件

以下示例使用板端 `cat@192.168.0.101`，实际环境按地址和用户替换。在仓库根目录执行，
`deploy/compose-single/.env` 是已填好但未提交的本地文件：

```bash
tar --exclude='*.env' --exclude='*.env.local' -czf - deploy sql/postgresql | \
  ssh cat@192.168.0.101 \
  'rm -rf /tmp/yudao-src && mkdir -p /tmp/yudao-src && tar -xzf - -C /tmp/yudao-src'
ssh cat@192.168.0.101 'umask 077; cat > /tmp/yudao-compose.env' \
  < deploy/compose-single/.env
ssh -t cat@192.168.0.101 \
  'sudo bash /tmp/yudao-src/deploy/compose-single/scripts/bootstrap-board.sh \
   --run --with-collector --service-user cat --env-file /tmp/yudao-compose.env'
ssh cat@192.168.0.101 'rm -rf /tmp/yudao-src /tmp/yudao-compose.env'
```

上传归档明确排除 `.env`、`deploy.env` 和 `*.env.local`，但保留 `*.env.example`；
初始化结束后删除整个暂存目录，避免私有环境文件残留。初始化会安装 Compose 文件到
`/opt/yudao/compose-single`，把整套 SQL（`platform/`、`collector/`、`migration/`、
`dbctl.sh`）安装到 `/opt/yudao/sql/postgresql`，创建
数据/发布/日志/备份目录。复制 Compose 文件本身不覆盖已有 `.env`；显式传 `--env-file`
则会用指定文件替换它（0600）。`--with-collector` 安装单元和 `/etc/yudao/env.remote`，
执行 `systemctl enable collector`（不加 `--now`），不会启动 collector。
`--run` 本身也不启动应用；只有同时指定 `--start` 才执行 `compose up -d`。

**Windows 行尾归一化（无需手工 sed）：** Windows 开发机 `core.autocrlf=true` 时，工作区
文本可能检出为 CRLF——`deploy/.gitattributes` 只强制 `deploy/` 内为 LF，`sql/postgresql/`
不在覆盖范围内，曾出现 `dbctl.sh` shebang 变成 `bash\r` 的事故。`bootstrap-board.sh` 安装到
`/opt/yudao` 后会按扩展名白名单（`sh`/`sql`/`yml`/`yaml`/`conf`/`service`）把 Compose 目录与
SQL 目录的文本统一归一化为 LF（systemd 单元在渲染管线中同步清理），幂等、只去行尾 CR，
不依赖板端安装 git，非 git 来源的 tar 包同样生效。`.env`/`deploy.env` 等真实凭据文件
（tar 复制本身也已排除）与 jar、图片、压缩包等二进制不匹配白名单，字节保持不变。
该机制只覆盖 bootstrap 首次部署链路：`release-server.sh` 经 scp 传 jar（二进制）、
`release-web.sh` 传前端构建产物，均不涉及 shell/SQL；`release-collector.sh` 传的
`deploy/scripts`、`deploy/systemd` 已由 `deploy/.gitattributes` 强制 LF，不在归一化范围内。

### 4.2 安装首个后端与前端版本

版本号可自定义，这里用 `VERSION=20261116-220000-first` 示例：

```bash
VERSION=20261116-220000-first
scp yudao-server/target/yudao-server.jar \
  cat@192.168.0.101:/tmp/yudao-server-$VERSION.jar
tar -C yudao-ui/yudao-ui-admin-vben/apps/web-antdv-next/dist -czf - . | \
  ssh cat@192.168.0.101 "rm -rf /tmp/yudao-web-$VERSION && mkdir -p /tmp/yudao-web-$VERSION && tar -xzf - -C /tmp/yudao-web-$VERSION"
ssh -t cat@192.168.0.101 "sudo bash /opt/yudao/compose-single/scripts/apply-server.sh \
  --jar /tmp/yudao-server-$VERSION.jar --version $VERSION --stage-only"
ssh -t cat@192.168.0.101 "sudo bash /opt/yudao/compose-single/scripts/apply-web.sh \
  --dist /tmp/yudao-web-$VERSION --version $VERSION --stage-only"
```

后端软链接为 `releases/backend/current.jar -> <版本>/yudao-server.jar`；前端为
`releases/web/current -> <版本>`。相对软链接在宿主机和容器挂载内都有效。

### 4.3 启动与检查

```bash
ssh -t cat@192.168.0.101 \
  'cd /opt/yudao/compose-single && sudo docker compose config -q && sudo docker compose up -d'
ssh cat@192.168.0.101 \
  'cd /opt/yudao/compose-single && sudo docker compose ps'
```

也可在暂存目录重新执行 `bootstrap-board.sh --run --start --env-file ...`。
首次 PostgreSQL 数据目录为空时才按顺序执行（`docker-entrypoint-initdb.d` 按文件名排序）：

1. `platform/ruoyi-vue-pro.sql`（system/infra 基线）；
2. `platform/quartz.sql`；
3. `collector/01_schema.sql`（采集器 22 表纯 DDL，含 `schema_migration`/`db_init_marker`）；
4. `collector/02_system_seed.sql`（协议、保留策略、采集器菜单与 5 个定时任务记录，幂等）；
5. `init/04a-collector-baseline-marker.sh`（登记 5 个迁移版本为已包含基线）；
6. `init/05-file-config.sh`（MinIO master 文件配置）。

> 手动初始化（非 compose 空卷路径）推荐使用 `sql/postgresql/dbctl.sh init <库> --new --run`，
> 它按相同顺序执行并完成版本登记与初始化标记；`migration/` 下的增量脚本仅供已有库升级。

后端启动后还需同步一次定时任务，报警判定作业才会进入 Quartz。推荐使用管理后台
“定时任务 → 同步”；该操作要求管理员账号具有 `infra:job:create` 权限。若使用命令行，
`POST /admin-api/infra/job/sync` 必须携带管理员访问令牌：

```bash
curl -X POST -H 'tenant-id: 1' -H 'Authorization: Bearer <管理员Token>' \
  http://192.168.0.101/admin-api/infra/job/sync
```

确认 `采集报警判定` 任务存在、状态为开启，并在“调度日志”中看到至少一次执行记录；
未同步时 `infra_job` 只有记录，Quartz 不会调度。

**不要在已有数据的库上手工重跑基线 SQL**，其中 `platform/` 脚本包含 DROP/重建语句。清空数据目录或
`down` 后重新 `up` 不等于安全升级；数据库结构升级必须另行备份与评估，并用 `dbctl.sh migrate`
（或 `migration/` 下的幂等脚本）增量执行。

**既有库升级（最新值看板、报警、聚合等）**：在发布新版 collector 之前，先在板端备份数据库，
再用版本控制工具执行增量迁移（不要重跑 `01_schema.sql`）：

```bash
# 一次性：对从未登记版本的旧库做结构探测基线标记（拒绝盲标，range_fix 不可证明时按指引处理）
sudo /opt/yudao/sql/postgresql/dbctl.sh mark-baseline ruoyi-vue-pro --run
# 之后每次发版：自动跳过已登记版本，只执行缺失迁移并刷新系统种子
sudo /opt/yudao/sql/postgresql/dbctl.sh migrate ruoyi-vue-pro --run
```

`ruoyi-vue-pro`（含连字符）与 `yudao_board` 是 dbctl 显式登记的正式库名，仅
`mark-baseline`/`migrate`/`seed-system`/`status` 精确放行；`init`/`seed-test`/
`drop-validation` 仍最严拒绝，其他连字符库名（如 `my-db`）也不会被泛化放行。

`bootstrap-board.sh` 会把 `dbctl.sh`、`platform/`、`collector/`、`migration/` 一并安装到
`/opt/yudao/sql/postgresql/`。新版 collector 启动时会检查 `collector_channel_latest`；
缺表时记录中文错误、提示先执行 `migration/collector_latest_migration.sql` 并以退出码 2
失败，避免只写 raw 静默丢最新值。

检查：

```bash
curl -i http://192.168.0.101/
curl -H 'tenant-id: 1' -H 'Content-Type: application/json' -d '{}' \
  http://192.168.0.101/admin-api/system/captcha/get
# 浏览器使用 admin / admin123 登录（首次登录后按现场要求修改密码）
cd /opt/yudao/compose-single
sudo docker compose exec -T postgres psql -U yudao -d ruoyi-vue-pro \
  -c "SELECT count(*) FROM pg_tables WHERE tablename LIKE 'collector_%';"
sudo docker compose exec -T postgres psql -U yudao -d ruoyi-vue-pro \
  -c "SELECT id,name,storage,master FROM infra_file_config ORDER BY id;"
sudo docker compose run --rm --no-deps minio-init
```

MinIO bucket 由 `minio-init` 自动创建。最后一条命令可幂等重跑 bucket 初始化。

### 4.4 既有库升级：报警引擎（历史说明，现由 dbctl 统一管理）

已有数据目录不要重跑 `collector/01_schema.sql`，报警引擎增量的原始步骤如下（幂等，
对应 `migration/collector_alarm_migration.sql`，现由 `dbctl.sh migrate` 自动按序执行）：

1. **维护窗口**：`collector_alarm_migration.sql` 包含 `collector_sample_raw` 全表回填，
   且事务内创建索引会阻塞写入；执行期间暂停采集写入，避免超时。
2. **结构迁移**：`migration/collector_alarm_migration.sql`（幂等，可重复执行）。
3. **系统种子**：`collector/02_system_seed.sql` 已合并原 `collector_web_seed.sql`（报警菜单）
   与 `collector_alarm_seed.sql`（定时任务记录），幂等；`dbctl.sh migrate` 末尾会自动刷新。
4. **同步 Quartz**：在管理后台“定时任务 → 同步”（需要 `infra:job:create`），或携带
   管理员 Token 调用一次 `POST /admin-api/infra/job/sync`。种子只写 `infra_job`，
   不同步则作业不会执行。
5. **验收检查**：确认“采集报警判定”任务存在且开启；在“调度日志”中看到至少一次
   执行记录；打开报警记录页确认能查询。部署验收清单应包含“查看一次作业执行日志”。

`bootstrap-board.sh` 会把整套 `platform/`、`collector/`、`migration/` 与 `dbctl.sh`
安装到板端 `/opt/yudao/sql/postgresql/`，升级直接执行：

```bash
sudo /opt/yudao/sql/postgresql/dbctl.sh migrate ruoyi-vue-pro --run
curl -X POST -H 'tenant-id: 1' -H 'Authorization: Bearer <管理员Token>' \
  http://192.168.0.101/admin-api/infra/job/sync
```

报警事件时间统一使用服务器本地墙钟时间（`Asia/Shanghai`），报警页面的时间范围查询
按同一墙钟语义解释；跨时区客户端请自行换算。

## 5. 日常更新

开发机先复制 `deploy.env.example` 为 `deploy.env`，配置 SSH 地址、用户、私钥；
sudo 密码推荐留空交互输入。CLI 参数优先于 `deploy.env`。

```bash
bash deploy/compose-single/scripts/release-server.sh --run
bash deploy/compose-single/scripts/release-server.sh --run --skip-build --version V2
bash deploy/compose-single/scripts/release-web.sh --run
bash deploy/compose-single/scripts/release-web.sh --run --skip-build --version V2
bash deploy/compose-single/scripts/release-collector.sh --run
bash deploy/compose-single/scripts/release-server.sh --run --dry-run
```

也可以把产物上传到板端后直接执行：

```bash
sudo bash /opt/yudao/compose-single/scripts/apply-server.sh --find
sudo bash /opt/yudao/compose-single/scripts/apply-web.sh --dist /tmp/yudao-web-V3
```

- 后端允许同版本替换；健康检查失败会恢复更新前 jar/软链接并重启。
- 前端当前版本不允许同版本覆盖；HTTP 检查失败恢复更新前软链接。
- 后端健康检查从 web 容器请求 48080 的验证码接口，同时覆盖容器网络、后端和 Redis；
  wget 确认 HTTP 成功后，还会解析响应体根节点，要求验证码 `ResponseModel.repCode="0000"`
  （同时兼容 `CommonResult.code=0`）。因此 HTTP 200 但业务失败也会触发回滚，超时时输出
  最后一次响应片段便于排查。
- 默认各保留最近 3 个版本，至少保留当前版本；用 `--keep` 调整。
- 所有脚本无参数或 `-h/--help` 都显示帮助并退出 0；执行发布需显式 `--run`
  （apply 脚本使用各自必需参数）。

手工回滚前先看 `readlink` 和版本目录，再执行（示例版本必须替换）：

```bash
cd /opt/yudao/releases/backend
sudo ln -s 上一版本/yudao-server.jar current.jar.tmp && sudo mv -Tf current.jar.tmp current.jar
cd /opt/yudao/compose-single && sudo docker compose restart yudao-server

cd /opt/yudao/releases/web
sudo ln -s 上一版本 current.tmp && sudo mv -Tf current.tmp current
cd /opt/yudao/compose-single && sudo docker compose exec -T web nginx -s reload
```

数据库结构若已变化，不能只回滚 jar；先评估兼容性，必要时从备份恢复。

## 6. 数据库备份与恢复

```bash
sudo bash /opt/yudao/compose-single/scripts/backup-db.sh --run --keep 7
ls -lh /opt/yudao/backups
```

备份使用容器内与服务端同版本的 `pg_dump -Fc`，权限 0600，默认保留 7 份。
MinIO 对象数据不在数据库 dump 内，需另行备份 `/opt/yudao/data/minio`。
恢复是高风险操作：先停止后端、确认 dump 与目标库，再由运维使用 `pg_restore`；
脚本不会自动恢复，也不要为“恢复”而重跑初始化 SQL。

## 7. MinIO 文件配置

初始化保留 id=4 的 DB 存储行，只取消它的 master，并新增 id=5 的 S3 master：

- endpoint：`http://minio:9000`（仅供服务端容器访问）；
- bucket/accessKey/accessSecret：来自 `.env`；
- `enablePathStyleAccess=true`、`enablePublicAccess=false`、`region=us-east-1`；
- domain：`MINIO_PUBLIC_DOMAIN`，例如 `http://192.168.0.101/minio/yudao`。

私有 bucket 的预签名 URL 经 Nginx `/minio/` 转发，MinIO API/Console 不需要暴露到局域网。
Nginx 对 MinIO 使用变量 `proxy_pass` 和 Docker resolver 动态解析，重建 MinIO 后无需重建 web。
修改板端地址、Web 端口或 bucket 时，必须同步修改 domain；非 80 端口要显式带端口。
已有数据库不会因修改 `.env` 自动更新 `infra_file_config`，需在后台文件配置中同步修改。
MinIO 初始化 SQL 只在空库首次执行；其 shell 通过 `psql` 变量构造 JSON，凭据不写入仓库。

## 8. collector 宿主机部署

collector 不进容器，继续复用 `deploy/scripts/deploy-collector.sh` 与
`deploy/systemd/collector.service`。bootstrap 会把单元中的 User/Group 渲染为指定用户，
并生成 `/etc/yudao/env.remote`，让 collector 连接 `127.0.0.1:5432`。bootstrap 和每次
collector 发布都会幂等执行 `systemctl enable collector`，但不会用 enable 隐式启动进程。
unit 不写死轮询间隔、超时和重试次数；它们分别读取 `collector_device` 的
`poll_interval_ms`、`timeout_ms`、`retries`，传感器与通道配置也会按指纹定期热加载。
后台修改这些配置后无需重启 collector，显式 CLI 覆盖项仍优先。结构配置只有在原串口
成功应用后才提交新指纹；flags ioctl 等应用失败会保留旧配置，首次在下一轮立即重试，
连续失败进入 2 秒起步、60 秒封顶的指数退避，DB 重连仍由 `--db-retry-interval` 独立节流。
启动时无应答的传感器不会被无关结构变化重新加入，仅新增、禁用后重新启用或进程重启时
再次探测。采集器启动要求 `collector_channel_latest` 已存在（既有库先执行
`collector_latest_migration.sql`），否则会提示迁移并退出，不能只写 raw。部署脚本
保留按实际 `SERVICE_USER` / `SERVICE_GROUP` 渲染 unit 的 `User` / `Group` 机制。

```bash
bash deploy/compose-single/scripts/release-collector.sh --run
systemctl is-enabled collector  # 期望 enabled
systemctl is-active collector   # 启动长期采集后期望 active
systemctl status collector
journalctl -u collector -n 100 --no-pager
tail -n 30 /opt/yudao/logs/collector.log
```

只有接好传感器并确认串口后才启动长期采集。没有传感器时可验证服务安装，但日志中的
无应答不代表 DB 或 Compose 故障。collector 断库时只写 JSONL，不会补写断连期间数据。

## 9. Kiosk、日志与运维

Kiosk 是宿主机桌面/浏览器配置，不再由项目提供 `kiosk.service`，也不要改动 GNOME。
可让浏览器打开 `http://127.0.0.1/`；具体自启动方式由板端系统镜像维护。

```bash
cd /opt/yudao/compose-single
sudo docker compose ps
sudo docker compose logs --tail=100 yudao-server
sudo docker compose logs --tail=100 postgres
sudo docker compose logs --tail=100 minio
sudo docker compose exec -T postgres pg_isready -U yudao -d ruoyi-vue-pro
sudo docker compose exec -T redis redis-cli ping
curl -f http://127.0.0.1:9000/minio/health/live   # 仅板端回环
```

**禁止 `docker compose down -v`**。虽然本配置没有 named volume，仍应统一使用
`docker compose stop` 或 `docker compose down`（不带 `-v`），绝不用删除数据目录的方式升级。

当前板端是 cgroup v1；本文件不依赖 cgroup v2 的 `memory.*` 限制或 systemd cgroup 配置。
JVM 默认 `-Xms512m -Xmx1024m -XX:+UseSerialGC`，应结合实际 RSS 与板端内存观察调整。
