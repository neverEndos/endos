/*
 采集器采集数据表初始化脚本（PostgreSQL 17）

 适用范围：collector 首期 15 张表（协议注册表、设备、传感器、通道、分时段轮询、配置版本、
 原始采样、小时/天聚合、聚合水位、报警、设备状态、心跳、命令、保留策略）。

 说明：
 1. 所有表位于 public schema；不建外键，表之间只做逻辑关联，引用完整性由 Service 校验。
 2. 配置表使用 bigint 主键 + 独立序列，并保留 BaseDO 字段（creator/create_time/
    updater/update_time/deleted）；时间序列与运行状态表使用业务主键，不保留 BaseDO。
 3. collector_sample_raw 按天 RANGE 分区，本脚本创建示例日分区与 DEFAULT 分区；
    后续分区由维护任务按 collector_retention_policy.partition_lead_days 提前创建。
 4. 业务时间统一使用 timestamp(3) without time zone，语义为 Asia/Shanghai 本地墙钟时间。
 5. 本脚本支持重复执行：开头会删除并重建下述 15 张 collector 表与 10 个序列，
    执行将清空采集器已有数据，请在确认后执行。
*/

SET client_encoding = 'UTF8';


-- =============================================================================
-- 0. 删除旧对象（重建会清空采集器数据）
--    先删表再删序列，以固定顺序清理本期对象；
--    列默认值不依赖这些序列，序列仅供种子数据与后续 DO 主键生成使用；
--    分区表只 DROP 父表，分区及其索引/约束随父表一并删除，不单独 DROP。
-- =============================================================================

DROP TABLE IF EXISTS public.collector_protocol;
DROP TABLE IF EXISTS public.collector_device;
DROP TABLE IF EXISTS public.collector_sensor;
DROP TABLE IF EXISTS public.collector_channel;
DROP TABLE IF EXISTS public.collector_poll_schedule;
DROP TABLE IF EXISTS public.collector_config_version;
DROP TABLE IF EXISTS public.collector_sample_raw;
DROP TABLE IF EXISTS public.collector_aggregate_hour;
DROP TABLE IF EXISTS public.collector_aggregate_day;
DROP TABLE IF EXISTS public.collector_aggregate_watermark;
DROP TABLE IF EXISTS public.collector_alarm;
DROP TABLE IF EXISTS public.collector_device_status;
DROP TABLE IF EXISTS public.collector_heartbeat;
DROP TABLE IF EXISTS public.collector_command;
DROP TABLE IF EXISTS public.collector_retention_policy;

DROP SEQUENCE IF EXISTS public.collector_protocol_seq;
DROP SEQUENCE IF EXISTS public.collector_device_seq;
DROP SEQUENCE IF EXISTS public.collector_sensor_seq;
DROP SEQUENCE IF EXISTS public.collector_channel_seq;
DROP SEQUENCE IF EXISTS public.collector_poll_schedule_seq;
DROP SEQUENCE IF EXISTS public.collector_config_version_seq;
DROP SEQUENCE IF EXISTS public.collector_alarm_seq;
DROP SEQUENCE IF EXISTS public.collector_heartbeat_seq;
DROP SEQUENCE IF EXISTS public.collector_command_seq;
DROP SEQUENCE IF EXISTS public.collector_retention_policy_seq;


-- =============================================================================
-- 1. 序列
-- =============================================================================

CREATE SEQUENCE public.collector_protocol_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.collector_device_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.collector_sensor_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.collector_channel_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.collector_poll_schedule_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.collector_config_version_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.collector_alarm_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.collector_heartbeat_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.collector_command_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE public.collector_retention_policy_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


-- =============================================================================
-- 2. 协议注册表
-- =============================================================================

CREATE TABLE public.collector_protocol (
    id bigint NOT NULL,
    protocol_code character varying(64) NOT NULL,
    protocol_name character varying(100) NOT NULL,
    transport_type character varying(16),
    driver_key character varying(128) NOT NULL,
    connection_schema jsonb DEFAULT '{}'::jsonb NOT NULL,
    channel_schema jsonb DEFAULT '{}'::jsonb NOT NULL,
    protocol_version character varying(32) DEFAULT '1.0.0'::character varying NOT NULL,
    enabled smallint DEFAULT 1 NOT NULL,
    sort integer DEFAULT 0 NOT NULL,
    description character varying(255),
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_collector_protocol PRIMARY KEY (id),
    CONSTRAINT chk_collector_protocol_code CHECK (length(protocol_code) > 0),
    CONSTRAINT chk_collector_protocol_name CHECK (length(protocol_name) > 0),
    CONSTRAINT chk_collector_protocol_driver CHECK (length(driver_key) > 0),
    CONSTRAINT chk_collector_protocol_version CHECK (length(protocol_version) > 0),
    CONSTRAINT chk_collector_protocol_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_collector_protocol_connection_schema CHECK (jsonb_typeof(connection_schema) = 'object'),
    CONSTRAINT chk_collector_protocol_channel_schema CHECK (jsonb_typeof(channel_schema) = 'object')
);

CREATE UNIQUE INDEX uk_collector_protocol_code
    ON public.collector_protocol USING btree (protocol_code)
    WHERE (deleted = 0);

COMMENT ON TABLE public.collector_protocol IS '采集协议注册表（驱动路由与连接/通道 JSON Schema）';
COMMENT ON COLUMN public.collector_protocol.id IS '主键编号';
COMMENT ON COLUMN public.collector_protocol.protocol_code IS '协议业务编码（业务唯一，不写死枚举）';
COMMENT ON COLUMN public.collector_protocol.protocol_name IS '协议展示名称';
COMMENT ON COLUMN public.collector_protocol.transport_type IS '传输大类：serial/tcp/udp/can/mqtt/http 等，可空、可扩展';
COMMENT ON COLUMN public.collector_protocol.driver_key IS 'collector 驱动标识，与驱动实现一一对应';
COMMENT ON COLUMN public.collector_protocol.connection_schema IS '传感器连接参数 JSON Schema，驱动传感器表单与校验';
COMMENT ON COLUMN public.collector_protocol.channel_schema IS '通道寻址/解析参数 JSON Schema，驱动通道表单与校验';
COMMENT ON COLUMN public.collector_protocol.protocol_version IS '协议定义版本（语义化版本），快照中固定';
COMMENT ON COLUMN public.collector_protocol.enabled IS '是否允许新增传感器引用：0 否、1 是；已有传感器仍按快照运行';
COMMENT ON COLUMN public.collector_protocol.sort IS '排序值';
COMMENT ON COLUMN public.collector_protocol.description IS '协议说明、驱动限制与升级注意事项';
COMMENT ON COLUMN public.collector_protocol.creator IS '创建者';
COMMENT ON COLUMN public.collector_protocol.create_time IS '创建时间';
COMMENT ON COLUMN public.collector_protocol.updater IS '更新者';
COMMENT ON COLUMN public.collector_protocol.update_time IS '更新时间';
COMMENT ON COLUMN public.collector_protocol.deleted IS '是否删除：0 否、1 是';


-- =============================================================================
-- 3. 采集设备
-- =============================================================================

CREATE TABLE public.collector_device (
    id bigint NOT NULL,
    device_code character varying(64) NOT NULL,
    device_name character varying(100) NOT NULL,
    enabled smallint DEFAULT 1 NOT NULL,
    poll_interval_ms integer DEFAULT 5000 NOT NULL,
    timeout_ms integer DEFAULT 1000 NOT NULL,
    retries smallint DEFAULT 2 NOT NULL,
    reconnect_interval_ms integer DEFAULT 5000 NOT NULL,
    description character varying(255),
    sort integer DEFAULT 0 NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_collector_device PRIMARY KEY (id),
    CONSTRAINT chk_collector_device_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_collector_device_code CHECK (length(device_code) > 0),
    CONSTRAINT chk_collector_device_poll CHECK (poll_interval_ms >= 100),
    CONSTRAINT chk_collector_device_timeout CHECK (timeout_ms >= 10),
    CONSTRAINT chk_collector_device_retries CHECK (retries BETWEEN 0 AND 10)
);

