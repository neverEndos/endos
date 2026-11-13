#!/usr/bin/env python3
"""HSI 采集原型入口。

板端运行（默认 /dev/ttyS4、5 秒间隔、运行 10 分钟后退出；启动时执行幂等种子）::

    cd ~/collector-proto/collector
    python3 main.py --duration 600

输出：
- ``~/collector-proto/data.jsonl``：每次成功采样一行（含 device_id/channel_id）；
- ``~/collector-proto/proto.log``：请求/应答、解析结果与最终摘要；
- PostgreSQL：raw 采样、设备状态与心跳（``YUDAO_DB_*`` 环境变量）。

DB 不可用时进程不退出：按 ``--db-retry-interval``（默认 60 秒）周期性重连、重新
执行幂等种子并重新解析通道映射；期间 JSONL 仍输出，``channel_id`` 为 null。
DB 恢复后自动开始落库，无需人工重启。

串口部分只使用标准库；数据库写入使用板端已安装的 ``psycopg2``。
"""

import argparse
import json
import logging
import os
import signal
import sys
import time

_HERE = os.path.dirname(os.path.abspath(__file__))
if _HERE not in sys.path:
    sys.path.insert(0, _HERE)

from hsi import cs1000, hlb1400  # noqa: E402
from poll import (  # noqa: E402
    DEFAULT_RS485_FLAGS,
    JsonlWriter,
    PollError,
    discover_rs485_flags,
    read_rss_kb,
    run_loop,
    summarize,
)
from seed import SENSOR_CODE_BY_DRIVER  # noqa: E402
from storage import DatabaseManager, unresolved_context  # noqa: E402

LOGGER_NAME = "hsi"
DEFAULT_OUTPUT = "~/collector-proto/data.jsonl"
DEFAULT_LOG = "~/collector-proto/proto.log"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="HYDAC HSI 采集原型（CS1000 + HLB1400）",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--port", default="/dev/ttyS4", help="串口设备")
    parser.add_argument(
        "--interval",
        type=float,
        default=None,
        help=(
            "轮询间隔（秒）。缺省时读取 DB collector_device.poll_interval_ms，"
            "读取不到才退回 5.0；显式传入时以本参数为准"
        ),
    )
    parser.add_argument(
        "--duration",
        type=float,
        default=600.0,
        help="运行时长（秒）；0 表示一直运行",
    )
    parser.add_argument("--timeout", type=float, default=1.0, help="单次请求超时（秒）")
    parser.add_argument(
        "--retries", type=int, default=1, help="单次采集失败后的重试次数"
    )
    parser.add_argument("--output", default=DEFAULT_OUTPUT, help="JSONL 输出路径")
    parser.add_argument("--log", default=DEFAULT_LOG, help="日志路径")
    parser.add_argument(
        "--flags",
        default="auto",
        help=(
            "RS485 flags：auto 时优先使用 collector_sensor.connection_config.rs485Flags"
            "（有值时），否则依次尝试 1,3,5,7,0；也可传逗号分隔列表"
        ),
    )
    parser.add_argument(
        "--require-sensors",
        action="store_true",
        help=(
            "严格模式：所有已启用传感器都必须应答，否则轮询器拒绝启动。"
            "默认宽松：缺失传感器只告警并在后续轮询中跳过"
        ),
    )
    parser.add_argument(
        "--partition-every",
        type=int,
        default=60,
        help="每 N 轮预创建一次 collector_sample_raw 未来日分区（0 表示只在启动时执行）",
    )
    parser.add_argument(
        "--no-jsonl",
        action="store_true",
        help="不写 JSONL 文件（仍按参数写日志）",
    )
    parser.add_argument(
        "--no-db",
        action="store_true",
        help="不连接 PostgreSQL，仅输出 JSONL（也不执行种子）",
    )
    parser.add_argument(
        "--no-seed",
        action="store_true",
        help="启动时不执行幂等 HSI 配置种子",
    )
    parser.add_argument(
        "--heartbeat-every",
        type=int,
        default=5,
        help="每 N 轮写一次心跳（首轮总是写；0 表示关闭心跳）",
    )
    parser.add_argument(
        "--db-retry-interval",
        type=positive_float,
        default=60.0,
        help="DB 不可用时的重连/重新种子间隔（秒），期间继续输出 JSONL",
    )
    return parser


