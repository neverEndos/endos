# Docker Compose 部署（单体版）

目标：用 Docker Compose V2 快速搭建一套可登录的体验环境，了解各组件之间的依赖关系。

包含：`yudao-server` 后端、前端 Nginx、PostgreSQL、Redis。仅用于入门体验与测试，不是生产高可用方案。

## 文件列表

```text
script/docker-compose
├── README.md
├── docker-compose.yml       <-- 编排文件，相对路径均相对于本目录
├── .env.example             <-- 环境变量示例，复制为 .env 后修改
├── .gitignore               <-- 忽略 .env、web/dist/
├── nginx
│   └── default.conf         <-- 静态资源、history 回退、/admin-api、/app-api、/infra/ws 反向代理
└── web
    └── dist                 <-- 需自行放入前端打包产物（不随仓库提供）
```

| 服务 | 镜像 | 说明 |
| --- | --- | --- |
| `postgres` | `postgres:17-alpine` | 首次启动导入 `sql/postgresql/ruoyi-vue-pro.sql`、`sql/postgresql/quartz.sql`，数据卷 `yudao-boot_postgres-data` |
| `redis` | `redis:7.4-alpine` | 开启密码与 AOF，数据卷 `yudao-boot_redis-data` |
| `yudao-server` | `yudao-server:${IMAGE_TAG}` | 由 `yudao-server/Dockerfile` 构建，端口 48080，只在 Compose 网络内访问 |
| `web` | `nginx:1.28-alpine` | 对宿主机开放 `${WEB_PORT}`（默认 8080） |

容器之间通过服务名（`postgres`、`redis`、`yudao-server`）访问。PostgreSQL、Redis、后端默认不映射到宿主机，不会与本机已有的 5432、6379、48080 冲突；需要时取消 `docker-compose.yml` 中对应 `ports` 的注释。

## 前置条件

- Docker Engine + Docker Compose V2.20.2 及以上（`docker compose version` 可查看）
- JDK 25、Maven（与源码根 `pom.xml` 的 `java.version` 一致）
- 已按前端项目的部署说明完成打包

## 1. 打包后端

在**源码根目录**执行：

```shell
mvn clean package -pl yudao-server -am -Dmaven.test.skip=true
```

产物为 `yudao-server/target/yudao-server.jar`，`docker compose build` 时由 `yudao-server/Dockerfile` 复制进镜像。

## 2. 准备前端产物

vben 管理后台源码位于 `yudao-ui/yudao-ui-admin-vben`，默认应用为 `apps/web-antdv-next`。打包前确认前端生产环境配置：

- 浏览器访问地址使用 Nginx 的同源地址，例如 `http://localhost:8080`，不要写 `yudao-server`、`127.0.0.1:48080` 等容器名或后端端口
- API 路径为 `/admin-api`：`apps/web-antdv-next/.env.production` 中的 `VITE_BASE_URL`、`VITE_GLOB_API_URL`
- 前端 `VITE_APP_CAPTCHA_ENABLE` 与 `.env` 中的 `YUDAO_CAPTCHA_ENABLE` 一致

在 `yudao-ui/yudao-ui-admin-vben` 目录执行：

```shell
pnpm install --frozen-lockfile
pnpm build:antdv-next
```

构建产物为 `apps/web-antdv-next/dist`。将产物目录中的内容（包含 `index.html`）复制到 `script/docker-compose/web/dist/`，在**源码根目录**执行：

```shell
mkdir -p script/docker-compose/web/dist
cp -R yudao-ui/yudao-ui-admin-vben/apps/web-antdv-next/dist/. script/docker-compose/web/dist/
```

## 3. 配置环境变量

```shell
cd script/docker-compose
cp .env.example .env
```

按需修改 `.env`，至少替换 `POSTGRES_PASSWORD`、`REDIS_PASSWORD` 两个示例密码。

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `WEB_PORT` | `8080` | 前端访问端口 |
| `IMAGE_TAG` | `local` | 后端镜像 tag |
| `POSTGRES_DB` | `ruoyi-vue-pro` | 数据库名 |
| `POSTGRES_USER` | `root` | 数据库账号 |
| `POSTGRES_PASSWORD` | 示例值 | 数据库密码，仅供初始化与运维使用 |
| `REDIS_PASSWORD` | 示例值 | Redis 密码 |
| `SPRING_PROFILES_ACTIVE` | `dev` | 后端 profile |
| `JAVA_OPTS` | `-Xms512m -Xmx512m ...` | JVM 参数 |
| `YUDAO_CAPTCHA_ENABLE` | `true` | 是否开启登录验证码 |

