# yudao-mini 开发测试中间件

本目录保存 yudao-mini 开发测试环境所用中间件的 Docker Compose 配置，供局域网开发机共用。

当前部署目标机：`192.168.0.23`，远程部署目录 `/home/stariot/yudao-infra`，Compose 项目名 `yudao-infra`。

已部署服务：PostgreSQL（ParadeDB）、Redis、RabbitMQ（含管理台）、MinIO。

## 文件

- `docker-compose.yml`：与远程实际部署一致的 Compose 配置
- `.env.example`：可选的变量模板，复制为 `.env` 后可覆盖默认值

## 端口与连接信息

| 服务 | 地址 | 端口 | 账号 / 密码 | 说明 |
| --- | --- | --- | --- | --- |
| PostgreSQL | `192.168.0.23` | `5432` | `root` / `123456` | 数据库 `ruoyi-vue-pro`，已启用 `vector` 扩展 |
| Redis | `192.168.0.23` | `6379` | 无密码 | 已开启 AOF |
| RabbitMQ AMQP | `192.168.0.23` | `5672` | `rabbit` / `rabbit` | |
| RabbitMQ 管理台 | `http://192.168.0.23:15672` | `15672` | `rabbit` / `rabbit` | |
| MinIO S3 API | `http://192.168.0.23:9000` | `9000` | `minioadmin` / `minioadmin` | S3 兼容对象存储 |
| MinIO 控制台 | `http://192.168.0.23:9001` | `9001` | `minioadmin` / `minioadmin` | |

连接串示例：

```text
PostgreSQL: jdbc:postgresql://192.168.0.23:5432/ruoyi-vue-pro
Redis:      192.168.0.23:6379（无密码）
RabbitMQ:   amqp://rabbit:rabbit@192.168.0.23:5672/
管理台:     http://192.168.0.23:15672
MinIO:      http://192.168.0.23:9000（AccessKey/SecretKey: minioadmin/minioadmin）
MinIO 控制台: http://192.168.0.23:9001
```

所有端口发布到 `0.0.0.0`，仅建议在受控局域网内使用。

## 镜像版本

镜像与 WeKnora 使用的中间件版本保持一致（PostgreSQL 采用 ParadeDB 发行版）。

| 服务 | 镜像 | 实际版本 | 镜像 digest |
| --- | --- | --- | --- |
| PostgreSQL | `paradedb/paradedb:v0.25.11-pg17` | PostgreSQL 17.11 + pgvector 0.8.4 + pg_search 0.25.11 | `sha256:78d65750f0f88a56548526201bcab9910f83b90fe929cdbf979890f3f80b0f95` |
| Redis | `redis:7.0-alpine` | 7.0.15 | `sha256:c9d92d840fd011c908f040592857c724ae6d877f2aba5c40ad963276507386b2` |
| RabbitMQ | `rabbitmq:4-management-alpine` | 4.4.0 | `sha256:4841f6384ef8313c3594cd3f2e9f40c10805f8a61c45f08a9a5d9ddea8199267` |
| MinIO | `pgsty/minio:RELEASE.2026-08-04T00-00-00Z` | RELEASE.2026-08-04T00-00-00Z | `sha256:b6bfe7239bfc83fb90d31612d9704d86039dd714f7904b3f1ad68f211e602372` |

目标机 Docker Hub 直连不可达，镜像拉取依赖已配置的华为云 SWR 加速器。更新镜像前先确认加速器可拉取目标 tag，并记录新 digest。

## 常用操作

以下命令均在远程目标机 `/home/stariot/yudao-infra` 目录执行。

```bash
# 启动 / 应用配置变更
docker compose up -d

# 查看状态与健康检查
docker compose ps

# 查看日志（指定服务名：postgres / redis / rabbitmq / minio）
docker compose logs -f postgres

# 重启
docker compose restart

# 停止（保留数据卷）
docker compose stop

# 更新镜像
docker compose pull && docker compose up -d
```

**禁止执行 `docker compose down -v`**：`-v` 会删除数据卷，导致数据丢失。普通 `docker compose down` 只删除容器和网络，数据卷仍然保留。

## 数据持久化

使用 Compose 命名数据卷：

| 数据卷 | 挂载点 | 用途 |
| --- | --- | --- |
| `yudao-infra_pgdata` | `/var/lib/postgresql/data` | PostgreSQL 数据 |
| `yudao-infra_redisdata` | `/data` | Redis AOF 持久化数据 |
| `yudao-infra_rabbitmqdata` | `/var/lib/rabbitmq` | RabbitMQ 数据 |
| `yudao-infra_miniodata` | `/data` | MinIO 对象数据 |

