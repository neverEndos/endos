# 板端部署手册（systemd 原生部署）

本文面向 RK3568 板端（Ubuntu 24.04，首期 4GB 内存 / 16GB eMMC）的先行验证部署：
Nginx 提供静态前端，`yudao-server` 与 HSI `collector` 以 systemd 原生服务运行；
PostgreSQL 与 Redis 使用局域网基础设施（`192.168.0.23`），板端不安装其服务端。

> 路径约定：本文中仓库内路径均相对**源代码仓库根目录**（工作区中的
> `yudao-boot-mini/` 或功能 worktree），板端路径是设备上的绝对路径。
> 命令中的 `192.168.0.105` 是当前板端地址示例，按实际地址替换。
> 板端命令默认以具备 `sudo` 权限的 `alientek` 用户执行。

## 0. 部署步骤总览

| 步骤 | 位置 | 产出/动作 |
|---|---|---|
| 1 | 开发机 | 构建 `yudao-server.jar` 与前端 `dist/` |
| 2 | 任意可访问 PG 的机器 | 创建 `yudao_board` 库，导入 core SQL 与 collector SQL |
| 3 | 板端 | 安装 JDK/Nginx/PostgreSQL 客户端，校准时区、NTP、串口权限、网络 |
| 4 | 板端 | 创建 `/opt/yudao`、`/etc/yudao`、`/var/log/yudao`，写入 `env.remote` |
| 5 | 板端 | 拷贝 jar/dist/collector，安装 systemd 单元与 Nginx 站点 |
| 6 | 板端 | 启动服务，执行健康检查 |
| 7 | 板端 | 记录版本与备份信息，纳入升级流程 |

## 1. 架构与资源预算

### 1.1 部署架构

```text
浏览器 / kiosk
      │ HTTP :80
      ▼
   Nginx（板端 alientek）
      ├── /            → /opt/yudao/web/current（静态文件，hash 路由）
      └── /admin-api/  → 127.0.0.1:48080（yudao-server）
          /app-api/    → 127.0.0.1:48080
                              │
                              ├── PostgreSQL 192.168.0.23:5432/yudao_board
                              └── Redis 192.168.0.23:6379 db1

collector（板端 alientek）── /dev/ttyS4（RS485 / HSI）── sensors
```

### 1.2 资源预算（含旧板实测值）

| 组件 | 实测/估算 | 说明 |
|---|---|---|
| Ubuntu 24.04 基础系统 | 约 500–700MB（估算） | 含 systemd、journald、SSH 等 |
| yudao-server（JVM） | **启动完成后 RSS 约 540MB（旧板实测）** | `-Xms256m -Xmx768m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC` |
| collector（Python 3.12） | **RSS 约 15MB（旧板实测）** | 纯标准库，无第三方运行时 |
| Nginx | < 30MB（估算） | 静态文件 + 反代 |
| 剩余余量 | 约 1.5–2GB | 4GB 旧板的文件缓存与峰值余量 |

旧板（4GB 内存，远程 PG / Redis db1）实测补充：

- `systemctl start yudao-server` 到监听 `48080` 约 **140 秒**，启动阶段需耐心等待，
  健康检查前先用 `journalctl -u yudao-server -f` 观察进度。
- 启动完成后 Java 进程 RSS 约 **540MB**；JVM 参数见第 11 节。
- collector 进程 RSS 约 **15MB**。
- Redis db1 已验证 OAuth2 token key 落在 db 1：`redis-cli -h 192.168.0.23 -p 6379
  -n 1` 执行 `SELECT 1` 后 `DBSIZE` 为 2（登录/刷新 token 后重新查看会更直观）。

磁盘（16GB eMMC）：系统约 6–8GB，JDK 与 jar、前端 dist 合计通常 < 1GB；
日志与 `collector-data.jsonl` 的占用取决于采样间隔与保留策略，需定期检查
`df -h`、`du -sh /var/log/yudao`。新板 8GB / 64GB 可显著放宽上述预算。

### 1.3 为什么不用 Docker

当前厂商内核未开启 `CONFIG_MEMCG`、`CONFIG_OVERLAY_FS`、
`CONFIG_BRIDGE_NETFILTER`，容器运行时无法正常工作。首期采用 systemd
原生部署，避免修改板端内核配置；新板到位后如内核补齐，可再评估容器化。

## 2. 前置条件

### 2.1 硬件与系统

- RK3568 板端，Ubuntu 24.04（aarch64）；首期 4GB 内存 / 16GB eMMC。
- 板端**只运行 Nginx、JVM、collector**，不安装 PostgreSQL/Redis 服务端。
- `alientek` 用户具备 `sudo` 权限；串口 `/dev/ttyS4` 属于 `dialout` 组。

### 2.2 安装软件包

板端 apt 源使用 Aliyun ubuntu-ports，需确认 `openjdk-25-jre-headless` 可获取：

```bash
sudo apt update
sudo apt install -y openjdk-25-jre-headless nginx postgresql-client python3-psycopg2
java -version          # 期望 openjdk version "25..."
nginx -v
python3 --version      # 期望 3.12.x
```

说明：

- `postgresql-client` 提供 `psql`、`pg_isready`，用于建库后的连通性检查；
  Ubuntu 24.04 仓库版本通常为 PostgreSQL 16，仅作客户端使用（见 12.1 的版本提醒）。
- `python3-psycopg2` 为 collector 后续接入 PostgreSQL 预留；当前原型只用标准库。
- 若 `apt-cache policy openjdk-25-jre-headless` 显示不可安装，先修正 apt 源，
  不要用低版本 JDK 替代（项目要求 JDK 25）。

### 2.3 时区与 NTP

