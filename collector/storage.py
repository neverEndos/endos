"""采集数据 PostgreSQL 落库。

落库目标是设计文档 §5.8/§5.13/§5.14 的三张表：

- ``collector_sample_raw``：每通道一行，``(channel_id, collect_time)`` 冲突时忽略；
- ``collector_device_status``：每个轮询周期更新在线/失败计数与最近一次耗时；
- ``collector_heartbeat``：每 N 轮写一次运行心跳。

通道 id 不硬编码：启动时按 ``sensor_code + channel_code`` 从 DB 解析（种子由
``seed.py`` 保证存在）。DB 不可用时不 spool、不退出，由调用方记录错误后继续轮询。
"""

import json
import logging
import socket
import time
from dataclasses import dataclass, replace
from datetime import datetime, timedelta, timezone
from typing import Any, Callable, Iterable

import partitions as partition_maintenance
from seed import (
    CHANNEL_SEEDS,
    COLLECTOR_VERSION,
    DEVICE_CODE,
    SENSOR_CODE_BY_DRIVER,
    SENSOR_SEED_BY_CODE,
    db_settings_from_env,
    seed_all,
)

LOGGER_NAME = "hsi"
DEFAULT_DB_RETRY_INTERVAL_S = 60.0
SHANGHAI = timezone(timedelta(hours=8), name="Asia/Shanghai")
DEVICE_TOTAL = 1
DEVICE_FAILURE_THRESHOLD = 3
DRIVER_SENSOR_CODE = SENSOR_CODE_BY_DRIVER
_HOSTNAME = socket.gethostname()[:40]
_PROCESS_STARTED_AT = datetime.now(SHANGHAI).strftime("%Y%m%dT%H%M%S")
INSTANCE_ID = f"{_HOSTNAME}-{_PROCESS_STARTED_AT}"


