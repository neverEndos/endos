/*
 采集器阈值结构迁移脚本（PostgreSQL 17；用于已有 collector 数据的库）

 背景：
   旧版 collector_channel 只有 threshold_low / threshold_high 两档阈值。
   新版改为“传感器类型 + 参数模板 + 通道量程 + 四档阈值 + 单档开关”：
     - 新增 collector_sensor_type 与 collector_sensor_template；
     - collector_sensor 增加 sensor_type_id；
     - collector_channel 删除 threshold_low/threshold_high，增加
       sensor_template_id、value_kind、value_min/value_max、四档阈值与四个开关。

 执行方式：
   PGPASSWORD=****** psql -h <host> -U <user> -d <db> -f collector_threshold_migration.sql

 说明：
   1. 本脚本幂等，可重复执行；新表/序列/列/索引使用 IF NOT EXISTS，
      约束使用 DROP IF EXISTS + ADD 的方式重建。
   2. 仅回填演示数据 CS1000/HLB1400：创建或复用类型与模板，关联两个传感器，
      并为已有通道补充量程、四档阈值与开关；已关联模板的通道不再更新。
      首次升级时，旧 threshold_low/threshold_high 分别保留为下/上 warning；
      对应旧值存在时，该侧新增 error 档保持 NULL，旧值缺失的档才按模板比例补全。
   3. 四档阈值按 value_min + 比例 × (value_max - value_min) 计算，
      比例默认 0.10 / 0.30 / 0.70 / 0.90；enum 通道四档阈值均为 NULL，
      alarm_enabled=0、四个阈值开关均为 0。
   4. 脚本通过 psql 的 ON_ERROR_STOP 失败即停，所有 DDL/DML 在一个事务内执行；
      任一步失败都会整体回滚。重复执行时旧列已删除，不会再次套用旧阈值。
   5. 删除旧列前先删除依赖它们的 CHECK 约束；本脚本不 CASCADE，
      若库中存在视图等未知依赖会报错并回滚，需人工确认。
   6. 本脚本不修改 collector_sample_raw / 聚合 / 报警等历史数据，
      也不触碰 system_* / infra_* 表。
*/

\set ON_ERROR_STOP on
SET client_encoding = 'UTF8';
BEGIN;


-- =============================================================================
-- 1. 新序列
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS public.collector_sensor_type_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE IF NOT EXISTS public.collector_sensor_template_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


-- =============================================================================
-- 2. 传感器类型表
-- =============================================================================

