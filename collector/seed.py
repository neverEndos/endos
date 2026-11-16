"""HSI 演示配置的幂等种子。

本模块把 ``collector_protocol`` / ``collector_sensor_type`` / ``collector_device`` /
``collector_sensor`` / ``collector_sensor_template`` / ``collector_channel`` 的开发种子
收敛到一处，供启动时的 ``seed_all`` 与落库、JSONL 输出共用同一套
``sensor_code`` / ``channel_code`` 映射。

设计要点：

- 先按业务唯一键（``protocol_code``、``type_code``、``device_code``、
  ``(device_id, sensor_code)``、``(sensor_type_id, channel_code)``、
  ``(sensor_id, channel_code)`` 且 ``deleted=0``）查询，存在则复用 id，不存在才用
  对应序列 ``nextval`` 插入，因此可重复执行且不会产生重复行；
- 传感器类型/参数模板先于实例写入；已存在但尚未关联类型的传感器会补写
  ``sensor_type_id``，已存在但尚未关联模板的通道会补写 ``sensor_template_id``，
  不覆盖用户已修改的阈值等配置；
- 数值通道按「下限 + 比例 × 量程」初始化四档阈值（比例 0.10/0.30/0.70/0.90），
  枚举通道四档阈值均为 NULL，且不产生阈值报警（``alarm_enabled=0`` 且四个开关为 0）；
- 连接参数全部来自 ``YUDAO_DB_*`` 环境变量（与板端 ``/etc/yudao/env.remote`` 一致）；
- 仅在真正连接数据库时才导入 ``psycopg2``，便于在无 psycopg2 的开发机跑单元测试。
"""

import json
import logging
import os
from dataclasses import dataclass

LOGGER_NAME = "hsi"

PROTOCOL_CODE = "hsi_rs485"
DEVICE_CODE = "rk3568-hsi-01"
COLLECTOR_VERSION = "0.1.0-prototype"

DEFAULT_DB_SETTINGS = {
    "host": "127.0.0.1",
    "port": 5432,
    "dbname": "yudao_board",
    "user": "root",
    "password": "123456",
    "connect_timeout": 5,
    "application_name": "yudao-collector",
}


@dataclass(frozen=True)
class SensorSeed:
    """传感器种子（业务键 + 展示信息 + 连接参数）。"""

    sensor_code: str
    sensor_name: str
    description: str
    sort: int
    address: int


@dataclass(frozen=True)
class ChannelSeed:
    """通道种子。

    ``field`` 是解析器返回的字段名，``field_index`` 是 msg=3 应答 Data 中
    int16 的 1 起始序号；两者同时写入 ``channel_config``，落库与 JSONL 输出
    都按 ``field_index`` 取原始值、按 ``field`` 取工程值。
    """

    sensor_code: str
    channel_code: str
    channel_name: str
    field: str
    field_index: int
    scale: float
    unit: str | None
    icon: str
    decimal_places: int
    sort: int
    description: str


@dataclass(frozen=True)
class SensorTypeSeed:
    """传感器类型/型号种子。"""

    type_code: str
    type_name: str
    manufacturer: str
    description: str
    sort: int


@dataclass(frozen=True)
class TemplateSeed:
    """参数模板种子。

    模板保存通道的解析位置、量程与四档阈值比例；实际通道的阈值在生成时
    按「量程下限 + 比例 × 量程」计算，生成后允许用户覆盖。
    """

    type_code: str
    channel_code: str
    channel_name: str
    value_kind: str
    unit: str | None
    icon: str
    value_min: float
    value_max: float
    scale: float
    decimal_places: int
    alarm_enabled: int
    switch_low_error: int
    switch_low_warning: int
    switch_high_warning: int
    switch_high_error: int
    address: int
    field_index: int
    sort: int


def _connection_config(address: int) -> dict:
    """HSI RS485 连接参数（对应设计文档 connection_config 字段）。"""

    return {
        "port": "/dev/ttyS4",
        "baud": 9600,
        "bytesize": 8,
        "parity": "none",
        "stopbits": 1,
        "rs485": True,
        "rs485Flags": 0,
    }


