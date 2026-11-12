# 环境变量文件规范（`.env.<profile>`）

本文档约定 yudao-boot-mini 的环境变量文件命名、变量命名、与 Spring profile 的对应关系、加载方式与优先级，以及 MinIO 文件存储的数据库配置注意事项。

## 1. 文件命名与 profile 对应关系

环境变量文件与 Spring profile 一一对应：

| Spring profile | 环境变量文件 | Spring 配置文件 | 用途 |
| --- | --- | --- | --- |
| `local` | `.env.local` | `yudao-server/src/main/resources/application-local.yaml` | 本机开发 |
| `dev` | `.env.dev` | `yudao-server/src/main/resources/application-dev.yaml` | 开发 / 测试环境 |
| `remote` | `.env.remote` | `yudao-server/src/main/resources/application-remote.yaml` | 局域网远程中间件联调 |

规则：

- 仓库中只提交模板 `.env.<profile>.example`。
- 实际使用的 `.env.<profile>` 已加入 `.gitignore`（`.env`、`.env.*`，但 `!.env.*.example`），**禁止提交**。
- 不修改、不提交真实生产密钥；模板只保留开发测试环境的默认值。
- `.env.<profile>` 放在仓库根目录（`yudao-boot-mini/`）。配置文件同时尝试从当前目录和上一级目录加载，因此从 `yudao-server/` 目录启动同样可用。

## 2. 变量命名

统一使用 `YUDAO_` 前缀，并按子系统分组：

```text
YUDAO_DB_HOST
YUDAO_DB_PORT
YUDAO_DB_NAME
YUDAO_DB_USERNAME
YUDAO_DB_PASSWORD

YUDAO_REDIS_HOST
YUDAO_REDIS_PORT
YUDAO_REDIS_PASSWORD

YUDAO_RABBITMQ_HOST
YUDAO_RABBITMQ_PORT
YUDAO_RABBITMQ_USERNAME
YUDAO_RABBITMQ_PASSWORD

YUDAO_MINIO_ENDPOINT
YUDAO_MINIO_BUCKET
YUDAO_MINIO_ACCESS_KEY
YUDAO_MINIO_SECRET_KEY
YUDAO_MINIO_DOMAIN
YUDAO_MINIO_ENABLE_PATH_STYLE
YUDAO_MINIO_ENABLE_PUBLIC_ACCESS

YUDAO_SECURITY_MOCK_ENABLE
YUDAO_CAPTCHA_ENABLE
YUDAO_ACCESS_LOG_ENABLE
```

命名约定：

- 全部大写，单词之间用下划线分隔。
- 新增变量沿用 `YUDAO_<子系统>_<含义>` 结构，不要在 YAML 中硬编码地址、账号或密码。
- `.env.<profile>` 使用 `KEY=VALUE` 格式，一行一个；空值写作 `KEY=`。
- 值中包含 `:`、`/`、`?` 等字符无需转义（按 `.properties` 解析）。

## 3. 加载方式

`application-<profile>.yaml` 通过 Spring Boot 的 `spring.config.import` 加载同名环境变量文件，例如 `application-remote.yaml`：

```yaml
spring:
  config:
    import:
      - optional:file:./.env.remote[.properties]
      - optional:file:../.env.remote[.properties]
```

说明：

- `optional:` 表示文件不存在时不报错，便于新环境直接使用 YAML 中的默认值启动。
- `[.properties]` 是 Spring Boot 的扩展名提示：`.env` 文件按 Java Properties 格式解析，`KEY=VALUE` 即可。
- 同时声明 `./` 与 `../`，兼容从仓库根目录或 `yudao-server/` 目录启动。
- YAML 中的引用形式为 `${YUDAO_DB_HOST:127.0.0.1}`，冒号后为默认值。
- 只有数据库、Redis、RabbitMQ、MinIO 及少量开关迁移到环境变量；其余配置仍保留在 YAML 中。

Redis 密码需要特别说明：无密码环境下，`application-<profile>.yaml` 中的 `password` 行**保持注释**。