CREATE TABLE IF NOT EXISTS public.collector_sensor_type (
    id bigint NOT NULL,
    type_code character varying(64) NOT NULL,
    type_name character varying(100) NOT NULL,
    protocol_code character varying(64) NOT NULL,
    manufacturer character varying(100),
    description character varying(255),
    enabled smallint DEFAULT 1 NOT NULL,
    sort integer DEFAULT 0 NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_collector_sensor_type PRIMARY KEY (id),
    CONSTRAINT chk_collector_sensor_type_code CHECK (length(type_code) > 0),
    CONSTRAINT chk_collector_sensor_type_name CHECK (length(type_name) > 0),
    CONSTRAINT chk_collector_sensor_type_protocol CHECK (length(protocol_code) > 0),
    CONSTRAINT chk_collector_sensor_type_enabled CHECK (enabled IN (0, 1))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_collector_sensor_type_code
    ON public.collector_sensor_type USING btree (type_code)
    WHERE (deleted = 0);

CREATE INDEX IF NOT EXISTS idx_collector_sensor_type_protocol
    ON public.collector_sensor_type USING btree (protocol_code)
    WHERE (deleted = 0);

COMMENT ON TABLE public.collector_sensor_type IS '传感器类型/型号定义表（型号下的通道参数模板归属）';
COMMENT ON COLUMN public.collector_sensor_type.id IS '主键编号';
COMMENT ON COLUMN public.collector_sensor_type.type_code IS '类型/型号业务编码（业务唯一）';
COMMENT ON COLUMN public.collector_sensor_type.type_name IS '类型/型号展示名称';
COMMENT ON COLUMN public.collector_sensor_type.protocol_code IS '协议编码（逻辑关联 collector_protocol.protocol_code，不建 FK）';
COMMENT ON COLUMN public.collector_sensor_type.manufacturer IS '制造商';
COMMENT ON COLUMN public.collector_sensor_type.description IS '型号说明、固件差异与字段映射备注';
COMMENT ON COLUMN public.collector_sensor_type.enabled IS '是否启用：0 否、1 是；停用只限制新增传感器引用';
COMMENT ON COLUMN public.collector_sensor_type.sort IS '排序值';
COMMENT ON COLUMN public.collector_sensor_type.creator IS '创建者';
COMMENT ON COLUMN public.collector_sensor_type.create_time IS '创建时间';
COMMENT ON COLUMN public.collector_sensor_type.updater IS '更新者';
COMMENT ON COLUMN public.collector_sensor_type.update_time IS '更新时间';
COMMENT ON COLUMN public.collector_sensor_type.deleted IS '是否删除：0 否、1 是';


-- =============================================================================
-- 3. 传感器参数模板表
-- =============================================================================

CREATE TABLE IF NOT EXISTS public.collector_sensor_template (
    id bigint NOT NULL,
    sensor_type_id bigint NOT NULL,
    channel_code character varying(64) NOT NULL,
    channel_name character varying(100) NOT NULL,
    value_kind character varying(16) DEFAULT 'number'::character varying NOT NULL,
    unit character varying(32),
    icon character varying(64),
    value_min double precision,
    value_max double precision,
    scale double precision DEFAULT 1 NOT NULL,
    value_offset double precision DEFAULT 0 NOT NULL,
    decimal_places smallint DEFAULT 0 NOT NULL,
    threshold_low_error_ratio double precision DEFAULT 0.10 NOT NULL,
    threshold_low_warning_ratio double precision DEFAULT 0.30 NOT NULL,
    threshold_high_warning_ratio double precision DEFAULT 0.70 NOT NULL,
    threshold_high_error_ratio double precision DEFAULT 0.90 NOT NULL,
    alarm_enabled smallint DEFAULT 1 NOT NULL,
    threshold_low_error_enabled smallint DEFAULT 1 NOT NULL,
    threshold_low_warning_enabled smallint DEFAULT 1 NOT NULL,
    threshold_high_warning_enabled smallint DEFAULT 1 NOT NULL,
    threshold_high_error_enabled smallint DEFAULT 1 NOT NULL,
    channel_config jsonb DEFAULT '{}'::jsonb NOT NULL,
    sort integer DEFAULT 0 NOT NULL,
    enabled smallint DEFAULT 1 NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT pk_collector_sensor_template PRIMARY KEY (id),
    CONSTRAINT chk_collector_sensor_template_code CHECK (length(channel_code) > 0),
    CONSTRAINT chk_collector_sensor_template_name CHECK (length(channel_name) > 0),
    CONSTRAINT chk_collector_sensor_template_value_kind CHECK (value_kind IN ('number', 'enum')),
    CONSTRAINT chk_collector_sensor_template_value_finite CHECK (
        (value_min IS NULL OR (
            value_min <> 'Infinity'::float8
            AND value_min <> '-Infinity'::float8
            AND value_min <> 'NaN'::float8
        ))
        AND (value_max IS NULL OR (
            value_max <> 'Infinity'::float8
            AND value_max <> '-Infinity'::float8
            AND value_max <> 'NaN'::float8
        ))
    ),
    CONSTRAINT chk_collector_sensor_template_value_range CHECK (
        value_min IS NULL OR value_max IS NULL OR value_min <= value_max
    ),
    CONSTRAINT chk_collector_sensor_template_scale CHECK (
        scale <> 'Infinity'::float8
        AND scale <> '-Infinity'::float8
        AND scale <> 'NaN'::float8
    ),
    CONSTRAINT chk_collector_sensor_template_offset CHECK (
        value_offset <> 'Infinity'::float8
        AND value_offset <> '-Infinity'::float8
        AND value_offset <> 'NaN'::float8
    ),
    CONSTRAINT chk_collector_sensor_template_decimal CHECK (decimal_places BETWEEN 0 AND 6),
    CONSTRAINT chk_collector_sensor_template_ratios CHECK (
        threshold_low_error_ratio <> 'Infinity'::float8
        AND threshold_low_error_ratio <> '-Infinity'::float8
        AND threshold_low_error_ratio <> 'NaN'::float8
        AND threshold_low_error_ratio BETWEEN 0 AND 1
        AND threshold_low_warning_ratio <> 'Infinity'::float8
        AND threshold_low_warning_ratio <> '-Infinity'::float8
        AND threshold_low_warning_ratio <> 'NaN'::float8
        AND threshold_low_warning_ratio BETWEEN 0 AND 1
        AND threshold_high_warning_ratio <> 'Infinity'::float8
        AND threshold_high_warning_ratio <> '-Infinity'::float8
        AND threshold_high_warning_ratio <> 'NaN'::float8
        AND threshold_high_warning_ratio BETWEEN 0 AND 1
        AND threshold_high_error_ratio <> 'Infinity'::float8
        AND threshold_high_error_ratio <> '-Infinity'::float8
        AND threshold_high_error_ratio <> 'NaN'::float8
        AND threshold_high_error_ratio BETWEEN 0 AND 1
    ),
    CONSTRAINT chk_collector_sensor_template_ratio_order CHECK (
        threshold_low_error_ratio <= threshold_low_warning_ratio
        AND threshold_low_warning_ratio <= threshold_high_warning_ratio
        AND threshold_high_warning_ratio <= threshold_high_error_ratio
    ),
    CONSTRAINT chk_collector_sensor_template_switches CHECK (
        alarm_enabled IN (0, 1)
        AND threshold_low_error_enabled IN (0, 1)
        AND threshold_low_warning_enabled IN (0, 1)
        AND threshold_high_warning_enabled IN (0, 1)
        AND threshold_high_error_enabled IN (0, 1)
    ),
    CONSTRAINT chk_collector_sensor_template_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_collector_sensor_template_config CHECK (jsonb_typeof(channel_config) = 'object')
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_collector_sensor_template_code
    ON public.collector_sensor_template USING btree (sensor_type_id, channel_code)
    WHERE (deleted = 0);

CREATE INDEX IF NOT EXISTS idx_collector_sensor_template_type
    ON public.collector_sensor_template USING btree (sensor_type_id)
    WHERE (deleted = 0);

COMMENT ON TABLE public.collector_sensor_template IS '传感器参数模板表（类型下的通道编码、量程、比例与四档阈值开关）';
COMMENT ON COLUMN public.collector_sensor_template.id IS '主键编号';
COMMENT ON COLUMN public.collector_sensor_template.sensor_type_id IS '所属传感器类型编号（逻辑关联 collector_sensor_type.id）';
COMMENT ON COLUMN public.collector_sensor_template.channel_code IS '通道编码（类型内唯一，生成实例通道时沿用）';
COMMENT ON COLUMN public.collector_sensor_template.channel_name IS '通道名称（生成实例通道时沿用）';
COMMENT ON COLUMN public.collector_sensor_template.value_kind IS '值类别：number 数值、enum 枚举；枚举不产生阈值报警';
COMMENT ON COLUMN public.collector_sensor_template.unit IS '工程单位（可空；空表示无量纲/状态量）';
COMMENT ON COLUMN public.collector_sensor_template.icon IS '图标标识（可空，实例通道可覆盖）';
COMMENT ON COLUMN public.collector_sensor_template.value_min IS '量程下限（数值通道用于初始化四档阈值）';
COMMENT ON COLUMN public.collector_sensor_template.value_max IS '量程上限（数值通道用于初始化四档阈值）';
COMMENT ON COLUMN public.collector_sensor_template.scale IS '数值缩放系数（生成实例通道的默认值）';
COMMENT ON COLUMN public.collector_sensor_template.value_offset IS '工程值偏移量（生成实例通道的默认值）';
COMMENT ON COLUMN public.collector_sensor_template.decimal_places IS '展示小数位';
COMMENT ON COLUMN public.collector_sensor_template.threshold_low_error_ratio IS '下严重阈值比例，默认 0.10；阈值 = value_min + 比例 × (value_max - value_min)';
COMMENT ON COLUMN public.collector_sensor_template.threshold_low_warning_ratio IS '下警告阈值比例，默认 0.30';
COMMENT ON COLUMN public.collector_sensor_template.threshold_high_warning_ratio IS '上警告阈值比例，默认 0.70';
COMMENT ON COLUMN public.collector_sensor_template.threshold_high_error_ratio IS '上严重阈值比例，默认 0.90';
COMMENT ON COLUMN public.collector_sensor_template.alarm_enabled IS '是否启用阈值报警：0 否、1 是；enum 默认 0';
COMMENT ON COLUMN public.collector_sensor_template.threshold_low_error_enabled IS '下严重阈值开关：0 否、1 是；enum 默认 0';
COMMENT ON COLUMN public.collector_sensor_template.threshold_low_warning_enabled IS '下警告阈值开关：0 否、1 是；enum 默认 0';
COMMENT ON COLUMN public.collector_sensor_template.threshold_high_warning_enabled IS '上警告阈值开关：0 否、1 是；enum 默认 0';
COMMENT ON COLUMN public.collector_sensor_template.threshold_high_error_enabled IS '上严重阈值开关：0 否、1 是；enum 默认 0';
COMMENT ON COLUMN public.collector_sensor_template.channel_config IS '协议专用模板参数 JSON，生成实例通道时复制；由所属传感器协议的 channel_schema 校验';
COMMENT ON COLUMN public.collector_sensor_template.sort IS '排序值';
COMMENT ON COLUMN public.collector_sensor_template.enabled IS '是否启用：0 否、1 是；停用只影响新增生成';
COMMENT ON COLUMN public.collector_sensor_template.creator IS '创建者';
COMMENT ON COLUMN public.collector_sensor_template.create_time IS '创建时间';
COMMENT ON COLUMN public.collector_sensor_template.updater IS '更新者';
COMMENT ON COLUMN public.collector_sensor_template.update_time IS '更新时间';
COMMENT ON COLUMN public.collector_sensor_template.deleted IS '是否删除：0 否、1 是';


-- =============================================================================
-- 4. collector_sensor 增加型号关联
-- =============================================================================

ALTER TABLE public.collector_sensor
    ADD COLUMN IF NOT EXISTS sensor_type_id bigint;

CREATE INDEX IF NOT EXISTS idx_collector_sensor_type
    ON public.collector_sensor USING btree (sensor_type_id)
    WHERE (sensor_type_id IS NOT NULL AND deleted = 0);

COMMENT ON COLUMN public.collector_sensor.sensor_type_id IS '传感器类型编号（逻辑关联 collector_sensor_type.id，可空；用于生成参数模板通道）';


-- =============================================================================
-- 5. collector_channel 增加新列
-- =============================================================================

ALTER TABLE public.collector_channel
    ADD COLUMN IF NOT EXISTS sensor_template_id bigint,
    ADD COLUMN IF NOT EXISTS value_kind character varying(16) DEFAULT 'number'::character varying NOT NULL,
    ADD COLUMN IF NOT EXISTS value_min double precision,
    ADD COLUMN IF NOT EXISTS value_max double precision,
    ADD COLUMN IF NOT EXISTS threshold_low_error double precision,
    ADD COLUMN IF NOT EXISTS threshold_low_warning double precision,
    ADD COLUMN IF NOT EXISTS threshold_high_warning double precision,
    ADD COLUMN IF NOT EXISTS threshold_high_error double precision,
    ADD COLUMN IF NOT EXISTS threshold_low_error_enabled smallint DEFAULT 1 NOT NULL,
    ADD COLUMN IF NOT EXISTS threshold_low_warning_enabled smallint DEFAULT 1 NOT NULL,
    ADD COLUMN IF NOT EXISTS threshold_high_warning_enabled smallint DEFAULT 1 NOT NULL,
    ADD COLUMN IF NOT EXISTS threshold_high_error_enabled smallint DEFAULT 1 NOT NULL;

CREATE INDEX IF NOT EXISTS idx_collector_channel_template
    ON public.collector_channel USING btree (sensor_template_id)
    WHERE (sensor_template_id IS NOT NULL AND deleted = 0);

COMMENT ON COLUMN public.collector_channel.sensor_template_id IS '生成该通道的模板编号（逻辑关联 collector_sensor_template.id，可空）';
COMMENT ON COLUMN public.collector_channel.value_kind IS '值类别：number 数值、enum 枚举；枚举不产生阈值报警';
COMMENT ON COLUMN public.collector_channel.value_min IS '量程下限（可空；模板生成时写入，用户可覆盖）';
COMMENT ON COLUMN public.collector_channel.value_max IS '量程上限（可空；模板生成时写入，用户可覆盖）';
COMMENT ON COLUMN public.collector_channel.threshold_low_error IS '下严重阈值（可空；低于该值产生 level=3 报警）';
COMMENT ON COLUMN public.collector_channel.threshold_low_warning IS '下警告阈值（可空；低于该值产生 level=2 报警）';
COMMENT ON COLUMN public.collector_channel.threshold_high_warning IS '上警告阈值（可空；高于该值产生 level=2 报警）';
COMMENT ON COLUMN public.collector_channel.threshold_high_error IS '上严重阈值（可空；高于该值产生 level=3 报警）';
COMMENT ON COLUMN public.collector_channel.threshold_low_error_enabled IS '下严重阈值开关：0 否、1 是';
COMMENT ON COLUMN public.collector_channel.threshold_low_warning_enabled IS '下警告阈值开关：0 否、1 是';
COMMENT ON COLUMN public.collector_channel.threshold_high_warning_enabled IS '上警告阈值开关：0 否、1 是';
COMMENT ON COLUMN public.collector_channel.threshold_high_error_enabled IS '上严重阈值开关：0 否、1 是';


-- =============================================================================
-- 6. 创建/复用 CS1000、HLB1400 传感器类型
-- =============================================================================

INSERT INTO public.collector_sensor_type (
    id, type_code, type_name, protocol_code, manufacturer, description,
    enabled, sort, creator, updater
)
SELECT
    nextval('public.collector_sensor_type_seq'),
    'cs1000', 'HYDAC CS1000 污染度传感器', 'hsi_rs485', 'HYDAC',
    'HSI 地址 0x61，msg=3 返回 10 个 int16 字段',
    1, 1, 'migration', 'migration'
WHERE NOT EXISTS (
    SELECT 1 FROM public.collector_sensor_type
    WHERE type_code = 'cs1000' AND deleted = 0
);

INSERT INTO public.collector_sensor_type (
    id, type_code, type_name, protocol_code, manufacturer, description,
    enabled, sort, creator, updater
)
SELECT
    nextval('public.collector_sensor_type_seq'),
    'hlb1400', 'HYDAC HLB1400 水分传感器', 'hsi_rs485', 'HYDAC',
    'HSI 地址 0x63，msg=3 旧格式 6 个 int16；扩展格式 8 个 int16',
    1, 2, 'migration', 'migration'
WHERE NOT EXISTS (
    SELECT 1 FROM public.collector_sensor_type
    WHERE type_code = 'hlb1400' AND deleted = 0
);


-- =============================================================================
-- 7. 创建/复用 CS1000、HLB1400 参数模板
--    数值通道：alarm_enabled=1、四个开关=1；
--    枚举通道 flow_state：alarm_enabled=0、四个开关=0。
--    量程为初始模板默认值，不代表现场标定，可在模板/通道中调整。
-- =============================================================================

INSERT INTO public.collector_sensor_template (
    id, sensor_type_id, channel_code, channel_name, value_kind, unit, icon,
    value_min, value_max, scale, value_offset, decimal_places,
    threshold_low_error_ratio, threshold_low_warning_ratio,
    threshold_high_warning_ratio, threshold_high_error_ratio,
    alarm_enabled, threshold_low_error_enabled, threshold_low_warning_enabled,
    threshold_high_warning_enabled, threshold_high_error_enabled,
    channel_config, sort, enabled, creator, updater
)
SELECT
    nextval('public.collector_sensor_template_seq'),
    t.id,
    v.channel_code, v.channel_name, v.value_kind, v.unit, v.icon,
    v.value_min, v.value_max, v.scale, 0, v.decimal_places,
    0.10, 0.30, 0.70, 0.90,
    v.alarm_enabled, v.switch_low_error, v.switch_low_warning,
    v.switch_high_warning, v.switch_high_error,
    jsonb_build_object(
        'address', v.address,
        'message', 3,
        'fieldIndex', v.field_index,
        'signed', true
    ),
    v.sort, 1, 'migration', 'migration'
FROM (VALUES
    -- CS1000（地址 0x61 = 97）
    ('cs1000', 'iso_4', 'ISO 4μm 清洁度', 'number', 'µm', 'i-ep:filter', 8::float8, 25::float8, 0.1::float8, 1, 1, 1, 1, 1, 1, 97, 1, 1),
    ('cs1000', 'iso_6', 'ISO 6μm 清洁度', 'number', 'µm', 'i-ep:filter', 7::float8, 24::float8, 0.1::float8, 1, 1, 1, 1, 1, 1, 97, 2, 2),
    ('cs1000', 'iso_14', 'ISO 14μm 清洁度', 'number', 'µm', 'i-ep:filter', 6::float8, 23::float8, 0.1::float8, 1, 1, 1, 1, 1, 1, 97, 3, 3),
    ('cs1000', 'sae_a', 'SAE A 等级', 'number', NULL, 'i-ep:medal', 0::float8, 14::float8, 0.1::float8, 1, 1, 1, 1, 1, 1, 97, 4, 4),
    ('cs1000', 'sae_b', 'SAE B 等级', 'number', NULL, 'i-ep:medal', 0::float8, 14::float8, 0.1::float8, 1, 1, 1, 1, 1, 1, 97, 5, 5),
    ('cs1000', 'sae_c', 'SAE C 等级', 'number', NULL, 'i-ep:medal', 0::float8, 14::float8, 0.1::float8, 1, 1, 1, 1, 1, 1, 97, 6, 6),
    ('cs1000', 'sae_d', 'SAE D 等级', 'number', NULL, 'i-ep:medal', 0::float8, 14::float8, 0.1::float8, 1, 1, 1, 1, 1, 1, 97, 7, 7),
    ('cs1000', 'temperature_c', '油液温度', 'number', '℃', 'i-ep:thermometer', -60::float8, 150::float8, 0.1::float8, 1, 1, 1, 1, 1, 1, 97, 8, 8),
    ('cs1000', 'flow_state', '流动状态', 'enum', NULL, 'i-ep:switch', 11::float8, 55::float8, 1::float8, 0, 0, 0, 0, 0, 0, 97, 9, 9),
    ('cs1000', 'drive_pct', '驱动百分比', 'number', '%', 'i-ep:odometer', 0::float8, 100::float8, 1::float8, 0, 1, 1, 1, 1, 1, 97, 10, 10),
    -- HLB1400（地址 0x63 = 99）
    ('hlb1400', 'conductivity_ns_m', '电导率', 'number', 'nS/m', 'i-ep:lightning', 0::float8, 100::float8, 0.1::float8, 1, 1, 1, 1, 1, 1, 99, 1, 1),
    ('hlb1400', 'conductivity_change_pct', '电导率相对变化', 'number', '%', 'i-ep:trend-charts', -100::float8, 200::float8, 0.1::float8, 1, 1, 1, 1, 1, 1, 99, 2, 2),
    ('hlb1400', 'dc', '介电常数 DC', 'number', NULL, 'i-ep:data-line', 1::float8, 10::float8, 0.01::float8, 2, 1, 1, 1, 1, 1, 99, 3, 3),
    ('hlb1400', 'dc_change_pct', 'DC 相对变化', 'number', '%', 'i-ep:trend-charts', -30::float8, 30::float8, 0.1::float8, 1, 1, 1, 1, 1, 1, 99, 4, 4),
    ('hlb1400', 'saturation_pct', '饱和度', 'number', '%rH', 'i-ep:drizzling', 0::float8, 100::float8, 1::float8, 0, 1, 1, 1, 1, 1, 99, 5, 5),
    ('hlb1400', 'temperature_c', '油液温度', 'number', '℃', 'i-ep:thermometer', -25::float8, 100::float8, 0.1::float8, 1, 1, 1, 1, 1, 1, 99, 6, 6)
) AS v(
    type_code, channel_code, channel_name, value_kind, unit, icon,
    value_min, value_max, scale, decimal_places,
    alarm_enabled, switch_low_error, switch_low_warning, switch_high_warning, switch_high_error,
    address, field_index, sort
)
JOIN public.collector_sensor_type t
    ON t.type_code = v.type_code AND t.deleted = 0
WHERE NOT EXISTS (
    SELECT 1
    FROM public.collector_sensor_template st
    WHERE st.sensor_type_id = t.id
      AND st.channel_code = v.channel_code
      AND st.deleted = 0
);


-- =============================================================================
-- 8. 关联 CS1000/HLB1400 传感器实例
-- =============================================================================

UPDATE public.collector_sensor s
SET sensor_type_id = t.id
FROM public.collector_sensor_type t
WHERE t.deleted = 0
  AND s.deleted = 0
  AND s.sensor_type_id IS NULL
  AND (
      (t.type_code = 'cs1000' AND s.sensor_code LIKE 'cs1000-%')
      OR (t.type_code = 'hlb1400' AND s.sensor_code LIKE 'hlb1400-%')
  );


-- =============================================================================
-- 9. 回填已有通道的量程、四档阈值与开关
--    仅回填尚未关联模板（sensor_template_id IS NULL）的通道。
--    首次执行先把旧两档值保存到临时表；重复执行时旧列已不存在，临时表为空。
-- =============================================================================

DO $$
DECLARE
    has_legacy_low boolean;
    has_legacy_high boolean;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'collector_channel'
          AND column_name = 'threshold_low'
    ), EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'collector_channel'
          AND column_name = 'threshold_high'
    ) INTO has_legacy_low, has_legacy_high;

    IF has_legacy_low OR has_legacy_high THEN
        EXECUTE 'CREATE TEMP TABLE collector_legacy_threshold ON COMMIT DROP AS '
            || 'SELECT id AS channel_id, '
            || CASE WHEN has_legacy_low
                    THEN 'threshold_low AS legacy_low'
                    ELSE 'NULL::double precision AS legacy_low' END || ', '
            || CASE WHEN has_legacy_high
                    THEN 'threshold_high AS legacy_high'
                    ELSE 'NULL::double precision AS legacy_high' END
            || ' FROM public.collector_channel';
    ELSE
        CREATE TEMP TABLE collector_legacy_threshold (
            channel_id bigint PRIMARY KEY,
            legacy_low double precision,
            legacy_high double precision
        ) ON COMMIT DROP;
    END IF;