SENSOR_SEEDS = (
    SensorSeed(
        sensor_code="cs1000-01",
        sensor_name="CS1000 污染度传感器",
        description="HYDAC CS1000，HSI 地址 0x61，msg=3 返回 10 个 int16",
        sort=1,
        address=0x61,
    ),
    SensorSeed(
        sensor_code="hlb1400-01",
        sensor_name="HLB1400 水分传感器",
        description="HYDAC HLB1400，HSI 地址 0x63，msg=3 旧格式返回 6 个 int16",
        sort=2,
        address=0x63,
    ),
)

SENSOR_TYPE_SEEDS = (
    SensorTypeSeed(
        type_code="cs1000",
        type_name="HYDAC CS1000 污染度传感器",
        manufacturer="HYDAC",
        description="HSI 地址 0x61，msg=3 返回 10 个 int16 字段",
        sort=1,
    ),
    SensorTypeSeed(
        type_code="hlb1400",
        type_name="HYDAC HLB1400 水分传感器",
        manufacturer="HYDAC",
        description="HSI 地址 0x63，msg=3 旧格式 6 个 int16；扩展格式 8 个 int16",
        sort=2,
    ),
)

SENSOR_TYPE_BY_SENSOR_CODE = {
    "cs1000-01": "cs1000",
    "hlb1400-01": "hlb1400",
}

# 参数模板的量程、值类别与四档开关。
# 量程是初始模板默认值（依据协议文档/常见工况），不代表现场标定，可在 UI 调整。
# 枚举通道 flow_state 不参与阈值报警。
_TEMPLATE_RANGES = {
    ("cs1000", "iso_4"): (8.0, 25.0, "number", 1, 1, 1, 1, 1),
    ("cs1000", "iso_6"): (7.0, 24.0, "number", 1, 1, 1, 1, 1),
    ("cs1000", "iso_14"): (6.0, 23.0, "number", 1, 1, 1, 1, 1),
    ("cs1000", "sae_a"): (0.0, 14.0, "number", 1, 1, 1, 1, 1),
    ("cs1000", "sae_b"): (0.0, 14.0, "number", 1, 1, 1, 1, 1),
    ("cs1000", "sae_c"): (0.0, 14.0, "number", 1, 1, 1, 1, 1),
    ("cs1000", "sae_d"): (0.0, 14.0, "number", 1, 1, 1, 1, 1),
    ("cs1000", "temperature_c"): (-60.0, 150.0, "number", 1, 1, 1, 1, 1),
    ("cs1000", "flow_state"): (11.0, 55.0, "enum", 0, 0, 0, 0, 0),
    ("cs1000", "drive_pct"): (0.0, 100.0, "number", 1, 1, 1, 1, 1),
    ("hlb1400", "conductivity_ns_m"): (0.0, 100.0, "number", 1, 1, 1, 1, 1),
    ("hlb1400", "conductivity_change_pct"): (-100.0, 200.0, "number", 1, 1, 1, 1, 1),
    ("hlb1400", "dc"): (1.0, 10.0, "number", 1, 1, 1, 1, 1),
    ("hlb1400", "dc_change_pct"): (-30.0, 30.0, "number", 1, 1, 1, 1, 1),
    ("hlb1400", "saturation_pct"): (0.0, 100.0, "number", 1, 1, 1, 1, 1),
    ("hlb1400", "temperature_c"): (-25.0, 100.0, "number", 1, 1, 1, 1, 1),
}