后端的数据库（主库 `master`、从库 `slave`）、Redis 连接，由 `docker-compose.yml` 通过环境变量覆盖 `application-dev.yaml` 中的 `127.0.0.1` 等地址；其余配置仍使用 `application.yaml`、`application-dev.yaml`。

## 4. 构建并启动

以下命令均在 `script/docker-compose` 目录执行：

```shell
# 检查配置（变量缺失会直接报错）
docker compose config

# 构建后端镜像并启动
docker compose up -d --build

# 查看状态与日志
docker compose ps
docker compose logs -f yudao-server
```

首次启动时 PostgreSQL 需要导入 SQL，`yudao-server` 会等到 PostgreSQL、Redis 健康检查通过后再启动。日志出现 `Started YudaoServerApplication` 后访问：

- 管理后台：http://localhost:8080 （`WEB_PORT` 修改后对应调整）

## 5. 更新

```shell
# 后端：回到源码根目录重新打包，再回到本目录重建、重启
cd ../..
mvn clean package -pl yudao-server -am -Dmaven.test.skip=true
cd script/docker-compose
docker compose up -d --build yudao-server

# 前端：清空 web/dist/ 内的文件（保留 dist 目录本身）后复制新产物，再重启 web；修改 nginx/default.conf 后同样执行
rm -rf web/dist/*
cp -R /path/to/前端打包产物目录/. web/dist/
docker compose restart web
```

如需回滚，可在打包前修改 `.env` 的 `IMAGE_TAG`，保留旧 tag 的镜像。

## 6. 停止与数据

```shell
# 停止并删除容器，保留数据卷
docker compose down
```

**不要执行 `docker compose down -v`**，它会删除 `yudao-boot_postgres-data`、`yudao-boot_redis-data` 数据卷，数据库内容将全部丢失。

## 数据库初始化说明

- SQL 只在 `postgres-data` 数据卷为空（首次启动）时执行一次；之后修改 SQL 文件、`.env` 中的 `POSTGRES_*` 账号密码，都不会作用于已有数据库。
- 首次导入中途失败（例如容器被强制停止），数据卷会处于半初始化状态，需要确认数据可丢弃后执行 `docker compose down`、`docker volume rm yudao-boot_postgres-data` 再重新启动。
- 已有数据卷升级到新版本时，SQL 不会自动执行。请先备份，再手动执行对应的升级 SQL，例如：

```shell
docker compose exec -T postgres sh -c 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < /path/to/upgrade.sql
```

- 使用外部 PostgreSQL 时：自行导入 `sql/postgresql/ruoyi-vue-pro.sql`、`sql/postgresql/quartz.sql`，删除 `docker-compose.yml` 中的 `postgres` 服务及 `yudao-server` 对它的 `depends_on`，并把 `SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_*`、`SLAVE_*` 改为外部地址；容器内的 `127.0.0.1` 指向容器自身，不是宿主机。

## 其他分支同步替换点

本目录按 JDK 25 + Spring Boot 4.x 分支编写（`yudao-server/Dockerfile` 基础镜像 `eclipse-temurin:25-jre`，Redis 使用 `SPRING_DATA_REDIS_*`）。与其他分支同步时，只需替换：

| 分支 | 后端 Dockerfile 基础镜像 | Redis 环境变量前缀 |
| --- | --- | --- |
| JDK 8 + Spring Boot 2.x | `eclipse-temurin:8-jre` | `SPRING_REDIS_*`（`spring.redis.*`） |
| JDK 17 + Spring Boot 3.x | `eclipse-temurin:17-jre` | `SPRING_DATA_REDIS_*`（`spring.data.redis.*`） |
| JDK 25 + Spring Boot 4.x | `eclipse-temurin:25-jre` | `SPRING_DATA_REDIS_*`（`spring.data.redis.*`） |

并核对该分支 `yudao-server/src/main/resources/application-*.yaml` 的数据源、Redis 配置项与 `sql/postgresql` 下的 SQL 文件名。
