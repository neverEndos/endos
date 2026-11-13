"""HSI 轮询原型。

本模块不依赖串口实现，只要求 ``transport`` 提供：
- ``set_rs485_flags(flags)``
- ``transact(request_bytes, timeout=...) -> HsiFrame``

这样单元测试可以用假 transport 覆盖重试、RS485 探测与统计逻辑。
"""

import json
import logging
import os
import time
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Callable

from hsi.frame import HsiFrame, validate_response
from seed import SENSOR_CODE_BY_DRIVER
from storage import (
    MISSING,
    collect_time_now,
    engineering_value,
    raw_value_for_binding,
)

LOGGER_NAME = "hsi"
RETRY_DELAY_S = 0.1
# 现场实测：内核 dw-apb-uart 的 RS485 自动方向模式（flags=1/3/5/7）在首帧后
# 会把 TX 队列卡死（TIOCOUTQ 持续增长，串口 close 等待约 30 秒），无法持续轮询；
# flags=0（关闭内核 RS485 模式）配合板载自动方向电路可以稳定收发，故作为回退候选。
DEFAULT_RS485_FLAGS = (1, 3, 5, 7, 0)
DEFAULT_REQUIRED_EXCHANGES = 2


class PollError(RuntimeError):
    """轮询器无法启动或无法完成一次有效采集。"""


@dataclass
class SensorStats:
    """单个传感器的轮询统计。"""

    name: str
    expected_fields: int
    cycles: int = 0
    successes: int = 0
    failures: int = 0
    attempt_count: int = 0
    retries: int = 0
    field_mismatches: int = 0
    latencies_ms: list[float] = field(default_factory=list)
    field_counts: dict[int, int] = field(default_factory=dict)
    last_error: str | None = None

    def record_success(self, latency_ms: float, field_count: int, attempts: int) -> None:
        self.successes += 1
        self.latencies_ms.append(latency_ms)
        self.field_counts[field_count] = self.field_counts.get(field_count, 0) + 1
        if field_count != self.expected_fields:
            self.field_mismatches += 1
        self._record_attempts(attempts)

    def record_failure(self, latency_ms: float, attempts: int, error: str) -> None:
        self.failures += 1
        self.last_error = error
        self._record_attempts(attempts)

    def _record_attempts(self, attempts: int) -> None:
        self.attempt_count += attempts
        if attempts > 1:
            self.retries += 1

    def to_summary(self) -> dict[str, Any]:
        cycles = self.cycles
        latencies = self.latencies_ms
        return {
            "cycles": cycles,
            "successes": self.successes,
            "failures": self.failures,
            "success_rate": round(self.successes / cycles, 4) if cycles else 0.0,
            "attempts": self.attempt_count,
            "retries": self.retries,
            "latency_ms_avg": round(sum(latencies) / len(latencies), 2) if latencies else None,
            "latency_ms_max": round(max(latencies), 2) if latencies else None,
            "expected_fields": self.expected_fields,
            "field_counts": {str(k): v for k, v in sorted(self.field_counts.items())},
            "field_mismatches": self.field_mismatches,
            "last_error": self.last_error,
        }


class JsonlWriter:
    """逐行写入 JSONL，每次写入立即 flush，便于运行中查看。"""

    def __init__(self, path: str) -> None:
        self.path = os.path.expanduser(path)
        parent = os.path.dirname(self.path)
        if parent:
            os.makedirs(parent, exist_ok=True)
        self._file = open(self.path, "w", encoding="utf-8")
        self.line_count = 0

    def write(self, record: dict[str, Any]) -> None:
        line = json.dumps(record, ensure_ascii=False, separators=(",", ":"))
        self._file.write(line + "\n")
        self._file.flush()
        self.line_count += 1

    def close(self) -> None:
        self._file.close()


def now_timestamp() -> str:
    """本地时区的 ISO 8601 时间戳（毫秒精度）。"""
    return datetime.now().astimezone().isoformat(timespec="milliseconds")


def read_rss_kb() -> int | None:
    """读取当前进程 RSS（Linux /proc）；其他平台返回 None。"""
    try:
        with open("/proc/self/status", encoding="utf-8") as status_file:
            for line in status_file:
                if line.startswith("VmRSS:"):
                    return int(line.split()[1])
    except OSError:
        return None
    return None