_CHANNELS_CS1000 = (
    # sensor_code, channel_code, 名称, 解析字段, 序号, 缩放, 单位, 图标, 小数位, 排序, 说明
    ("cs1000-01", "iso_4", "ISO 4μm 清洁度", "iso_4", 1, 0.1, "µm", "i-ep:filter", 1, 1, "ISO 4μm 通道，原始值 ×0.1"),
    ("cs1000-01", "iso_6", "ISO 6μm 清洁度", "iso_6", 2, 0.1, "µm", "i-ep:filter", 1, 2, "ISO 6μm 通道，原始值 ×0.1"),
    ("cs1000-01", "iso_14", "ISO 14μm 清洁度", "iso_14", 3, 0.1, "µm", "i-ep:filter", 1, 3, "ISO 14μm 通道，原始值 ×0.1"),
    ("cs1000-01", "sae_a", "SAE A 等级", "sae_a", 4, 0.1, None, "i-ep:medal", 1, 4, "SAE A 等级，原始值 ×0.1"),
    ("cs1000-01", "sae_b", "SAE B 等级", "sae_b", 5, 0.1, None, "i-ep:medal", 1, 5, "SAE B 等级，原始值 ×0.1"),
    ("cs1000-01", "sae_c", "SAE C 等级", "sae_c", 6, 0.1, None, "i-ep:medal", 1, 6, "SAE C 等级，原始值 ×0.1"),
    ("cs1000-01", "sae_d", "SAE D 等级", "sae_d", 7, 0.1, None, "i-ep:medal", 1, 7, "SAE D 等级，原始值 ×0.1"),
    ("cs1000-01", "temperature_c", "油液温度", "temperature_c", 8, 0.1, "℃", "i-ep:thermometer", 1, 8, "温度，原始值 ×0.1 ℃"),
    ("cs1000-01", "flow_state", "流动状态", "flow_state", 9, 1.0, None, "i-ep:switch", 0, 9, "FlowState 枚举原始值 11/22/33/44/55"),
    ("cs1000-01", "drive_pct", "驱动百分比", "drive_pct", 10, 1.0, "%", "i-ep:odometer", 0, 10, "驱动百分比 0..100"),
)

_CHANNELS_HLB1400 = (
    ("hlb1400-01", "conductivity_ns_m", "电导率", "conductivity_ns_m", 1, 0.1, "nS/m", "i-ep:lightning", 1, 1, "电导率，原始值 ×0.1 nS/m"),
    ("hlb1400-01", "conductivity_change_pct", "电导率相对变化", "conductivity_change_pct", 2, 0.1, "%", "i-ep:trend-charts", 1, 2, "相对电导率变化，原始值 ×0.1 %"),
    ("hlb1400-01", "dc", "介电常数 DC", "dc", 3, 0.01, None, "i-ep:data-line", 2, 3, "DC，原始值 ×0.01"),
    ("hlb1400-01", "dc_change_pct", "DC 相对变化", "dc_change_pct", 4, 0.1, "%", "i-ep:trend-charts", 1, 4, "相对 DC 变化，原始值 ×0.1 %"),
    ("hlb1400-01", "saturation_pct", "饱和度", "saturation_pct", 5, 1.0, "%rH", "i-ep:drizzling", 0, 5, "饱和度 0..100 %rH"),
    ("hlb1400-01", "temperature_c", "油液温度", "temperature_c", 6, 0.1, "℃", "i-ep:thermometer", 1, 6, "温度，原始值 ×0.1 ℃"),
)


def _make_channel_seeds(rows) -> tuple[ChannelSeed, ...]:
    return tuple(
        ChannelSeed(
            sensor_code=row[0],
            channel_code=row[1],
            channel_name=row[2],
            field=row[3],
            field_index=row[4],
            scale=row[5],
            unit=row[6],
            icon=row[7],
            decimal_places=row[8],
            sort=row[9],
            description=row[10],
        )
        for row in rows
    )


CHANNEL_SEEDS = _make_channel_seeds(_CHANNELS_CS1000 + _CHANNELS_HLB1400)

CHANNEL_SEED_BY_KEY = {
    (seed.sensor_code, seed.channel_code): seed for seed in CHANNEL_SEEDS
}
SENSOR_SEED_BY_CODE = {seed.sensor_code: seed for seed in SENSOR_SEEDS}
SENSOR_TYPE_SEED_BY_CODE = {seed.type_code: seed for seed in SENSOR_TYPE_SEEDS}


