# HSI 采集原型（CS1000 + HLB1400）

这是采集器功能的最小实现：在 RK3568 板端通过 `/dev/ttyS4` + RS485 轮询两颗
HYDAC HSI 传感器，验证帧协议与解析正确性，把原始采样、设备状态和心跳写入
PostgreSQL（`yudao_board`），同时保留 JSONL 输出便于对照。启动时自动执行幂等的
HSI 演示配置种子（协议、2 个传感器类型、1 台设备、2 个传感器、16 个参数模板、
16 个通道）。

- 串口与协议全部使用 Python 3.12 标准库（`os` / `termios` / `select` /
  `fcntl` / `struct`），不引入 pyserial 等第三方依赖，板端无需 `pip install`；
  PostgreSQL 落库使用 apt 安装的 `python3-psycopg2`；
- 帧编解码、校验、应答解析、按 Length 分支的解析器全部是纯函数，可脱离串口做
  单元测试；
- 打开串口前保存 termios 设置，关闭时恢复；RS485 模式在进程内保持。

## 目录结构

```
collector/
├── main.py              # CLI 入口
├── poll.py              # 轮询器、RS485 flags 探测、统计与 JSONL 输出
├── seed.py              # 幂等 HSI 演示配置种子（协议/类型/设备/传感器/模板/通道）
├── storage.py           # PostgreSQL 落库、配置指纹与通道映射解析
├── runtime_config.py    # CLI/DB 参数优先级与运行中配置热加载
├── partitions.py        # collector_sample_raw 未来日分区预创建（纯函数 + DDL）
├── transport.py         # Linux 串口 + TIOCSRS485 传输层
├── hsi/
│   ├── frame.py         # HSI 帧编码/校验/解析
│   ├── cs1000.py        # CS1000 驱动（0x61）
│   └── hlb1400.py       # HLB1400 驱动（0x63）
├── tests/               # unittest（不依赖串口）
└── README.md
```

## 板端部署与运行

板端部署目录固定为 `~/collector-proto/`，代码与输出都放在该目录下：

```bash
# 在源代码仓库根目录（功能 worktree 根，即 yudao-boot-mini/）执行
scp -i ~/.ssh/id_rsa -r collector alientek@192.168.0.105:~/collector-proto/

# 板端运行：默认 /dev/ttyS4、5 秒间隔、600 秒后自动退出
cd ~/collector-proto/collector
python3 main.py --duration 600

# 也可按文档建议改为 60 秒轮询
python3 main.py --interval 60 --duration 600
```

常用参数：

| 参数 | 默认值 | 说明 |
|---|---|---|
| `--port` | `/dev/ttyS4` | 串口设备 |
| `--baud` | `9600` | 串口波特率；HSI 当前固定 9600 |
| `--interval` | 读取 DB（缺失时 `5.0`） | 轮询间隔（秒）。缺省时用 `collector_device.poll_interval_ms`，读取不到才退回 5.0；显式传入优先 |
| `--duration` | `600.0` | 运行时长（秒），`0` 表示一直运行 |
| `--timeout` | 读取 DB（缺失时 `1.0`） | 单次请求等待应答超时（秒）。缺省时用 `collector_device.timeout_ms`（毫秒换算为秒），显式传入优先 |
| `--retries` | 读取 DB（缺失时 `1`） | 单次采集失败后的重试次数。缺省时用 `collector_device.retries`，显式传入优先，显式 `0` 表示不重试 |
| `--config-check-interval` | 跟随当前轮询间隔 | DB 配置指纹检查间隔（秒）；轮询间隔热更新后，缺省检查间隔也随之更新 |
| `--output` | `~/collector-proto/data.jsonl` | JSONL 输出路径（每次启动覆盖） |
| `--log` | `~/collector-proto/proto.log` | 日志路径（每次启动覆盖） |
| `--flags` | `auto` | `auto` 优先使用 `collector_sensor.connection_config.rs485Flags`（有值时），否则依次尝试 `1,3,5,7,0`；也可传逗号分隔列表（显式列表覆盖 DB 配置） |
| `--require-sensors` | 关闭 | 严格模式：所有已启用传感器都必须应答，否则拒绝启动；默认宽松，缺失传感器只告警并在轮询中跳过 |
| `--partition-every` | `60` | 每 N 轮预创建一次 `collector_sample_raw` 未来日分区；`0` 表示只在启动（或 DB 恢复）时执行 |
| `--no-jsonl` | 关闭 | 不写 JSONL 文件，仍写日志与数据库 |
| `--no-db` | 关闭 | 不连接 PostgreSQL，仅输出 JSONL（也不执行种子） |
| `--no-seed` | 关闭 | 启动时不执行幂等种子（要求 DB 中已有配置） |
| `--heartbeat-every` | `5` | 每 N 轮写一次心跳，首轮总是写；`0` 表示关闭心跳 |
| `--db-retry-interval` | `60` | DB 不可用时的重连/重新种子/重新解析映射间隔（秒） |

