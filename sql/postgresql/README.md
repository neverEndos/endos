# PostgreSQL 脚本与 dbctl 使用说明

本目录是 yudao-mini（含 collector 采集器）PostgreSQL 17 脚本的唯一来源，按
「平台基线 / 采集器结构 / 系统种子 / 增量迁移 / 校验演示数据」分层。数据库的创建、
初始化、升级与销毁统一由 `dbctl.sh` 控制，避免手工执行破坏性脚本。

## 1. 目录结构

```
sql/postgresql/
├── dbctl.sh                         # 初始化/迁移/种子/删库控制脚本（默认 dry-run）
├── platform/                        # 平台全量重建脚本（含 DROP，仅限空库）
│   ├── ruoyi-vue-pro.sql            # system/infra 基线（pg_dump 风格，不拆分）
│   └── quartz.sql                   # Quartz 11 张调度表
├── collector/
│   ├── 01_schema.sql                # 采集器纯 DDL：22 业务表 + 13 序列
│   │                                #   + schema_migration/db_init_marker 版本表
│   ├── 02_system_seed.sql           # 系统种子（幂等、单事务）：3 协议、8 保留策略、
│   │                                #   菜单 780~805 与角色授权、5 个定时任务记录
│   └── test/
│       └── 01_demo_seed.sql         # 校验库演示数据（1 设备/2 类型/2 传感器/16 模板/16 通道）
├── migration/                       # 已有库增量迁移（幂等、单事务、带版本头与自登记）
│   ├── collector_threshold_migration.sql   # 20261116.1 ddl
│   ├── collector_range_fix.sql             # 20261116.2 data_repair（纯 DML）
│   ├── collector_alarm_migration.sql       # 20261117.1 ddl
│   ├── collector_latest_migration.sql      # 20261117.2 ddl
│   └── collector_aggregate_migration.sql   # 20261117.3 ddl
├── tests/
│   ├── test_dbctl.sh                # dbctl 离线护栏测试（无需数据库）
│   └── check_demo_seed_keys.py      # 演示种子与 collector/seed.py 业务键交叉核对
└── archive/                         # 预留：废弃脚本归档位（当前为空）
```

## 2. 三类脚本的边界

- **全量重建（platform/、collector/01_schema.sql）**：`platform/` 两个脚本含
  `DROP TABLE ...`，只能在空库或 docker 空数据卷首次初始化路径执行，**严禁对已有库重跑**。
  `01_schema.sql` 是纯 DDL（无 DROP、无 INSERT），重复执行会因对象已存在而报错，属预期。
- **增量迁移（migration/）**：幂等、单事务、`ON_ERROR_STOP` 失败即停，面向已有数据的库；
  每个文件带版本头，并在事务内自登记到 `schema_migration`（表不存在时跳过登记）。
- **种子（02_system_seed.sql、test/01_demo_seed.sql）**：均幂等、单事务。系统种子只放
  平台级配置（协议/保留策略/菜单/定时任务）；演示数据是业务数据，仅限校验库。
  现场库的演示设备/传感器/通道由 `collector/seed.py` 幂等写入，本目录 SQL 不替代它。

## 3. dbctl.sh 命令

写操作默认 dry-run，必须显式 `--run` 才执行；`status` 只读。密码只从 `PGPASSWORD`
环境变量读取。连接参数用 `PGHOST/PGPORT/PGUSER/PGMAINTDB`（默认 127.0.0.1:5432/postgres）。

| 命令 | 作用 |
| --- | --- |
| `init <库> --new --run` | 全新应用库：建库 → platform → quartz → 01_schema → 02_system_seed → 标记 `purpose=app` → 登记全部迁移版本；失败保留现场 |
| `init <库> --validation --run` | 全新校验库：同上但标记 `purpose=validation`；任一步失败自动删库 |
| `migrate <库> --run` | 跳过已登记版本，只执行缺失迁移，末尾刷新系统种子 |
| `mark-baseline <库> --run` | 旧库一次性基线：逐版本结构探测，全部可证明才登记，拒绝盲标 |
| `seed-system <库> --run` | 仅刷新系统种子（幂等） |
| `seed-test <库> --run` | 仅校验库（`purpose=validation`）写入演示数据 |
| `drop-validation <库> --run` | 仅校验库可删；校验库标记是删库唯一凭证 |
| `status <库>` | 只读查看标记、已登记/待执行版本、collector 结构概况 |

### 安全护栏

库名按子命令破坏性分级（psql 无法参数化标识符，所有标识符先校验、插值时双引号包裹）：

1. **最严档（`init` / `seed-test` / `drop-validation`）**：库名必须匹配
   `^[a-z][a-z0-9_]{2,30}$`，校验库还须匹配 `^yudao_validate(_[a-z0-9]+)?$`；正式库
   `ruoyi-vue-pro`、`yudao_board` 与系统库 `postgres`/`template0`/`template1` 一律拒绝；
2. **升级/只读档（`migrate` / `mark-baseline` / `seed-system` / `status`）**：允许两个
   显式登记的正式库名 `ruoyi-vue-pro`、`yudao_board`（精确匹配，连字符不向其他库名泛化，
   如 `my-db`、`ruoyi-vue-prox` 仍拒绝），或符合安全标识规则的应用库；系统库任何子命令都拒绝；
3. `seed-test`/`drop-validation` 还强制校验 `db_init_marker.purpose=validation`，
   marker 是删库唯一凭证；
4. `init` 拒绝在已存在的库上执行；校验库初始化任一步失败（含 marker 写入与版本登记）由
   ERR trap 自动删除本次创建的库，应用库失败保留并报告最后失败命令；
5. `mark-baseline` 对每个版本做结构探测，不能证明已应用则不登记；其中纯 DML 的
   `20261116.2`（range_fix）无法证明时，只能先执行该修正脚本，或在确认不适用时显式加
   `--ack-data-repair`，不会被静默跳过。

## 4. 典型流程

```bash
export PGPASSWORD='***'
# 全新校验库：初始化 → 演示数据 → 核对 → 销毁
./dbctl.sh init yudao_validate_t1 --validation --run
./dbctl.sh seed-test yudao_validate_t1 --run
./dbctl.sh status yudao_validate_t1
./dbctl.sh drop-validation yudao_validate_t1 --run

# 已有旧库：先基线标记（一次性），之后按版本增量迁移
./dbctl.sh mark-baseline ruoyi-vue-pro --run
./dbctl.sh migrate ruoyi-vue-pro --run
```

定时任务种子只写 `infra_job`，执行后仍需调用一次
`POST /admin-api/infra/job/sync`（或后台“定时任务 → 同步”）才会注册到 Quartz。

## 5. 与 docker 空数据卷初始化的关系

`deploy/compose-single/docker-compose.yml` 把脚本按 `01..05` 顺序挂到
`docker-entrypoint-initdb.d`，仅在 PG 数据卷为空时由官方镜像执行一次：
`01 platform/ruoyi-vue-pro → 02 platform/quartz → 03 collector/01_schema →
04 collector/02_system_seed → 04a 基线版本登记 → 05 MinIO 文件配置`。
该路径与 `dbctl.sh init` 等价（同样登记 5 个迁移版本），但只适用于空卷；已有数据卷
不会重新执行，升级必须走 `dbctl.sh migrate`。
