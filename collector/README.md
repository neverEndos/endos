# HSI 采集原型（CS1000 + HLB1400）

这是采集器功能的最小验证原型：在 RK3568 板端通过 `/dev/ttyS4` + RS485 轮询两颗
HYDAC HSI 传感器，验证帧协议、解析正确性与 5 秒轮询稳定性，并输出 JSONL 供后续
collector 接入。原型只验证协议驱动与轮询，不接数据库、不写 raw 表。

- 纯 Python 3.12 标准库（`os` / `termios` / `select` / `fcntl` / `struct`），
  不引入 pyserial 等第三方依赖，板端无需 `pip install`；
- 帧编解码、校验、应答解析、按 Length 分支的解析器全部是纯函数，可脱离串口做
  单元测试；
- 打开串口前保存 termios 设置，关闭时恢复；RS485 模式在进程内保持。

## 目录结构

```
collector/
├── main.py              # CLI 入口
├── poll.py              # 轮询器、RS485 flags 探测、统计与 JSONL 输出
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
# 在工作区根目录执行，把本目录复制到板端
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
| `--interval` | `5.0` | 轮询间隔（秒） |
| `--duration` | `600.0` | 运行时长（秒），`0` 表示一直运行 |
| `--timeout` | `1.0` | 单次请求等待应答超时（秒） |
| `--retries` | `1` | 单次采集失败后的重试次数 |
| `--output` | `~/collector-proto/data.jsonl` | JSONL 输出路径（每次启动覆盖） |
| `--log` | `~/collector-proto/proto.log` | 日志路径（每次启动覆盖） |
| `--flags` | `auto` | RS485 flags：`auto` 依次尝试 `1,3,5,7,0`，或逗号分隔列表 |

启动流程：打开串口 → 保存原 termios/RS485 配置 → 先以 flags=0 清除遗留状态 →
依次尝试 RS485 flags；每个 flags 要求两颗传感器各连续 2 次 msg=1 状态应答，失败
则重开串口再试下一候选（本现场会回退到 0）→ 固定该 flags 进入轮询。进程收到
SIGINT/SIGTERM 或达到 `--duration` 后关闭 JSONL、恢复串口设置，并输出统计摘要。

## 输出

`data.jsonl` 每次成功采样一行（失败只记日志）：

```json
{"timestamp":"2026-11-13T18:00:00.123+08:00","sensor":"CS1000","address":97,"fields":{"iso_4":10.0,"iso_6":20.0,"iso_14":30.0,"sae_a":2.1,"sae_b":2.2,"sae_c":2.3,"sae_d":2.4,"temperature_c":25.0,"flow_state":11,"drive_pct":55},"quality":0,"latency_ms":8.12,"attempts":1,"format":"0x16","cycle":1}
```

- `timestamp`：本地时区 ISO 8601（毫秒）；
- `sensor` / `address`：传感器名与 HSI 地址（CS1000=97、HLB1400=99）；
- `fields`：解析后的数值字段；
- `quality`：`0` 表示正常；CS1000 的 `flow_state` 不在 11/22/33/44/55 时置 `1`；
- `latency_ms`：本次采集从发送到解析完成的耗时；
- `attempts`：本次采集实际请求次数（成功前含重试）；
- `format`：应答 Length，用于区分 HLB1400 的旧/扩展格式。

`proto.log` 记录 RS485 flags 探测过程、每次请求/应答十六进制、解析结果、失败原因
和最终 `SUMMARY`（含成功率、平均/最大延迟、RSS、JSONL 行数）。

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
| msg=1 | 状态查询，应答 Length=0x05，无效时 StatusByte≠0 | 启动时用于 RS485 flags 连通确认 |
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

- **Length=0x12**（release 13.08 扩展格式）：16 字节，8×int16 小端。按任务卡
  给定的“8 字段、含 Raw Conductivity/Raw DC/Raw DAC”整理为
  `conductivity_ns_m`、`conductivity_change_pct`、`dc`、`saturation_pct`、
  `temperature_c`、`raw_conductivity`、`raw_dc`、`raw_dac`。该分支当前现场传感器
  不会返回，仅由单元测试覆盖；后续拿到正式版协议文档后需复核字段顺序。

## 单元测试

测试不依赖串口，可在 Windows/Linux 上运行（Python 3.11+）：

```bash
# 在工作区根目录执行
python -m unittest discover -s collector/tests -v
```

覆盖：帧编码与校验和、错误帧拒绝、CS1000 10 字段解析、HLB1400 旧/扩展格式分支、
状态应答、重试逻辑、RS485 flags 探测、JSONL 输出与统计摘要。

## 约束与已知限制

- 只读协议原型，不写数据库、不修改板端系统配置，不使用 `ttyS3` / `ttyS8`；
- 不依赖 msg=0 / msg=2（现场无应答）；
- 当前两颗传感器处于自测随机模式，数值为随机值，不代表真实工况；本轮只验证
  通信成功率、解析正确性与稳定性；
- 状态应答的 StatusByte 位置按“Data 首字节”处理，待正式文档确认；
- 默认 5 秒轮询只做了 10 分钟短跑验证，文档建议的 60 秒模式尚未长跑验证。