END $$;

UPDATE public.collector_channel c
SET sensor_template_id = st.id,
    value_kind = st.value_kind,
    value_min = st.value_min,
    value_max = st.value_max,
    threshold_low_error = CASE
        WHEN st.value_kind = 'enum' OR st.value_min IS NULL OR st.value_max IS NULL THEN NULL
        WHEN legacy.legacy_low IS NULL THEN round((st.value_min + st.threshold_low_error_ratio
            * (st.value_max - st.value_min))::numeric, 4)::double precision
        ELSE NULL
    END,
    threshold_low_warning = CASE
        WHEN st.value_kind = 'enum' THEN NULL
        WHEN legacy.legacy_low IS NOT NULL THEN legacy.legacy_low
        WHEN st.value_min IS NULL OR st.value_max IS NULL THEN NULL
        ELSE round((st.value_min + st.threshold_low_warning_ratio
            * (st.value_max - st.value_min))::numeric, 4)::double precision
    END,
    threshold_high_warning = CASE
        WHEN st.value_kind = 'enum' THEN NULL
        WHEN legacy.legacy_high IS NOT NULL THEN legacy.legacy_high
        WHEN st.value_min IS NULL OR st.value_max IS NULL THEN NULL
        ELSE round((st.value_min + st.threshold_high_warning_ratio
            * (st.value_max - st.value_min))::numeric, 4)::double precision
    END,
    threshold_high_error = CASE
        WHEN st.value_kind = 'enum' OR st.value_min IS NULL OR st.value_max IS NULL THEN NULL
        WHEN legacy.legacy_high IS NULL THEN round((st.value_min + st.threshold_high_error_ratio
            * (st.value_max - st.value_min))::numeric, 4)::double precision
        ELSE NULL
    END,
    threshold_low_error_enabled = CASE
        WHEN st.value_kind = 'enum' THEN 0 ELSE st.threshold_low_error_enabled
    END,
    threshold_low_warning_enabled = CASE
        WHEN st.value_kind = 'enum' THEN 0 ELSE st.threshold_low_warning_enabled
    END,
    threshold_high_warning_enabled = CASE
        WHEN st.value_kind = 'enum' THEN 0 ELSE st.threshold_high_warning_enabled
    END,
    threshold_high_error_enabled = CASE
        WHEN st.value_kind = 'enum' THEN 0 ELSE st.threshold_high_error_enabled
    END,
    alarm_enabled = CASE WHEN st.value_kind = 'enum' THEN 0 ELSE st.alarm_enabled END