业务时间语义为 `Asia/Shanghai` 本地墙钟时间（collector 时间戳、数据库
`timestamp without time zone`），板端必须校准时区：

```bash
sudo timedatectl set-timezone Asia/Shanghai
sudo systemctl enable --now systemd-timesyncd
timedatectl status                       # 确认 Time zone 与 NTP synchronized
date
```

### 2.4 串口权限

```bash
groups alientek                         # 应包含 dialout
ls -l /dev/ttyS4                        # 应存在，属组通常为 dialout
# 若不在 dialout 组：
sudo usermod -aG dialout alientek       # 重新登录后生效
```

`collector.service` 同时通过 `SupplementaryGroups=dialout` 显式补充组权限。

### 2.5 网络连通性

```bash
pg_isready -h 192.168.0.23 -p 5432
timeout 2 bash -c '</dev/tcp/192.168.0.23/6379' && echo "redis tcp ok"
```

板端到 PG 实测约 2.3ms、Redis 约 2.8ms。若不通，先排查网段、防火墙与
`192.168.0.23` 服务状态，不要继续部署。

## 3. 构建产物（在开发机完成，禁止在板端构建）

板端 4GB 内存与 eMMC 不适合执行 Maven/Node 构建；统一在开发机构建后传输。

### 3.1 后端

在源代码仓库根目录执行：

```bash
mvn -B clean package -pl yudao-server -am -Dmaven.test.skip=true
```

产物：`yudao-server/target/yudao-server.jar`（`yudao-server/pom.xml` 的
`finalName` 为 `${project.artifactId}`，并由 `spring-boot-maven-plugin`
`repackage` 为可执行 fat jar）。

### 3.2 前端

仓库已把板端所需的构建值直接提交在
`yudao-ui/yudao-ui-admin-vben/apps/web-antdv-next/.env.production`：

```dotenv
VITE_BASE_URL=
VITE_GLOB_API_URL=/admin-api
VITE_APP_CAPTCHA_ENABLE=false
# 其余行保持不变（VITE_BASE=/、VITE_ROUTER_HISTORY=hash、VITE_ARCHIVER=true 等）
```

为什么必须改这个文件：

- `.env.production` 原本写的是 `VITE_GLOB_API_URL=http://127.0.0.1:48080/admin-api`。
  生产包运行时读取的是构建时生成的
  `_app-config-<version>-<hash>.js`（`window._VBEN_ADMIN_PRO_APP_CONF_`），
  该文件由 `internal/vite-config` 的 `extra-app-config` 插件直接从 `.env*`
  文件解析生成，**shell 环境变量不会覆盖它**。不改文件、只在构建命令前加
  环境变量，登录请求仍会指向浏览器所在机器的 `127.0.0.1:48080`，必然失败。
- 改为相对路径 `/admin-api` 后，浏览器请求同源地址，由板端 Nginx 反代到
  `127.0.0.1:48080`。
- `VITE_BASE_URL=` 必须留空：模板代码会拼接 `${VITE_BASE_URL}/druid/...`
  这类地址，空值得到 `/druid/...`；若写成 `/` 会得到 `//druid/...`。
- `VITE_APP_CAPTCHA_ENABLE=false` 与板端 `YUDAO_CAPTCHA_ENABLE=false` 对齐。
- 前端为 hash 路由（`VITE_ROUTER_HISTORY=hash`），无需服务端 history 回退。

构建（开发机，`pnpm` 版本由仓库 `packageManager` 指定为 11.x）：

```bash
cd yudao-ui/yudao-ui-admin-vben
pnpm install --frozen-lockfile

# 在应用目录执行
cd apps/web-antdv-next
pnpm build
# 产物：apps/web-antdv-next/dist/（以及 dist.zip，部署使用 dist/ 解包内容）
```

如果 workspace 依赖尚未构建，可在 monorepo 根目录改用 turbo 构建
（会先构建 `@vben/vite-config` 等依赖）：

```bash
cd yudao-ui/yudao-ui-admin-vben
pnpm build:antdv-next
```

本地开发若需要恢复旧的 `127.0.0.1:48080` 生产构建，可新建不入库的
`apps/web-antdv-next/.env.production.local` 覆盖（该文件已被 `.gitignore` 忽略）。

## 4. 基础设施准备（PostgreSQL / Redis）

### 4.1 创建数据库

**在可访问 `192.168.0.23` 的机器上**执行（板端 `psql` 同样可用）：

```bash
psql -h 192.168.0.23 -p 5432 -U root -d postgres \
  -c "CREATE DATABASE yudao_board ENCODING 'UTF8' TEMPLATE template0;"
```

`yudao_board` 不会被 SQL 脚本自动创建；`sql/postgresql/` 下的三个脚本都
只操作 `public` schema。

### 4.2 导入脚本

先把 SQL 传到板端（或直接在数据库主机上使用仓库文件）：

```bash
# 开发机，仓库根目录
ssh alientek@192.168.0.105 'mkdir -p /tmp/yudao-sql'
scp sql/postgresql/ruoyi-vue-pro.sql sql/postgresql/collector.sql \
  alientek@192.168.0.105:/tmp/yudao-sql/
```

板端导入（顺序固定）：

```bash
cd /tmp/yudao-sql
export PGPASSWORD='123456'   # 仅 dev 环境；真实环境用 .pgpass 或交互输入

# 1) 核心 system/infra 表与初始化数据
psql -h 192.168.0.23 -p 5432 -U root -d yudao_board \
  -v ON_ERROR_STOP=1 -f ruoyi-vue-pro.sql

# 2) collector 15 张表（协议、设备、传感器、通道、采样分区、聚合、报警等）
psql -h 192.168.0.23 -p 5432 -U root -d yudao_board \
  -v ON_ERROR_STOP=1 -f collector.sql
```