启动流程：打开串口 → 保存原 termios/RS485 配置 → 先以 flags=0 清除遗留状态 →
连接 PostgreSQL（失败不退出）并执行幂等种子、解析设备/通道配置（含启用状态、
`poll_interval_ms`、`timeout_ms`、`retries`、`connection_config.rs485Flags`）→ 按 `--flags` 候选探测
RS485，每个 flags 要求**能应答的**传感器各连续 2 次 msg=1 状态应答，失败则重开
串口再试下一候选（本现场会回退到 0）。默认宽松策略：探测中无应答的传感器只
WARN，并在后续轮询中跳过；`--require-sensors` 可恢复“所有已启用传感器都必须
应答”的严格校验。`enabled=0` 的传感器/通道不参与探测、轮询与输出。DB 连接
失败不会退出进程：按
`--db-retry-interval`（默认 60 秒）周期性重连，期间继续轮询并输出 `channel_id`
为 null 的 JSONL；DB 恢复后自动重新执行幂等种子、重新解析通道映射，并从下一轮
起恢复 raw/设备状态/心跳写入，无需人工重启。同一类连接错误在一个失败周期内只
告警一次，恢复时记录 INFO，避免日志刷屏。**例外**：启动自检发现缺少
`collector_channel_latest` 时不会进入重试，而是提示执行
`sql/postgresql/migration/collector_latest_migration.sql` 并以退出码 2 结束。进程收到
SIGINT/SIGTERM 或达到 `--duration` 后关闭 JSONL/DB 连接、恢复串口设置，并输出
统计摘要。

### DB 配置热加载

运行中按 `--config-check-interval` 检查配置；未显式设置时跟随当前轮询间隔。
每次检查只执行**一条 SQL**，分别读取 `collector_device`、`collector_sensor`、
`collector_channel` 的 `MAX(update_time)` 与行数。空表或 `update_time` 全为空时，
`MAX` 由 `COALESCE(..., TIMESTAMP 'epoch')` 兜底；行数用于识别同时间戳下的增删。
设备表指纹单独比较，传感器/通道表指纹共同作为结构指纹：

- 指纹不变：不重新查询完整配置，不重建 driver；
- 仅设备表变化：重新加载上下文并更新轮询间隔、请求超时、重试次数，不重建
  driver，也不修改串口；CLI 显式传入的 `--interval` / `--timeout` / `--retries`
  始终覆盖 DB，因此这类显式覆盖项不会随 DB 热更新；
- 传感器或通道变化：重新加载通道映射并重建启用传感器的 driver 列表。被禁用或
  删除的传感器会移出轮询集合并记录中文日志，之后重新启用则自动加入；串口始终
  复用同一个已打开实例，不执行 close/reopen。`--flags auto` 且 `rs485Flags` 改变时，
  只在原串口实例上以严格模式重新设置 flags；显式 `--flags` 列表仍覆盖 DB；
