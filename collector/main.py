#!/usr/bin/env python3
"""HSI 采集原型入口。

板端运行（默认 /dev/ttyS4、5 秒间隔、运行 10 分钟后退出）::

    cd ~/collector-proto/collector
    python3 main.py --duration 600

输出：
- ``~/collector-proto/data.jsonl``：每次成功采样一行；
- ``~/collector-proto/proto.log``：请求/应答、解析结果与最终摘要。

本原型不写数据库、不修改板端系统配置，只使用标准库。
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

LOGGER_NAME = "hsi"
DEFAULT_OUTPUT = "~/collector-proto/data.jsonl"
DEFAULT_LOG = "~/collector-proto/proto.log"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="HYDAC HSI 采集原型（CS1000 + HLB1400）",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--port", default="/dev/ttyS4", help="串口设备")
    parser.add_argument("--interval", type=float, default=5.0, help="轮询间隔（秒）")
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
        help="RS485 flags：auto 或逗号分隔列表，例如 1,3,5,7",
    )
    return parser


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
            "串口已打开 port=%s baud=%d interval_s=%.1f duration_s=%.1f timeout_s=%.2f",
            args.port,
            transport.baud,
            args.interval,
            args.duration,
            args.timeout,
        )
        # 先关闭内核 RS485 模式，清除上一次进程可能遗留的卡死状态。
        transport.set_rs485_flags(0)
        flags = discover_rs485_flags(
            transport,
            drivers,
            candidates=parse_flags(args.flags),
            timeout=args.timeout,
            logger=logger,
            reopen=transport.reopen,
        )
        logger.info("RS485 连通确认 flags=%d sensors=%s", flags, [d.name for d in drivers])

        writer = JsonlWriter(output_path)
        duration = None if args.duration <= 0 else args.duration
        stats = run_loop(
            transport,
            drivers,
            writer=writer,
            interval_s=args.interval,
            duration_s=duration,
            timeout=args.timeout,
            retries=args.retries,
            logger=logger,
            should_stop=lambda: stop_requested["value"],
        )
        summary = summarize(
            stats,
            flags=transport.rs485_flags,
            duration_s=time.monotonic() - started,
            rss_kb=read_rss_kb(),
            jsonl_lines=writer.line_count,
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