注意：

- `ruoyi-vue-pro.sql` 开头会 `DROP TABLE IF EXISTS` 重建 system/infra 表，
  **只用于空库或重置环境；不要在已有业务数据的库上重复执行**。
- `collector.sql` 开头同样 `DROP` 并重建 15 张 collector 表与相关序列，
  执行即清空采集器已有数据；日常升级一般不需要重新导入。
- `collector_sample_raw` 为按天 RANGE 分区表，脚本内置示例日分区与
  `DEFAULT` 分区；后续分区由维护任务按 `collector_retention_policy`
  提前创建。
- 导入完成后可用 `\dt public.collector_*` 检查 15 张表是否齐全。

### 4.3 Quartz 说明

`application-remote.yaml` 显式排除了
`org.springframework.boot.quartz.autoconfigure.QuartzAutoConfiguration`，
因此首期不需要 `quartz.sql`，定时任务也不会运行。将来启用 Quartz 时：

1. 向 `yudao_board` 导入 `sql/postgresql/quartz.sql`；
2. 修改 `application-remote.yaml`（或新增 profile）移除上述 exclude 项；
3. 重启 `yudao-server`。

### 4.4 数据库账号风险

开发/联调阶段直接使用 `root` 超级用户连接，仅限内网临时环境。后续应建立
独立低权限账号并替换 `/etc/yudao/env.remote`：

```sql
-- 在 yudao_board 所属实例上以 root 执行；密码替换为强口令
CREATE ROLE yudao_board LOGIN PASSWORD '<强密码>';
GRANT CONNECT ON DATABASE yudao_board TO yudao_board;
GRANT USAGE ON SCHEMA public TO yudao_board;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO yudao_board;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO yudao_board;
-- 需要代码生成写表结构时再按需授予 CREATE
```

### 4.5 PostgreSQL 兼容性：必须关闭 Druid 预编译语句缓存

**症状**：登录接口间歇性失败（实测约 50% 概率），前端返回
`code=500 系统异常`；后端日志出现：

```text
org.postgresql.util.PSQLException: This statement has been closed
```

**原因**：`application-remote.yaml` 默认启用 Druid 的
`pool-prepared-statements: true` 与
`max-pool-prepared-statement-per-connection-size: 20`。PostgreSQL 的
服务端预编译语句被关闭后，连接池仍复用旧语句句柄，后续请求即报错。

**修复**：在 `/etc/yudao/env.remote` 中关闭预编译语句缓存（`deploy/env.example`
已默认包含）：

```dotenv
SPRING_DATASOURCE_DYNAMIC_DRUID_POOL_PREPARED_STATEMENTS=false
SPRING_DATASOURCE_DYNAMIC_DRUID_MAX_POOL_PREPARED_STATEMENT_PER_CONNECTION_SIZE=0
```

修改后执行 `sudo systemctl restart yudao-server`。实测设置后连续 10 次登录
全部成功。该问题与远程 PostgreSQL 相关，板端部署必须保留这两项。

## 5. 板端目录、用户与配置文件

```text
/opt/yudao/
├── backend/
│   ├── yudao-server.jar -> releases/<版本>/yudao-server.jar   # 软链接，systemd 指向这里
│   └── releases/<版本>/yudao-server.jar
├── collector/
│   ├── current -> releases/<版本>/       # systemd 工作目录（目录软链接）
│   └── releases/<版本>/                  # main.py、poll.py、transport.py、
│                                          # storage.py、seed.py、hsi/、tests/、README.md
└── web/
    ├── current -> releases/<版本>/       # Nginx 静态根（目录软链接）
    └── releases/<版本>/                  # dist 内容
/etc/yudao/env.remote                     # 0600，root 所有
/var/log/yudao/                           # alientek 所有
```

> systemd 的 `ExecStart` 固定为 `/opt/yudao/backend/yudao-server.jar`，升级时
> 切换该 jar 软链接即可；前端仍使用目录软链接。

创建目录（板端）：

```bash
sudo install -d -o alientek -g alientek -m 0755 \
  /opt/yudao \
  /opt/yudao/backend \
  /opt/yudao/backend/releases \
  /opt/yudao/web \
  /opt/yudao/web/releases \
  /opt/yudao/collector \
  /opt/yudao/collector/releases
sudo install -d -o alientek -g alientek -m 0750 /var/log/yudao
sudo install -d -o root -g root -m 0755 /etc/yudao
```

把部署模板传到板端：

```bash
# 开发机，仓库根目录
ssh alientek@192.168.0.105 'mkdir -p /tmp/yudao-deploy'
scp -r deploy/* alientek@192.168.0.105:/tmp/yudao-deploy/
```

`deploy/.gitattributes` 已强制本目录文件以 LF 检出；若在 Windows 上从其他
途径拷贝模板，请确认 systemd 单元与 Nginx 配置为 LF 行尾。

写入环境变量文件（模板只含开发/联调占位值）：

```bash
sudo install -o root -g root -m 0600 \
  /tmp/yudao-deploy/env.example /etc/yudao/env.remote
sudoedit /etc/yudao/env.remote          # 按实际环境修改密码等
sudo chmod 600 /etc/yudao/env.remote
```

## 6. 环境变量与 profile

`deploy/systemd/yudao-server.service` 通过
`EnvironmentFile=/etc/yudao/env.remote` 注入变量；Spring Boot 把操作系统
环境变量视为最高优先级配置源。板端**不需要**再在仓库目录创建 `.env.remote`
（`application-remote.yaml` 中的 `spring.config.import` 是给开发机直接运行
jar 时用的可选路径）。