CREATE UNIQUE INDEX uk_collector_device_code
    ON public.collector_device USING btree (device_code)
    WHERE (deleted = 0);

COMMENT ON TABLE public.collector_device IS '采集设备/采集主机通用配置表（协议无关，采集对象最高层）';
COMMENT ON COLUMN public.collector_device.id IS '主键编号';
COMMENT ON COLUMN public.collector_device.device_code IS '设备编码（业务唯一）';
COMMENT ON COLUMN public.collector_device.device_name IS '设备名称';
COMMENT ON COLUMN public.collector_device.enabled IS '是否启用：0 否、1 是';
COMMENT ON COLUMN public.collector_device.poll_interval_ms IS '设备默认轮询周期（毫秒），传感器与通道可继承';
COMMENT ON COLUMN public.collector_device.timeout_ms IS '单次请求超时（毫秒）';
COMMENT ON COLUMN public.collector_device.retries IS '失败重试次数';
COMMENT ON COLUMN public.collector_device.reconnect_interval_ms IS '断线重连间隔（毫秒）';
COMMENT ON COLUMN public.collector_device.description IS '描述';
COMMENT ON COLUMN public.collector_device.sort IS '排序值';
COMMENT ON COLUMN public.collector_device.creator IS '创建者';
COMMENT ON COLUMN public.collector_device.create_time IS '创建时间';
COMMENT ON COLUMN public.collector_device.updater IS '更新者';
COMMENT ON COLUMN public.collector_device.update_time IS '更新时间';
COMMENT ON COLUMN public.collector_device.deleted IS '是否删除：0 否、1 是';


-- =============================================================================
-- 4. 采集传感器
-- =============================================================================

CREATE TABLE public.collector_sensor (
    id bigint NOT NULL,
    device_id bigint NOT NULL,
    sensor_code character varying(64) NOT NULL,
    sensor_name character varying(100) NOT NULL,
    protocol_code character varying(64) NOT NULL,
    connection_config jsonb DEFAULT '{}'::jsonb NOT NULL,
    enabled smallint DEFAULT 1 NOT NULL,
    description character varying(255),
    sort integer DEFAULT 0 NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_collector_sensor PRIMARY KEY (id),
    CONSTRAINT chk_collector_sensor_code CHECK (length(sensor_code) > 0),
    CONSTRAINT chk_collector_sensor_name CHECK (length(sensor_name) > 0),
    CONSTRAINT chk_collector_sensor_protocol CHECK (length(protocol_code) > 0),
    CONSTRAINT chk_collector_sensor_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_collector_sensor_connection CHECK (jsonb_typeof(connection_config) = 'object')
);

CREATE UNIQUE INDEX uk_collector_sensor_code
    ON public.collector_sensor USING btree (device_id, sensor_code)
    WHERE (deleted = 0);

CREATE INDEX idx_collector_sensor_device
    ON public.collector_sensor USING btree (device_id)
    WHERE (deleted = 0);

CREATE INDEX idx_collector_sensor_protocol
    ON public.collector_sensor USING btree (protocol_code)
    WHERE (deleted = 0);

COMMENT ON TABLE public.collector_sensor IS '采集传感器表（设备下级，绑定协议与连接参数）';
COMMENT ON COLUMN public.collector_sensor.id IS '主键编号';
COMMENT ON COLUMN public.collector_sensor.device_id IS '所属设备编号（逻辑关联 collector_device.id）';
COMMENT ON COLUMN public.collector_sensor.sensor_code IS '传感器编码（设备内唯一）';
COMMENT ON COLUMN public.collector_sensor.sensor_name IS '传感器名称';
COMMENT ON COLUMN public.collector_sensor.protocol_code IS '协议编码（逻辑关联 collector_protocol.protocol_code，不建 FK）';
COMMENT ON COLUMN public.collector_sensor.connection_config IS '协议连接参数 JSON，由 protocol.connection_schema 校验';
COMMENT ON COLUMN public.collector_sensor.enabled IS '是否启用：0 否、1 是';
COMMENT ON COLUMN public.collector_sensor.description IS '描述';
COMMENT ON COLUMN public.collector_sensor.sort IS '排序值';
COMMENT ON COLUMN public.collector_sensor.creator IS '创建者';
COMMENT ON COLUMN public.collector_sensor.create_time IS '创建时间';
COMMENT ON COLUMN public.collector_sensor.updater IS '更新者';
COMMENT ON COLUMN public.collector_sensor.update_time IS '更新时间';
COMMENT ON COLUMN public.collector_sensor.deleted IS '是否删除：0 否、1 是';


-- =============================================================================
-- 5. 采集通道
-- =============================================================================

CREATE TABLE public.collector_channel (
    id bigint NOT NULL,
    device_id bigint NOT NULL,
    sensor_id bigint NOT NULL,
    channel_code character varying(64) NOT NULL,
    channel_name character varying(100) NOT NULL,
    enabled smallint DEFAULT 1 NOT NULL,
    value_type character varying(16) NOT NULL,
    channel_config jsonb DEFAULT '{}'::jsonb NOT NULL,
    scale double precision DEFAULT 1 NOT NULL,
    value_offset double precision DEFAULT 0 NOT NULL,
    unit character varying(32),
    icon character varying(64),
    decimal_places smallint DEFAULT 0 NOT NULL,
    deadband double precision DEFAULT 0 NOT NULL,
    threshold_low double precision,
    threshold_high double precision,
    alarm_deadband double precision DEFAULT 0 NOT NULL,
    alarm_enabled smallint DEFAULT 0 NOT NULL,
    poll_interval_ms integer,
    description character varying(255),
    sort integer DEFAULT 0 NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_collector_channel PRIMARY KEY (id),
    CONSTRAINT chk_collector_channel_code CHECK (length(channel_code) > 0),
    CONSTRAINT chk_collector_channel_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_collector_channel_value_type CHECK (value_type = 'number'),
    CONSTRAINT chk_collector_channel_scale CHECK (is_finite(scale)),
    CONSTRAINT chk_collector_channel_offset CHECK (is_finite(value_offset)),
    CONSTRAINT chk_collector_channel_deadband CHECK (is_finite(deadband) AND deadband >= 0),
    CONSTRAINT chk_collector_channel_threshold_finite CHECK (
        (threshold_low IS NULL OR is_finite(threshold_low))
        AND (threshold_high IS NULL OR is_finite(threshold_high))
    ),
    CONSTRAINT chk_collector_channel_alarm_deadband CHECK (is_finite(alarm_deadband) AND alarm_deadband >= 0),
    CONSTRAINT chk_collector_channel_decimal CHECK (decimal_places BETWEEN 0 AND 6),
    CONSTRAINT chk_collector_channel_threshold CHECK (
        threshold_low IS NULL OR threshold_high IS NULL OR threshold_low <= threshold_high
    ),
    CONSTRAINT chk_collector_channel_alarm_enabled CHECK (alarm_enabled IN (0, 1)),
    CONSTRAINT chk_collector_channel_poll CHECK (poll_interval_ms IS NULL OR poll_interval_ms >= 100),
    CONSTRAINT chk_collector_channel_config CHECK (jsonb_typeof(channel_config) = 'object')
);

CREATE UNIQUE INDEX uk_collector_channel_code
    ON public.collector_channel USING btree (sensor_id, channel_code)
    WHERE (deleted = 0);

CREATE INDEX idx_collector_channel_sensor
    ON public.collector_channel USING btree (sensor_id)
    WHERE (deleted = 0);

CREATE INDEX idx_collector_channel_device
    ON public.collector_channel USING btree (device_id)
    WHERE (deleted = 0);