def _make_template_seeds(channels) -> tuple[TemplateSeed, ...]:
    """由通道种子与量程配置生成参数模板种子，避免两份字段映射漂移。"""

    seeds = []
    for channel in channels:
        type_code = SENSOR_TYPE_BY_SENSOR_CODE[channel.sensor_code]
        (
            value_min,
            value_max,
            value_kind,
            alarm_enabled,
            switch_low_error,
            switch_low_warning,
            switch_high_warning,
            switch_high_error,
        ) = _TEMPLATE_RANGES[(type_code, channel.channel_code)]
        seeds.append(
            TemplateSeed(
                type_code=type_code,
                channel_code=channel.channel_code,
                channel_name=channel.channel_name,
                value_kind=value_kind,
                unit=channel.unit,
                icon=channel.icon,
                value_min=value_min,
                value_max=value_max,
                scale=channel.scale,
                decimal_places=channel.decimal_places,
                alarm_enabled=alarm_enabled,
                switch_low_error=switch_low_error,
                switch_low_warning=switch_low_warning,
                switch_high_warning=switch_high_warning,
                switch_high_error=switch_high_error,
                address=SENSOR_SEED_BY_CODE[channel.sensor_code].address,
                field_index=channel.field_index,
                sort=channel.sort,
            )
        )
    return tuple(seeds)


TEMPLATE_SEEDS = _make_template_seeds(CHANNEL_SEEDS)
TEMPLATE_SEED_BY_KEY = {
    (seed.type_code, seed.channel_code): seed for seed in TEMPLATE_SEEDS
}


def _initial_threshold(value_min: float, value_max: float, ratio: float) -> float:
    """按量程比例初始化阈值。"""

    return round(value_min + ratio * (value_max - value_min), 4)
# 驱动名（解析器返回的 sensor 名）到种子 sensor_code 的映射，JSONL 与落库共用。
SENSOR_CODE_BY_DRIVER = {
    "CS1000": "cs1000-01",
    "HLB1400": "hlb1400-01",
}


def db_settings_from_env() -> dict:
    """从 ``YUDAO_DB_*`` 环境变量读取连接参数。"""

    settings = dict(DEFAULT_DB_SETTINGS)
    settings["host"] = os.environ.get("YUDAO_DB_HOST", settings["host"])
    settings["port"] = int(os.environ.get("YUDAO_DB_PORT", settings["port"]))
    settings["dbname"] = os.environ.get(
        "YUDAO_DB_NAME", os.environ.get("YUDAO_DB_DATABASE", settings["dbname"])
    )
    settings["user"] = os.environ.get("YUDAO_DB_USERNAME", settings["user"])
    settings["password"] = os.environ.get("YUDAO_DB_PASSWORD", settings["password"])
    settings["connect_timeout"] = int(
        os.environ.get("YUDAO_DB_CONNECT_TIMEOUT", settings["connect_timeout"])
    )
    return settings


def mask_settings(settings: dict) -> dict:
    """日志用连接信息：不打印密码。"""

    masked = dict(settings)
    masked.pop("password", None)
    return masked