| 变量 | 板端值 | 作用 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `remote` | 启用 `application-remote.yaml`（端口 48080 等）；systemd 单元同时用 CLI 参数指定 |
| `SPRING_DATASOURCE_DYNAMIC_DRUID_POOL_PREPARED_STATEMENTS` | `false` | **必须**关闭 Druid 预编译语句缓存，见 4.5 |
| `SPRING_DATASOURCE_DYNAMIC_DRUID_MAX_POOL_PREPARED_STATEMENT_PER_CONNECTION_SIZE` | `0` | 同上 |
| `YUDAO_DB_HOST` / `PORT` / `NAME` | `192.168.0.23` / `5432` / `yudao_board` | 覆盖远程数据源 |
| `YUDAO_DB_USERNAME` / `PASSWORD` | `root` / `123456` | **仅 dev**，见 4.4 |
| `YUDAO_REDIS_HOST` / `PORT` | `192.168.0.23` / `6379` | 远程 Redis |
| `SPRING_DATA_REDIS_DATABASE` | `1` | 覆盖 YAML 中写死的 `database: 0`，无需改代码 |
| `YUDAO_REDIS_PASSWORD` | 空 | 无密码 Redis；YAML 的 password 行默认注释 |
| `YUDAO_SECURITY_MOCK_ENABLE` | `true` | **内网联调跳过鉴权**，上线前必须改 `false` |
| `YUDAO_CAPTCHA_ENABLE` | `false` | 与前端 `VITE_APP_CAPTCHA_ENABLE=false` 对齐 |
| `YUDAO_ACCESS_LOG_ENABLE` | `false` | 关闭访问日志，降低板端写入压力 |
| `LOGGING_FILE_NAME` | `/var/log/yudao/yudao-server.log` | 覆盖 `${user.home}/logs` 默认路径 |

要点：

- `application-remote.yaml` 中数据库、Redis 默认值都是 `127.0.0.1`，**未注入
  环境变量时服务会尝试连接板端本机**，因此 `/etc/yudao/env.remote` 是必需的。
- `systemd EnvironmentFile` 不支持行尾注释，注释必须独占一行；值不要加引号。
- profile 为 `remote` 时服务端口固定为 `48080`，Nginx 反代目标与此一致。

## 7. 部署后端

```bash
# 开发机，仓库根目录：传输 jar
scp yudao-server/target/yudao-server.jar alientek@192.168.0.105:/tmp/
```

板端（`alientek` 用户，目录已归属该用户）：

```bash
VERSION=$(date +%Y%m%d-%H%M)-$(sha256sum /tmp/yudao-server.jar | cut -c1-8)
install -d /opt/yudao/backend/releases/$VERSION
cp /tmp/yudao-server.jar /opt/yudao/backend/releases/$VERSION/
# systemd 的 ExecStart 固定为 /opt/yudao/backend/yudao-server.jar
ln -sfn releases/$VERSION/yudao-server.jar /opt/yudao/backend/yudao-server.jar
ls -l /opt/yudao/backend/yudao-server.jar
```

安装并启动 systemd 单元：

```bash
sudo install -m 0644 /tmp/yudao-deploy/systemd/yudao-server.service /etc/systemd/system/
sudo install -m 0644 /tmp/yudao-deploy/systemd/collector.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now yudao-server
```

首次启动可观察（旧板实测约 140 秒后才监听 48080，属正常现象）：

```bash
systemctl status yudao-server --no-pager
journalctl -u yudao-server -f          # 观察启动进度
ss -ltnp | grep ':48080'
```

> 服务以 `alientek` 运行，`ExecStart` 使用 `/usr/bin/java`（由
> `openjdk-25-jre-headless` 的 alternatives 提供）。如 `readlink -f
> /usr/bin/java` 不是 JDK 25，改用实际路径并同步修改单元文件。

## 8. 部署前端与 Nginx

```bash
# 开发机：在 apps/web-antdv-next 目录执行，上传 dist
VERSION=$(date +%Y%m%d-%H%M)
ssh alientek@192.168.0.105 "mkdir -p /tmp/yudao-web-$VERSION"
scp -r dist alientek@192.168.0.105:/tmp/yudao-web-$VERSION/
echo "前端版本: $VERSION"    # 板端使用同一个 VERSION
```

板端：

```bash
VERSION=20261113-1800   # 示例值，必须与开发机 echo 输出的版本一致
install -d /opt/yudao/web/releases/$VERSION
cp -a /tmp/yudao-web-$VERSION/dist/. /opt/yudao/web/releases/$VERSION/
ln -sfn /opt/yudao/web/releases/$VERSION /opt/yudao/web/current
ls /opt/yudao/web/current/index.html
```

安装 Nginx 站点：

```bash
sudo install -m 0644 /tmp/yudao-deploy/nginx/collector.conf /etc/nginx/conf.d/collector.conf
sudo rm -f /etc/nginx/sites-enabled/default   # 避免默认站点抢占 80 端口
sudo nginx -t
sudo systemctl enable --now nginx
sudo systemctl reload nginx
```

配置要点：

- 静态根为 `/opt/yudao/web/current`，升级时切换软链接即可。
- `/admin-api/`、`/app-api/` 反代到 `127.0.0.1:48080`，`proxy_pass` 不带
  URI，保留请求前缀。
- `client_max_body_size 32m` 与 `application.yaml` 的 `max-request-size`
  一致。
- 前端 hash 路由：页面路径在 `#` 之后，服务端只需返回 `index.html`。
- WebSocket（`/infra/ws`）与 Druid/Swagger/Admin 页面为可选块，默认注释；
  板端不建议开放 Druid 监控。

## 9. 部署 collector

collector 与后端/前端一致，采用“版本目录 + `current` 软链接”部署。
`collector.service` 的 `WorkingDirectory` 与 `ExecStart` 均指向
`/opt/yudao/collector/current`，升级时只需切换软链接并重启服务。