COMMENT ON TABLE public.collector_channel IS '采集通道通用配置表（协议无关，含 UI 渲染字段）';
COMMENT ON COLUMN public.collector_channel.id IS '主键编号';
COMMENT ON COLUMN public.collector_channel.device_id IS '所属设备编号（冗余，Service 保证与传感器所属设备一致）';
COMMENT ON COLUMN public.collector_channel.sensor_id IS '所属传感器编号（逻辑关联 collector_sensor.id）';
COMMENT ON COLUMN public.collector_channel.channel_code IS '通道编码（传感器内唯一）';
COMMENT ON COLUMN public.collector_channel.channel_name IS '通道名称（列表/卡片展示）';
COMMENT ON COLUMN public.collector_channel.enabled IS '是否启用：0 否、1 是';
COMMENT ON COLUMN public.collector_channel.value_type IS '通用值类型：首期仅 number；bit 等协议值由驱动转换为数值 0/1';
COMMENT ON COLUMN public.collector_channel.channel_config IS '协议专用寻址/解析参数 JSON，由所属传感器的 protocol.channel_schema 校验';
COMMENT ON COLUMN public.collector_channel.scale IS '数值缩放系数（仅 value_type=number）';
COMMENT ON COLUMN public.collector_channel.value_offset IS '工程值偏移量（仅 value_type=number）';
COMMENT ON COLUMN public.collector_channel.unit IS '工程单位（可空；空表示无量纲/状态量）';
COMMENT ON COLUMN public.collector_channel.icon IS '图标标识（如 i-ep:thermometer；空则按 value_type 取默认图标）';
COMMENT ON COLUMN public.collector_channel.decimal_places IS '展示小数位';
COMMENT ON COLUMN public.collector_channel.deadband IS '变化死区（0 表示不抑制）';
COMMENT ON COLUMN public.collector_channel.threshold_low IS '报警下限';
COMMENT ON COLUMN public.collector_channel.threshold_high IS '报警上限';
COMMENT ON COLUMN public.collector_channel.alarm_deadband IS '报警恢复死区';
COMMENT ON COLUMN public.collector_channel.alarm_enabled IS '是否启用阈值报警：0 否、1 是';
COMMENT ON COLUMN public.collector_channel.poll_interval_ms IS '独立轮询周期（NULL 继承设备）';
COMMENT ON COLUMN public.collector_channel.description IS '描述';
COMMENT ON COLUMN public.collector_channel.sort IS '排序值';
COMMENT ON COLUMN public.collector_channel.creator IS '创建者';
COMMENT ON COLUMN public.collector_channel.create_time IS '创建时间';
COMMENT ON COLUMN public.collector_channel.updater IS '更新者';
COMMENT ON COLUMN public.collector_channel.update_time IS '更新时间';
COMMENT ON COLUMN public.collector_channel.deleted IS '是否删除：0 否、1 是';


-- =============================================================================
-- 6. 分时段轮询策略
-- =============================================================================

CREATE TABLE public.collector_poll_schedule (
    id bigint NOT NULL,
    device_id bigint NOT NULL,
    sensor_id bigint DEFAULT 0 NOT NULL,
    channel_id bigint DEFAULT 0 NOT NULL,
    schedule_name character varying(100) NOT NULL,
    weekdays smallint DEFAULT 127 NOT NULL,
    start_time time(0) without time zone NOT NULL,
    end_time time(0) without time zone NOT NULL,
    interval_ms integer NOT NULL,
    priority smallint DEFAULT 0 NOT NULL,
    effective_start_date date,
    effective_end_date date,
    enabled smallint DEFAULT 1 NOT NULL,
    description character varying(255),
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_collector_poll_schedule PRIMARY KEY (id),
    CONSTRAINT chk_collector_poll_schedule_sensor CHECK (sensor_id >= 0),
    CONSTRAINT chk_collector_poll_schedule_channel CHECK (channel_id >= 0 AND (channel_id = 0 OR sensor_id > 0)),
    CONSTRAINT chk_collector_poll_schedule_weekdays CHECK (weekdays BETWEEN 0 AND 127),
    CONSTRAINT chk_collector_poll_schedule_interval CHECK (interval_ms >= 100),
    CONSTRAINT chk_collector_poll_schedule_dates CHECK (
        effective_start_date IS NULL OR effective_end_date IS NULL
        OR effective_start_date <= effective_end_date
    ),
    CONSTRAINT chk_collector_poll_schedule_enabled CHECK (enabled IN (0, 1))
);

CREATE INDEX idx_collector_poll_schedule_device
    ON public.collector_poll_schedule USING btree (device_id, sensor_id, channel_id, enabled)
    WHERE (deleted = 0);

CREATE INDEX idx_collector_poll_schedule_sensor
    ON public.collector_poll_schedule USING btree (sensor_id)
    WHERE (sensor_id > 0 AND deleted = 0);

CREATE INDEX idx_collector_poll_schedule_channel
    ON public.collector_poll_schedule USING btree (channel_id)
    WHERE (channel_id > 0 AND deleted = 0);

COMMENT ON TABLE public.collector_poll_schedule IS '采集分时段轮询策略表';
COMMENT ON COLUMN public.collector_poll_schedule.id IS '主键编号';
COMMENT ON COLUMN public.collector_poll_schedule.device_id IS '所属设备编号（所有级别必填）';
COMMENT ON COLUMN public.collector_poll_schedule.sensor_id IS '传感器编号；channel_id=0 时大于 0 表示传感器级，0 表示设备级';
COMMENT ON COLUMN public.collector_poll_schedule.channel_id IS '通道编号；大于 0 表示通道级，且 sensor_id 必须指向其所属传感器';
COMMENT ON COLUMN public.collector_poll_schedule.schedule_name IS '策略名称';
COMMENT ON COLUMN public.collector_poll_schedule.weekdays IS '生效星期位图：bit0=周一…bit6=周日，127=每天';
COMMENT ON COLUMN public.collector_poll_schedule.start_time IS '开始时间；start=end 表示全天';
COMMENT ON COLUMN public.collector_poll_schedule.end_time IS '结束时间；start>end 表示跨午夜';
COMMENT ON COLUMN public.collector_poll_schedule.interval_ms IS '该时间窗内轮询周期（毫秒）';
COMMENT ON COLUMN public.collector_poll_schedule.priority IS '优先级，数值越大越优先';
COMMENT ON COLUMN public.collector_poll_schedule.effective_start_date IS '生效开始日期（可空）';
COMMENT ON COLUMN public.collector_poll_schedule.effective_end_date IS '生效结束日期（可空）';
COMMENT ON COLUMN public.collector_poll_schedule.enabled IS '是否启用：0 否、1 是';
COMMENT ON COLUMN public.collector_poll_schedule.description IS '描述';
COMMENT ON COLUMN public.collector_poll_schedule.creator IS '创建者';
COMMENT ON COLUMN public.collector_poll_schedule.create_time IS '创建时间';
COMMENT ON COLUMN public.collector_poll_schedule.updater IS '更新者';
COMMENT ON COLUMN public.collector_poll_schedule.update_time IS '更新时间';
COMMENT ON COLUMN public.collector_poll_schedule.deleted IS '是否删除：0 否、1 是';


-- =============================================================================
-- 7. 配置版本快照
-- =============================================================================

CREATE TABLE public.collector_config_version (
    id bigint NOT NULL,
    version_no bigint NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    snapshot jsonb NOT NULL,
    checksum character varying(64) NOT NULL,
    change_note character varying(500),
    source character varying(16) DEFAULT 'manual'::character varying NOT NULL,
    based_on_version_id bigint,
    published_at timestamp(3) without time zone,
    published_by bigint,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_collector_config_version PRIMARY KEY (id),
    CONSTRAINT chk_collector_config_version_status CHECK (status IN (0, 1, 2)),
    CONSTRAINT chk_collector_config_version_source CHECK (source IN ('manual', 'import', 'rollback')),
    CONSTRAINT chk_collector_config_version_checksum CHECK (length(checksum) = 64),
    CONSTRAINT chk_collector_config_version_published CHECK (
        status <> 1 OR published_at IS NOT NULL
    )
);

CREATE UNIQUE INDEX uk_collector_config_version_no
    ON public.collector_config_version USING btree (version_no)
    WHERE (deleted = 0);

CREATE UNIQUE INDEX uk_collector_config_version_active
    ON public.collector_config_version USING btree (status)
    WHERE (status = 1 AND deleted = 0);

CREATE INDEX idx_collector_config_version_checksum
    ON public.collector_config_version USING btree (checksum);