def poll_sensor_once(
    transport: Any,
    driver: Any,
    *,
    timeout: float = 1.0,
    retries: int = 1,
    logger: logging.Logger | None = None,
) -> dict[str, Any]:
    """对单个传感器执行一次测量，失败时按 ``retries`` 重试。

    返回字典包含 ``ok``、``attempts``、``latency_ms``，成功时还有
    ``fields``/``quality``/``format``/``request_hex``/``response_hex``。
    """
    if retries < 0:
        raise ValueError("retries 不能为负数")
    log = logger or logging.getLogger(LOGGER_NAME)
    request = driver.build_measure_request()
    last_error: str | None = None
    started_all = time.monotonic()

    for attempt in range(1, retries + 2):
        started = time.monotonic()
        try:
            frame: HsiFrame = transport.transact(request, timeout=timeout)
            validate_response(frame, 3)
            parsed = driver.parse_measure(frame)
            latency_ms = (time.monotonic() - started) * 1000.0
            field_count = len(parsed["fields"])
            log.info(
                "sensor=%s address=0x%02X msg=3 attempt=%d tx=%s rx=%s ok latency_ms=%.2f fields=%d",
                driver.name,
                driver.address,
                attempt,
                request.hex(),
                frame.raw.hex(),
                latency_ms,
                field_count,
            )
            log.info(
                "sensor=%s msg=3 parsed format=%s quality=%d fields=%s",
                driver.name,
                parsed.get("format"),
                parsed.get("quality", 0),
                json.dumps(parsed["fields"], ensure_ascii=False, separators=(",", ":")),
            )
            return {
                "ok": True,
                "attempts": attempt,
                "latency_ms": round(latency_ms, 2),
                "total_latency_ms": round((time.monotonic() - started_all) * 1000.0, 2),
                "fields": parsed["fields"],
                "raw_fields": parsed.get("raw_fields", []),
                "quality": parsed.get("quality", 0),
                "format": parsed.get("format"),
                "request_hex": request.hex(),
                "response_hex": frame.raw.hex(),
            }
        except Exception as exc:  # 串口超时、帧错误、解析错误都按本次失败处理
            latency_ms = (time.monotonic() - started) * 1000.0
            last_error = f"{type(exc).__name__}: {exc}"
            log.warning(
                "sensor=%s address=0x%02X msg=3 attempt=%d tx=%s failed latency_ms=%.2f error=%s",
                driver.name,
                driver.address,
                attempt,
                request.hex(),
                latency_ms,
                last_error,
            )
            if attempt <= retries:
                time.sleep(RETRY_DELAY_S)

    return {
        "ok": False,
        "attempts": retries + 1,
        "latency_ms": round((time.monotonic() - started_all) * 1000.0, 2),
        "error": last_error,
        "request_hex": request.hex(),
        "response_hex": None,
    }