版本命名与后端一致：`YYYYMMDD-HHMM-<git sha 前 8 位>`。开发机在仓库根目录先
生成版本号，再把整个 `collector/` 目录（含 `hsi/`、`tests/`、`README.md`）上传到
板端临时目录：

```bash
# 开发机，源代码仓库根目录（或功能 worktree）
VERSION=$(date +%Y%m%d-%H%M)-$(git rev-parse --short=8 HEAD)
echo "collector 版本: $VERSION"
ssh alientek@192.168.0.105 "mkdir -p /tmp/yudao-collector-$VERSION"
scp -r collector alientek@192.168.0.105:/tmp/yudao-collector-$VERSION/
```

板端（`alientek` 用户，`/opt/yudao/collector` 已归属该用户）：

```bash
VERSION=20261113-2059-7b6c3ac9   # 必须与开发机 echo 输出的版本一致
install -d /opt/yudao/collector/releases/$VERSION
cp -a /tmp/yudao-collector-$VERSION/collector/. \
  /opt/yudao/collector/releases/$VERSION/
ln -sfn releases/$VERSION /opt/yudao/collector/current
ls -l /opt/yudao/collector/current
ls /opt/yudao/collector/current/main.py
```

安装 systemd 单元并启动：

```bash
sudo install -m 0644 /tmp/yudao-deploy/systemd/collector.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now collector
systemctl status collector --no-pager
systemctl show -p WorkingDirectory -p ExecStart collector
```

从旧版平铺目录迁移时，先 `sudo systemctl stop collector`，把原平铺文件移入
`releases/<版本>/`（或直接按上述流程上传新版本），创建 `current` 软链接后再删除
平铺的 `main.py`、`poll.py`、`transport.py`、`storage.py`、`seed.py`、`hsi/`、
`tests/`、`README.md` 与 `__pycache__`，只保留 `releases/` 与 `current`。
JSONL 与日志仍在 `/var/log/yudao/`，不要随代码目录删除。

`collector.service` 使用 `main.py` 的真实参数：

| 参数 | 值 | 说明 |
|---|---|---|
| `--port` | `/dev/ttyS4` | HSI RS485 总线 |
| `--interval` | `60` | collector README 建议的轮询间隔（秒）；原型默认 5 |
| `--duration` | `0` | 一直运行；原型默认 600 秒后退出 |
| `--timeout` | `1.0` | 单次应答超时 |
| `--retries` | `1` | 单次采集失败后的重试次数 |
| `--output` | `/var/log/yudao/collector-data.jsonl` | 每次启动覆盖 |
| `--log` | `/var/log/yudao/collector.log` | 每次启动覆盖 |
| `--flags` | `auto` | 依次探测 `1,3,5,7` 并回退 `0`（本现场实际为 `0`） |
| `--db-retry-interval` | `60` | DB 不可用时的重连/重新种子间隔（秒） |

注意：

- 原型 README 的 `~/collector-proto/` 是手工运行目录；systemd 部署统一使用
  `/opt/yudao/collector/current`，不要混淆。
- DB 不可用时 collector 不会退出：继续写 JSONL（`channel_id` 为 null），并按
  `--db-retry-interval` 自动重连、重新执行种子与解析映射；恢复后自动落库。
- `--output` / `--log` 每次进程启动都会覆盖原文件；服务重启会丢失旧文件内容。
- 若串口设备名变化，用 `systemctl edit collector` 增加 drop-in 覆盖
  `ExecStart`（或修改单元后 `daemon-reload`）。
- 切换 `current` 后必须重启服务（systemd 已解析工作目录与启动命令），仅切软链接
  不会让运行中的进程加载新代码。
- 60 秒间隔尚未做长跑验证（见 `collector/README.md`），首期需持续观察成功率。

## 10. 健康检查

### 10.1 后端

```bash
systemctl status yudao-server --no-pager
ss -ltnp | grep ':48080'
journalctl -u yudao-server -n 100 --no-pager
tail -n 50 /var/log/yudao/yudao-server.log

# 验证码接口为 @PermitAll，能同时验证后端与 Redis 连通性
curl -s -o /dev/null -w '%{http_code}\n' \
  -H 'tenant-id: 1' -H 'Content-Type: application/json' -d '{}' \
  -X POST http://127.0.0.1:48080/admin-api/system/captcha/get
```

期望：`java` 进程监听 48080，接口返回 `200`，日志无数据库/Redis 连接异常。

可选（需安装 `redis-tools`）：确认 OAuth2 token 写入 db 1，而不是 db 0：

```bash
redis-cli -h 192.168.0.23 -p 6379 -n 1 DBSIZE   # 登录/刷新 token 后应大于 0
```

### 10.2 前端与 Nginx

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1/
curl -s -o /dev/null -w '%{http_code}\n' \
  -H 'tenant-id: 1' -H 'Content-Type: application/json' -d '{}' \
  -X POST http://127.0.0.1/admin-api/system/captcha/get
```

用浏览器访问 `http://192.168.0.105/`，确认登录页可打开；浏览器 Network 中
接口请求应为 `/admin-api/...`（同源），而不是 `127.0.0.1:48080`。

### 10.3 collector

```bash
systemctl status collector --no-pager
journalctl -u collector -n 50 --no-pager
tail -n 20 /var/log/yudao/collector.log
grep -c '"sensor"' /var/log/yudao/collector-data.jsonl 2>/dev/null || true
```

期望日志出现 RS485 flags 探测结果、`cycle` 轮询与解析字段；JSONL 行数持续增加。
若出现串口权限错误，检查 `groups alientek` 与 `/dev/ttyS4` 属组。

## 11. JVM 参数建议

`yudao-server.service` 与板端实际部署一致：