def seed_all(settings: dict | None = None, logger: logging.Logger | None = None) -> dict:
    """执行幂等种子并返回 protocol/device/sensor/channel 的 id。

    整个过程在单个事务内完成：任一步失败则回滚，不会留下半套配置。
    """

    import psycopg2
    from psycopg2.extras import Json

    log = logger or logging.getLogger(LOGGER_NAME)
    settings = settings or db_settings_from_env()
    ids: dict = {
        "protocol_id": None,
        "device_id": None,
        "sensor_types": {},
        "sensors": {},
        "templates": {},
        "channels": {},
    }

    conn = psycopg2.connect(**settings)
    try:
        conn.autocommit = False
        with conn:
            with conn.cursor() as cur:
                ids["protocol_id"] = _seed_protocol(cur, Json)
                for sensor_type in SENSOR_TYPE_SEEDS:
                    type_id = _seed_sensor_type(cur, sensor_type)
                    ids["sensor_types"][sensor_type.type_code] = type_id
                ids["device_id"] = _seed_device(cur)
                for template in TEMPLATE_SEEDS:
                    template_id = _seed_sensor_template(
                        cur,
                        Json,
                        ids["sensor_types"][template.type_code],
                        template,
                    )
                    ids["templates"][
                        (template.type_code, template.channel_code)
                    ] = template_id
                for sensor in SENSOR_SEEDS:
                    type_code = SENSOR_TYPE_BY_SENSOR_CODE[sensor.sensor_code]
                    sensor_id = _seed_sensor(
                        cur,
                        Json,
                        ids["device_id"],
                        ids["sensor_types"][type_code],
                        sensor,
                    )
                    ids["sensors"][sensor.sensor_code] = sensor_id
                    for channel in [
                        item
                        for item in CHANNEL_SEEDS
                        if item.sensor_code == sensor.sensor_code
                    ]:
                        template = TEMPLATE_SEED_BY_KEY[
                            (type_code, channel.channel_code)
                        ]
                        channel_id = _seed_channel(
                            cur,
                            Json,
                            ids["device_id"],
                            sensor_id,
                            ids["templates"][
                                (type_code, channel.channel_code)
                            ],
                            template,
                            sensor,
                            channel,
                        )
                        ids["channels"][
                            (sensor.sensor_code, channel.channel_code)
                        ] = channel_id
    finally:
        conn.close()

    log.info(
        "HSI 配置种子完成 protocol_id=%s device_id=%s types=%d sensors=%s templates=%d channels=%d",
        ids["protocol_id"],
        ids["device_id"],
        len(ids["sensor_types"]),
        ids["sensors"],
        len(ids["templates"]),
        len(ids["channels"]),
    )
    return ids


def _seed_protocol(cur, json_adapter) -> int:
    cur.execute(
        "SELECT id FROM public.collector_protocol"
        " WHERE protocol_code = %s AND deleted = 0",
        (PROTOCOL_CODE,),
    )
    row = cur.fetchone()
    if row:
        return int(row[0])

    cur.execute(
        """
        INSERT INTO public.collector_protocol (
            id, protocol_code, protocol_name, transport_type, driver_key,
            connection_schema, channel_schema, protocol_version, enabled, sort,
            description, creator, updater
        ) VALUES (
            nextval('public.collector_protocol_seq'), %s, %s, %s, %s,
            %s, %s, %s, 1, %s, %s, 'collector', 'collector'
        )
        RETURNING id
        """,
        (
            PROTOCOL_CODE,
            "HYDAC HSI (RS485)",
            "serial",
            "hsi_rs485_driver",
            json_adapter({"type": "object"}),
            json_adapter({"type": "object"}),
            "1.0.0",
            30,
            "HYDAC HSI 总线；9600 8N1 无流控，RS485 需 TIOCSRS485 方向控制；"
            "msg=1 状态、msg=3 测量",
        ),
    )
    return int(cur.fetchone()[0])


def _seed_sensor_type(cur, sensor_type: SensorTypeSeed) -> int:
    """按 type_code 复用或创建传感器类型。"""

    cur.execute(
        "SELECT id FROM public.collector_sensor_type"
        " WHERE type_code = %s AND deleted = 0",
        (sensor_type.type_code,),
    )
    row = cur.fetchone()
    if row:
        return int(row[0])

    cur.execute(
        """
        INSERT INTO public.collector_sensor_type (
            id, type_code, type_name, protocol_code, manufacturer,
            description, enabled, sort, creator, updater
        ) VALUES (
            nextval('public.collector_sensor_type_seq'), %s, %s, %s, %s,
            %s, 1, %s, 'collector', 'collector'
        )
        RETURNING id
        """,
        (
            sensor_type.type_code,
            sensor_type.type_name,
            PROTOCOL_CODE,
            sensor_type.manufacturer,
            sensor_type.description,
            sensor_type.sort,
        ),
    )
    return int(cur.fetchone()[0])