def discover_rs485_flags(
    transport: Any,
    drivers: list[Any],
    *,
    candidates: tuple[int, ...] = DEFAULT_RS485_FLAGS,
    timeout: float = 1.0,
    attempts_per_flag: int = DEFAULT_REQUIRED_EXCHANGES,
    logger: logging.Logger | None = None,
    reopen: Callable[[], None] | None = None,
    require_all: bool = False,
    responsive_out: set | None = None,
) -> int:
    """依次尝试候选 RS485 flags，确认链路可持续通信后返回可用 flags。

    默认宽松模式：只要**至少一个**传感器连续成功完成 ``attempts_per_flag`` 次
    msg=1 状态查询，该 flags 即视为可用；无应答的传感器只 WARN，由调用方在后续
    轮询中跳过，不阻止启动。``require_all=True`` 时恢复严格校验：所有传入的
    传感器都必须应答，否则继续尝试下一候选（全部失败则抛 ``PollError``）。

    每个传感器必须连续成功，避免把“首帧能通、随后 TX 卡死”的 flags 误判为可用。
    候选失败且还有后续候选时，通过 ``reopen`` 重新打开串口，因为部分内核的
    RS485 模式一旦卡死，只能重开端口恢复。``responsive_out`` 会写入本次选用
    flags 下实际应答的传感器名集合，供调用方过滤轮询列表。
    """
    log = logger or logging.getLogger(LOGGER_NAME)
    candidates = tuple(candidates)
    driver_names = [driver.name for driver in drivers]
    for index, flags in enumerate(candidates):
        transport.set_rs485_flags(flags)
        log.info(
            "RS485 探测 flags=%d（%s模式，要求每个应答传感器连续 %d 次状态应答）",
            flags,
            "严格" if require_all else "宽松",
            attempts_per_flag,
        )
        responsive: set[str] = set()
        candidate_ok = True
        for driver in drivers:
            sensor_ok = True
            last_error = None
            for attempt in range(1, attempts_per_flag + 1):
                try:
                    frame = transport.transact(driver.build_status_request(), timeout=timeout)
                    validate_response(frame, 1)
                    status = driver.parse_status(frame)
                    log.info(
                        "RS485 flags=%d sensor=%s msg=1 应答成功 attempt=%d/%d rx=%s "
                        "status_lo=%s status_hi=%s valid=%s raw=%s",
                        flags,
                        driver.name,
                        attempt,
                        attempts_per_flag,
                        frame.raw.hex(),
                        status.get("status_code_lo", status["status_byte"]),
                        status.get("status_code_hi", 0),
                        status["valid"],
                        status["raw_data_hex"],
                    )
                except Exception as exc:
                    sensor_ok = False
                    last_error = f"{type(exc).__name__}: {exc}"
                    log.info(
                        "RS485 flags=%d sensor=%s msg=1 失败 attempt=%d/%d error=%s",
                        flags,
                        driver.name,
                        attempt,
                        attempts_per_flag,
                        last_error,
                    )
                    break
                if attempt < attempts_per_flag:
                    time.sleep(RETRY_DELAY_S)
            if sensor_ok:
                responsive.add(driver.name)
                continue
            log.warning(
                "RS485 flags=%d sensor=%s 无法连续应答（最后错误: %s）",
                flags,
                driver.name,
                last_error,
            )
            if require_all:
                candidate_ok = False
                break
            log.warning(
                "宽松模式：sensor=%s 本轮不可用，后续轮询将跳过它", driver.name
            )
        if require_all:
            candidate_ok = candidate_ok and responsive == set(driver_names)
        else:
            candidate_ok = bool(responsive)
        if candidate_ok:
            missing = [name for name in driver_names if name not in responsive]
            if missing:
                log.warning(
                    "RS485 使用 flags=%d（缺失传感器 %s 将在轮询中跳过）",
                    flags,
                    missing,
                )
            else:
                log.info("RS485 使用 flags=%d（全部传感器应答）", flags)
            if responsive_out is not None:
                responsive_out.update(responsive)
            return flags
        if index < len(candidates) - 1 and reopen is not None:
            log.warning(
                "RS485 flags=%d 未通过持续通信校验，重新打开串口后尝试下一候选", flags
            )
            try:
                reopen()
            except Exception as exc:
                raise PollError(f"重新打开串口失败: {exc}") from exc
    raise PollError(
        f"所有候选 RS485 flags 均无法连通: {candidates}"
        f"（严格模式要求全部传感器应答：{require_all}）"
    )


def build_channel_entries(
    result: dict[str, Any],
    bindings: Any,
    *,
    logger: logging.Logger | None = None,
    warned_fields: set | None = None,
) -> list[dict[str, Any]]:
    """把一次成功的传感器解析结果展开为通道级 JSONL 条目。

    ``channel_id`` 来自启动时按 ``sensor_code + channel_code`` 的 DB 解析结果；
    未解析到 id 的通道仍保留条目（``channel_id`` 为 null），但不会写入 raw。
    原始值按字段名关联，工程值按通道配置的 ``scale``/``value_offset`` 计算，
    与落库使用同一口径；当前格式不存在的字段会被跳过并只记录一次 INFO。
    """

    raw_fields = result.get("raw_fields") or []
    raw_by_field = result.get("raw_by_field")
    if raw_by_field is not None:
        raw_by_field = {str(k): int(v) for k, v in raw_by_field.items()}
    log = logger or logging.getLogger(LOGGER_NAME)
    fmt = result.get("format") or "?"
    sensor = result.get("sensor") or result.get("sensor_code") or "?"
    entries = []
    for binding in bindings:
        if not binding.enabled:
            continue
        raw = raw_value_for_binding(binding, raw_by_field, raw_fields)
        if raw is MISSING:
            key = ("jsonl-absent-field", sensor, fmt, binding.field)
            if warned_fields is not None:
                if key in warned_fields:
                    continue
                warned_fields.add(key)
            log.info(
                "sensor=%s format=%s 通道字段 %s 不存在，JSONL 条目已跳过",
                sensor,
                fmt,
                binding.field,
            )
            continue
        entries.append(
            {
                "channel_id": binding.channel_id,
                "channel_code": binding.channel_code,
                "field": binding.field,
                "value": engineering_value(raw, binding),
                "raw_value": int(raw),
            }
        )
    return entries