- 结构应用采用“DB 准备 → 串口/driver 应用 → DB 提交”两阶段流程。只有 flags ioctl
  与结构回调全部成功后才提交新上下文和新指纹；任一环节失败都保留旧上下文、旧运行
  参数、旧 driver 和旧指纹，不输出“已热加载”成功日志。首次失败后的下一轮立即重试；
  若仍失败则按 2、4、8、16、32、60 秒指数退避，60 秒封顶，且实际等待不小于当前
  轮询间隔。冷却期间继续用旧配置采集，成功应用一次后失败计数、冷却与日志去重状态
  全部复位，后续变更恢复正常检查节奏。新增或重新启用传感器无应答只跳过该传感器，
  不会回滚其他有效的通道配置；
- 相同硬件错误只在首次出现、错误原因变化或退避档位提升时写 ERROR；60 秒封顶后的
  重复失败不刷日志。以最小 100ms 轮询、忽略单次操作用时计算，持续失败 60 秒内最多
  尝试 6 次：首次、下一轮立即重试，以及约 2/6/14/30 秒四次退避重试；下一次还需等待
  32 秒，落在约 62 秒；
- 启动时无应答且持续保持启用的传感器，在普通参数/通道/其他传感器结构变化时仍保持
  跳过，不会被意外加回；只有新增、禁用后重新启用，或进程重启时才再次执行连通探测。
  新传感器探测复用当前 flags 和原串口，不遍历候选，也不 close/reopen；
- 读取指纹或完整配置失败：保留上一次有效的运行参数、driver 与通道映射继续采集，
  记录告警并进入 DB 未就绪状态；重连只受 `--db-retry-interval` 节流，硬件重试标记
  不会提前触发 DB 连接或指纹查询。DB 恢复后重新加载，无需重启；断库期间 raw 仍按
  首期策略丢弃，不做本地 spool；
- 运行中全部传感器被禁用时采集循环保持运行，以便之后重新启用；只有**启动时**
  就全部禁用才沿用正常退出（exit 0）行为。

变更日志会标明“运行参数”或“结构”，并输出间隔、超时、重试次数等变更前后值；
结构变化还会输出轮询传感器、通道绑定数与 `rs485Flags` 的变化。

> 指纹依赖 Web/后端正确维护 `update_time`。若直接改库但不更新该字段，且表行数也
> 没变，则本次修改不会被指纹发现；通过正常后台接口修改无此限制。

## 输出

`data.jsonl` 每次成功采样一行（失败只记日志）：

```json
{"timestamp":"2026-11-13T18:00:00.123+08:00","sensor":"CS1000","address":97,"sensor_code":"cs1000-01","device_id":1,"fields":{"iso_4":10.0,"iso_6":20.0,"iso_14":30.0,"sae_a":2.1,"sae_b":2.2,"sae_c":2.3,"sae_d":2.4,"temperature_c":25.0,"flow_state":11,"drive_pct":55},"raw":[100,200,300,21,22,23,24,250,11,55],"channels":[{"channel_id":1,"channel_code":"iso_4","field":"iso_4","value":10.0,"raw_value":100}],"quality":0,"latency_ms":8.12,"attempts":1,"format":"0x16","cycle":1}
```

- `timestamp`：本地时区 ISO 8601（毫秒）；
- `sensor` / `address`：传感器名与 HSI 地址（CS1000=97、HLB1400=99）；
- `sensor_code` / `device_id`：启动时按种子业务键从 DB 解析；DB 不可用时为 `null`；
- `fields`：解析器按协议换算后的数值，仅用于 JSONL 对照；
- `raw`：msg=3 应答中的 int16 原始值（按字段顺序）；
- `raw_by_field` / `field_names`：当前应答格式的“字段名 → 原始值”映射与字段顺序。
  轮询层会把解析器的字段名映射原样透传到 JSONL 与落库记录，0x0E/0x12 都按字段名
  取值；字段在当前格式中不存在时跳过并只记录一次 INFO，绝不回退到固定下标；
