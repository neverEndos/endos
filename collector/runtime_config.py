"""采集器运行参数解析与 DB 配置热加载协调。

本模块不接触串口和真实数据库，便于用假 DB 管理器、假 driver 工厂验证
参数变化与结构变化的不同处理路径。
"""

import logging
import time
from dataclasses import dataclass
from typing import Any, Callable

LOGGER_NAME = "hsi"


def resolve_interval_s(
    cli_interval: float | None,
    poll_interval_ms: int | None,
    default: float = 5.0,
) -> tuple[float, str]:
    """决定轮询间隔：CLI 显式值 > DB ``poll_interval_ms`` > 默认值。"""

    if cli_interval is not None:
        return float(cli_interval), "命令行 --interval"
    if poll_interval_ms and int(poll_interval_ms) > 0:
        return int(poll_interval_ms) / 1000.0, "collector_device.poll_interval_ms"
    return float(default), f"默认 {default}"


def resolve_timeout_s(
    cli_timeout: float | None,
    timeout_ms: int | None,
    default: float = 1.0,
) -> tuple[float, str]:
    """决定请求超时：CLI 显式值 > DB ``timeout_ms`` > 默认值。"""

    if cli_timeout is not None:
        return float(cli_timeout), "命令行 --timeout"
    if timeout_ms and int(timeout_ms) > 0:
        return int(timeout_ms) / 1000.0, "collector_device.timeout_ms"
    return float(default), f"默认 {default}"


def resolve_retries(
    cli_retries: int | None,
    db_retries: int | None,
    default: int = 1,
) -> tuple[int, str]:
    """决定失败重试次数：CLI 显式值 > DB ``retries`` > 默认值。"""

    if cli_retries is not None:
        return int(cli_retries), "命令行 --retries"
    if db_retries is not None and int(db_retries) >= 0:
        return int(db_retries), "collector_device.retries"
    return int(default), f"默认 {default}"


@dataclass(frozen=True)
class RuntimeSettings:
    interval_s: float
    timeout_s: float
    retries: int
    interval_source: str
    timeout_source: str
    retries_source: str


def resolve_runtime_settings(
    context: Any,
    *,
    cli_interval: float | None = None,
    cli_timeout: float | None = None,
    cli_retries: int | None = None,
) -> RuntimeSettings:
    interval_s, interval_source = resolve_interval_s(
        cli_interval, context.poll_interval_ms
    )
    timeout_s, timeout_source = resolve_timeout_s(cli_timeout, context.timeout_ms)
    retries, retries_source = resolve_retries(cli_retries, context.retries)
    return RuntimeSettings(
        interval_s=interval_s,
        timeout_s=timeout_s,
        retries=retries,
        interval_source=interval_source,
        timeout_source=timeout_source,
        retries_source=retries_source,
    )