def _warn_unresolved_channels(
    logger: logging.Logger,
    sensor_name: str,
    entries: list[dict[str, Any]],
    warned_missing: set | None,
) -> None:
    missing = [entry["channel_code"] for entry in entries if entry["channel_id"] is None]
    if not missing:
        return
    key = (sensor_name, tuple(missing))
    if warned_missing is not None:
        if key in warned_missing:
            return
        warned_missing.add(key)
    logger.warning(
        "sensor=%s 以下通道未解析到 channel_id，JSONL 仍输出但跳过 raw 写入: %s",
        sensor_name,
        missing,
    )


def poll_cycle(
    transport: Any,
    drivers: list[Any],
    stats: dict[str, SensorStats],
    *,
    writer: JsonlWriter | None,
    cycle: int,
    timeout: float,
    retries: int,
    logger: logging.Logger,
    channel_context: Any | None = None,
    warned_missing: set | None = None,
    warned_fields: set | None = None,
) -> list[dict[str, Any]]:
    """顺序访问所有传感器，成功后写一行 JSONL，并返回可落库的记录。"""

    records: list[dict[str, Any]] = []
    for driver in drivers:
        result = poll_sensor_once(
            transport, driver, timeout=timeout, retries=retries, logger=logger
        )
        sensor_stats = stats[driver.name]
        sensor_stats.cycles += 1
        if result["ok"]:
            sensor_stats.record_success(
                result["latency_ms"], len(result["fields"]), result["attempts"]
            )
            sensor_code = SENSOR_CODE_BY_DRIVER.get(driver.name)
            if channel_context is not None and sensor_code is not None:
                bindings = channel_context.bindings_for(sensor_code)
                device_id = channel_context.device_id
            else:
                bindings = ()
                device_id = None
            entries = build_channel_entries(
                result,
                bindings,
                logger=logger,
                warned_fields=warned_fields,
            )
            _warn_unresolved_channels(logger, driver.name, entries, warned_missing)
            record = {
                "timestamp": now_timestamp(),
                "sensor": driver.name,
                "address": driver.address,
                "sensor_code": sensor_code,
                "device_id": device_id,
                "fields": result["fields"],
                "raw": result.get("raw_fields", []),
                "channels": entries,
                "quality": result["quality"],
                "latency_ms": result["latency_ms"],
                "attempts": result["attempts"],
                "format": result["format"],
                "cycle": cycle,
            }
            if writer is not None:
                writer.write(record)
            records.append(
                {
                    **record,
                    "ok": True,
                    "raw_fields": result.get("raw_fields", []),
                }
            )
        else:
            sensor_stats.record_failure(
                result["latency_ms"], result["attempts"], result["error"]
            )
            logger.warning(
                "cycle=%d sensor=%s 采集失败 attempts=%d error=%s",
                cycle,
                driver.name,
                result["attempts"],
                result["error"],
            )
    return records


def _sleep_until(deadline: float, should_stop: Callable[[], bool] | None) -> None:
    while True:
        if should_stop and should_stop():
            return
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            return
        time.sleep(min(remaining, 0.2))