COMMENT ON TABLE public.collector_config_version IS '采集配置版本快照表';
COMMENT ON COLUMN public.collector_config_version.id IS '主键编号';
COMMENT ON COLUMN public.collector_config_version.version_no IS '业务版本号（单调递增）';
COMMENT ON COLUMN public.collector_config_version.status IS '状态：0 草稿、1 已发布、2 已被取代';
COMMENT ON COLUMN public.collector_config_version.snapshot IS '全量配置快照（protocols/devices/sensors/channels/schedules），checksum 覆盖传感器及协议配置';
COMMENT ON COLUMN public.collector_config_version.checksum IS '快照规范化 JSON 的 SHA-256 十六进制值';
COMMENT ON COLUMN public.collector_config_version.change_note IS '变更说明';
COMMENT ON COLUMN public.collector_config_version.source IS '来源：manual/import/rollback';
COMMENT ON COLUMN public.collector_config_version.based_on_version_id IS '回滚/复制所基于的历史版本编号';
COMMENT ON COLUMN public.collector_config_version.published_at IS '发布时间';
COMMENT ON COLUMN public.collector_config_version.published_by IS '发布人用户编号';
COMMENT ON COLUMN public.collector_config_version.creator IS '创建者';
COMMENT ON COLUMN public.collector_config_version.create_time IS '创建时间';
COMMENT ON COLUMN public.collector_config_version.updater IS '更新者';
COMMENT ON COLUMN public.collector_config_version.update_time IS '更新时间';
COMMENT ON COLUMN public.collector_config_version.deleted IS '是否删除：0 否、1 是（已发布版本禁止删除）';


-- =============================================================================
-- 8. 原始采样（按天 RANGE 分区）
-- =============================================================================

CREATE TABLE public.collector_sample_raw (
    channel_id bigint NOT NULL,
    device_id bigint NOT NULL,
    collect_time timestamp(3) without time zone NOT NULL,
    value double precision,
    raw_value bigint,
    quality smallint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_collector_sample_raw PRIMARY KEY (channel_id, collect_time),
    CONSTRAINT chk_collector_sample_raw_value CHECK (value IS NULL OR is_finite(value)),
    CONSTRAINT chk_collector_sample_raw_quality CHECK (quality IN (0, 1, 2))
) PARTITION BY RANGE (collect_time);

-- 示例日分区：正式运行由维护任务按保留策略提前创建未来分区
CREATE TABLE public.collector_sample_raw_p20261113
    PARTITION OF public.collector_sample_raw
    FOR VALUES FROM ('2026-11-13 00:00:00') TO ('2026-11-14 00:00:00');

CREATE TABLE public.collector_sample_raw_p20261114
    PARTITION OF public.collector_sample_raw
    FOR VALUES FROM ('2026-11-14 00:00:00') TO ('2026-11-15 00:00:00');

-- 默认分区：兜底，正常运行时必须保持为空
CREATE TABLE public.collector_sample_raw_default
    PARTITION OF public.collector_sample_raw DEFAULT;

-- 父表索引会自动下发到现有与后续分区
CREATE INDEX idx_collector_sample_raw_device_time
    ON public.collector_sample_raw USING btree (device_id, collect_time);

COMMENT ON TABLE public.collector_sample_raw IS '原始采样数据表（按天 RANGE 分区）';
COMMENT ON COLUMN public.collector_sample_raw.channel_id IS '通道编号（逻辑关联 collector_channel.id）';
COMMENT ON COLUMN public.collector_sample_raw.device_id IS '设备编号（冗余，用于设备级查询）';
COMMENT ON COLUMN public.collector_sample_raw.collect_time IS '采样时间（Asia/Shanghai 本地时间，毫秒）';
COMMENT ON COLUMN public.collector_sample_raw.value IS '工程值（缩放/偏移后的 double）';
COMMENT ON COLUMN public.collector_sample_raw.raw_value IS '整数原始值（u/i/bit；浮点类型可为空）';
COMMENT ON COLUMN public.collector_sample_raw.quality IS '质量码：0 good、1 uncertain、2 bad';


-- =============================================================================
-- 9. 小时聚合
-- =============================================================================

CREATE TABLE public.collector_aggregate_hour (
    channel_id bigint NOT NULL,
    device_id bigint NOT NULL,
    bucket_time timestamp(3) without time zone NOT NULL,
    sample_count integer DEFAULT 0 NOT NULL,
    good_count integer DEFAULT 0 NOT NULL,
    bad_count integer DEFAULT 0 NOT NULL,
    value_avg double precision,
    value_min double precision,
    value_max double precision,
    value_sum double precision,
    value_first double precision,
    value_last double precision,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_collector_aggregate_hour PRIMARY KEY (channel_id, bucket_time),
    CONSTRAINT chk_collector_aggregate_hour_count CHECK (
        sample_count >= 0 AND good_count >= 0 AND bad_count >= 0
    ),
    CONSTRAINT chk_collector_aggregate_hour_finite CHECK (
        (value_avg IS NULL OR is_finite(value_avg))
        AND (value_min IS NULL OR is_finite(value_min))
        AND (value_max IS NULL OR is_finite(value_max))
        AND (value_sum IS NULL OR is_finite(value_sum))
        AND (value_first IS NULL OR is_finite(value_first))
        AND (value_last IS NULL OR is_finite(value_last))
    )
);

CREATE INDEX idx_collector_aggregate_hour_device_time
    ON public.collector_aggregate_hour USING btree (device_id, bucket_time);

CREATE INDEX idx_collector_aggregate_hour_bucket
    ON public.collector_aggregate_hour USING btree (bucket_time);

COMMENT ON TABLE public.collector_aggregate_hour IS '小时聚合数据表（统计值仅用 quality=0 且 value 非 NULL 的 raw 行；无有效统计值时为 NULL）';
COMMENT ON COLUMN public.collector_aggregate_hour.channel_id IS '通道编号';
COMMENT ON COLUMN public.collector_aggregate_hour.device_id IS '设备编号（冗余）';
COMMENT ON COLUMN public.collector_aggregate_hour.bucket_time IS '小时桶起始时间';
COMMENT ON COLUMN public.collector_aggregate_hour.sample_count IS '该桶全部 raw 行数（含 good/uncertain/bad）';
COMMENT ON COLUMN public.collector_aggregate_hour.good_count IS 'quality=0 的 raw 行数';
COMMENT ON COLUMN public.collector_aggregate_hour.bad_count IS 'quality=2 的 raw 行数；uncertain 数由 sample-good-bad 推导';
COMMENT ON COLUMN public.collector_aggregate_hour.value_avg IS '平均值';
COMMENT ON COLUMN public.collector_aggregate_hour.value_min IS '最小值';
COMMENT ON COLUMN public.collector_aggregate_hour.value_max IS '最大值';
COMMENT ON COLUMN public.collector_aggregate_hour.value_sum IS '求和（用于天聚合与能耗类指标）';
COMMENT ON COLUMN public.collector_aggregate_hour.value_first IS '桶内首个值';
COMMENT ON COLUMN public.collector_aggregate_hour.value_last IS '桶内最后值';
COMMENT ON COLUMN public.collector_aggregate_hour.update_time IS '最后重算时间';


-- =============================================================================
-- 10. 天聚合
-- =============================================================================

CREATE TABLE public.collector_aggregate_day (
    channel_id bigint NOT NULL,
    device_id bigint NOT NULL,
    bucket_date date NOT NULL,
    sample_count integer DEFAULT 0 NOT NULL,
    good_count integer DEFAULT 0 NOT NULL,
    bad_count integer DEFAULT 0 NOT NULL,
    value_avg double precision,
    value_min double precision,
    value_max double precision,
    value_sum double precision,
    value_first double precision,
    value_last double precision,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_collector_aggregate_day PRIMARY KEY (channel_id, bucket_date),
    CONSTRAINT chk_collector_aggregate_day_count CHECK (
        sample_count >= 0 AND good_count >= 0 AND bad_count >= 0
    ),
    CONSTRAINT chk_collector_aggregate_day_finite CHECK (
        (value_avg IS NULL OR is_finite(value_avg))
        AND (value_min IS NULL OR is_finite(value_min))
        AND (value_max IS NULL OR is_finite(value_max))
        AND (value_sum IS NULL OR is_finite(value_sum))
        AND (value_first IS NULL OR is_finite(value_first))
        AND (value_last IS NULL OR is_finite(value_last))
    )
);