FROM public.collector_sensor s,
     public.collector_sensor_type t,
     public.collector_sensor_template st,
     pg_temp.collector_legacy_threshold legacy
WHERE c.sensor_id = s.id
  AND s.sensor_type_id = t.id
  AND t.deleted = 0
  AND st.sensor_type_id = t.id
  AND st.channel_code = c.channel_code
  AND st.deleted = 0
  AND legacy.channel_id = c.id
  AND c.deleted = 0
  AND s.deleted = 0
  AND c.sensor_template_id IS NULL;

-- 未匹配到模板的通道保持原有可空字段与默认开关；给出提示，不阻断迁移。
DO $$
DECLARE
    unmatched_count integer;
BEGIN
    SELECT count(*) INTO unmatched_count
    FROM public.collector_channel c
    WHERE c.deleted = 0
      AND c.sensor_template_id IS NULL;

    IF unmatched_count > 0 THEN
        RAISE WARNING '有 % 条 collector_channel 未匹配到模板，请人工检查 channel_code 与 sensor.sensor_type_id', unmatched_count;
    END IF;
END $$;


-- =============================================================================
-- 10. 删除旧两档阈值列
--     先删除依赖的 CHECK 约束，再删除列；不使用 CASCADE。
-- =============================================================================

ALTER TABLE public.collector_channel
    DROP CONSTRAINT IF EXISTS chk_collector_channel_threshold_finite;