def resolve_interval_s(
    cli_interval: float | None,
    poll_interval_ms: int | None,
    default: float = 5.0,
) -> tuple[float, str]:
    """决定轮询间隔：CLI 显式值 > ``collector_device.poll_interval_ms`` > 默认。

    返回 ``(间隔秒, 来源说明)``，便于日志与测试。
    """

    if cli_interval is not None:
        return float(cli_interval), "命令行 --interval"
    if poll_interval_ms and int(poll_interval_ms) > 0:
        return int(poll_interval_ms) / 1000.0, "collector_device.poll_interval_ms"
    return float(default), f"默认 {default}"


def positive_float(value: str) -> float:
    try:
        parsed = float(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError(f"无法解析数值: {value}") from exc
    if parsed <= 0:
        raise argparse.ArgumentTypeError("必须大于 0")
    return parsed


def configure_logging(path: str) -> logging.Logger:
    path = os.path.expanduser(path)
    parent = os.path.dirname(path)
    if parent:
        os.makedirs(parent, exist_ok=True)
    logger = logging.getLogger(LOGGER_NAME)
    logger.setLevel(logging.INFO)
    logger.handlers.clear()
    formatter = logging.Formatter("%(asctime)s %(levelname)s %(message)s")
    file_handler = logging.FileHandler(path, mode="w", encoding="utf-8")
    file_handler.setFormatter(formatter)
    stream_handler = logging.StreamHandler()
    stream_handler.setFormatter(formatter)
    logger.addHandler(file_handler)
    logger.addHandler(stream_handler)
    return logger


def parse_flags(value: str) -> tuple[int, ...]:
    if value.strip().lower() == "auto":
        return DEFAULT_RS485_FLAGS
    try:
        flags = tuple(int(item, 0) for item in value.split(",") if item.strip())
    except ValueError as exc:
        raise argparse.ArgumentTypeError(f"无法解析 flags: {value}") from exc
    if not flags:
        raise argparse.ArgumentTypeError("flags 列表不能为空")
    return flags


def run(args: argparse.Namespace, logger: logging.Logger) -> int:
    # 仅在需要真实串口时导入 Linux 专用模块，--help 在任意平台可用。
    from transport import SerialRS485Transport

    drivers = [cs1000.CS1000(), hlb1400.HLB1400()]
    stop_requested = {"value": False}
    db_manager: DatabaseManager | None = None
    # 即使 DB 不可用，也用种子映射输出 device_id/channel_id（此时 id 为 null），
    # 便于 JSONL 结构与落库对照；DB 恢复后由 DatabaseManager 刷新该映射。
    channel_context = unresolved_context()

    def handle_signal(signum, _frame) -> None:
        logger.info("收到信号 %s，准备停止", signum)
        stop_requested["value"] = True

    signal.signal(signal.SIGINT, handle_signal)
    signal.signal(signal.SIGTERM, handle_signal)

    output_path = os.path.expanduser(args.output)
    writer = None
    transport = None
    started = time.monotonic()
    try:
        transport = SerialRS485Transport(args.port, logger=logger)
        transport.open()
        logger.info(
            "串口已打开 port=%s baud=%d duration_s=%.1f timeout_s=%.2f",
            args.port,
            transport.baud,
            args.duration,
            args.timeout,
        )
        # 先关闭内核 RS485 模式，清除上一次进程可能遗留的卡死状态。
        # 内核不支持 TIOCSRS485 时 transport 会告警并继续（依赖板载自动方向）。
        transport.set_rs485_flags(0)

        # 先初始化 DB：轮询间隔、启用传感器与 rs485Flags 都来自 DB 配置；
        # DB 不可用时仍按默认值启动，期间只输出 JSONL，由管理器周期性重连。
        if not args.no_db:
            # 首次尝试立即执行；失败后由管理器按 --db-retry-interval 节流重试，
            # 进程不退出，期间继续输出 JSONL。
            db_manager = DatabaseManager(
                seed_enabled=not args.no_seed,
                retry_interval_s=args.db_retry_interval,
                logger=logger,
            )
            db_manager.ensure_ready()
            channel_context = db_manager.context

        # enabled=0 的传感器不参与探测与轮询。
        target_drivers = [
            driver
            for driver in drivers
            if channel_context.sensor_enabled(
                SENSOR_CODE_BY_DRIVER.get(driver.name, "")
            )
        ]
        if not target_drivers:
            raise PollError("DB 中所有传感器均为 enabled=0，没有可轮询的传感器")

        auto_flags = args.flags.strip().lower() == "auto"
        if auto_flags:
            configured = channel_context.rs485_flags
            if configured is not None:
                candidates = (configured,) + tuple(
                    flag for flag in DEFAULT_RS485_FLAGS if flag != configured
                )
                logger.info(
                    "使用 connection_config.rs485Flags=%d 作为首选候选", configured
                )
            else:
                candidates = DEFAULT_RS485_FLAGS
        else:
            candidates = parse_flags(args.flags)

        responsive: set[str] = set()
        flags = discover_rs485_flags(
            transport,
            target_drivers,
            candidates=candidates,
            timeout=args.timeout,
            logger=logger,
            reopen=transport.reopen,
            require_all=args.require_sensors,
            responsive_out=responsive,
        )
        active_drivers = [d for d in target_drivers if d.name in responsive]
        skipped_drivers = [d.name for d in target_drivers if d.name not in responsive]
        if skipped_drivers:
            logger.warning(
                "以下传感器探测无应答，后续轮询将跳过：%s（如需严格校验请加 --require-sensors）",
                skipped_drivers,
            )
        logger.info(
            "RS485 连通确认 flags=%s sensors=%s require_sensors=%s",
            flags,
            [d.name for d in active_drivers],
            args.require_sensors,
        )

        interval_s, interval_source = resolve_interval_s(
            args.interval, channel_context.poll_interval_ms
        )
        if interval_s <= 0:
            raise PollError(f"轮询间隔必须大于 0：{interval_s}")
        if args.partition_every < 0:
            raise PollError("--partition-every 不能为负数")
        logger.info(
            "轮询间隔 interval_s=%.3f（来源：%s）", interval_s, interval_source
        )

        writer = None if args.no_jsonl else JsonlWriter(output_path)
        duration = None if args.duration <= 0 else args.duration
        stats = run_loop(
            transport,
            active_drivers,
            writer=writer,
            interval_s=interval_s,
            duration_s=duration,
            timeout=args.timeout,
            retries=args.retries,
            logger=logger,
            should_stop=lambda: stop_requested["value"],
            db_manager=db_manager,
            channel_context=channel_context,
            heartbeat_every=args.heartbeat_every,
            partition_every=args.partition_every,
        )
        summary = summarize(
            stats,
            flags=transport.rs485_flags,
            duration_s=time.monotonic() - started,
            rss_kb=read_rss_kb(),
            jsonl_lines=writer.line_count if writer is not None else 0,
        )
        summary["database"] = (
            db_manager.status()
            if db_manager is not None
            else {
                "enabled": False,
                "ready": False,
                "device_id": None,
                "channels_resolved": 0,
            }
        )
        logger.info("SUMMARY %s", json.dumps(summary, ensure_ascii=False))
        print(json.dumps(summary, ensure_ascii=False, indent=2))
        return 0
    except PollError as exc:
        logger.error("轮询器无法启动: %s", exc)
        return 2
    except Exception as exc:
        logger.exception("运行失败: %s", exc)
        return 1
    finally:
        if writer is not None:
            writer.close()
        if db_manager is not None:
            db_manager.close()
        if transport is not None:
            transport.close()


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    logger = configure_logging(args.log)
    logger.info("HSI 采集原型启动 argv=%s", sys.argv)
    return run(args, logger)


if __name__ == "__main__":
    sys.exit(main())