四个服务均设置 `restart: unless-stopped`，目标机重启后会自动拉起。

## 数据库初始化

仓库自带 PostgreSQL 初始化 SQL：

- `sql/postgresql/ruoyi-vue-pro.sql`
- `sql/postgresql/quartz.sql`

**仅在空库首次初始化时执行；两个脚本包含 `DROP TABLE IF EXISTS`，在已有数据的库上重跑会清空表。**

将两个文件传到目标机临时目录后，按顺序导入：

```bash
mkdir -p /tmp/yudao-sql
# 在开发机使用 pscp 上传上面两个 SQL 文件到 /tmp/yudao-sql/

docker exec -i yudao-infra-postgres psql -U root -d ruoyi-vue-pro -v ON_ERROR_STOP=1 -q -f - < /tmp/yudao-sql/ruoyi-vue-pro.sql
docker exec -i yudao-infra-postgres psql -U root -d ruoyi-vue-pro -v ON_ERROR_STOP=1 -q -f - < /tmp/yudao-sql/quartz.sql
```

`ON_ERROR_STOP=1` 保证出错立即停止；出现任何 `ERROR` 都不应视为导入成功。

重新初始化数据库后需重新启用向量扩展：

```bash
docker exec yudao-infra-postgres psql -U root -d ruoyi-vue-pro -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

验证：

```bash
docker exec yudao-infra-postgres psql -U root -d ruoyi-vue-pro -c "\dt"
docker exec yudao-infra-postgres psql -U root -d ruoyi-vue-pro -c "SELECT count(*) FROM system_users;"
docker exec yudao-infra-postgres psql -U root -d ruoyi-vue-pro -c "SELECT 1 FROM dual;"
docker exec yudao-infra-postgres psql -U root -d ruoyi-vue-pro -c "SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';"
```

`dual` 表用于 Druid 的 `SELECT 1 FROM DUAL` 探测，必须存在。

## 备份与恢复（PostgreSQL）

版本切换（`postgres:17-alpine` → `paradedb/paradedb:v0.25.11-pg17`）前已在目标机 `/home/stariot/yudao-infra/` 生成备份：

```text
ruoyi-vue-pro-20261112-103735.sql.gz    # ruoyi-vue-pro 数据库（pg_dump 明文 SQL + gzip）
pg-globals-20261112-103735.sql          # 全局角色（pg_dumpall --globals-only）
```

备份命令：

```bash
cd /home/stariot/yudao-infra
TS=$(date +%Y%m%d-%H%M%S)
docker exec -i yudao-infra-postgres pg_dump -U root -d ruoyi-vue-pro --no-owner --no-privileges | gzip > ruoyi-vue-pro-$TS.sql.gz
docker exec -i yudao-infra-postgres pg_dumpall -U root --globals-only > pg-globals-$TS.sql
gzip -t ruoyi-vue-pro-$TS.sql.gz
```

如数据卷损坏或镜像不兼容导致无法启动，可停止 PostgreSQL、保留旧数据卷不动，另建新数据卷并恢复备份（`POSTGRES_USER=root` 会在空数据目录上自动创建 `root` 角色和 `ruoyi-vue-pro` 数据库）。恢复后需重新执行 `CREATE EXTENSION IF NOT EXISTS vector;`。

## 注意事项与风险

- **Redis 无密码**：这是开发者沿用项目默认的选择。端口对 `0.0.0.0` 开放，局域网内任何主机都可读写 Redis，仅限受控内网使用。
- **Redis 为 plain 7.0**：不包含 RediSearch / 向量检索模块，仅提供标准 KV、过期、发布订阅等能力。
- **PostgreSQL 使用 `root` 超级用户**：开发测试便利优先；生产环境应创建最小权限业务账号。
- 默认凭据（PostgreSQL `root/123456`、RabbitMQ `rabbit/rabbit`、MinIO `minioadmin/minioadmin`）是开发测试用途，禁止用于生产环境。
- ParadeDB 镜像内 `pg_search` 扩展可用但**当前未启用**（`shared_preload_libraries` 为空）。仅 `vector` 扩展已在 `ruoyi-vue-pro` 库中创建。若后续需要 ParadeDB 全文检索，需显式配置 `shared_preload_libraries = 'pg_search'` 并重启 PostgreSQL。
- MinIO 使用默认 `minioadmin/minioadmin`，控制台与 S3 API 均对局域网开放。