ALTER TABLE public.collector_channel
    DROP CONSTRAINT IF EXISTS chk_collector_channel_threshold;

ALTER TABLE public.collector_channel
    DROP COLUMN IF EXISTS threshold_low;

ALTER TABLE public.collector_channel
    DROP COLUMN IF EXISTS threshold_high;


-- =============================================================================
-- 11. 重建新列 CHECK 约束（幂等）
-- =============================================================================

ALTER TABLE public.collector_channel
    DROP CONSTRAINT IF EXISTS chk_collector_channel_value_kind;
ALTER TABLE public.collector_channel
    ADD CONSTRAINT chk_collector_channel_value_kind
    CHECK (value_kind IN ('number', 'enum'));

ALTER TABLE public.collector_channel
    DROP CONSTRAINT IF EXISTS chk_collector_channel_value_finite;
ALTER TABLE public.collector_channel
    ADD CONSTRAINT chk_collector_channel_value_finite CHECK (
        (value_min IS NULL OR (
            value_min <> 'Infinity'::float8
            AND value_min <> '-Infinity'::float8
            AND value_min <> 'NaN'::float8
        ))
        AND (value_max IS NULL OR (
            value_max <> 'Infinity'::float8
            AND value_max <> '-Infinity'::float8
            AND value_max <> 'NaN'::float8
        ))
    );