CREATE INDEX idx_collector_aggregate_day_device_date
    ON public.collector_aggregate_day USING btree (device_id, bucket_date);

CREATE INDEX idx_collector_aggregate_day_bucket
    ON public.collector_aggregate_day USING btree (bucket_date);

COMMENT ON TABLE public.collector_aggregate_day IS '天聚合数据表（优先小时、缺小时回退 raw；与小时统计保持同口径）';
COMMENT ON COLUMN public.collector_aggregate_day.channel_id IS '通道编号';
COMMENT ON COLUMN public.collector_aggregate_day.device_id IS '设备编号（冗余）';
COMMENT ON COLUMN public.collector_aggregate_day.bucket_date IS '天桶日期';
COMMENT ON COLUMN public.collector_aggregate_day.sample_count IS '该桶全部 raw 行数（含 good/uncertain/bad）';
COMMENT ON COLUMN public.collector_aggregate_day.good_count IS 'quality=0 的 raw 行数';
COMMENT ON COLUMN public.collector_aggregate_day.bad_count IS 'quality=2 的 raw 行数；uncertain 数由 sample-good-bad 推导';
COMMENT ON COLUMN public.collector_aggregate_day.value_avg IS '加权平均值';
COMMENT ON COLUMN public.collector_aggregate_day.value_min IS '最小值';
COMMENT ON COLUMN public.collector_aggregate_day.value_max IS '最大值';
COMMENT ON COLUMN public.collector_aggregate_day.value_sum IS '求和';
COMMENT ON COLUMN public.collector_aggregate_day.value_first IS '当日首个有效统计值（仅 quality=0 且 value 非 NULL，按 collect_time）';
COMMENT ON COLUMN public.collector_aggregate_day.value_last IS '当日最后有效统计值（仅 quality=0 且 value 非 NULL，按 collect_time）';
COMMENT ON COLUMN public.collector_aggregate_day.update_time IS '最后重算时间';


-- =============================================================================
-- 11. 聚合水位与补跑状态
-- =============================================================================

CREATE TABLE public.collector_aggregate_watermark (
    agg_type character varying(8) NOT NULL,
    channel_id bigint NOT NULL,
    device_id bigint NOT NULL,
    watermark_time timestamp(3) without time zone,
    backfill_from timestamp(3) without time zone,
    backfill_to timestamp(3) without time zone,
    status smallint DEFAULT 0 NOT NULL,
    retry_count integer DEFAULT 0 NOT NULL,
    last_error character varying(500),
    locked_until timestamp(3) without time zone,
    last_success_time timestamp(3) without time zone,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_collector_aggregate_watermark PRIMARY KEY (agg_type, channel_id),
    CONSTRAINT chk_collector_aggregate_watermark_type CHECK (agg_type IN ('hour', 'day')),
    CONSTRAINT chk_collector_aggregate_watermark_status CHECK (status IN (0, 1, 2)),
    CONSTRAINT chk_collector_aggregate_watermark_retry CHECK (retry_count >= 0)
);

CREATE INDEX idx_collector_aggregate_watermark_status
    ON public.collector_aggregate_watermark USING btree (status, locked_until);

CREATE INDEX idx_collector_aggregate_watermark_device
    ON public.collector_aggregate_watermark USING btree (device_id);

COMMENT ON TABLE public.collector_aggregate_watermark IS '聚合水位与补跑状态表';
COMMENT ON COLUMN public.collector_aggregate_watermark.agg_type IS '聚合类型：hour / day';
COMMENT ON COLUMN public.collector_aggregate_watermark.channel_id IS '通道编号';
COMMENT ON COLUMN public.collector_aggregate_watermark.device_id IS '设备编号（冗余）';
COMMENT ON COLUMN public.collector_aggregate_watermark.watermark_time IS '最新已成功提交桶的结束时间（exclusive）；NULL 表示从未成功';
COMMENT ON COLUMN public.collector_aggregate_watermark.backfill_from IS '手工补跑起始时间（可空，含）';
COMMENT ON COLUMN public.collector_aggregate_watermark.backfill_to IS '手工补跑结束时间（可空，不含）';
COMMENT ON COLUMN public.collector_aggregate_watermark.status IS '状态：0 空闲、1 运行中、2 失败';
COMMENT ON COLUMN public.collector_aggregate_watermark.retry_count IS '连续失败次数';
COMMENT ON COLUMN public.collector_aggregate_watermark.last_error IS '最近一次错误信息';
COMMENT ON COLUMN public.collector_aggregate_watermark.locked_until IS '任务锁到期时间，防止并发重复聚合';
COMMENT ON COLUMN public.collector_aggregate_watermark.last_success_time IS '最近一次成功时间';
COMMENT ON COLUMN public.collector_aggregate_watermark.update_time IS '更新时间';


-- =============================================================================
-- 12. 报警事件
-- =============================================================================

CREATE TABLE public.collector_alarm (
    id bigint NOT NULL,
    device_id bigint NOT NULL,
    sensor_id bigint DEFAULT 0 NOT NULL,
    channel_id bigint DEFAULT 0 NOT NULL,
    alarm_type character varying(32) NOT NULL,
    level smallint DEFAULT 2 NOT NULL,
    dedupe_key character varying(128) NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    message character varying(500),
    trigger_value double precision,
    threshold_value double precision,
    triggered_at timestamp(3) without time zone NOT NULL,
    last_seen_at timestamp(3) without time zone,
    occurrence_count integer DEFAULT 1 NOT NULL,
    acknowledged_at timestamp(3) without time zone,
    acknowledged_by bigint,
    cleared_at timestamp(3) without time zone,
    clear_reason character varying(255),
    CONSTRAINT pk_collector_alarm PRIMARY KEY (id),
    CONSTRAINT chk_collector_alarm_sensor CHECK (sensor_id >= 0),
    CONSTRAINT chk_collector_alarm_channel CHECK (channel_id >= 0 AND (channel_id = 0 OR sensor_id > 0)),
    CONSTRAINT chk_collector_alarm_value_finite CHECK (
        (trigger_value IS NULL OR is_finite(trigger_value))
        AND (threshold_value IS NULL OR is_finite(threshold_value))
    ),
    CONSTRAINT chk_collector_alarm_level CHECK (level IN (1, 2, 3)),
    CONSTRAINT chk_collector_alarm_status CHECK (status IN (0, 1, 2)),
    CONSTRAINT chk_collector_alarm_cleared CHECK ((status = 2) = (cleared_at IS NOT NULL)),
    CONSTRAINT chk_collector_alarm_count CHECK (occurrence_count >= 1)
);

CREATE UNIQUE INDEX uk_collector_alarm_active_dedupe
    ON public.collector_alarm USING btree (dedupe_key)
    WHERE (cleared_at IS NULL);

CREATE INDEX idx_collector_alarm_device_time
    ON public.collector_alarm USING btree (device_id, triggered_at DESC);

CREATE INDEX idx_collector_alarm_sensor_time
    ON public.collector_alarm USING btree (sensor_id, triggered_at DESC);

CREATE INDEX idx_collector_alarm_channel_time
    ON public.collector_alarm USING btree (channel_id, triggered_at DESC);

CREATE INDEX idx_collector_alarm_active
    ON public.collector_alarm USING btree (status, triggered_at DESC)
    WHERE (cleared_at IS NULL);