- `channels`：通道级明细，`channel_id` 由启动时 `sensor_code + channel_code` 查询
  得到；`raw_value` 按字段名关联，`value` 按通道配置 `scale`/`value_offset`
  计算（与落库同一口径）；某通道未解析到 id 时仍输出该条目
  （`channel_id=null`），但不会写入 raw 表；`enabled=0` 的通道不会输出；
- `quality`：`0` 表示正常；CS1000 的 `flow_state` 不在 11/22/33/44/55 时置 `1`；
- `latency_ms`：本次采集从发送到解析完成的耗时；
- `attempts`：本次采集实际请求次数（成功前含重试）；
- `format`：应答 Length，用于区分 HLB1400 的旧/扩展格式。

`proto.log` 记录 RS485 flags 探测过程、每次请求/应答十六进制、解析结果、失败原因
和最终 `SUMMARY`（含成功率、平均/最大延迟、RSS、JSONL 行数、DB 状态）。

## PostgreSQL 落库

连接参数来自 `YUDAO_DB_*` 环境变量（systemd 通过 `/etc/yudao/env.remote` 注入）：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `YUDAO_DB_HOST` | `127.0.0.1` | PostgreSQL 主机 |
| `YUDAO_DB_PORT` | `5432` | 端口 |
| `YUDAO_DB_NAME` | `yudao_board` | 数据库名 |
| `YUDAO_DB_USERNAME` | `root` | 用户名 |
| `YUDAO_DB_PASSWORD` | `123456` | 密码 |

启动流程：执行幂等种子 → 连接 DB → 按 `sensor_code + channel_code` 解析设备/
通道配置与 id → 开始轮询。启动或运行中 DB 不可用时，由同一个重连状态机按
`--db-retry-interval` 节流重试：每次重试都会重新执行幂等种子并重新解析映射，
恢复后自动落库；写入失败会重置连接并纳入同一节流，不会每个轮询周期反复建连。
种子内容：

- `collector_protocol`：`hsi_rs485`（HYDAC HSI (RS485)，`driver_key=hsi_rs485_driver`）；
- `collector_sensor_type`：`cs1000`、`hlb1400`（含制造商与型号说明）；
- `collector_device`：`rk3568-hsi-01`，`poll_interval_ms=60000`、`timeout_ms=1000`、
  `retries=1`；
- `collector_sensor`：`cs1000-01`（0x61）、`hlb1400-01`（0x63），通过
  `sensor_type_id` 关联类型；连接参数为 `/dev/ttyS4` 9600 8N1 + `rs485=true`、
  `rs485Flags=0`；
- `collector_sensor_template`：16 条（CS1000 10 条、HLB1400 6 条），保存量程、
  缩放、单位、解析位置、四档阈值比例（默认 0.10/0.30/0.70/0.90）与开关；
- `collector_channel`：16 条，由模板生成，保存 `sensor_template_id`、`value_kind`、
  量程、四档阈值与开关；`channel_config` 含 `address/message/fieldIndex/signed`。
  数值通道 `alarm_enabled=1` 且四个阈值开关为 1；枚举通道 `flow_state` 保留
  11~55 的枚举原始值展示量程，但四档阈值统一为 NULL、`alarm_enabled=0` 且四个开关为 0。
  CS1000 数值量程为 ISO-4 8~25、ISO-6 7~24、ISO-14 6~23、SAE A/B/C/D 0~14、
  温度 -60~150、Drive 0~100。

已有旧版（仅两档阈值）数据库需先执行一次
`sql/postgresql/migration/collector_threshold_migration.sql`，再启动新版种子；新库直接执行
`sql/postgresql/collector/01_schema.sql` 与 `sql/postgresql/collector/02_system_seed.sql`
（或使用 `sql/postgresql/dbctl.sh init`）即可。种子可重复执行，不会重复创建类型、模板、
设备、传感器或通道。