def _seed_sensor_template(cur, json_adapter, sensor_type_id: int, template: TemplateSeed) -> int:
    """按 (sensor_type_id, channel_code) 复用或创建参数模板。"""

    cur.execute(
        "SELECT id FROM public.collector_sensor_template"
        " WHERE sensor_type_id = %s AND channel_code = %s AND deleted = 0",
        (sensor_type_id, template.channel_code),
    )
    row = cur.fetchone()
    if row:
        return int(row[0])

    channel_config = {
        "address": template.address,
        "message": 3,
        "fieldIndex": template.field_index,
        "signed": True,
        "scale": template.scale,
        "offset": 0,
        "unit": template.unit,
        "decimals": template.decimal_places,
    }
    cur.execute(
        """
        INSERT INTO public.collector_sensor_template (
            id, sensor_type_id, channel_code, channel_name, value_kind,
            unit, icon, value_min, value_max, scale, value_offset,
            decimal_places, threshold_low_error_ratio,
            threshold_low_warning_ratio, threshold_high_warning_ratio,
            threshold_high_error_ratio, alarm_enabled,
            threshold_low_error_enabled, threshold_low_warning_enabled,
            threshold_high_warning_enabled, threshold_high_error_enabled,
            channel_config, sort, enabled, creator, updater
        ) VALUES (
            nextval('public.collector_sensor_template_seq'), %s, %s, %s, %s,
            %s, %s, %s, %s, %s, 0,
            %s, 0.10, 0.30, 0.70, 0.90, %s,
            %s, %s, %s, %s,
            %s, %s, 1, 'collector', 'collector'
        )
        RETURNING id
        """,
        (
            sensor_type_id,
            template.channel_code,
            template.channel_name,
            template.value_kind,
            template.unit,
            template.icon,
            template.value_min,
            template.value_max,
            template.scale,
            template.decimal_places,
            template.alarm_enabled,
            template.switch_low_error,
            template.switch_low_warning,
            template.switch_high_warning,
            template.switch_high_error,
            json_adapter(channel_config),
            template.sort,
        ),
    )
    return int(cur.fetchone()[0])


def _seed_device(cur) -> int:
    cur.execute(
        "SELECT id FROM public.collector_device"
        " WHERE device_code = %s AND deleted = 0",
        (DEVICE_CODE,),
    )
    row = cur.fetchone()
    if row:
        return int(row[0])

    cur.execute(
        """
        INSERT INTO public.collector_device (
            id, device_code, device_name, enabled, poll_interval_ms, timeout_ms,
            retries, reconnect_interval_ms, description, sort, creator, updater
        ) VALUES (
            nextval('public.collector_device_seq'), %s, %s, 1, 60000, 1000,
            1, 5000, %s, 1, 'collector', 'collector'
        )
        RETURNING id
        """,
        (DEVICE_CODE, "RK3568 HSI 采集主机", "RK3568 板端 HSI 采集演示设备"),
    )
    return int(cur.fetchone()[0])


def _seed_sensor(
    cur, json_adapter, device_id: int, sensor_type_id: int, sensor: SensorSeed
) -> int:
    cur.execute(
        "SELECT id FROM public.collector_sensor"
        " WHERE device_id = %s AND sensor_code = %s AND deleted = 0",
        (device_id, sensor.sensor_code),
    )
    row = cur.fetchone()
    if row:
        sensor_id = int(row[0])
        # 兼容旧种子：只补空值，不覆盖用户选择的型号。
        cur.execute(
            "UPDATE public.collector_sensor"
            " SET sensor_type_id = %s"
            " WHERE id = %s AND sensor_type_id IS NULL",
            (sensor_type_id, sensor_id),
        )
        return sensor_id

    cur.execute(
        """
        INSERT INTO public.collector_sensor (
            id, device_id, sensor_type_id, sensor_code, sensor_name, protocol_code,
            connection_config, enabled, description, sort, creator, updater
        ) VALUES (
            nextval('public.collector_sensor_seq'), %s, %s, %s, %s, %s,
            %s, 1, %s, %s, 'collector', 'collector'
        )
        RETURNING id
        """,
        (
            device_id,
            sensor_type_id,
            sensor.sensor_code,
            sensor.sensor_name,
            PROTOCOL_CODE,
            json_adapter(_connection_config(sensor.address)),
            sensor.description,
            sensor.sort,
        ),
    )
    return int(cur.fetchone()[0])