def collect_time_now() -> datetime:
    """Asia/Shanghai 本地时间，毫秒精度、无时区（对应 timestamp(3)）。"""

    now = datetime.now(SHANGHAI)
    return now.replace(tzinfo=None, microsecond=(now.microsecond // 1000) * 1000)


def mask_settings(settings: dict) -> dict:
    masked = dict(settings)
    masked.pop("password", None)
    return masked


@dataclass(frozen=True)
class ChannelBinding:
    """一个通道的解析结果：种子字段映射 + 启动时从 DB 解析出的配置与 id。

    ``scale`` / ``value_offset`` / ``unit`` / ``enabled`` 来自 ``collector_channel``
    列；``field`` 是解析器返回的字段名，``field_index`` 仅作为无字段名映射时的
    兼容回退。工程值统一按 ``raw * scale + value_offset`` 计算。
    """

    sensor_code: str
    channel_code: str
    field: str
    field_index: int
    address: int
    channel_id: int | None = None
    sensor_id: int | None = None
    device_id: int | None = None
    scale: float = 1.0
    value_offset: float = 0.0
    unit: str | None = None
    enabled: bool = True

    @property
    def key(self) -> tuple[str, str]:
        return (self.sensor_code, self.channel_code)


@dataclass(frozen=True)
class DeviceContext:
    """设备与通道映射上下文。``resolved=False`` 表示 DB 不可用或种子缺失。"""

    device_id: int | None
    bindings: tuple[ChannelBinding, ...]
    resolved: bool
    poll_interval_ms: int | None = None
    rs485_flags: int | None = None
    active_sensor_codes: tuple[str, ...] | None = None

    def bindings_for(self, sensor_code: str) -> tuple[ChannelBinding, ...]:
        return tuple(b for b in self.bindings if b.sensor_code == sensor_code)

    def missing_bindings(self) -> tuple[ChannelBinding, ...]:
        return tuple(b for b in self.bindings if b.channel_id is None)

    def sensor_enabled(self, sensor_code: str) -> bool:
        """未从 DB 解析出传感器清单时（DB 不可用）视为全部启用。"""

        return (
            self.active_sensor_codes is None
            or sensor_code in self.active_sensor_codes
        )


def seed_bindings() -> tuple[ChannelBinding, ...]:
    """仅根据种子构造绑定（id 全为 None），用于 DB 不可用时仍输出 JSONL。"""

    bindings = []
    for channel in CHANNEL_SEEDS:
        sensor = SENSOR_SEED_BY_CODE[channel.sensor_code]
        bindings.append(
            ChannelBinding(
                sensor_code=channel.sensor_code,
                channel_code=channel.channel_code,
                field=channel.field,
                field_index=channel.field_index,
                address=sensor.address,
                scale=channel.scale,
                value_offset=0.0,
                unit=channel.unit,
            )
        )
    return tuple(bindings)


def unresolved_context() -> DeviceContext:
    return DeviceContext(device_id=None, bindings=seed_bindings(), resolved=False)


def _parse_config(config: Any) -> dict:
    if isinstance(config, str):
        try:
            config = json.loads(config)
        except ValueError:
            return {}
    return config if isinstance(config, dict) else {}


def _field_index_from_config(config: Any, default: int) -> int:
    parsed = _parse_config(config)
    value = parsed.get("fieldIndex")
    if isinstance(value, int) and value >= 1:
        return value
    return default


def _field_name_from_config(config: Any, default: str) -> str:
    parsed = _parse_config(config)
    value = parsed.get("field")
    if isinstance(value, str) and value:
        return value
    return default


def _number_from_config(config: Any, key: str, default: float) -> float:
    parsed = _parse_config(config)
    value = parsed.get(key)
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return float(value)
    return default


def _rs485_flags_from_config(config: Any) -> int | None:
    parsed = _parse_config(config)
    value = parsed.get("rs485Flags")
    if isinstance(value, int) and not isinstance(value, bool) and value >= 0:
        return value
    return None


def context_from_rows(
    device_id: int | None,
    channel_rows: Iterable[tuple],
    *,
    poll_interval_ms: int | None = None,
    rs485_flags: int | None = None,
    active_sensor_codes: tuple[str, ...] | None = None,
) -> DeviceContext:
    """把 DB 查询结果合并到种子绑定。

    ``channel_rows`` 每项为 ``(sensor_code, channel_code, sensor_id, channel_id,
    device_id, channel_config, scale, value_offset, unit, enabled)``；也兼容旧的
    6 元组（此时 scale/unit 沿用种子值）。``enabled=0`` 的通道会被移除，不参与
    采集。只更新种子中存在的通道；DB 中多出来的通道不会被采集器写入。
    """

    merged = {binding.key: binding for binding in seed_bindings()}
    for row in channel_rows:
        if len(row) >= 10:
            (
                sensor_code,
                channel_code,
                sensor_id,
                channel_id,
                row_device_id,
                config,
                scale,
                value_offset,
                unit,
                enabled,
            ) = row[:10]
        else:
            sensor_code, channel_code, sensor_id, channel_id, row_device_id, config = row[:6]
            scale = None
            value_offset = None
            unit = None
            enabled = 1
        key = (sensor_code, channel_code)
        current = merged.get(key)
        if current is None:
            continue
        if not int(enabled):
            merged.pop(key, None)
            continue
        merged[key] = replace(
            current,
            channel_id=int(channel_id),
            sensor_id=int(sensor_id),
            device_id=int(row_device_id) if row_device_id is not None else device_id,
            field=_field_name_from_config(config, current.field),
            field_index=_field_index_from_config(config, current.field_index),
            scale=(
                float(scale)
                if scale is not None
                else _number_from_config(config, "scale", current.scale)
            ),
            value_offset=(
                float(value_offset)
                if value_offset is not None
                else _number_from_config(config, "offset", current.value_offset)
            ),
            unit=unit if unit is not None else current.unit,
            enabled=True,
        )
    return DeviceContext(
        device_id=device_id,
        bindings=tuple(merged.values()),
        resolved=device_id is not None,
        poll_interval_ms=poll_interval_ms,
        rs485_flags=rs485_flags,
        active_sensor_codes=active_sensor_codes,
    )


MISSING = object()


def raw_value_for_binding(
    binding: ChannelBinding,
    raw_by_field: dict[str, int] | None,
    raw_fields: Iterable[int],
) -> Any:
    """按字段名取原始值；缺少字段名映射时退回 ``field_index``（1 起始）。

    返回 ``MISSING`` 表示当前应答格式中不存在该字段，调用方应跳过该通道，
    不能用其他位置的值顶替。
    """

    if raw_by_field is not None:
        return raw_by_field.get(binding.field, MISSING)
    index = binding.field_index - 1
    raw_fields = list(raw_fields)
    if index < 0 or index >= len(raw_fields):
        return MISSING
    return raw_fields[index]


def engineering_value(raw: Any, binding: ChannelBinding) -> float | None:
    """按通道配置计算工程值：``raw * scale + value_offset``。"""

    if raw is MISSING or raw is None:
        return None
    try:
        value = float(raw) * float(binding.scale) + float(binding.value_offset)
    except (TypeError, ValueError):
        return None
    value = round(value, 10)
    return 0.0 if value == 0 else value


def _info_once(
    logger: logging.Logger,
    warned: set | None,
    key: tuple,
    message: str,
    *args: Any,
) -> None:
    if warned is not None:
        if key in warned:
            return
        warned.add(key)
    logger.info(message, *args)


def build_sample_rows(
    bindings: Iterable[ChannelBinding],
    results: Iterable[dict[str, Any]],
    collect_time: datetime,
    device_id: int | None,
    *,
    logger: logging.Logger | None = None,
    warned_fields: set | None = None,
) -> list[tuple[int, int, datetime, Any, int, int]]:
    """把一轮解析结果转换为 ``collector_sample_raw`` 行。

    原始值与工程值都按**字段名**关联：``raw_by_field`` 由解析器给出当前应答
    格式的字段集合，``value`` 按通道配置的 ``scale``/``value_offset`` 计算。
    当前格式中不存在的通道字段、以及当前格式中存在但未配置通道的字段都会被
    跳过；后者每个字段只记录一次 INFO，避免每轮刷屏。
    """

    log = logger or logging.getLogger(LOGGER_NAME)
    rows: list[tuple[int, int, datetime, Any, int, int]] = []
    for result in results:
        if not result.get("ok"):
            continue
        sensor_code = result.get("sensor_code") or DRIVER_SENSOR_CODE.get(
            result.get("sensor", "")
        )
        raw_fields = result.get("raw_fields") or []
        raw_by_field = result.get("raw_by_field")
        if raw_by_field is not None:
            raw_by_field = {str(k): int(v) for k, v in raw_by_field.items()}
        quality = int(result.get("quality") or 0)
        sensor_bindings = [b for b in bindings if b.sensor_code == sensor_code]
        configured_fields = {b.field for b in sensor_bindings}
        fmt = result.get("format") or "?"
        if raw_by_field is not None:
            for name in raw_by_field:
                if name not in configured_fields:
                    _info_once(
                        log,
                        warned_fields,
                        ("unconfigured-field", sensor_code, fmt, name),
                        "sensor=%s format=%s 字段 %s 未配置通道，已跳过（不写 raw）",
                        sensor_code,
                        fmt,
                        name,
                    )
        for binding in sensor_bindings:
            if binding.channel_id is None or not binding.enabled:
                continue
            raw = raw_value_for_binding(binding, raw_by_field, raw_fields)
            if raw is MISSING:
                _info_once(
                    log,
                    warned_fields,
                    ("absent-field", sensor_code, fmt, binding.field),
                    "sensor=%s format=%s 通道字段 %s 不存在，已跳过（不写 raw）",
                    sensor_code,
                    fmt,
                    binding.field,
                )
                continue
            row_device_id = device_id if device_id is not None else binding.device_id
            if row_device_id is None:
                continue
            rows.append(
                (
                    int(binding.channel_id),
                    int(row_device_id),
                    collect_time,
                    engineering_value(raw, binding),
                    int(raw),
                    quality,
                )
            )
    rows.sort(key=lambda item: item[0])
    return rows


@dataclass(frozen=True)
class DeviceState:
    """一个轮询周期后的设备状态（纯计算结果）。"""

    online: int
    consecutive_failures: int
    last_online_at: datetime | None
    last_offline_at: datetime | None
    last_success_at: datetime | None
    last_error: str | None
    last_latency_ms: int | None


def next_device_state(
    previous_online: int,
    previous_failures: int,
    *,
    success: bool,
    latency_ms: int | None,
    error: str | None,
    now: datetime,
    threshold: int = DEVICE_FAILURE_THRESHOLD,
) -> DeviceState:
    """按“连续失败达到阈值才离线”的规则推进设备状态。"""

    if success:
        return DeviceState(
            online=1,
            consecutive_failures=0,
            last_online_at=now,
            last_offline_at=None,
            last_success_at=now,
            last_error=None,
            last_latency_ms=latency_ms,
        )

    failures = previous_failures + 1
    offline = failures >= threshold
    return DeviceState(
        online=0 if offline else previous_online,
        consecutive_failures=failures,
        last_online_at=None,
        last_offline_at=now if offline and previous_online == 1 else None,
        last_success_at=None,
        last_error=error,
        last_latency_ms=None,
    )


def cycle_outcome(results: Iterable[dict[str, Any]]) -> tuple[bool, int | None, str | None]:
    """汇总一轮结果：任一传感器成功即视为设备本轮成功。"""

    successes = [r for r in results if r.get("ok")]
    failures = [r for r in results if not r.get("ok")]
    latency_ms = (
        int(max(float(r.get("latency_ms") or 0) for r in successes)) if successes else None
    )
    error = None
    if failures:
        error = "; ".join(
            f"{r.get('sensor')}: {r.get('error') or 'unknown'}" for r in failures
        )[:500]
    return bool(successes), latency_ms, error


def build_heartbeat_row(
    instance_id: str,
    heartbeat_time: datetime,
    online: int,
    write_lag_ms: int | None,
    *,
    device_total: int = DEVICE_TOTAL,
    collector_version: str = COLLECTOR_VERSION,
) -> dict[str, Any]:
    """构造 ``collector_heartbeat`` 行；资源字段留空（后续任务补充）。"""

    return {
        "instance_id": instance_id,
        "heartbeat_time": heartbeat_time,
        "status": "running",
        "write_lag_ms": write_lag_ms,
        "device_total": device_total,
        "device_online": online,
        "collector_version": collector_version,
    }


_SAMPLE_INSERT_SQL = """
INSERT INTO public.collector_sample_raw (
    channel_id, device_id, collect_time, value, raw_value, quality
) VALUES (%s, %s, %s, %s, %s, %s)
ON CONFLICT (channel_id, collect_time) DO NOTHING
"""

_STATUS_SUCCESS_SQL = """
INSERT INTO public.collector_device_status (
    device_id, online, last_online_at, last_offline_at, last_success_at,
    last_error, consecutive_failures, last_latency_ms, update_time
) VALUES (%s, 1, %s, NULL, %s, NULL, 0, %s, %s)
ON CONFLICT (device_id) DO UPDATE SET
    online = 1,
    last_online_at = CASE
        WHEN collector_device_status.online = 0 THEN EXCLUDED.last_online_at
        ELSE collector_device_status.last_online_at
    END,
    last_success_at = EXCLUDED.last_success_at,
    last_error = NULL,
    consecutive_failures = 0,
    last_latency_ms = EXCLUDED.last_latency_ms,
    update_time = EXCLUDED.update_time
"""

_STATUS_FAILURE_SQL = """
INSERT INTO public.collector_device_status (
    device_id, online, last_online_at, last_offline_at, last_success_at,
    last_error, consecutive_failures, last_latency_ms, update_time
) VALUES (%s, %s, NULL, %s, NULL, %s, %s, NULL, %s)
ON CONFLICT (device_id) DO UPDATE SET
    online = EXCLUDED.online,
    last_offline_at = CASE
        WHEN collector_device_status.online = 1 AND EXCLUDED.online = 0
            THEN EXCLUDED.last_offline_at
        ELSE collector_device_status.last_offline_at
    END,
    last_error = EXCLUDED.last_error,
    consecutive_failures = EXCLUDED.consecutive_failures,
    update_time = EXCLUDED.update_time
"""

_HEARTBEAT_INSERT_SQL = """
INSERT INTO public.collector_heartbeat (
    id, instance_id, heartbeat_time, status, write_lag_ms,
    device_total, device_online, collector_version
) VALUES (
    nextval('public.collector_heartbeat_seq'), %s, %s, %s, %s, %s, %s, %s
) RETURNING id
"""


class PgStorage:
    """psycopg2 落库封装；连接断开时下次写入自动重连。"""

    def __init__(
        self, settings: dict | None = None, logger: logging.Logger | None = None
    ) -> None:
        self.settings = settings or db_settings_from_env()
        self.logger = logger or logging.getLogger(LOGGER_NAME)
        self.context = unresolved_context()
        self._conn = None
        self._online = 0
        self._failures = 0
        self._last_write_lag_ms: int | None = None
        self._warned_fields: set = set()

    # ------------------------------------------------------------------ 连接
    def connect(self) -> None:
        import psycopg2

        self._conn = psycopg2.connect(**self.settings)
        self._conn.autocommit = False

    def close(self) -> None:
        if self._conn is not None:
            try:
                self._conn.close()
            finally:
                self._conn = None

    def _ensure_conn(self) -> None:
        if self._conn is None or getattr(self._conn, "closed", 0):
            self.connect()
            self.logger.info(
                "PostgreSQL 已连接 %s", mask_settings(self.settings)
            )

    def _reset_conn(self) -> None:
        try:
            self.close()
        except Exception:  # 连接已坏时 close 也可能失败
            self._conn = None

    # ------------------------------------------------------------------ 启动解析
    def load_context(self) -> DeviceContext:
        """从 DB 解析设备/通道 id，并读取已存在的设备状态。"""

        self._ensure_conn()
        try:
            with self._conn.cursor() as cur:
                cur.execute(
                    "SELECT id, poll_interval_ms FROM public.collector_device"
                    " WHERE device_code = %s AND deleted = 0",
                    (DEVICE_CODE,),
                )
                row = cur.fetchone()
                device_id = int(row[0]) if row else None
                poll_interval_ms = (
                    int(row[1])
                    if row and row[1] is not None and int(row[1]) > 0
                    else None
                )
                if device_id is None:
                    self.logger.error(
                        "未找到设备种子 device_code=%s，请检查 seed.py 是否执行成功",
                        DEVICE_CODE,
                    )
                    self.context = unresolved_context()
                    return self.context

                # 传感器配置：enabled 过滤 + connection_config.rs485Flags
                cur.execute(
                    """
                    SELECT sensor_code, enabled, connection_config
                    FROM public.collector_sensor
                    WHERE device_id = %s AND deleted = 0
                    ORDER BY sort, id
                    """,
                    (device_id,),
                )
                sensor_rows = cur.fetchall()
                enabled_sensor_codes = tuple(
                    sensor_code
                    for sensor_code, enabled, _ in sensor_rows
                    if int(enabled)
                )
                disabled_sensors = [
                    sensor_code
                    for sensor_code, enabled, _ in sensor_rows
                    if not int(enabled)
                ]
                rs485_flags = None
                for sensor_code, enabled, config in sensor_rows:
                    if not int(enabled):
                        continue
                    configured = _rs485_flags_from_config(config)
                    if configured is not None:
                        rs485_flags = configured
                        break

                cur.execute(
                    """
                    SELECT s.sensor_code, c.channel_code, s.id, c.id,
                           c.device_id, c.channel_config,
                           c.scale, c.value_offset, c.unit, c.enabled
                    FROM public.collector_channel c
                    JOIN public.collector_sensor s ON s.id = c.sensor_id
                    WHERE c.deleted = 0 AND s.deleted = 0 AND c.device_id = %s
                      AND s.enabled = 1 AND c.enabled = 1
                    ORDER BY c.sort, c.id
                    """,
                    (device_id,),
                )
                rows = cur.fetchall()
                self.context = context_from_rows(
                    device_id,
                    rows,
                    poll_interval_ms=poll_interval_ms,
                    rs485_flags=rs485_flags,
                    active_sensor_codes=enabled_sensor_codes,
                )

                cur.execute(
                    """
                    SELECT count(*)
                    FROM public.collector_channel c
                    JOIN public.collector_sensor s ON s.id = c.sensor_id
                    WHERE c.deleted = 0 AND s.deleted = 0 AND c.device_id = %s
                      AND (s.enabled = 0 OR c.enabled = 0)
                    """,
                    (device_id,),
                )
                skipped_channels = int(cur.fetchone()[0])
                if disabled_sensors or skipped_channels:
                    self.logger.info(
                        "已跳过禁用传感器 %s 与禁用通道 %d 个（enabled=0）",
                        disabled_sensors or "无",
                        skipped_channels,
                    )

                cur.execute(
                    "SELECT online, consecutive_failures"
                    " FROM public.collector_device_status WHERE device_id = %s",
                    (device_id,),
                )
                status = cur.fetchone()
                if status:
                    self._online = int(status[0])
                    self._failures = int(status[1])
        except Exception:
            self._reset_conn()
            raise

        missing = self.context.missing_bindings()
        if missing:
            self.logger.warning(
                "有 %d 个通道未在 DB 中解析到 channel_id（这些通道不会写入 raw）: %s",
                len(missing),
                [b.channel_code for b in missing],
            )
        self.logger.info(
            "通道映射解析完成 device_id=%s channels=%d/%d",
            self.context.device_id,
            len(self.context.bindings) - len(missing),
            len(self.context.bindings),
        )
        return self.context

    # ------------------------------------------------------------------ 写入
    def write_cycle(
        self,
        results: Iterable[dict[str, Any]],
        collect_time: datetime | None = None,
        cycle: int | None = None,
    ) -> dict[str, Any]:
        """一个事务内写 raw 行并更新设备状态；异常由调用方记录后继续轮询。"""

        results = list(results)
        collect_time = collect_time or collect_time_now()
        if self.context.device_id is None:
            self.logger.warning(
                "设备 id 未解析（DB 不可用或种子缺失），本轮 %s 个传感器数据不入库",
                len(results),
            )
            return {"written": 0, "skipped": True}

        rows = build_sample_rows(
            self.context.bindings,
            results,
            collect_time,
            self.context.device_id,
            logger=self.logger,
            warned_fields=self._warned_fields,
        )
        success, latency_ms, error = cycle_outcome(results)
        state = next_device_state(
            self._online,
            self._failures,
            success=success,
            latency_ms=latency_ms,
            error=error,
            now=collect_time,
        )

        started = time.monotonic()
        self._ensure_conn()
        try:
            with self._conn:
                with self._conn.cursor() as cur:
                    if success:
                        cur.execute(
                            _STATUS_SUCCESS_SQL,
                            (
                                self.context.device_id,
                                state.last_online_at,
                                state.last_success_at,
                                state.last_latency_ms,
                                collect_time,
                            ),
                        )
                    else:
                        cur.execute(
                            _STATUS_FAILURE_SQL,
                            (
                                self.context.device_id,
                                state.online,
                                state.last_offline_at,
                                state.last_error,
                                state.consecutive_failures,
                                collect_time,
                            ),
                        )
                    if rows:
                        cur.executemany(_SAMPLE_INSERT_SQL, rows)
        except Exception:
            self._reset_conn()
            raise

        self._online = state.online
        self._failures = state.consecutive_failures
        self._last_write_lag_ms = int((time.monotonic() - started) * 1000)
        self.logger.info(
            "DB 写入完成 cycle=%s raw_rows=%d success=%s online=%d failures=%d lag_ms=%d",
            cycle,
            len(rows),
            success,
            state.online,
            state.consecutive_failures,
            self._last_write_lag_ms,
        )
        return {
            "written": len(rows),
            "success": success,
            "online": state.online,
            "consecutive_failures": state.consecutive_failures,
            "write_lag_ms": self._last_write_lag_ms,
        }

    def ensure_partitions(
        self, today=None, lead_days: int | None = None
    ) -> list[str]:
        """确保未来日分区存在；失败向上抛异常，由 DatabaseManager 告警。"""

        self._ensure_conn()
        with self._conn:
            return partition_maintenance.ensure_partitions(
                self._conn,
                today=today,
                lead_days=lead_days,
                logger=self.logger,
            )

    def write_heartbeat(self) -> int | None:
        """插入一行心跳，返回心跳 id；DB 不可用时由调用方处理异常。"""

        if self.context.device_id is None:
            return None
        heartbeat_time = collect_time_now()
        row = build_heartbeat_row(
            INSTANCE_ID, heartbeat_time, self._online, self._last_write_lag_ms
        )
        self._ensure_conn()
        try:
            with self._conn:
                with self._conn.cursor() as cur:
                    cur.execute(
                        _HEARTBEAT_INSERT_SQL,
                        (
                            row["instance_id"],
                            row["heartbeat_time"],
                            row["status"],
                            row["write_lag_ms"],
                            row["device_total"],
                            row["device_online"],
                            row["collector_version"],
                        ),
                    )
                    heartbeat_id = int(cur.fetchone()[0])
        except Exception:
            self._reset_conn()
            raise
        self.logger.info(
            "心跳写入完成 id=%d online=%d lag_ms=%s",
            heartbeat_id,
            row["device_online"],
            row["write_lag_ms"],
        )
        return heartbeat_id


class DatabaseUnavailableError(RuntimeError):
    """一次 DB 初始化尝试未成功（连不上、种子缺失或映射无法解析）。"""


class DatabaseManager:
    """DB 初始化与重连闸门。

    启动与运行中共用同一个状态机：

    - 未就绪时，每次 :meth:`ensure_ready` 最多 ``retry_interval_s`` 秒尝试一次，
      避免每个轮询周期都重复建连；
    - 每次尝试先执行幂等种子（可用 ``seed_enabled=False`` 关闭），再连接并解析
      设备/通道映射；
    - 任一步失败则关闭连接、清空映射并记录（同一错误只告警一次），随后继续轮询
      并输出 ``channel_id`` 为 null 的 JSONL；
    - DB 恢复后自动重新种子、重新解析映射，下一轮起恢复 raw/状态/心跳写入；
    - 运行中写入失败与启动失败走同一状态机，不会出现两套重连逻辑互相打架。

    ``storage_factory``、``seed_func``、``clock`` 均可注入，便于单元测试脱离真实
    数据库与真实时间。
    """

    def __init__(
        self,
        *,
        enabled: bool = True,
        seed_enabled: bool = True,
        retry_interval_s: float = DEFAULT_DB_RETRY_INTERVAL_S,
        settings: dict | None = None,
        logger: logging.Logger | None = None,
        storage_factory: Callable[[], Any] | None = None,
        seed_func: Callable[..., Any] | None = None,
        clock: Callable[[], float] = time.monotonic,
    ) -> None:
        if retry_interval_s < 0:
            raise ValueError("retry_interval_s 不能为负数")
        self.logger = logger or logging.getLogger(LOGGER_NAME)
        self.enabled = enabled
        self.seed_enabled = seed_enabled
        self.retry_interval_s = retry_interval_s
        self.settings = settings if settings is not None else db_settings_from_env()
        self._storage_factory = storage_factory or (
            lambda: PgStorage(settings=self.settings, logger=self.logger)
        )
        self._seed_func = seed_func or seed_all
        self._clock = clock
        self.storage: Any | None = None
        self.context: DeviceContext = unresolved_context()
        self.ready = False
        self._last_attempt: float | None = None
        self._attempts = 0
        self._last_error: str | None = None
        self._last_partition_error: str | None = None

    # ------------------------------------------------------------------ 状态
    def ensure_ready(self) -> bool:
        """按节流节奏尝试初始化；返回当前是否可落库。"""

        if not self.enabled:
            return False
        if self.ready and self.storage is not None:
            return True
        now = self._clock()
        if (
            self._last_attempt is not None
            and now - self._last_attempt < self.retry_interval_s
        ):
            return False
        self._last_attempt = now
        self._attempts += 1
        try:
            self._initialize()
        except Exception as exc:
            self._mark_unavailable(exc)
            return False
        if self._last_error is not None:
            self.logger.info(
                "PostgreSQL 已恢复，自动重新执行种子并解析通道映射（共尝试 %d 次）",
                self._attempts,
            )
            self._last_error = None
        self.ready = True
        # 启动（或 DB 恢复）后预创建未来日分区；失败只告警，不影响落库。
        self.ensure_partitions()
        missing = self.context.missing_bindings()
        self.logger.info(
            "PostgreSQL 落库已启用 device_id=%s channels=%d/%d（DB 尝试 %d 次）",
            self.context.device_id,
            len(self.context.bindings) - len(missing),
            len(self.context.bindings),
            self._attempts,
        )
        return True

    def status(self) -> dict[str, Any]:
        """摘要用状态（不包含密码等敏感信息）。"""

        return {
            "enabled": self.enabled,
            "ready": self.ready,
            "device_id": self.context.device_id,
            "channels_resolved": sum(
                1 for binding in self.context.bindings if binding.channel_id is not None
            ),
            "attempts": self._attempts,
            "retry_interval_s": self.retry_interval_s,
            "last_error": self._last_error,
        }

    def _initialize(self) -> None:
        if self.seed_enabled:
            try:
                self._seed_func(logger=self.logger)
            except Exception as exc:
                # 与原有行为一致：种子失败不阻断采集，继续尝试连接并复用已有配置。
                self.logger.warning(
                    "HSI 配置种子执行失败（继续尝试连接并复用已有配置）: %s: %s",
                    type(exc).__name__,
                    exc,
                )
        storage = self._storage_factory()
        try:
            storage.connect()
            context = storage.load_context()
        except Exception:
            self._safe_close(storage)
            raise
        if context.device_id is None:
            self._safe_close(storage)
            raise DatabaseUnavailableError("设备种子缺失，通道映射未能解析")
        self.storage = storage
        self.context = context

    def _mark_unavailable(self, exc: Exception, reason: str = "PostgreSQL 暂不可用") -> None:
        if self.storage is not None:
            self._safe_close(self.storage)
        self.storage = None
        self.context = unresolved_context()
        self.ready = False
        # 运行中写入失败后同样按重连间隔节流，避免每个轮询周期反复建连。
        self._last_attempt = self._clock()
        error = f"{type(exc).__name__}: {exc}"
        if error == self._last_error:
            # 同一错误周期只告警一次，避免每分钟刷屏；恢复时再记录 INFO。
            return
        self._last_error = error
        self.logger.warning(
            "%s（DB 尝试 %d 次失败），本轮继续仅输出 JSONL（channel_id 为空），"
            "%.0f 秒后自动重试: %s",
            reason,
            self._attempts,
            self.retry_interval_s,
            error,
        )

    def _safe_close(self, storage: Any) -> None:
        try:
            storage.close()
        except Exception:  # 连接已坏时 close 也可能失败
            pass

    def ensure_partitions(self) -> list[str]:
        """维护 ``collector_sample_raw`` 未来日分区；失败只告警不抛异常。

        权限不足（例如采集账号无建表权限）时记录 WARN，并提示运维手工创建；
        同一错误在一个恢复周期内只告警一次，避免每轮刷屏。
        """

        if not self.ready or self.storage is None:
            return []
        handler = getattr(self.storage, "ensure_partitions", None)
        if handler is None:
            return []
        try:
            created = handler()
        except Exception as exc:
            error = f"{type(exc).__name__}: {exc}"
            if error != self._last_partition_error:
                self._last_partition_error = error
                self.logger.warning(
                    "collector_sample_raw 分区预创建失败（采集继续；若为权限不足，"
                    "请运维按 collector_retention_policy.partition_lead_days 手工建分区）: %s",
                    error,
                )
            return []
        self._last_partition_error = None
        return created

    # ------------------------------------------------------------------ 写入
    def write_cycle(
        self,
        results: Iterable[dict[str, Any]],
        collect_time: datetime | None = None,
        cycle: int | None = None,
    ) -> dict[str, Any]:
        """写一轮数据；DB 不可用时返回 skipped，不向上抛异常。"""

        if not self.ensure_ready():
            return {"written": 0, "skipped": True, "ready": False}
        try:
            outcome = self.storage.write_cycle(results, collect_time, cycle=cycle)
        except Exception as exc:
            self._mark_unavailable(exc, reason=f"cycle={cycle} DB 写入失败")
            return {"written": 0, "skipped": True, "ready": False}
        return {**outcome, "ready": True}

    def write_heartbeat(self) -> int | None:
        """写一次心跳；DB 不可用时返回 None，不向上抛异常。"""

        if not self.ensure_ready():
            return None
        try:
            return self.storage.write_heartbeat()
        except Exception as exc:
            self._mark_unavailable(exc, reason="心跳写入失败")
            return None

    def close(self) -> None:
        self.ready = False
        if self.storage is not None:
            self._safe_close(self.storage)
            self.storage = None