原因：`${YUDAO_REDIS_PASSWORD:}` 在变量为空时会解析为空字符串，而 Redisson 会把空字符串当作密码发送 `AUTH`，对未配置密码的 Redis 会报错 `ERR AUTH called without any password configured for the default user`。

- 无密码：模板中的 `YUDAO_REDIS_PASSWORD=` 保持为空，YAML 不启用 `password`。
- 有密码：取消 YAML 中 `password` 行的注释并填入实际值，或直接设置标准环境变量 `SPRING_DATA_REDIS_PASSWORD`（优先级更高，且不需要改动 YAML）。

## 4. 优先级

从高到低：

1. 命令行参数 / JVM 系统属性
2. 操作系统环境变量
3. `.env.<profile>`（由 `spring.config.import` 导入）
4. `application-<profile>.yaml` 中的 `${...:默认值}` 默认值

因此临时覆盖某个值无需改动文件，例如：

```bash
YUDAO_DB_HOST=127.0.0.1 SPRING_PROFILES_ACTIVE=remote java -jar yudao-server/target/yudao-server.jar
```

## 5. 启动方式

前置条件：JDK 25、Maven 3.9+。以下命令均在仓库根目录（`yudao-boot-mini/`）执行。

```bash
# 构建可运行包
mvn -B package -pl yudao-server -am -Dmaven.test.skip=true

# 以 remote profile 启动（加载 .env.remote）
SPRING_PROFILES_ACTIVE=remote java -jar yudao-server/target/yudao-server.jar
```

PowerShell：

```powershell
$env:SPRING_PROFILES_ACTIVE = "remote"
java -jar yudao-server/target/yudao-server.jar
```

默认端口为 `48080`。启动成功的标志是日志中出现 `Started YudaoServerApplication`。

注意：当前精简版未引入 `spring-boot-starter-actuator`，因此 `/actuator/health` 不可用；健康状态请以启动日志和实际业务接口（如登录、文件上传）为准。

首次使用某个 profile 时：

```bash
cp .env.remote.example .env.remote
# 按实际环境修改 .env.remote；该文件不会被提交
```

## 6. MinIO 文件存储的数据库配置注意事项

文件存储的主配置**不在 YAML 中**，而是保存在 `infra_file_config` 表：

- `storage = 20` 表示 S3 协议（MinIO / 阿里云 / 腾讯云等）。
- `master = true` 的行决定应用实际使用的文件存储器；同一时刻只应有一行 master。
- `config` 为 JSON，必须保留 `@class` 字段，值为 `cn.iocoder.yudao.module.infra.framework.file.core.client.s3.S3FileClientConfig`。
- MinIO 推荐 `enablePathStyleAccess = true`；如需返回可直接访问的 URL，需将 bucket 设为匿名可下载，并设置 `enablePublicAccess = true`。

`.env.remote` 中的 `YUDAO_MINIO_*` 变量与应用启动解耦，仅作为维护 `infra_file_config` 时的取值参考。修改数据库中的主配置后：

1. 重启应用，或等待配置缓存（约 10 秒）刷新；
2. 通过 `POST /admin-api/infra/file/upload` 上传文件验证；
3. 确认返回 URL 可访问、对象存储中存在对象、`infra_file` 表有记录。

远程 MinIO bucket 的匿名下载策略示例（在 MinIO 容器内执行）：

```bash
mc alias set local http://127.0.0.1:9000 minioadmin minioadmin
mc mb --ignore-existing local/yudao
mc anonymous set download local/yudao
```

修改 `infra_file_config` 前请先备份该表：

```bash
docker exec yudao-infra-postgres pg_dump -U root -d ruoyi-vue-pro -t infra_file_config > infra_file_config_backup.sql
```

## 7. 禁止事项

- 禁止提交 `.env`、`.env.<profile>` 或任何真实生产密钥。
- 禁止在 YAML、脚本、文档中硬编码本机盘符或绝对路径。
- 禁止把与 profile 无关的敏感配置（第三方支付/公众号密钥等）搬入 `.env`；当前只迁移数据库、Redis、RabbitMQ、MinIO 与少量开关。