def run_loop(
    transport: Any,
    drivers: list[Any],
    *,
    writer: JsonlWriter | None = None,
    interval_s: float = 5.0,
    duration_s: float | None = None,
    timeout: float = 1.0,
    retries: int = 1,
    logger: logging.Logger | None = None,
    should_stop: Callable[[], bool] | None = None,
    storage: Any | None = None,
    channel_context: Any | None = None,
    heartbeat_every: int = 5,
    db_manager: Any | None = None,
    partition_every: int = 60,
) -> dict[str, SensorStats]:
    """按固定间隔循环轮询，直到达到 duration 或被 should_stop 终止。

    传入 ``db_manager`` 时，每轮先按重连节奏确保 DB 可用并使用其最新的通道映射
    （DB 恢复后 JSONL 里的 ``channel_id`` 会自动补齐），再把 raw 行、设备状态与
    心跳交给它写入；DB 不可用只记录日志并继续轮询（首期接受丢数据，不做本地
    spool）。``heartbeat_every`` 表示每多少轮写一次心跳，首轮总是写。

    传入 ``storage`` 时不维护连接状态，写入异常由本函数记录后继续下一轮；新代码
    应优先使用 ``db_manager``。
    """
    if interval_s <= 0:
        raise ValueError("interval_s 必须大于 0")
    log = logger or logging.getLogger(LOGGER_NAME)
    stats = {
        driver.name: SensorStats(driver.name, driver.field_count)
        for driver in drivers
    }
    warned_missing: set = set()
    warned_fields: set = set()
    started = time.monotonic()
    next_at = started
    cycle = 0

    while True:
        if should_stop and should_stop():
            log.info("收到停止信号，退出轮询循环")
            break
        if duration_s is not None and time.monotonic() - started >= duration_s:
            log.info("达到运行时长 %.1f 秒，退出轮询循环", duration_s)
            break

        cycle += 1
        log.info("========== 轮询 cycle=%d 开始 ==========", cycle)
        context = channel_context
        if db_manager is not None:
            # 先按重连节奏尝试恢复 DB，再用最新映射输出 JSONL。
            db_manager.ensure_ready()
            context = db_manager.context
        records = poll_cycle(
            transport,
            drivers,
            stats,
            writer=writer,
            cycle=cycle,
            timeout=timeout,
            retries=retries,
            logger=log,
            channel_context=context,
            warned_missing=warned_missing,
            warned_fields=warned_fields,
        )
        if db_manager is not None:
            db_manager.write_cycle(records, collect_time_now(), cycle=cycle)
            if heartbeat_every > 0 and (cycle == 1 or cycle % heartbeat_every == 0):
                db_manager.write_heartbeat()
            if partition_every > 0 and cycle % partition_every == 0:
                # 分区预创建失败只告警，不中断轮询。
                try:
                    db_manager.ensure_partitions()
                except Exception as exc:  # 防御性兜底：任何维护错误都不得中断采集
                    log.warning(
                        "cycle=%d 分区预创建失败（继续轮询）: %s: %s",
                        cycle,
                        type(exc).__name__,
                        exc,
                    )
        elif storage is not None:
            try:
                storage.write_cycle(records, collect_time_now(), cycle=cycle)
            except Exception as exc:
                log.warning(
                    "cycle=%d DB 写入失败，按首期策略丢弃本批数据并继续轮询: %s: %s",
                    cycle,
                    type(exc).__name__,
                    exc,
                )
            if heartbeat_every > 0 and (cycle == 1 or cycle % heartbeat_every == 0):
                try:
                    storage.write_heartbeat()
                except Exception as exc:
                    log.warning(
                        "cycle=%d 心跳写入失败（继续轮询）: %s: %s",
                        cycle,
                        type(exc).__name__,
                        exc,
                    )

        next_at += interval_s
        now = time.monotonic()
        if next_at <= now:
            log.warning(
                "cycle=%d 耗时超过轮询间隔 %.2f 秒，立即开始下一周期", cycle, interval_s
            )
            next_at = now
        _sleep_until(next_at, should_stop)

    elapsed = time.monotonic() - started
    log.info("轮询结束 cycles=%d elapsed_s=%.2f", cycle, elapsed)
    return stats


def summarize(
    stats: dict[str, SensorStats],
    *,
    flags: int | None,
    duration_s: float,
    rss_kb: int | None,
    jsonl_lines: int,
) -> dict[str, Any]:
    """生成板端运行摘要。"""
    return {
        "rs485_flags": flags,
        "duration_s": round(duration_s, 2),
        "poll_cycles": max((s.cycles for s in stats.values()), default=0),
        "jsonl_lines": jsonl_lines,
        "rss_kb": rss_kb,
        "sensors": {name: sensor.to_summary() for name, sensor in stats.items()},
    }