COMMENT ON TABLE public.collector_alarm IS '采集报警事件表';
COMMENT ON COLUMN public.collector_alarm.id IS '主键编号';
COMMENT ON COLUMN public.collector_alarm.device_id IS '设备编号（所有级别必填）';
COMMENT ON COLUMN public.collector_alarm.sensor_id IS '传感器编号；channel_id=0 时大于 0 表示传感器级，0 表示设备级';
COMMENT ON COLUMN public.collector_alarm.channel_id IS '通道编号；大于 0 表示通道级，且 sensor_id 必须指向其所属传感器';
COMMENT ON COLUMN public.collector_alarm.alarm_type IS '报警类型，如 threshold_high/threshold_low/comm_timeout/device_offline/quality_bad';
COMMENT ON COLUMN public.collector_alarm.level IS '级别：1 提示、2 警告、3 严重';
COMMENT ON COLUMN public.collector_alarm.dedupe_key IS '活动报警去重键（dev:{deviceId}:sensor:{sensorId}:ch:{channelId}:type:{alarmType}，包含 sensor 维度）';
COMMENT ON COLUMN public.collector_alarm.status IS '状态：0 活动、1 已确认、2 已清除';
COMMENT ON COLUMN public.collector_alarm.message IS '报警描述';
COMMENT ON COLUMN public.collector_alarm.trigger_value IS '触发时数值';
COMMENT ON COLUMN public.collector_alarm.threshold_value IS '触发阈值';
COMMENT ON COLUMN public.collector_alarm.triggered_at IS '首次触发时间';
COMMENT ON COLUMN public.collector_alarm.last_seen_at IS '最近一次触发时间';
COMMENT ON COLUMN public.collector_alarm.occurrence_count IS '活动期间重复触发次数';
COMMENT ON COLUMN public.collector_alarm.acknowledged_at IS '确认时间';
COMMENT ON COLUMN public.collector_alarm.acknowledged_by IS '确认人用户编号';
COMMENT ON COLUMN public.collector_alarm.cleared_at IS '清除时间；为空表示活动报警';
COMMENT ON COLUMN public.collector_alarm.clear_reason IS '清除原因';


-- =============================================================================
-- 13. 设备当前状态
-- =============================================================================

CREATE TABLE public.collector_device_status (
    device_id bigint NOT NULL,
    online smallint DEFAULT 0 NOT NULL,
    last_online_at timestamp(3) without time zone,
    last_offline_at timestamp(3) without time zone,
    last_success_at timestamp(3) without time zone,
    last_error character varying(500),
    consecutive_failures integer DEFAULT 0 NOT NULL,
    last_latency_ms integer,
    config_version_id bigint,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_collector_device_status PRIMARY KEY (device_id),
    CONSTRAINT chk_collector_device_status_online CHECK (online IN (0, 1)),
    CONSTRAINT chk_collector_device_status_failures CHECK (consecutive_failures >= 0)
);

COMMENT ON TABLE public.collector_device_status IS '采集设备当前状态表';
COMMENT ON COLUMN public.collector_device_status.device_id IS '设备编号（主键）';
COMMENT ON COLUMN public.collector_device_status.online IS '是否在线：0 否、1 是';
COMMENT ON COLUMN public.collector_device_status.last_online_at IS '最近上线时间';
COMMENT ON COLUMN public.collector_device_status.last_offline_at IS '最近离线时间';
COMMENT ON COLUMN public.collector_device_status.last_success_at IS '最近一次采集成功时间';
COMMENT ON COLUMN public.collector_device_status.last_error IS '最近一次错误信息';
COMMENT ON COLUMN public.collector_device_status.consecutive_failures IS '连续失败次数';
COMMENT ON COLUMN public.collector_device_status.last_latency_ms IS '最近一次请求耗时（毫秒）';
COMMENT ON COLUMN public.collector_device_status.config_version_id IS '当前加载配置版本编号';
COMMENT ON COLUMN public.collector_device_status.update_time IS '更新时间';


-- =============================================================================
-- 14. 采集器心跳
-- =============================================================================

CREATE TABLE public.collector_heartbeat (
    id bigint NOT NULL,
    instance_id character varying(64) NOT NULL,
    heartbeat_time timestamp(3) without time zone NOT NULL,
    status character varying(16) DEFAULT 'running'::character varying NOT NULL,
    write_lag_ms integer,
    device_total integer DEFAULT 0 NOT NULL,
    device_online integer DEFAULT 0 NOT NULL,
    cpu_percent numeric(5,2),
    mem_percent numeric(5,2),
    disk_free_bytes bigint,
    disk_used_percent numeric(5,2),
    temperature_c numeric(5,2),
    config_version_id bigint,
    config_checksum character varying(64),
    collector_version character varying(32),
    message character varying(255),
    CONSTRAINT pk_collector_heartbeat PRIMARY KEY (id),
    CONSTRAINT chk_collector_heartbeat_status CHECK (status IN ('starting', 'running', 'degraded', 'stopped')),
    CONSTRAINT chk_collector_heartbeat_finite CHECK (
        (cpu_percent IS NULL OR is_finite(cpu_percent))
        AND (mem_percent IS NULL OR is_finite(mem_percent))
        AND (disk_used_percent IS NULL OR is_finite(disk_used_percent))
        AND (temperature_c IS NULL OR is_finite(temperature_c))
    ),
    CONSTRAINT chk_collector_heartbeat_device CHECK (device_total >= 0 AND device_online >= 0)
);

CREATE INDEX idx_collector_heartbeat_instance_time
    ON public.collector_heartbeat USING btree (instance_id, heartbeat_time DESC);

CREATE INDEX idx_collector_heartbeat_time
    ON public.collector_heartbeat USING btree (heartbeat_time);

COMMENT ON TABLE public.collector_heartbeat IS '采集器心跳与资源监控表';
COMMENT ON COLUMN public.collector_heartbeat.id IS '主键编号';
COMMENT ON COLUMN public.collector_heartbeat.instance_id IS '采集器实例标识（主机+进程/启动批次）';
COMMENT ON COLUMN public.collector_heartbeat.heartbeat_time IS '心跳时间';
COMMENT ON COLUMN public.collector_heartbeat.status IS '状态：starting/running/degraded/stopped';
COMMENT ON COLUMN public.collector_heartbeat.write_lag_ms IS '写入延迟（毫秒）';
COMMENT ON COLUMN public.collector_heartbeat.device_total IS '配置设备总数';
COMMENT ON COLUMN public.collector_heartbeat.device_online IS '在线设备数';
COMMENT ON COLUMN public.collector_heartbeat.cpu_percent IS 'CPU 使用率（%）';
COMMENT ON COLUMN public.collector_heartbeat.mem_percent IS '内存使用率（%）';
COMMENT ON COLUMN public.collector_heartbeat.disk_free_bytes IS '磁盘剩余字节';
COMMENT ON COLUMN public.collector_heartbeat.disk_used_percent IS '磁盘使用率（%）';
COMMENT ON COLUMN public.collector_heartbeat.temperature_c IS '板端温度（摄氏度）';
COMMENT ON COLUMN public.collector_heartbeat.config_version_id IS '当前加载配置版本编号';
COMMENT ON COLUMN public.collector_heartbeat.config_checksum IS '当前加载配置 checksum';
COMMENT ON COLUMN public.collector_heartbeat.collector_version IS 'collector 程序版本';
COMMENT ON COLUMN public.collector_heartbeat.message IS '附加状态信息';


-- =============================================================================
-- 15. 命令下发与结果
-- =============================================================================

CREATE TABLE public.collector_command (
    id bigint NOT NULL,
    device_id bigint NOT NULL,
    sensor_id bigint DEFAULT 0 NOT NULL,
    channel_id bigint DEFAULT 0 NOT NULL,
    command_type character varying(32) NOT NULL,
    payload jsonb,
    status smallint DEFAULT 0 NOT NULL,
    requested_by bigint,
    requested_at timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expire_time timestamp(3) without time zone DEFAULT (CURRENT_TIMESTAMP + INTERVAL '60 seconds') NOT NULL,
    dispatched_at timestamp(3) without time zone,
    finished_at timestamp(3) without time zone,
    executed_by character varying(64),
    result jsonb,
    error_message character varying(500),
    CONSTRAINT pk_collector_command PRIMARY KEY (id),
    CONSTRAINT chk_collector_command_sensor CHECK (sensor_id >= 0),
    CONSTRAINT chk_collector_command_channel CHECK (channel_id >= 0 AND (channel_id = 0 OR sensor_id > 0)),
    CONSTRAINT chk_collector_command_reload_scope CHECK (
        command_type <> 'reload_config' OR (sensor_id = 0 AND channel_id = 0)
    ),
    CONSTRAINT chk_collector_command_status CHECK (status IN (0, 1, 2, 3, 4, 5))
);