**既有库升级到最新值看板**：先执行一次幂等的
`sql/postgresql/migration/collector_latest_migration.sql`，再启动新版采集器（或直接用
`dbctl.sh migrate` 统一管理版本）。采集器启动时会
检查 `public.collector_channel_latest`；缺表时记录中文错误并提示“请先执行
collector_latest_migration.sql”，随后以退出码 2 失败，不会只写 raw 而静默丢失
最新值。全新库由 `collector/01_schema.sql` 一并建表，无需单独迁移。

配置消费（不再“只写不读”）：

- `collector_channel.scale` / `value_offset`：落库与 JSONL 的工程值统一按
  `raw * scale + value_offset` 计算，解析器内置缩放只用于 JSONL 的 `fields` 对照；
- `collector_channel.enabled` / `collector_sensor.enabled`：`enabled=0` 的传感器
  与通道在解析时被过滤，不探测、不轮询、不输出/落库，日志记录跳过数量；运行中
  修改后由结构热加载自动应用；
- `collector_channel.value_kind` / 量程 / 四档阈值 / 单档开关：当前采集进程只消费
  `scale`、`value_offset` 与 `enabled`，阈值供后续报警判断与 UI 编辑使用；
  枚举通道不参与阈值报警；
- `collector_device.poll_interval_ms` / `timeout_ms` / `retries`：分别作为默认轮询
  间隔、请求超时（毫秒换算为秒）与失败重试次数，CLI `--interval` / `--timeout` /
  `--retries` 显式值优先；`timeout_ms` 已从仅保存配置变为实际生效，三项均支持热加载；
- `collector_sensor.connection_config.rs485Flags`：`--flags auto` 时作为首选候选，
  显式逗号列表仍可覆盖；结构热加载时在原串口实例上应用新值；
- `collector_retention_policy.partition_lead_days`：分区预创建使用该值（默认 3）。

落库行为：

- `collector_sample_raw`：每通道一行，`collect_time` 为 Asia/Shanghai 本地
  `timestamp(3)`，`value` 按通道配置计算、`raw_value` 为按字段名关联的 int16
  原始值；每批一个事务，`ON CONFLICT (channel_id, collect_time) DO NOTHING`；
- `collector_channel_latest`：每通道一行最新值，与 raw 在**同一事务、同一 cursor**
  内写入；数值通道写 `value_num`，枚举通道写 `value_text`，本轮未产生 raw 的通道
  不更新。UPSERT 仅接受不早于现有 `sample_time` 的采样，重复重放不会倒退。任一
  latest 写入失败会连同 raw 一起回滚；两表共享 Asia/Shanghai `timestamp(3)` 墙钟；
- 分区维护：启动（或 DB 恢复）时以及每 `--partition-every` 轮（默认 60 轮），
  读取 `collector_retention_policy.data_class='sample_raw'` 的
  `partition_lead_days`（默认 3），为今天起该天数内缺失的日分区执行幂等
  `CREATE TABLE IF NOT EXISTS ... PARTITION OF collector_sample_raw`；查询现有分区
  限定 `public` schema；失败时重置数据库连接并只 WARN 不退出，权限不足时提示
  运维手工创建。`collector_sample_raw_default` 正常应保持为空；
- `collector_device_status`：任一颗传感器成功即视为本轮成功，更新 `online=1`、
  `last_success_at`、批次最大 `last_latency_ms`；连续 3 轮失败后 `online=0` 并写
  `last_error`；
- `collector_heartbeat`：首轮及每 `--heartbeat-every` 轮写一行，`instance_id`
  为“主机名 + 进程启动时间”，`collector_version=0.1.0-prototype`；
- DB 不可用时记录 WARN（同一错误只告警一次）并继续轮询；若启动后尚未解析过有效
  配置，JSONL 的 `device_id`/`channel_id` 为 null，若已有有效配置则保留上一次的
  映射。按 `--db-retry-interval` 自动重连、重新种子并重新解析映射，恢复后无需重启
  即继续落库（首期接受断连期间丢数据，不做本地 spool）。