ALTER TABLE public.collector_channel
    DROP CONSTRAINT IF EXISTS chk_collector_channel_value_range;
ALTER TABLE public.collector_channel
    ADD CONSTRAINT chk_collector_channel_value_range
    CHECK (value_min IS NULL OR value_max IS NULL OR value_min <= value_max);

ALTER TABLE public.collector_channel
    DROP CONSTRAINT IF EXISTS chk_collector_channel_thresholds_finite;
ALTER TABLE public.collector_channel
    ADD CONSTRAINT chk_collector_channel_thresholds_finite CHECK (
        (threshold_low_error IS NULL OR (
            threshold_low_error <> 'Infinity'::float8
            AND threshold_low_error <> '-Infinity'::float8
            AND threshold_low_error <> 'NaN'::float8
        ))
        AND (threshold_low_warning IS NULL OR (
            threshold_low_warning <> 'Infinity'::float8
            AND threshold_low_warning <> '-Infinity'::float8
            AND threshold_low_warning <> 'NaN'::float8
        ))
        AND (threshold_high_warning IS NULL OR (
            threshold_high_warning <> 'Infinity'::float8
            AND threshold_high_warning <> '-Infinity'::float8
            AND threshold_high_warning <> 'NaN'::float8
        ))
        AND (threshold_high_error IS NULL OR (
            threshold_high_error <> 'Infinity'::float8
            AND threshold_high_error <> '-Infinity'::float8
            AND threshold_high_error <> 'NaN'::float8
        ))
    );