```text
/usr/bin/java -Xms256m -Xmx768m -XX:MaxMetaspaceSize=256m \
  -XX:+UseSerialGC -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8 \
  -jar /opt/yudao/backend/yudao-server.jar --spring.profiles.active=remote
```

- 4GB 旧板优先省内存，避免 JVM 挤压系统与文件缓存。
- 旧板实测：启动约 140 秒，监听后 RSS 约 540MB；`-Xmx768m` 对当前精简模块
  （system + infra）足够，若频繁 GC 或 OOM 再上调。
- `-XX:+UseSerialGC` 减少小内存板上的 GC 线程与本地内存开销；4GB 板建议保留。
- `-Duser.timezone=Asia/Shanghai` 与 `-Dfile.encoding=UTF-8` 固定板端时区与编码，
  与 `Asia/Shanghai` 的业务时间语义一致。
- 新板 8GB 可调整为 `-Xms512m -Xmx1024m`（或 1536m），同步观察 RSS。
- 不建议在 4GB 板上与 GNOME/kiosk 同时运行；生产验证期优先
  `sudo systemctl set-default multi-user.target`（无桌面）。

## 12. 升级与回滚

### 12.1 升级前备份数据库

**版本提醒**：数据库为 PostgreSQL 17（SQL dump 头部为 17.11），而 Ubuntu 24.04
的 `postgresql-client` 通常是 16.x；`pg_dump` 不允许客户端版本低于服务端。
因此备份命令应在 **PG 主机（192.168.0.23）本地**或装有 PostgreSQL 17+
客户端的机器上执行，不要在板端直接用 16.x 的 `pg_dump` 备份 PG 17：

```bash
pg_dump -h 192.168.0.23 -p 5432 -U root -Fc \
  -f yudao_board_$(date +%F).dump yudao_board
```

恢复（仅在确认需要回滚数据库时执行）：

```bash
pg_restore -h 192.168.0.23 -p 5432 -U root \
  -d yudao_board --clean --if-exists yudao_board_2026-11-13.dump
```

### 12.2 升级后端 jar

```bash
# 板端：新 jar 放入新版本目录，切换 jar 软链接后重启
VERSION=20261114-0900   # 示例值，按实际新版本命名
install -d /opt/yudao/backend/releases/$VERSION
cp /tmp/yudao-server.jar /opt/yudao/backend/releases/$VERSION/
ln -sfn releases/$VERSION/yudao-server.jar /opt/yudao/backend/yudao-server.jar
sudo systemctl restart yudao-server
systemctl status yudao-server --no-pager
```

### 12.3 升级前端

```bash
VERSION=20261114-0900   # 示例值，按实际新版本命名
install -d /opt/yudao/web/releases/$VERSION
cp -a /tmp/yudao-web-$VERSION/dist/. /opt/yudao/web/releases/$VERSION/
ln -sfn /opt/yudao/web/releases/$VERSION /opt/yudao/web/current
sudo systemctl reload nginx
```

### 12.4 升级 collector

```bash
# 开发机：生成版本号并上传（命令同第 9 节）
VERSION=$(date +%Y%m%d-%H%M)-$(git rev-parse --short=8 HEAD)
ssh alientek@192.168.0.105 "mkdir -p /tmp/yudao-collector-$VERSION"
scp -r collector alientek@192.168.0.105:/tmp/yudao-collector-$VERSION/

# 板端：新版本放入 releases，切换 current 后重启
VERSION=20261114-0900-abcdef12   # 按实际版本替换
install -d /opt/yudao/collector/releases/$VERSION
cp -a /tmp/yudao-collector-$VERSION/collector/. \
  /opt/yudao/collector/releases/$VERSION/
ln -sfn releases/$VERSION /opt/yudao/collector/current
sudo systemctl restart collector
systemctl status collector --no-pager
systemctl show -p WorkingDirectory -p ExecStart collector
```

升级前建议先 `sudo systemctl stop collector` 再上传（串口同时只能被一个进程
占用）；上传、切换后再 `systemctl start collector`。切换 `current` 后必须重启
服务，运行中的进程不会自动加载新代码。

旧版本目录保留策略：

- 至少保留当前版本与一个上一版本，便于快速回滚；确认新版本稳定运行后再清理更早
  的目录（板端 16GB eMMC，collector 目录本身只有几百 KB，通常无需急于清理）。
- 清理时只删除 `/opt/yudao/collector/releases/<旧版本>`，不要删除 `current`
  或 `/var/log/yudao/` 下的日志。
- 若新版本有问题，切回旧目录并重启即可（见 12.5），无需重新上传旧代码。

### 12.5 回滚

```bash
# 后端：切回上一版本的 jar 并重启（以 20261113-1800 为例）
ln -sfn releases/20261113-1800/yudao-server.jar /opt/yudao/backend/yudao-server.jar
sudo systemctl restart yudao-server

# 前端：切回上一版本目录并重载 Nginx
ln -sfn /opt/yudao/web/releases/20261113-1800 /opt/yudao/web/current
sudo systemctl reload nginx

# collector：切回旧版本目录并重启（须先确认旧目录仍在 releases/ 下）
ls -1 /opt/yudao/collector/releases
ln -sfn releases/20261113-2059-7b6c3ac9 /opt/yudao/collector/current
sudo systemctl restart collector
systemctl status collector --no-pager
```

建议至少保留一个上一版本目录；若数据库结构与代码版本绑定，回滚 jar 前先用
12.1 的备份评估数据兼容性，不要随意重跑 `ruoyi-vue-pro.sql` / `collector.sql`。

## 13. 旧板 → 新板迁移清单

新板为 8GB 内存 / 64GB 存储、同为 Ubuntu 24.04。迁移原则：**迁移配置与产物，
不克隆整机镜像**；数据库在 `192.168.0.23`，无需迁移数据。