常用核对 SQL（板端）：

```bash
PGPASSWORD=123456 psql -h 192.168.0.23 -U root -d yudao_board \
  -c "select count(*) from collector_sample_raw" \
  -c "select count(distinct channel_id) from collector_sample_raw" \
  -c "select * from collector_device_status" \
  -c "select id,instance_id,heartbeat_time,device_online,collector_version from collector_heartbeat order by id desc limit 3" \
  -c "select child.relname from pg_inherits join pg_class parent on parent.oid=inhparent join pg_class child on child.oid=inhrelid where parent.relname='collector_sample_raw' order by 1" \
  -c "select count(*) from collector_sample_raw_default"
```

## 最新值看板接口

`GET /admin-api/collector/dashboard/latest?deviceId=<id>`，权限
`collector:dashboard:query`；返回设备状态、按传感器分组的启用通道最新值。页面
同时调用 `/admin-api/collector/device/simple-list`，因此看板角色还需要
`collector:device:query`；`collector/02_system_seed.sql`（原 collector_web_seed.sql）
会给已获得看板菜单（802）的角色幂等补齐采集设备菜单（781）。

- **不做 ETag/304**：响应只由采样与设备状态生成时看似可缓存，但
  `online/staleOffline/stale/serverTime` 会随服务器时间变化；若用采样时间做版本
  令牌，采集器停采后令牌冻结，页面会永远显示在线。本版每次返回完整响应（16 通道
  约 2 KB，10 秒轮询可忽略），后续如需带宽优化再设计包含派生状态的版本令牌。
- **陈旧与离线**：通道采样超过 `3 × poll_interval_ms` 时 `stale=true`；设备最大
  采样时间超过 `10 ×` 时 `staleOffline=true` 且强制 `online=false`。设备活性采样
  独立统计全部未删除通道，包含列表中过滤掉的禁用通道，避免禁用最后一条通道洗掉
  离线状态。完全没有采样时回退到 `lastPollAt`（设备状态更新时间）做同样的十倍
  周期判定，因此刚上电的短窗口仍在线、停采后会离线；采样与状态时间都缺失则直接
  返回 `online=false, staleOffline=true`。两者都由接口按当前 `serverTime` 实时派生，
  不写回数据库；前端在两次请求之间也按本地时间继续老化显示。v1 使用设备级
  `poll_interval_ms`，通道级轮询尚未落地，查询已预留 `collector_channel.poll_interval_ms`
  但暂不参与判定。
- **质量码**：latest/raw 的 `quality` 保留存储语义（0 正常、1 不确定、2 坏质量）；
  “陈旧”是独立的派生标记，不再复用 `quality=2`。
- **写入延迟**：`lastWriteLagMs` 来自 `collector_heartbeat`，是采集器**进程级**
  数据库写入延迟，不是设备级指标；页面显示为“采集器写入延迟”。
- **通道过滤**：`collector_channel.enabled=0` 的通道不出现在看板中。
- **时间**：采集器、DDL 与后端统一使用 `Asia/Shanghai` 墙钟；接口中的
  `serverTime/lastPollAt/sampleTime` 由服务端显式按该时区转为 epoch millis（前端按
  `null | number` 处理），不经过 JVM 默认时区。裸 jar 启动仍统一使用
  `java -Duser.timezone=Asia/Shanghai -jar yudao-server/target/yudao-server.jar`，保证其他
  接口的全局时间序列化一致。页面分别显示“最后成功请求”（浏览器请求时间）与
  “最后数据更新”（看板中最大采样时间），断连时保留最后一次成功数据。

## HSI 协议要点

帧格式：

```
STX(0x02) | Length | Address | Number | Data... | Checksum | ETX(0x03)
```

