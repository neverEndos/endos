# yudao-boot-mini

<p align="center">
 <img src="https://img.shields.io/badge/JDK-25-blue.svg" alt="JDK 25">
 <img src="https://img.shields.io/badge/Spring%20Boot-4.1.2-blue.svg" alt="Spring Boot 4.1.2">
 <img src="https://img.shields.io/badge/License-MIT-green.svg" alt="MIT License">
</p>

## 项目简介

`yudao-boot-mini` 是芋道源码 / ruoyi-vue-pro 的**精简版后端**，在完整版基础上只保留两个业务模块：

- `yudao-module-system`：用户、部门、角色权限、菜单、字典、OAuth2、短信 / 邮件 / 站内信、租户等。
- `yudao-module-infra`：代码生成、文件、定时任务、API 日志、配置、数据库 / Redis 监控、WebSocket 等。

本仓库当前技术基线为 **JDK 25 + Spring Boot 4.1.2**，采用 Maven 多模块构建。工作流、支付、商城、CRM、ERP、AI、IoT、IM 等模块均未启用：它们只以注释形式保留在 `pom.xml` 中，不参与编译。本文档只描述本仓库实际具备的能力。

上游项目：

- 完整版：[ruoyi-vue-pro](https://gitee.com/zhijiantianya/ruoyi-vue-pro)
- 精简版：[yudao-boot-mini](https://gitee.com/yudaocode/yudao-boot-mini)

## 技术栈与版本

版本以 [`pom.xml`](pom.xml)、[`yudao-dependencies/pom.xml`](yudao-dependencies/pom.xml) 中的实际配置为准。

| 组件 | 版本 / 说明 |
| --- | --- |
| Java | 25（`java.version`） |
| Spring Boot | 4.1.2（`spring.boot.version`） |
| 项目版本 | `2026.10-jdk25-SNAPSHOT`（`${revision}`） |
| MyBatis | 3.5.19 |
| MyBatis-Plus | 3.5.17（`mybatis-plus-spring-boot4-starter`） |
| MyBatis-Plus-Join | 1.5.9 |
| Druid | 1.2.28 |
| Dynamic Datasource | 4.6.0 |
| Redis 客户端 | Redisson 4.8.0 |
| 接口文档 | springdoc 3.1.1 + Knife4j 4.5.0 |
| Bean 转换 | MapStruct 1.7.0.Beta2 |
| 代码简写 | Lombok 1.18.48 |
| 数据库 | MySQL 8.x（初始化脚本由 MySQL 8.2 导出，Docker Compose 使用 `mysql:8.4`） |
| 缓存 | Redis 7.x（Docker Compose 使用 `redis:7.4-alpine`） |
| 构建工具 | Maven 3.x（仓库未提供 Maven Wrapper） |

## 模块结构

```text
yudao-boot-mini
├── yudao-dependencies       # 仅 BOM：统一管理依赖版本
├── yudao-framework          # 技术组件（starter），不依赖业务模块
│   ├── yudao-common
│   └── yudao-spring-boot-starter-*
├── yudao-module-infra       # 基础设施模块
├── yudao-module-system      # 系统模块
├── yudao-server             # 启动容器，聚合业务模块
├── yudao-ui/                # 前端仓库占位说明，不含可构建源码
├── script/                  # 部署脚本（Docker Compose 等）
├── sql/                     # 各数据库初始化脚本与转换工具
└── pom.xml
```

依赖关系：

```text
yudao-server
├── yudao-module-system
│   └── yudao-module-infra
└── yudao-module-infra
```

- `yudao-server` 是刻意保持轻量的容器，只扫描 `${yudao.info.base-package}.server` 和 `${yudao.info.base-package}.module`，自身没有业务代码。
- `yudao-module-system` → `yudao-module-infra` 是唯一的模块间依赖。
- `yudao-framework` 下的 starter 不依赖任何业务模块。需要跨模块调用时，API 接口定义在 [`yudao-common` 的 `biz/` 目录](yudao-framework/yudao-common/src/main/java/cn/iocoder/yudao/framework/common/biz/)中，由业务模块实现。
- 当前引入的 framework starter：`web`、`security`、`mybatis`、`redis`、`mq`、`job`、`monitor`、`protection`、`websocket`、`excel`、`biz-tenant`、`biz-data-permission`、`biz-ip`。
- 根 [`pom.xml`](pom.xml) 和 [`yudao-server/pom.xml`](yudao-server/pom.xml) 中，member、bpm、report、mp、pay、mall、crm、erp、iot、mes、wms、hrm、fms、pms、oa、im、ai 等模块均被注释。新增模块时，需要在这两处同时取消注释。

## 快速开始

### 1. 环境要求

- JDK 25
- Maven 3.x
- MySQL 8.x
- Redis 7.x

后端启动依赖 MySQL 与 Redis；不需要构建前端即可运行后端。

### 2. 初始化数据库

默认数据库名为 `ruoyi-vue-pro`。初始化脚本本身不创建数据库，需要先手动创建，再按顺序导入：

```bash
# 创建数据库
mysql -uroot -p -e "CREATE DATABASE \`ruoyi-vue-pro\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 先导入业务表，再导入 Quartz 表
mysql -uroot -p ruoyi-vue-pro < sql/mysql/ruoyi-vue-pro.sql
mysql -uroot -p ruoyi-vue-pro < sql/mysql/quartz.sql
```

- 业务及基础设施表：[`sql/mysql/ruoyi-vue-pro.sql`](sql/mysql/ruoyi-vue-pro.sql)
- 定时任务表：[`sql/mysql/quartz.sql`](sql/mysql/quartz.sql)

`sql/` 下还提供 dm、highgo、kingbase、oceanbase、opengauss、oracle、postgresql、sqlserver 等数据库目录；`sql/db2` 目前只有说明文档，尚未适配。测试数据库快速启动与 MySQL 转换工具见 [`sql/tools/README.md`](sql/tools/README.md)。

### 3. 关键配置

[`application.yaml`](yudao-server/src/main/resources/application.yaml) 是共享配置，默认激活 `local` profile：

```yaml
spring:
  profiles:
    active: local
```

[`application-local.yaml`](yudao-server/src/main/resources/application-local.yaml) 是本地开发默认配置：

| 配置项 | 默认值 |
| --- | --- |
| 服务端口 | `48080`（`server.port`） |
| MySQL | `jdbc:mysql://127.0.0.1:3306/ruoyi-vue-pro`，`root` / `123456` |
| Redis | `127.0.0.1:6379`，database `0`，未配置密码 |
| 定时任务 | 排除 `QuartzAutoConfiguration` |
| 安全 mock | `yudao.security.mock-enable: true` |
| 图片验证码 | `yudao.captcha.enable: false` |
| 访问日志 | `yudao.access-log.enable: false` |

[`application-dev.yaml`](yudao-server/src/main/resources/application-dev.yaml) 供 Docker Compose 等环境使用，端口同为 `48080`；其中的 MySQL / Redis 地址会被 Compose 的环境变量覆盖。

### 4. 构建与启动

以下命令在仓库根目录执行：

```bash
# 完整多模块构建（CI 使用的命令，跳过测试）
mvn -B package -Dmaven.test.skip=true

# 构建可运行的 server 包（连同上游模块）
mvn clean package -pl yudao-server -am -Dmaven.test.skip=true

# 启动
java -jar yudao-server/target/yudao-server.jar
```

- 打包产物：`yudao-server/target/yudao-server.jar`。
- 启动类：[`YudaoServerApplication`](yudao-server/src/main/java/cn/iocoder/yudao/server/YudaoServerApplication.java)，包名 `cn.iocoder.yudao.server`。
- 也可以在 IDE 中直接运行 `cn.iocoder.yudao.server.YudaoServerApplication`。
- 只编译某个模块可用：`mvn -pl yudao-module-system -am compile -Dmaven.test.skip=true`。

当前分支没有单元测试套件。`yudao-server/src/test` 下的 `ProjectReactor.java` 是项目改名工具，不是测试；CI 同样使用 `-Dmaven.test.skip=true`。CI 配置见 [`.github/workflows/maven.yml`](.github/workflows/maven.yml)。

## Docker Compose 体验环境

[`script/docker-compose`](script/docker-compose/README.md) 提供 MySQL、Redis、后端与 Nginx 前端的单机体验编排，仅用于入门和测试，不是生产高可用方案。详细步骤与注意事项以该目录的 [README](script/docker-compose/README.md) 为准，主要流程如下：

```bash
# 1. 在仓库根目录先打包后端
mvn clean package -pl yudao-server -am -Dmaven.test.skip=true

# 2. 准备前端产物
#    将前端打包产物（包含 index.html）复制到 script/docker-compose/web/dist/

# 3. 配置环境变量并启动
cd script/docker-compose
cp .env.example .env    # 至少修改 MySQL、Redis 的示例密码
docker compose up -d --build
```

- 默认访问地址：`http://localhost:8080`（由 `WEB_PORT` 控制）。
- 首次启动时，MySQL 按顺序导入 [`sql/mysql/ruoyi-vue-pro.sql`](sql/mysql/ruoyi-vue-pro.sql) 和 [`sql/mysql/quartz.sql`](sql/mysql/quartz.sql)；之后修改 SQL 文件不会作用于已有数据卷。
- MySQL、Redis、后端默认不映射到宿主机端口，避免与本机的 3306、6379、48080 冲突。
- 后端镜像由 [`yudao-server/Dockerfile`](yudao-server/Dockerfile) 构建。
- **不要执行 `docker compose down -v`**，它会删除 MySQL、Redis 数据卷并清空数据库。停止环境使用 `docker compose down`。
- 前端是独立仓库，仓库内不构建前端源码；`web/dist` 需自行放入打包产物。

## 开发约定

### 代码分层

每个业务模块的包结构一致（`cn.iocoder.yudao.module.<module>`）：

```text
api/          # 跨模块接口 + Impl + DTO（ApiImpl 标注 @Service）
controller/
  admin/      # 管理后台接口，含 vo/ 请求/响应对象
  app/        # 用户端接口
convert/      # MapStruct 转换器
dal/
  dataobject/ # *DO 实体
  mysql/      # *Mapper，继承 BaseMapperX
enums/        # 模块枚举、ErrorCodeConstants
framework/    # 模块级 Spring 配置 / 客户端
job/          # Quartz 定时任务
mq/           # 消息生产者 / 消费者
service/      # *Service 接口 + *ServiceImpl
util/
```

调用链固定为 Controller → Service → Mapper，Controller 不直接访问 Mapper。请求 / 响应对象使用 `*ReqVO` / `*RespVO`，持久化对象使用 `*DO`。

### 接口与响应

- Controller 使用普通 `@RestController` 和 `@RequestMapping("/<module>/<domain>")`，例如 `/system/user`、`/infra/codegen`。
- 框架根据包路径自动添加 URL 前缀（见 [`WebProperties`](yudao-framework/yudao-spring-boot-starter-web/src/main/java/cn/iocoder/yudao/framework/web/config/WebProperties.java)）：
  - `**.controller.admin.**` → `/admin-api`
  - `**.controller.app.**` → `/app-api`
- 最终地址形如 `/admin-api/system/user/create`。
- 所有响应包装为 [`CommonResult<T>`](yudao-framework/yudao-common/src/main/java/cn/iocoder/yudao/framework/common/pojo/CommonResult.java)，分页入参使用 [`PageParam`](yudao-framework/yudao-common/src/main/java/cn/iocoder/yudao/framework/common/pojo/PageParam.java)，分页出参使用 [`PageResult<T>`](yudao-framework/yudao-common/src/main/java/cn/iocoder/yudao/framework/common/pojo/PageResult.java)。
- 错误通过 `ServiceException` + `ErrorCode` 抛出，错误码常量写入各模块的 `enums/ErrorCodeConstants`（[system](yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/enums/ErrorCodeConstants.java)、[infra](yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/enums/ErrorCodeConstants.java)）。错误码号段定义在 [`ServiceErrorCodeRange`](yudao-framework/yudao-common/src/main/java/cn/iocoder/yudao/framework/common/exception/enums/ServiceErrorCodeRange.java)。
- 权限使用 `@PreAuthorize("@ss.hasPermission('system:user:create')")`，权限字符串格式为 `<module>:<domain>:<action>`。

### 持久层

- DO 继承 [`BaseDO`](yudao-framework/yudao-spring-boot-starter-mybatis/src/main/java/cn/iocoder/yudao/framework/mybatis/core/dataobject/BaseDO.java)，租户表继承 [`TenantBaseDO`](yudao-framework/yudao-spring-boot-starter-biz-tenant/src/main/java/cn/iocoder/yudao/framework/tenant/core/db/TenantBaseDO.java)。
- `BaseDO` 提供 `creator`、`createTime`、`updater`、`updateTime`、`deleted`（逻辑删除）。
- Mapper 继承 [`BaseMapperX<T>`](yudao-framework/yudao-spring-boot-starter-mybatis/src/main/java/cn/iocoder/yudao/framework/mybatis/core/mapper/BaseMapperX.java)，查询条件使用 [`LambdaQueryWrapperX`](yudao-framework/yudao-spring-boot-starter-mybatis/src/main/java/cn/iocoder/yudao/framework/mybatis/core/query/LambdaQueryWrapperX.java) 的空值安全方法。
- 表名按模块加前缀（`system_*`、`infra_*`）；默认主键由数据库生成（`mybatis-plus.global-config.db-config.id-type: NONE`）。
- DO ↔ VO / DTO 转换由 `convert/` 下的 MapStruct 接口完成。

### 多租户与数据权限

- 多租户由 `yudao-spring-boot-starter-biz-tenant` 提供，通过 `yudao.tenant.enable` 开关；需要跳过时使用 [`@TenantIgnore`](yudao-framework/yudao-spring-boot-starter-biz-tenant/src/main/java/cn/iocoder/yudao/framework/tenant/core/aop/TenantIgnore.java)。
- 数据权限由 `yudao-spring-boot-starter-biz-data-permission` 提供，通过 AOP + SQL 改写实现；内部任务或测试需要跳过时使用 [`DataPermissionUtils.executeIgnore(...)`](yudao-framework/yudao-spring-boot-starter-biz-data-permission/src/main/java/cn/iocoder/yudao/framework/datapermission/core/util/DataPermissionUtils.java)。

### 自动配置

每个 starter 的自动配置类必须登记在 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中，否则不会生效。可参考 web starter 的 [`.imports` 文件](yudao-framework/yudao-spring-boot-starter-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports)。

### 编码风格

代码注释、Javadoc、日志、异常信息和提交信息使用中文；新增错误码时保持模块号段与命名一致。

## 代码生成器

代码生成器位于 `yudao-module-infra`，配置前缀为 `yudao.codegen`，对应 [`CodegenProperties`](yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/framework/codegen/config/CodegenProperties.java)。`application.yaml` 中的默认配置包括：

- `base-package: ${yudao.info.base-package}`
- `db-schemas: ${spring.datasource.dynamic.datasource.master.name}`
- `front-type: 20`、`vo-type: 10`
- `delete-batch-enable: true`
- `unit-test-enable: false`（默认不生成单元测试）
- `import-enable: false`（默认不生成 Excel 导入接口）

## 前端说明

本仓库不包含可构建的前端源码。`yudao-ui/` 下的目录主要是上游前端仓库的 README 占位：

- [`yudao-ui-admin-vue3`](yudao-ui/yudao-ui-admin-vue3/README.md)
- [`yudao-ui-admin-vben`](yudao-ui/yudao-ui-admin-vben/README.md)
- [`yudao-ui-admin-vue2`](yudao-ui/yudao-ui-admin-vue2/README.md)
- [`yudao-ui-admin-uniapp`](yudao-ui/yudao-ui-admin-uniapp/README.md)
- [`yudao-ui-mall-uniapp`](yudao-ui/yudao-ui-mall-uniapp/README.md)

真实前端源码请使用各自 README 中指向的外部仓库。Docker Compose 环境同样需要先在前端仓库完成打包，再把产物复制到 `script/docker-compose/web/dist`。

## 许可证与致谢

本仓库采用 [MIT License](LICENSE)，版权与许可声明归上游 ruoyi-vue-pro / 芋道源码项目所有。感谢上游项目及所有贡献者。

## 常见问题

**为什么仓库里没有 `master-jdk25` 分支？**

本仓库当前以 `main` 为基线分支，本地也存在 `master` 分支。`master-jdk25` 是上游 yudao-boot-mini 中对应 JDK 25 + Spring Boot 4.x 的分支名；不同镜像仓库的分支命名可能不同，请以当前仓库的实际分支为准。

**为什么没有单元测试？**

当前分支不包含测试套件，CI 构建使用 `-Dmaven.test.skip=true`。`yudao-server/src/test` 下的 `ProjectReactor.java` 是项目改名工具，不参与构建。

**为什么很多模块在 `pom.xml` 里被注释了？**

未启用的业务模块保持注释是刻意为之，用于缩短编译时间。需要某个模块时，在根 `pom.xml` 的 `<modules>` 与 `yudao-server/pom.xml` 的 `<dependencies>` 两处同时取消注释。

**`application.yaml` 里的 AI、支付、公众号配置为什么存在？**

配置文件沿用上游完整版，包含部分未启用模块的配置片段。它们不代表当前构建启用了对应功能；当前实际编译和运行的业务模块只有 `yudao-module-system` 与 `yudao-module-infra`。

**启动失败怎么办？**

`local` profile 默认要求本机可访问 MySQL（`127.0.0.1:3306`）和 Redis（`127.0.0.1:6379`），并已导入初始化 SQL。请先确认这两个服务可用，再检查 `application-local.yaml` 中的连接配置。