CREATE INDEX idx_collector_command_pending
    ON public.collector_command USING btree (status, requested_at)
    WHERE (status = 0);

CREATE INDEX idx_collector_command_device_time
    ON public.collector_command USING btree (device_id, requested_at DESC);

CREATE INDEX idx_collector_command_sensor_time
    ON public.collector_command USING btree (sensor_id, requested_at DESC);

COMMENT ON TABLE public.collector_command IS '采集命令下发与结果表';
COMMENT ON COLUMN public.collector_command.id IS '主键编号';
COMMENT ON COLUMN public.collector_command.device_id IS '目标设备编号（命令最高作用域，所有命令必填）';
COMMENT ON COLUMN public.collector_command.sensor_id IS '目标传感器编号；channel_id=0 时大于 0 表示传感器级，0 表示设备级';
COMMENT ON COLUMN public.collector_command.channel_id IS '目标通道编号；大于 0 时 sensor_id 必须指向其所属传感器';
COMMENT ON COLUMN public.collector_command.command_type IS '命令类型，如 test_read/write_register/reconnect/reload_config；reload_config 仅允许 sensor_id=0、channel_id=0 的设备级命令';
COMMENT ON COLUMN public.collector_command.payload IS '命令参数 JSON';
COMMENT ON COLUMN public.collector_command.status IS '状态：0 待执行、1 已下发、2 成功、3 失败、4 超时、5 取消';
COMMENT ON COLUMN public.collector_command.requested_by IS '发起人用户编号';
COMMENT ON COLUMN public.collector_command.requested_at IS '发起时间';
COMMENT ON COLUMN public.collector_command.expire_time IS '过期时间，过期后 collector 不得执行';
COMMENT ON COLUMN public.collector_command.dispatched_at IS 'collector 领取时间';
COMMENT ON COLUMN public.collector_command.finished_at IS '执行结束时间';
COMMENT ON COLUMN public.collector_command.executed_by IS '执行该命令的采集器实例';
COMMENT ON COLUMN public.collector_command.result IS '执行结果 JSON';
COMMENT ON COLUMN public.collector_command.error_message IS '失败原因';


-- =============================================================================
-- 16. 保留策略
-- =============================================================================

CREATE TABLE public.collector_retention_policy (
    id bigint NOT NULL,
    data_class character varying(32) NOT NULL,
    retention_days integer NOT NULL,
    partition_lead_days integer DEFAULT 3 NOT NULL,
    action character varying(16) DEFAULT 'delete_rows'::character varying NOT NULL,
    enabled smallint DEFAULT 1 NOT NULL,
    description character varying(255),
    last_run_at timestamp(3) without time zone,
    last_run_result character varying(255),
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_collector_retention_policy PRIMARY KEY (id),
    CONSTRAINT chk_collector_retention_policy_class CHECK (
        data_class IN ('sample_raw', 'aggregate_hour', 'aggregate_day', 'alarm', 'heartbeat', 'command')
    ),
    CONSTRAINT chk_collector_retention_policy_days CHECK (retention_days >= 0),
    CONSTRAINT chk_collector_retention_policy_lead CHECK (partition_lead_days BETWEEN 0 AND 30),
    CONSTRAINT chk_collector_retention_policy_action CHECK (
        (data_class = 'sample_raw' AND action = 'drop_partition')
        OR (data_class <> 'sample_raw' AND action = 'delete_rows')
    ),
    CONSTRAINT chk_collector_retention_policy_enabled CHECK (enabled IN (0, 1))
);

CREATE UNIQUE INDEX uk_collector_retention_policy_class
    ON public.collector_retention_policy USING btree (data_class)
    WHERE (deleted = 0);

COMMENT ON TABLE public.collector_retention_policy IS '采集数据保留策略表';
COMMENT ON COLUMN public.collector_retention_policy.id IS '主键编号';
COMMENT ON COLUMN public.collector_retention_policy.data_class IS '数据类：sample_raw/aggregate_hour/aggregate_day/alarm/heartbeat/command';
COMMENT ON COLUMN public.collector_retention_policy.retention_days IS '保留天数；0 表示永久保留';
COMMENT ON COLUMN public.collector_retention_policy.partition_lead_days IS '未来分区提前创建天数；由独立分区预建调度使用，不受 enabled 影响';
COMMENT ON COLUMN public.collector_retention_policy.action IS '清理动作：sample_raw 只能 drop_partition，其他 data_class 只能 delete_rows';
COMMENT ON COLUMN public.collector_retention_policy.enabled IS '是否启用过期清理：0 否、1 是；不影响未来分区预建';
COMMENT ON COLUMN public.collector_retention_policy.description IS '策略说明';
COMMENT ON COLUMN public.collector_retention_policy.last_run_at IS '最近执行时间';
COMMENT ON COLUMN public.collector_retention_policy.last_run_result IS '最近执行结果摘要';
COMMENT ON COLUMN public.collector_retention_policy.creator IS '创建者';
COMMENT ON COLUMN public.collector_retention_policy.create_time IS '创建时间';
COMMENT ON COLUMN public.collector_retention_policy.updater IS '更新者';
COMMENT ON COLUMN public.collector_retention_policy.update_time IS '更新时间';
COMMENT ON COLUMN public.collector_retention_policy.deleted IS '是否删除：0 否、1 是';


-- =============================================================================
-- 17. 首期种子数据
-- =============================================================================