- `Length` = `Address` + `Number` + `Data` 字节数；整帧长度 = `Length + 4`；
- `Checksum = (255 - ((Length + Address + Number + ΣData) & 0xFF)) & 0xFF`；
- 请求地址为传感器地址（CS1000 `0x61`、HLB1400 `0x63`），应答地址固定 `0x2A`；
- 应答 `Number` 与请求功能码一致；驱动会校验地址、功能码、Length 与校验和。

功能码：

| 功能码 | 用途 | 原型是否使用 |
|---|---|---|
| msg=1 | 状态查询，应答 Length=0x05，Data 为 StatusByte/StatusCodeLo/Hi 三字节，三字节全 0 才算有效 | 启动时用于 RS485 flags 连通确认 |
| msg=3 | 测量值 | 每次轮询使用 |
| msg=0 | 传感器 ID（本现场两颗传感器无应答） | 不使用 |
| msg=2 | 传感器信息（本现场两颗传感器无应答） | 不使用 |

RS485：9600 / 8N1、无流控；通过 termios ioctl `TIOCSRS485`（`0x542F`，
`struct serial_rs485` 8×uint32）配置方向控制。

> **板端实测结论（RK3568 / Linux 6.1.141 dw-apb-uart）**：开启内核 RS485 自动
> 方向模式（flags=1/3/5/7）时，每个新打开的端口只有**首帧**能收到应答，之后
> `TIOCOUTQ` 持续增长（TX 队列卡死）、串口 `close` 需等待约 30 秒；重新打开端口
> 才能恢复。`flags=0`（关闭内核 RS485 模式）依赖板载自动方向电路，可以持续稳定
> 收发。因此 `auto` 会依次尝试 `1,3,5,7`（每个 flags 要求两颗传感器各连续 2 次
> 状态应答，失败则重开串口）并回退到 `0`，本现场实际使用 `flags=0`。可用
> `--flags` 显式指定候选，便于在其他硬件上复测。

### CS1000（0x61）

msg=3 应答 Length=0x16，20 字节，10×int16 小端：

| 字段 | 缩放 | 单位 |
|---|---|---|
| `iso_4` / `iso_6` / `iso_14` | ×0.1 | ISO 清洁度 |
| `sae_a` / `sae_b` / `sae_c` / `sae_d` | ×0.1 | SAE 等级 |
| `temperature_c` | ×0.1 | ℃ |
| `flow_state` | 1:1 | 枚举 11/22/33/44/55 |
| `drive_pct` | 1:1 | % |

### HLB1400（0x63）

msg=3 按应答 Length 分支：

- **Length=0x0E**（旧格式，现场传感器实际返回）：12 字节，6×int16 小端：

  | 字段 | 缩放 | 单位 |
  |---|---|---|
  | `conductivity_ns_m` | ×0.1 | nS/m |
  | `conductivity_change_pct` | ×0.1 | % |
  | `dc` | ×0.01 | — |
  | `dc_change_pct` | ×0.1 | % |
  | `saturation_pct` | 1:1 | % |
  | `temperature_c` | ×0.1 | ℃ |

- **Length=0x12**（release 13.08 扩展格式）：16 字节，8×int16 小端。字段顺序为
  `conductivity_ns_m`、`conductivity_change_pct`、`dc`、`saturation_pct`、
  `temperature_c`、`raw_conductivity`、`raw_dc`、`raw_dac`（**没有**
  `dc_change_pct`，且温度之后多了 3 个 Raw 字段）。

两种格式的通道映射都**按字段名**关联（解析器输出的 `raw_by_field`），不再按
固定 `fieldIndex` 顺序取 `raw`。该映射由 `poll_sensor_once` 返回、`poll_cycle`
原样透传到 JSONL 记录与落库记录，因此 0x12 在真实轮询链路上也不会错位。
0x0E 的 6 个字段全部有配置通道；0x12 中
`dc_change_pct` 不存在，该通道跳过，`raw_conductivity`/`raw_dc`/`raw_dac`
未配置通道，默认只记录一次 INFO 后跳过。这样即使现场某天返回 0x12，也不会把
饱和度写进 DC 变化、把温度写进原始电导率等错位行。该分支当前现场传感器不会
返回，仅由单元测试覆盖；后续拿到正式版协议文档后需复核字段顺序。