- [ ] 记录并迁移 `/etc/yudao/env.remote`（权限保持 0600）。
- [ ] 迁移 systemd 单元：`yudao-server.service`、`collector.service`
      （`kiosk.service` 按需）。
- [ ] 迁移 `/etc/nginx/conf.d/collector.conf`，禁用默认站点。
- [ ] 新板重新安装 apt 包（JDK 25、nginx、postgresql-client、python3-psycopg2），
      校验 `java -version`。
- [ ] 重新部署后端 jar 与前端 dist（重新走第 3、7、8 节，不复制旧版本目录）。
- [ ] 重新部署 `collector/`（第 9 节）。
- [ ] 重新校准时区 `Asia/Shanghai`、NTP、`dialout` 组。
- [ ] 核对串口设备名：`ls -l /dev/ttyS*`、`ls -l /dev/serial/by-id/`；
      新旧板命名可能不同，必要时修改 `collector.service` 的 `--port`。
- [ ] 若使用 SD 卡启动，用 `lsblk` 确认设备名后扩展分区与文件系统
      （`growpart` / `resize2fs`），注意不要误操作其他盘。
- [ ] 新板 8GB 可上调 JVM `-Xmx`（第 11 节），并重新做健康检查。
- [ ] 旧板保留为回退环境，直到新板连续运行稳定后再下线。

## 14. 已知限制与风险

1. **远程基础设施单点**：PostgreSQL/Redis 均在 `192.168.0.23`，网络或主机故障
   会导致管理后台不可用；collector 在 DB 不可用时继续采样并写 JSONL，按
   `--db-retry-interval` 自动重连，但断连期间的数据不会补写数据库（开发者已
   接受首期该风险）。
2. **无断网 spool**：collector 没有本地重传队列；DB 不可用期间仅保留 JSONL，
   恢复后从下一轮开始落库，断连期间的 raw 数据丢失。
3. **无 swap**：当前板端未启用 swap。可选启用 1GB zram（`systemd-zram-generator`，
   安装后配置 `/etc/systemd/zram-generator.conf` 的 `[zram0] zram-size = 1024`
   并重启）；zram 会占用内存，是否启用需结合实际压力评估。
4. **4GB 内存**：不建议同时运行 GNOME/kiosk 与后端/collector；验证期建议无桌面。
   kiosk 单元仅在具备图形会话时使用。
5. **安全**：`YUDAO_SECURITY_MOCK_ENABLE=true` 会跳过真实鉴权，且数据库使用
   `root` 超级用户；仅限内网联调，上线前必须改为真实鉴权与独立数据库账号。
6. **无 HTTPS**：Nginx 仅监听 80，建议限制在受信内网网段（防火墙只放行
   80/22 等必要端口）。
7. **collector 为原型**：已写 `collector_sample_raw` / `collector_device_status` /
   `collector_heartbeat`；`--output`/`--log` 每次启动覆盖；60 秒轮询尚未长跑
   验证；传感器可能处于自测随机模式。
8. **collector 输出无轮转**：JSONL 与 Python 日志不会自动切割，需监控
   `/var/log/yudao` 占用。
9. **前端生产配置**：`.env.production` 已改为板端相对路径；本地若需要旧的
   `127.0.0.1:48080` 构建，使用不入库的 `.env.production.local` 覆盖。
10. **Druid/Swagger/Admin 页面**：未默认开放反代；需要时再启用 Nginx 可选块，
    并评估监控页面暴露风险。

## 15. 本目录文件清单

| 文件 | 用途 |
|---|---|
| `deploy/README.md` | 本文，板端部署手册 |
| `deploy/env.example` | `/etc/yudao/env.remote` 模板（systemd EnvironmentFile） |
| `deploy/systemd/yudao-server.service` | 后端 systemd 单元 |
| `deploy/systemd/collector.service` | HSI collector systemd 单元 |
| `deploy/systemd/kiosk.service` | 可选 kiosk 单元（依赖图形会话） |
| `deploy/nginx/collector.conf` | Nginx 站点配置（静态 + API 反代） |
| `deploy/.gitattributes` | 强制 deploy 目录以 LF 行尾检出（板端直接用） |
| `deploy/release.sh` | 开发机部署入口（构建、上传、远端调用） |
| `deploy/deploy.env.example` | 本机部署配置模板（复制为 deploy.env 后使用） |
| `deploy/.gitignore` | 忽略本机 deploy.env（含密码，禁止提交） |
| `deploy/scripts/lib/common.sh` | 板端脚本公共函数（日志、sudo、版本、软链接、健康检查） |
| `deploy/scripts/bootstrap-board.sh` | 板端新板初始化（幂等） |
| `deploy/scripts/deploy-backend.sh` | 板端后端 jar 版本化部署 |
| `deploy/scripts/deploy-frontend.sh` | 板端前端 dist 版本化部署 |
| `deploy/scripts/deploy-collector.sh` | 板端 collector 版本化部署 |
| `deploy/scripts/update-all.sh` | 板端按顺序更新多个组件 |
| `deploy/scripts/rollback.sh` | 板端按组件回滚 |

## 16. 部署脚本（deploy/scripts）

> **`init-db.sh` 暂未提供**（开发者待定）。数据库初始化仍按第 4 节手工执行：
> 先在 PostgreSQL 主机建库，再导入 `sql/postgresql` 下的 system/infra 与
> collector 脚本。部署脚本不会创建数据库或执行 SQL。

本节脚本均满足同一条帮助约定：**不带任何参数（或 `-h` / `--help`）时只输出
完整帮助并退出 0，不执行任何动作**；未知参数、缺少必要参数时打印帮助与错误、
以非 0 退出。`--dry-run` 只打印动作，不改动系统。

