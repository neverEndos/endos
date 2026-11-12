# yudao-mini 开发测试中间件

本目录保存 yudao-mini 开发测试环境所用中间件的 Docker Compose 配置，供局域网开发机共用。

当前部署目标机：`192.168.0.23`，远程部署目录 `/home/stariot/yudao-infra`，Compose 项目名 `yudao-infra`。

已部署服务：PostgreSQL、Redis、RabbitMQ（含管理台）。MinIO 因目标机无法从 Docker Hub 拉取镜像而**暂未部署**，详见文末说明。

## 文件

- `docker-compose.yml`：与远程实际部署一致的 Compose 配置
- `.env.example`：可选的变量模板，复制为 `.env` 后可覆盖默认值

## 端口与连接信息

| 服务 | 地址 | 端口 | 账号 / 密码 | 说明 |
| --- | --- | --- | --- | --- |
| PostgreSQL | `192.168.0.23` | `5432` | `root` / `123456` | 数据库 `ruoyi-vue-pro` |
| Redis | `192.168.0.23` | `6379` | 无密码 | 已开启 AOF |
| RabbitMQ AMQP | `192.168.0.23` | `5672` | `rabbit` / `rabbit` | |
| RabbitMQ 管理台 | `http://192.168.0.23:15672` | `15672` | `rabbit` / `rabbit` | |

连接串示例：

```text
PostgreSQL: jdbc:postgresql://192.168.0.23:5432/ruoyi-vue-pro
Redis:      192.168.0.23:6379（无密码）
RabbitMQ:   amqp://rabbit:rabbit@192.168.0.23:5672/
管理台:     http://192.168.0.23:15672
```

所有端口发布到 `0.0.0.0`，仅建议在受控局域网内使用。

## 镜像版本

| 服务 | 镜像 | 实际版本 | 镜像 digest |
| --- | --- | --- | --- |
| PostgreSQL | `postgres:17-alpine` | 17.11 | `sha256:e5d2edd28b1f8d40a0a6c0c9cdc545b19b56a0d5d88f94bc11a97d970d4700d0` |
| Redis | `redis:7.4-alpine` | 7.4.12 | `sha256:7f7d30b7fdd09467bac58198a1f166dd69789d348d4fe859cccb20e0cf287fbe` |
| RabbitMQ | `rabbitmq:4-management-alpine` | 4.4.0 | `sha256:4841f6384ef8313c3594cd3f2e9f40c10805f8a61c45f08a9a5d9ddea8199267` |

## 常用操作

以下命令均在远程目标机 `/home/stariot/yudao-infra` 目录执行。

```bash
# 启动 / 应用配置变更
docker compose up -d

# 查看状态与健康检查
docker compose ps

# 查看日志（指定服务名：postgres / redis / rabbitmq）
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

三个服务均设置 `restart: unless-stopped`，目标机重启后会自动拉起。

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

验证：

```bash
docker exec yudao-infra-postgres psql -U root -d ruoyi-vue-pro -c "\dt"
docker exec yudao-infra-postgres psql -U root -d ruoyi-vue-pro -c "SELECT count(*) FROM system_users;"
docker exec yudao-infra-postgres psql -U root -d ruoyi-vue-pro -c "SELECT 1 FROM dual;"
```

`dual` 表用于 Druid 的 `SELECT 1 FROM DUAL` 探测，必须存在。

## 注意事项与风险

- **Redis 无密码**：这是开发者沿用项目默认的选择。端口对 `0.0.0.0` 开放，局域网内任何主机都可读写 Redis，仅限受控内网使用。
- 默认凭据（PostgreSQL `root/123456`、RabbitMQ `rabbit/rabbit`）是开发测试用途，禁止用于生产环境。
- 目标机 Docker Hub 直连不可达，镜像拉取依赖已配置的华为云镜像加速器；未缓存的镜像（如 MinIO）可能拉取失败。
- 更新镜像前建议先确认加速器可拉取目标 tag，并记录新 digest。

## MinIO（暂未部署）

目标机 Docker Hub 直连被网络阻断，已配置的镜像加速器未缓存 `minio/minio`，多次拉取均回落到 Docker Hub 后超时。因此当前 Compose 文件不包含 MinIO 服务，`9000` / `9001` 端口未开放。

取得开发者确认的可用镜像来源后，可按以下方式补充（镜像需固定 tag 或 digest，避免版本漂移）：

```yaml
  minio:
    image: <经确认的镜像来源>/minio/minio:<固定 tag 或 digest>
    container_name: yudao-infra-minio
    restart: unless-stopped
    environment:
      TZ: Asia/Shanghai
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    command: ["server", "/data", "--console-address", ":9001"]
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - miniodata:/data
```

并在文件末尾的 `volumes` 中增加 `miniodata:`。控制台地址为 `http://192.168.0.23:9001`，健康检查可访问 `http://192.168.0.23:9000/minio/health/live`。