class ConfigReloader:
    """在轮询周期间应用 DB 配置，不在采集事务中重建串口。

    仅运行参数变化时更新 :attr:`settings`；传感器/通道结构变化时才调用
    ``driver_factory`` 重建 driver 列表。``on_structure_change`` 可用于在同一个
    已打开串口上应用新的 RS485 flags，本类不会调用 open/close/reopen。
    """

    def __init__(
        self,
        *,
        db_manager: Any,
        context: Any,
        drivers: list[Any],
        driver_factory: Callable[[Any], list[Any]],
        cli_interval: float | None = None,
        cli_timeout: float | None = None,
        cli_retries: int | None = None,
        check_interval_s: float | None = None,
        logger: logging.Logger | None = None,
        clock: Callable[[], float] = time.monotonic,
        on_structure_change: Callable[[Any, Any, list[Any]], list[Any] | None]
        | None = None,
    ) -> None:
        if check_interval_s is not None and check_interval_s <= 0:
            raise ValueError("check_interval_s 必须大于 0")
        self.db_manager = db_manager
        self.context = context
        self.drivers = list(drivers)
        self.settings = resolve_runtime_settings(
            context,
            cli_interval=cli_interval,
            cli_timeout=cli_timeout,
            cli_retries=cli_retries,
        )
        self._driver_factory = driver_factory
        self._cli_interval = cli_interval
        self._cli_timeout = cli_timeout
        self._cli_retries = cli_retries
        self._check_interval_s = check_interval_s
        self._on_structure_change = on_structure_change
        self.logger = logger or logging.getLogger(LOGGER_NAME)
        self._clock = clock
        self._last_check = clock()
        self._retry_pending = False

    def _effective_check_interval_s(self) -> float:
        if self._check_interval_s is not None:
            return self._check_interval_s
        return self.settings.interval_s

    def before_cycle(self, *, force: bool = False) -> str:
        """在下一轮采集前同步配置，返回本次应用的变化类型。

        正常热加载采用“DB 准备 → 硬件应用 → DB 提交”两阶段流程。硬件应用失败
        时恢复旧上下文/旧指纹，并在下一轮忽略检查间隔立即重试同一变化。
        """

        if self.db_manager is None:
            return "unsupported"
        snapshot_loader = getattr(self.db_manager, "config_snapshot", None)
        snapshot = snapshot_loader() if snapshot_loader is not None else None
        # 写入发生在上一轮 before_cycle 之后，可能已把 manager.ready 置为 False；
        # 必须读取调用前的实时状态，否则“本次调用内恢复”会被漏判。
        was_ready = bool(self.db_manager.ready)
        ready = self.db_manager.ensure_ready()
        now = self._clock()
        if not ready:
            self._last_check = now
            return "unavailable"
        if not was_ready:
            self._last_check = now
            result = self._adopt(
                "structure", self.db_manager.context, recovered=True
            )
            if result == "error":
                self._retry_pending = True
                self._restore_snapshot(snapshot)
            else:
                self._retry_pending = False
            return result
        if (
            not force
            and not self._retry_pending
            and now - self._last_check < self._effective_check_interval_s()
        ):
            return "none"
        self._last_check = now

        prepare = getattr(self.db_manager, "prepare_config_change", None)
        if prepare is None:
            # 兼容旧参考 DB 管理器；真实 PgStorage 路径始终走两阶段提交。
            kind = self.db_manager.reload_if_changed()
            if kind in ("parameters", "structure"):
                result = self._adopt(kind, self.db_manager.context)
                self._retry_pending = result == "error"
                return result
            self._retry_pending = False
            return kind

        kind = prepare()
        if kind not in ("parameters", "structure"):
            if kind in ("none", "unsupported"):
                self._retry_pending = False
            return kind
        result = self._adopt(kind, self.db_manager.pending_context)
        if result == "error":
            self.db_manager.rollback_config_change()
            self._retry_pending = True
            return result
        # 只有 flags/新增传感器探测等外部应用全部成功后才推进 DB 指纹。
        self.db_manager.commit_config_change()
        self._retry_pending = False
        return result

    def _restore_snapshot(self, snapshot: Any) -> None:
        if snapshot is None:
            return
        self.db_manager.restore_config_snapshot(snapshot)

    def _adopt(self, kind: str, context: Any, *, recovered: bool = False) -> str:
        old_context = self.context
        old_settings = self.settings
        old_drivers = self.drivers
        new_settings = resolve_runtime_settings(
            context,
            cli_interval=self._cli_interval,
            cli_timeout=self._cli_timeout,
            cli_retries=self._cli_retries,
        )
        new_drivers = old_drivers
        if kind == "structure":
            try:
                candidate_drivers = self._driver_factory(context)
                if self._on_structure_change is not None:
                    applied_drivers = self._on_structure_change(
                        old_context, context, candidate_drivers
                    )
                    if applied_drivers is not None:
                        candidate_drivers = list(applied_drivers)
                new_drivers = candidate_drivers
            except Exception as exc:
                self.logger.error(
                    "结构配置应用失败，保留旧上下文、旧 driver 与旧配置指纹，"
                    "下一轮继续重试: %s: %s",
                    type(exc).__name__,
                    exc,
                )
                return "error"

        # 外部应用全部成功后才提交协调器状态；此前任何异常都保持旧对象不变。
        self.context = context
        self.settings = new_settings
        self.drivers = new_drivers
        self._log_parameter_changes(old_settings, new_settings, kind=kind)
        if kind != "structure":
            return kind

        old_names = {driver.name for driver in old_drivers}
        new_names = {driver.name for driver in new_drivers}
        removed = sorted(old_names - new_names)
        added = sorted(new_names - old_names)
        if removed:
            self.logger.warning(
                "配置热加载：传感器已禁用或删除，移出轮询集合：%s", removed
            )
        if added:
            self.logger.info("配置热加载：传感器加入轮询集合：%s", added)
        self.logger.info(
            "配置热加载类型=结构%s：轮询传感器 %s -> %s，通道绑定数 %d -> %d，"
            "rs485Flags %s -> %s（串口保持打开，driver 已重建）",
            "（DB 恢复）" if recovered else "",
            sorted(old_names),
            sorted(new_names),
            len(old_context.bindings),
            len(context.bindings),
            old_context.rs485_flags,
            context.rs485_flags,
        )
        if not self.drivers:
            self.logger.warning("当前没有已启用传感器，采集循环继续运行并等待再次启用")
        return kind

    def _log_parameter_changes(
        self,
        old: RuntimeSettings,
        new: RuntimeSettings,
        *,
        kind: str,
    ) -> None:
        self.logger.info(
            "配置热加载类型=%s：interval_s %.3f -> %.3f（%s），"
            "timeout_s %.3f -> %.3f（%s），retries %d -> %d（%s）",
            "运行参数" if kind == "parameters" else "结构",
            old.interval_s,
            new.interval_s,
            new.interval_source,
            old.timeout_s,
            new.timeout_s,
            new.timeout_source,
            old.retries,
            new.retries,
            new.retries_source,
        )