## 单元测试

测试不依赖串口，可在 Windows/Linux 上运行（Python 3.11+）：

```bash
# 在源代码仓库根目录（功能 worktree 根，即 yudao-boot-mini/）执行
python -m unittest discover -s collector/tests -v
```

覆盖：帧编码与校验和、错误帧拒绝、CS1000 10 字段解析、HLB1400 旧/扩展格式分支
（0x12 全链路集成测试：`poll_sensor_once → poll_cycle → JSONL/落库` 按字段名映射、
无 `dc_change_pct` 错位行、未配置 Raw 字段被跳过）、状态应答 Length=0x05 与
StatusByte/StatusCodeLo/Hi 三字节判定、重试逻辑、RS485 flags 探测（宽松/严格
两种策略）、JSONL 输出与统计摘要、配置驱动（scale/value_offset、enabled=0 跳过、
poll_interval/timeout/retries 的 CLI > DB > 默认优先级、rs485Flags 优先）、
单 SQL 配置指纹、无变化不重载、参数变化不重建 driver、结构变化重建 driver 且不重开
串口、禁用传感器移出轮询、启动无应答状态保留与新增/重新启用探测、flags 严格失败传播、
应用失败保留旧上下文/旧指纹、首次立即重试与后续指数退避、退避期间 DB 重连独立节流、
相同错误日志去重及成功复位、`run_loop` 参数与结构热加载集成、
热加载读取失败保留旧配置、分区预创建（名称/边界/幂等/
schema 限定/DDL 失败不中断轮询），以及 DB 首次不可用后恢复、映射解析失败后恢复、
重连节流（不会每轮建连）、运行中写入失败自动重连、latest 与 raw 同事务更新
（枚举/数值列、失败整轮回滚、无当前值不 upsert）、启动缺少 latest 表时报告不可
重试错误。板端脚本另有
`deploy/tests/test_common.sh` 与 `deploy/tests/test_mock_gate.sh`。

## 约束与已知限制

- 不修改板端系统配置与网络配置，不使用 `ttyS3` / `ttyS8`；
- 不依赖 msg=0 / msg=2（现场无应答）；
- 当前两颗传感器处于自测随机模式，数值为随机值，不代表真实工况；本轮只验证
  通信成功率、解析正确性、稳定落库；
- 状态应答按 Length=0x05 校验，Data 三字节按 StatusByte/StatusCodeLo/StatusCodeHi
  全部参与判定（三字节全 0 才有效）；正式文档到位后需复核三字节具体语义；
- `enabled=0` 的传感器/通道不参与采集；若**启动时**所有传感器都被禁用，进程记录
  WARN 后以退出码 0 正常退出，避免 `Restart=on-failure` 触发重启风暴，启用传感器后
  需手动重启 collector；运行中全部禁用则保持循环，之后可通过热加载重新启用；
- 首期不做本地 spool：DB 不可用期间的数据直接丢弃；
- 配置版本快照、聚合、报警尚未接入，`config_version_id` 等字段留空；
- 默认宽松启动：单颗传感器无应答不会阻止启动，但该传感器会被跳过（需要传感器
  全量在线时使用 `--require-sensors`）。普通结构重载不会重新尝试持续启用但无应答的
  传感器；新增、禁用后重新启用或进程重启时才再次探测，避免无关配置修改造成意外轮询；
- 分区预创建需要采集账号具备建表权限；权限不足时只告警，需运维手工创建，否则
  数据会落进 `collector_sample_raw_default`；
- 默认 5 秒轮询只做了短跑验证；systemd 不再传死轮询参数，系统部署由 DB 种子默认
  `poll_interval_ms=60000`（60 秒）控制。