def _seed_channel(
    cur,
    json_adapter,
    device_id: int,
    sensor_id: int,
    sensor_template_id: int,
    template: TemplateSeed,
    sensor: SensorSeed,
    channel: ChannelSeed,
) -> int:
    cur.execute(
        "SELECT id FROM public.collector_channel"
        " WHERE sensor_id = %s AND channel_code = %s AND deleted = 0",
        (sensor_id, channel.channel_code),
    )
    row = cur.fetchone()
    if row:
        channel_id = int(row[0])
        # 兼容旧种子：仅补模板关联，不覆盖已有量程/阈值/开关等用户配置。
        cur.execute(
            "UPDATE public.collector_channel"
            " SET sensor_template_id = %s"
            " WHERE id = %s AND sensor_template_id IS NULL",
            (sensor_template_id, channel_id),
        )
        return channel_id

    channel_config = {
        "address": sensor.address,
        "message": 3,
        "fieldIndex": channel.field_index,
        "signed": True,
        "scale": channel.scale,
        "offset": 0,
        "unit": channel.unit,
        "decimals": channel.decimal_places,
    }
    if template.value_kind == "enum":
        threshold_low_error = None
        threshold_low_warning = None
        threshold_high_warning = None
        threshold_high_error = None
    else:
        threshold_low_error = _initial_threshold(
            template.value_min, template.value_max, 0.10
        )
        threshold_low_warning = _initial_threshold(
            template.value_min, template.value_max, 0.30
        )
        threshold_high_warning = _initial_threshold(
            template.value_min, template.value_max, 0.70
        )
        threshold_high_error = _initial_threshold(
            template.value_min, template.value_max, 0.90
        )
    cur.execute(
        """
        INSERT INTO public.collector_channel (
            id, device_id, sensor_id, sensor_template_id, channel_code,
            channel_name, enabled, value_type, value_kind, channel_config,
            scale, value_offset, unit, icon, decimal_places, deadband,
            value_min, value_max, threshold_low_error,
            threshold_low_warning, threshold_high_warning,
            threshold_high_error, threshold_low_error_enabled,
            threshold_low_warning_enabled, threshold_high_warning_enabled,
            threshold_high_error_enabled, alarm_deadband, alarm_enabled,
            poll_interval_ms, description, sort, creator, updater
        ) VALUES (
            nextval('public.collector_channel_seq'), %s, %s, %s, %s,
            %s, 1, 'number', %s, %s,
            %s, 0, %s, %s, %s, 0,
            %s, %s, %s,
            %s, %s,
            %s, %s,
            %s, %s,
            %s, 0, %s,
            NULL, %s, %s, 'collector', 'collector'
        )
        RETURNING id
        """,
        (
            device_id,
            sensor_id,
            sensor_template_id,
            channel.channel_code,
            channel.channel_name,
            template.value_kind,
            json_adapter(channel_config),
            channel.scale,
            channel.unit,
            channel.icon,
            channel.decimal_places,
            template.value_min,
            template.value_max,
            threshold_low_error,
            threshold_low_warning,
            threshold_high_warning,
            threshold_high_error,
            template.switch_low_error,
            template.switch_low_warning,
            template.switch_high_warning,
            template.switch_high_error,
            template.alarm_enabled,
            channel.description,
            channel.sort,
        ),
    )
    return int(cur.fetchone()[0])


def dump_seed(logger: logging.Logger | None = None) -> str:
    """打印种子摘要，便于排查（不涉及数据库）。"""

    log = logger or logging.getLogger(LOGGER_NAME)
    summary = {
        "protocol_code": PROTOCOL_CODE,
        "device_code": DEVICE_CODE,
        "sensor_types": [t.type_code for t in SENSOR_TYPE_SEEDS],
        "sensors": [s.sensor_code for s in SENSOR_SEEDS],
        "templates": [
            {"type_code": t.type_code, "channel_code": t.channel_code}
            for t in TEMPLATE_SEEDS
        ],
        "channels": [
            {"sensor_code": c.sensor_code, "channel_code": c.channel_code}
            for c in CHANNEL_SEEDS
        ],
    }
    text = json.dumps(summary, ensure_ascii=False)
    log.info("HSI 配置种子摘要 %s", text)
    return text