-- 17.1 协议注册表：仅首期支持的 Modbus RTU / Modbus TCP。
-- id 使用序列生成，保证后续通过序列新增协议时编号不冲突。
-- connection_schema / channel_schema 为合法 JSON Schema（Draft 2020-12），
-- 包含必填字段、枚举、默认值与示例；Service 保存与发布配置时按此校验。
INSERT INTO public.collector_protocol (
    id, protocol_code, protocol_name, transport_type, driver_key,
    connection_schema, channel_schema, protocol_version, enabled, sort, description
) VALUES (
    nextval('public.collector_protocol_seq'),
    'modbus_rtu',
    'Modbus RTU',
    'serial',
    'modbus_rtu_driver',
    $json$
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://collector.local/schema/modbus_rtu/connection.json",
  "title": "Modbus RTU 连接参数",
  "type": "object",
  "additionalProperties": false,
  "required": ["serialPort", "baudRate", "dataBits", "stopBits", "parity", "unitId"],
  "properties": {
    "serialPort": {
      "type": "string",
      "title": "串口设备",
      "description": "串口设备路径，例如 /dev/ttyS1",
      "minLength": 1,
      "examples": ["/dev/ttyS1", "/dev/ttyUSB0"]
    },
    "baudRate": {
      "type": "integer",
      "title": "波特率",
      "enum": [1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200],
      "default": 9600
    },
    "dataBits": {
      "type": "integer",
      "title": "数据位",
      "enum": [5, 6, 7, 8],
      "default": 8
    },
    "stopBits": {
      "type": "integer",
      "title": "停止位",
      "enum": [1, 2],
      "default": 1
    },
    "parity": {
      "type": "string",
      "title": "校验方式",
      "enum": ["none", "even", "odd"],
      "default": "none"
    },
    "unitId": {
      "type": "integer",
      "title": "从站地址",
      "minimum": 1,
      "maximum": 247,
      "default": 1
    },
    "interFrameDelayMs": {
      "type": "integer",
      "title": "帧间延迟（毫秒）",
      "minimum": 0,
      "maximum": 1000,
      "default": 0
    }
  },
  "x-ui": {
    "order": ["serialPort", "baudRate", "dataBits", "stopBits", "parity", "unitId", "interFrameDelayMs"],
    "groups": [
      {"title": "串口参数", "fields": ["serialPort", "baudRate", "dataBits", "stopBits", "parity"]},
      {"title": "从站参数", "fields": ["unitId", "interFrameDelayMs"]}
    ]
  }
}
$json$::jsonb,
    $json$
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://collector.local/schema/modbus_rtu/channel.json",
  "title": "Modbus RTU 通道参数",
  "type": "object",
  "additionalProperties": false,
  "required": ["registerArea", "functionCode", "startAddress", "registerCount", "dataType", "byteOrder"],
  "properties": {
    "registerArea": {
      "type": "string",
      "title": "寄存器区",
      "enum": ["holding", "input", "coil", "discrete"],
      "default": "holding",
      "examples": ["holding"]
    },
    "functionCode": {
      "type": "integer",
      "title": "功能码",
      "enum": [1, 2, 3, 4],
      "default": 3
    },
    "startAddress": {
      "type": "integer",
      "title": "起始地址",
      "minimum": 0,
      "maximum": 65535,
      "examples": [0, 100]
    },
    "registerCount": {
      "type": "integer",
      "title": "寄存器数量",
      "minimum": 1,
      "maximum": 125,
      "default": 1
    },
    "dataType": {
      "type": "string",
      "title": "数据类型",
      "enum": ["u16", "i16", "u32", "i32", "f32", "f64", "bit"],
      "default": "u16"
    },
    "byteOrder": {
      "type": "string",
      "title": "字节序",
      "enum": ["ABCD", "CDAB", "BADC", "DCBA"],
      "default": "ABCD"
    },
    "bitIndex": {
      "type": "integer",
      "title": "位序号",
      "description": "dataType=bit 时使用，取值 0-15",
      "minimum": 0,
      "maximum": 15
    }
  },
  "x-ui": {
    "order": ["registerArea", "functionCode", "startAddress", "registerCount", "dataType", "byteOrder", "bitIndex"],
    "groups": [
      {"title": "寄存器寻址", "fields": ["registerArea", "functionCode", "startAddress", "registerCount"]},
      {"title": "数值解析", "fields": ["dataType", "byteOrder", "bitIndex"]}
    ]
  }
}
$json$::jsonb,
    '1.0.0',
    1,
    10,
    '串口 Modbus RTU 从站；连接参数含串口、波特率、数据位、停止位、校验与从站地址'
), (
    nextval('public.collector_protocol_seq'),
    'modbus_tcp',
    'Modbus TCP',
    'tcp',
    'modbus_tcp_driver',
    $json$
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://collector.local/schema/modbus_tcp/connection.json",
  "title": "Modbus TCP 连接参数",
  "type": "object",
  "additionalProperties": false,
  "required": ["host", "port", "unitId"],
  "properties": {
    "host": {
      "type": "string",
      "title": "主机地址",
      "description": "IP 地址或可解析主机名",
      "minLength": 1,
      "examples": ["192.168.1.10", "plc.local"]
    },
    "port": {
      "type": "integer",
      "title": "端口",
      "minimum": 1,
      "maximum": 65535,
      "default": 502
    },
    "unitId": {
      "type": "integer",
      "title": "从站地址",
      "minimum": 1,
      "maximum": 247,
      "default": 1
    },
    "blockMergeEnabled": {
      "type": "boolean",
      "title": "启用块合并",
      "description": "将连续寄存器合并为批量读取，减少请求次数",
      "default": true
    },
    "blockMaxRegisters": {
      "type": "integer",
      "title": "单次最大寄存器数",
      "minimum": 1,
      "maximum": 125,
      "default": 64
    },
    "blockMaxGap": {
      "type": "integer",
      "title": "块合并最大间隔",
      "minimum": 0,
      "maximum": 125,
      "default": 8
    },
    "interFrameDelayMs": {
      "type": "integer",
      "title": "帧间延迟（毫秒）",
      "minimum": 0,
      "maximum": 1000,
      "default": 0
    }
  },
  "x-ui": {
    "order": ["host", "port", "unitId", "blockMergeEnabled", "blockMaxRegisters", "blockMaxGap", "interFrameDelayMs"],
    "groups": [
      {"title": "网络参数", "fields": ["host", "port"]},
      {"title": "从站参数", "fields": ["unitId"]},
      {"title": "读取优化", "fields": ["blockMergeEnabled", "blockMaxRegisters", "blockMaxGap", "interFrameDelayMs"]}
    ]
  }
}
$json$::jsonb,
    $json$
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://collector.local/schema/modbus_tcp/channel.json",
  "title": "Modbus TCP 通道参数",
  "type": "object",
  "additionalProperties": false,
  "required": ["registerArea", "functionCode", "startAddress", "registerCount", "dataType", "byteOrder"],
  "properties": {
    "registerArea": {
      "type": "string",
      "title": "寄存器区",
      "enum": ["holding", "input", "coil", "discrete"],
      "default": "holding",
      "examples": ["holding"]
    },
    "functionCode": {
      "type": "integer",
      "title": "功能码",
      "enum": [1, 2, 3, 4],
      "default": 3
    },
    "startAddress": {
      "type": "integer",
      "title": "起始地址",
      "minimum": 0,
      "maximum": 65535,
      "examples": [0, 100]
    },
    "registerCount": {
      "type": "integer",
      "title": "寄存器数量",
      "minimum": 1,
      "maximum": 125,
      "default": 1
    },
    "dataType": {
      "type": "string",
      "title": "数据类型",
      "enum": ["u16", "i16", "u32", "i32", "f32", "f64", "bit"],
      "default": "u16"
    },
    "byteOrder": {
      "type": "string",
      "title": "字节序",
      "enum": ["ABCD", "CDAB", "BADC", "DCBA"],
      "default": "ABCD"
    },
    "bitIndex": {
      "type": "integer",
      "title": "位序号",
      "description": "dataType=bit 时使用，取值 0-15",
      "minimum": 0,
      "maximum": 15
    }
  },
  "x-ui": {
    "order": ["registerArea", "functionCode", "startAddress", "registerCount", "dataType", "byteOrder", "bitIndex"],
    "groups": [
      {"title": "寄存器寻址", "fields": ["registerArea", "functionCode", "startAddress", "registerCount"]},
      {"title": "数值解析", "fields": ["dataType", "byteOrder", "bitIndex"]}
    ]
  }
}
$json$::jsonb,
    '1.0.0',
    1,
    20,
    'TCP Modbus TCP 从站；连接参数含主机、端口、从站地址与块读取优化参数'
);

-- 17.2 保留策略初始建议（对应设计稿容量结论）：
--   sample_raw     30 天，按天分区，过期分区整体 DROP；
--   aggregate_hour 365 天，按 bucket_time 分批 DELETE；
--   aggregate_day  0 表示永久保留，不做清理；
--   alarm          仅清理已清除且超过 180 天的记录；
--   heartbeat      7 天；command 30 天。
INSERT INTO public.collector_retention_policy (
    id, data_class, retention_days, partition_lead_days, action, enabled, description
) VALUES
(
    nextval('public.collector_retention_policy_seq'),
    'sample_raw',
    30,
    7,
    'drop_partition',
    1,
    '原始采样：按天分区，保留 30 天，过期分区整体 DROP'
), (
    nextval('public.collector_retention_policy_seq'),
    'aggregate_hour',
    365,
    3,
    'delete_rows',
    1,
    '小时聚合：保留 365 天，按 bucket_time 分批删除'
), (
    nextval('public.collector_retention_policy_seq'),
    'aggregate_day',
    0,
    3,
    'delete_rows',
    1,
    '天聚合：永久保留（0 表示不清理）'
), (
    nextval('public.collector_retention_policy_seq'),
    'alarm',
    180,
    3,
    'delete_rows',
    1,
    '报警：仅清理已清除（cleared_at 非空）且超过 180 天的记录'
), (
    nextval('public.collector_retention_policy_seq'),
    'heartbeat',
    7,
    3,
    'delete_rows',
    1,
    '心跳：保留 7 天'
), (
    nextval('public.collector_retention_policy_seq'),
    'command',
    30,
    3,
    'delete_rows',
    1,
    '命令：保留 30 天'
);

-- 17.3 聚合水位：主键为 (agg_type, channel_id)，按通道分别记录。
-- 空库尚无设备/通道，故不预置 hour/day 行；通道创建或首个配置版本发布后，
-- 由聚合任务为每个 number 通道初始化 (hour, channel_id) 与 (day, channel_id) 水位，watermark_time 均为 NULL。