### 16.1 脚本清单

| 脚本 | 运行位置 | 用途 |
|---|---|---|
| `deploy/release.sh` | 开发机 | 构建 + 上传 + 调用板端脚本；子命令 `bootstrap` / `backend` / `frontend` / `collector` / `all` / `rollback` |
| `deploy/scripts/bootstrap-board.sh` | 板端 | 新板初始化（apt 包、目录、systemd、Nginx、enable 服务、清理旧版本） |
| `deploy/scripts/deploy-backend.sh` | 板端 | jar 放入 `releases/<版本>`，切 jar 软链接，重启并等待 48080 |
| `deploy/scripts/deploy-frontend.sh` | 板端 | dist 放入 `releases/<版本>`，切 `current`，`nginx -t` + reload，检查 200 |
| `deploy/scripts/deploy-collector.sh` | 板端 | 代码放入 `releases/<版本>`，切 `current`，重启并检查 active |
| `deploy/scripts/update-all.sh` | 板端 | 按 backend → frontend → collector 顺序调用上述三个脚本 |
| `deploy/scripts/rollback.sh` | 板端 | 按组件切回上一版本（或 `--version` 指定版本）并做健康检查 |
| `deploy/scripts/lib/common.sh` | 板端 | 日志、sudo、版本号、软链接、清理、健康检查公共函数 |

### 16.2 快速上手

首次在新板部署：

```bash
# 开发机，工作区根目录
cp deploy/deploy.env.example deploy/deploy.env   # 填板端地址；sudo 密码可选
./deploy/release.sh bootstrap                    # 上传模板并初始化板端
./deploy/release.sh backend                      # 构建并部署后端
./deploy/release.sh frontend                     # 构建并部署前端
./deploy/release.sh collector                    # 打包并部署 collector
```

日常更新可以单独执行，也可以一条命令按顺序更新三个组件：

```bash
./deploy/release.sh backend                      # 仅后端
./deploy/release.sh frontend --skip-build        # 复用已有 dist
./deploy/release.sh collector
./deploy/release.sh all                          # 共用同一版本号，依次更新
./deploy/release.sh all --dry-run                # 只打印构建/上传/远端命令
```

回滚（默认回滚到板端当前版本的上一版本）：

```bash
./deploy/release.sh rollback frontend
./deploy/release.sh rollback backend --version 20261113-1842-d785623e
./deploy/release.sh rollback collector --dry-run
```

不经过开发机、直接在板端执行时：

```bash
# 上传脚本（示例；release.sh 会自动完成）
tar -C deploy -czf - scripts | ssh alientek@192.168.0.105 \
  'mkdir -p /tmp/yudao-deploy && tar -xzf - -C /tmp/yudao-deploy'

# 板端（alientek，systemctl/nginx 动作内部使用 sudo）
bash /tmp/yudao-deploy/scripts/deploy-backend.sh --jar /tmp/yudao-server.jar
bash /tmp/yudao-deploy/scripts/deploy-frontend.sh --dist /tmp/dist
bash /tmp/yudao-deploy/scripts/deploy-collector.sh --src /tmp/collector
bash /tmp/yudao-deploy/scripts/update-all.sh --all --jar /tmp/j.jar --dist /tmp/dist --src /tmp/collector
bash /tmp/yudao-deploy/scripts/rollback.sh collector --dry-run
```

### 16.3 版本号与保留策略

- 后端/collector 默认版本：`YYYYMMDD-HHMM-<git sha 前 8 位>`；板端没有 git
  仓库时后端退化为 jar 的 sha256 前 8 位，前端/collector 退化为时间戳。
- `release.sh backend|frontend|collector` 每次在开发机生成版本号并回显；
  `release.sh all` 生成一次版本号，三个组件共用，便于对应回滚。
- `--keep N`（默认 3）控制 `releases/` 保留数量；只删除 `releases/` 下的一级
  版本目录，`current` 软链接与 `/var/log/yudao/` 不会被清理。
- 后端健康检查失败会自动切回上一版本并重启；collector 启动失败同样自动回滚。
  前端健康检查失败不会自动回滚，请手工执行 `rollback.sh frontend`。

### 16.4 deploy.env 与安全提示

`deploy/release.sh` 启动时读取 `deploy/deploy.env`（存在时），CLI 参数优先：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `BOARD_HOST` | `192.168.0.105` | 板端地址（`--host`） |
| `BOARD_USER` | `alientek` | SSH 用户（`--user`） |
| `BOARD_PORT` | `22` | SSH 端口（`--port`） |
| `SSH_KEY` | `~/.ssh/id_rsa` | 本机私钥（`--key`） |
| `BOARD_SUDO_PASSWORD` | 空 | 板端 sudo 密码（`--sudo-password`）；空则 `ssh -t` 交互输入 |

- `deploy.env` 已被 `deploy/.gitignore` 忽略，**禁止提交**；模板只含占位值。
- 配置了 `BOARD_SUDO_PASSWORD` 时，release.sh 通过 ssh 的 stdin 把密码送入远端
  `sudo -S`，不会出现在远端命令行；但 `--sudo-password` 写在本地命令行仍会进入
  shell 历史，推荐写入 `deploy.env`。
- 远端脚本以 sudo 执行，部署产物会自动 `chown` 回 `alientek`，不影响后续
  以普通用户手工维护。
- 以上密码方式仅限开发/联调；生产环境应改用受限 sudoers 规则或人工输入。
- 前端构建依赖 `pnpm`；后端构建依赖 `mvn` 与 JDK 25；开发机为 Windows Git
  Bash 时脚本不依赖 `rsync`，使用 `scp` / `tar | ssh`。