ALTER TABLE public.collector_channel
    DROP CONSTRAINT IF EXISTS chk_collector_channel_thresholds_order;
ALTER TABLE public.collector_channel
    ADD CONSTRAINT chk_collector_channel_thresholds_order CHECK (
        (threshold_low_error IS NULL OR threshold_low_warning IS NULL OR threshold_low_error <= threshold_low_warning)
        AND (threshold_low_warning IS NULL OR threshold_high_warning IS NULL OR threshold_low_warning <= threshold_high_warning)
        AND (threshold_high_warning IS NULL OR threshold_high_error IS NULL OR threshold_high_warning <= threshold_high_error)
    );

ALTER TABLE public.collector_channel
    DROP CONSTRAINT IF EXISTS chk_collector_channel_threshold_switches;
ALTER TABLE public.collector_channel
    ADD CONSTRAINT chk_collector_channel_threshold_switches CHECK (
        threshold_low_error_enabled IN (0, 1)
        AND threshold_low_warning_enabled IN (0, 1)
        AND threshold_high_warning_enabled IN (0, 1)
        AND threshold_high_error_enabled IN (0, 1)
    );


-- =============================================================================
-- 12. 表注释同步
-- =============================================================================

COMMENT ON TABLE public.collector_sensor IS '采集传感器表（设备下级，绑定协议、型号与连接参数）';
COMMENT ON TABLE public.collector_channel IS '采集通道通用配置表（协议无关，含 UI 渲染字段与四档阈值）';
COMMENT ON TABLE public.collector_config_version IS '采集配置版本快照表';
COMMENT ON COLUMN public.collector_config_version.snapshot IS '全量配置快照（protocols/devices/sensors/channels/schedules），channels 含 valueKind/量程/四档阈值与开关；checksum 覆盖传感器及协议配置';

COMMIT;
