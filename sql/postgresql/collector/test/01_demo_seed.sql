-- 采集器演示数据种子（PostgreSQL）—— 仅限 dbctl.sh 创建的校验库
--
-- 内容与 collector/seed.py 的 HSI 演示种子严格一一对应（业务键完全一致）：
--   设备 rk3568-hsi-01 ×1、传感器类型 cs1000 / hlb1400 ×2、
--   传感器 cs1000-01（0x61）/ hlb1400-01（0x63）×2、参数模板 ×16、通道 ×16。
-- 本脚本不注册 hsi_rs485 协议（由 collector/02_system_seed.sql 负责），只引用。
--
-- 使用规则：
-- 1. 只允许在带 db_init_marker（purpose=validation）的校验库上执行；
--    dbctl.sh seed-test 会强制校验该标记，手工执行请自行确认风险。
-- 2. 存在非演示采集设备时整体拒绝并回滚，避免演示数据混入真实库。
-- 3. 所有写入按业务键判重（存在即跳过），不覆盖任何已有配置，可重复执行；
--    与 seed.py 幂等逻辑等价，但二者面向不同场景（本脚本仅校验库，seed.py 现场库）。
-- 4. 与 seed.py 一致：value_type 恒为 'number'（含枚举通道）；
--    数值通道四档阈值按「量程下限 + 比例 × 量程」初始化（0.10/0.30/0.70/0.90），
--    枚举通道（flow_state）四档阈值为 NULL、报警与四个开关均为 0。
-- 5. 所有修改在一个事务内执行；通过 psql 执行时 ON_ERROR_STOP 失败即停。

\set ON_ERROR_STOP on
SET client_encoding = 'UTF8';
BEGIN;

-- ========== 前置校验 ==========

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.collector_protocol
        WHERE protocol_code = 'hsi_rs485' AND deleted = 0
    ) THEN
        RAISE EXCEPTION '缺少 hsi_rs485 协议注册行，请先执行 collector/02_system_seed.sql（或使用 dbctl.sh init）';
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.collector_device
        WHERE device_code <> 'rk3568-hsi-01' AND deleted = 0
    ) THEN
        RAISE EXCEPTION '库中已存在非演示采集设备，拒绝写入演示种子（本脚本仅限校验库使用）';
    END IF;
END $$;

-- ========== 1. 演示设备（device_code = rk3568-hsi-01） ==========
INSERT INTO public.collector_device (
    id, device_code, device_name, enabled, poll_interval_ms, timeout_ms, retries,
    reconnect_interval_ms, description, sort, creator, updater
)
SELECT
    nextval('public.collector_device_seq'),
    'rk3568-hsi-01', 'RK3568 HSI 采集主机',
    1, 60000, 1000, 1, 5000,
    'RK3568 板端 HSI 采集演示设备', 1, 'collector', 'collector'
WHERE NOT EXISTS (
    SELECT 1 FROM public.collector_device
    WHERE device_code = 'rk3568-hsi-01' AND deleted = 0
);

-- ========== 2. 传感器类型 ==========
INSERT INTO public.collector_sensor_type (
    id, type_code, type_name, protocol_code, manufacturer, description,
    enabled, sort, creator, updater
)
SELECT nextval('public.collector_sensor_type_seq'), 'cs1000',
    'HYDAC CS1000 污染度传感器', 'hsi_rs485', 'HYDAC',
    'HSI 地址 0x61，msg=3 返回 10 个 int16 字段', 1, 1, 'collector', 'collector'
WHERE NOT EXISTS (
    SELECT 1 FROM public.collector_sensor_type
    WHERE type_code = 'cs1000' AND deleted = 0
);

INSERT INTO public.collector_sensor_type (
    id, type_code, type_name, protocol_code, manufacturer, description,
    enabled, sort, creator, updater
)
SELECT nextval('public.collector_sensor_type_seq'), 'hlb1400',
    'HYDAC HLB1400 水分传感器', 'hsi_rs485', 'HYDAC',
    'HSI 地址 0x63，msg=3 旧格式 6 个 int16；扩展格式 8 个 int16', 1, 2, 'collector', 'collector'
WHERE NOT EXISTS (
    SELECT 1 FROM public.collector_sensor_type
    WHERE type_code = 'hlb1400' AND deleted = 0
);

-- ========== 3. 传感器（连接参数与 seed.py 的 _connection_config 一致） ==========
INSERT INTO public.collector_sensor (
    id, device_id, sensor_type_id, sensor_code, sensor_name, protocol_code,
    connection_config, enabled, description, sort, creator, updater
)
SELECT
    nextval('public.collector_sensor_seq'),
    d.id, t.id, v.sensor_code, v.sensor_name, 'hsi_rs485',
    jsonb_build_object(
        'port', '/dev/ttyS4', 'baud', 9600, 'bytesize', 8,
        'parity', 'none', 'stopbits', 1, 'rs485', true, 'rs485Flags', 0),
    1, v.description, v.sort, 'collector', 'collector'
FROM (VALUES
    ('cs1000-01', 'cs1000', 'CS1000 污染度传感器', 'HYDAC CS1000，HSI 地址 0x61，msg=3 返回 10 个 int16', 1),
    ('hlb1400-01', 'hlb1400', 'HLB1400 水分传感器', 'HYDAC HLB1400，HSI 地址 0x63，msg=3 旧格式返回 6 个 int16', 2)
) AS v(sensor_code, type_code, sensor_name, description, sort)
JOIN public.collector_device d
    ON d.device_code = 'rk3568-hsi-01' AND d.deleted = 0
JOIN public.collector_sensor_type t
    ON t.type_code = v.type_code AND t.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM public.collector_sensor s
    WHERE s.device_id = d.id AND s.sensor_code = v.sensor_code AND s.deleted = 0
);

-- ========== 4. 参数模板与通道共用的种子行（与 seed.py 单一来源，禁止手工改单边） ==========
-- 列：type_code, sensor_code, channel_code, channel_name, value_kind, unit, icon,
--     value_min, value_max, scale, decimal_places, alarm_enabled,
--     s_le, s_lw, s_hw, s_he（四档开关）, address, field_index, sort, description
CREATE TEMP TABLE collector_demo_seed (
    type_code varchar(64) NOT NULL,
    sensor_code varchar(64) NOT NULL,
    channel_code varchar(64) NOT NULL,
    channel_name varchar(100) NOT NULL,
    value_kind varchar(16) NOT NULL,
    unit varchar(32),
    icon varchar(64),
    value_min double precision,
    value_max double precision,
    scale double precision NOT NULL,
    decimal_places smallint NOT NULL,
    alarm_enabled smallint NOT NULL,
    s_le smallint NOT NULL,
    s_lw smallint NOT NULL,
    s_hw smallint NOT NULL,
    s_he smallint NOT NULL,
    address integer NOT NULL,
    field_index integer NOT NULL,
    sort integer NOT NULL,
    description varchar(255)
) ON COMMIT DROP;

INSERT INTO collector_demo_seed VALUES
    ('cs1000', 'cs1000-01', 'iso_4', 'ISO 4μm 清洁度', 'number', 'µm', 'i-ep:filter', 8.0, 25.0, 0.1, 1, 1, 1, 1, 1, 1, 97, 1, 1, 'ISO 4μm 通道，原始值 ×0.1'),
    ('cs1000', 'cs1000-01', 'iso_6', 'ISO 6μm 清洁度', 'number', 'µm', 'i-ep:filter', 7.0, 24.0, 0.1, 1, 1, 1, 1, 1, 1, 97, 2, 2, 'ISO 6μm 通道，原始值 ×0.1'),
    ('cs1000', 'cs1000-01', 'iso_14', 'ISO 14μm 清洁度', 'number', 'µm', 'i-ep:filter', 6.0, 23.0, 0.1, 1, 1, 1, 1, 1, 1, 97, 3, 3, 'ISO 14μm 通道，原始值 ×0.1'),
    ('cs1000', 'cs1000-01', 'sae_a', 'SAE A 等级', 'number', NULL, 'i-ep:medal', 0.0, 14.0, 0.1, 1, 1, 1, 1, 1, 1, 97, 4, 4, 'SAE A 等级，原始值 ×0.1'),
    ('cs1000', 'cs1000-01', 'sae_b', 'SAE B 等级', 'number', NULL, 'i-ep:medal', 0.0, 14.0, 0.1, 1, 1, 1, 1, 1, 1, 97, 5, 5, 'SAE B 等级，原始值 ×0.1'),
    ('cs1000', 'cs1000-01', 'sae_c', 'SAE C 等级', 'number', NULL, 'i-ep:medal', 0.0, 14.0, 0.1, 1, 1, 1, 1, 1, 1, 97, 6, 6, 'SAE C 等级，原始值 ×0.1'),
    ('cs1000', 'cs1000-01', 'sae_d', 'SAE D 等级', 'number', NULL, 'i-ep:medal', 0.0, 14.0, 0.1, 1, 1, 1, 1, 1, 1, 97, 7, 7, 'SAE D 等级，原始值 ×0.1'),
    ('cs1000', 'cs1000-01', 'temperature_c', '油液温度', 'number', '℃', 'i-ep:thermometer', -60.0, 150.0, 0.1, 1, 1, 1, 1, 1, 1, 97, 8, 8, '温度，原始值 ×0.1 ℃'),
    ('cs1000', 'cs1000-01', 'flow_state', '流动状态', 'enum', NULL, 'i-ep:switch', 11.0, 55.0, 1.0, 0, 0, 0, 0, 0, 0, 97, 9, 9, 'FlowState 枚举原始值 11/22/33/44/55'),
    ('cs1000', 'cs1000-01', 'drive_pct', '驱动百分比', 'number', '%', 'i-ep:odometer', 0.0, 100.0, 1.0, 0, 1, 1, 1, 1, 1, 97, 10, 10, '驱动百分比 0..100'),
    ('hlb1400', 'hlb1400-01', 'conductivity_ns_m', '电导率', 'number', 'nS/m', 'i-ep:lightning', 0.0, 100.0, 0.1, 1, 1, 1, 1, 1, 1, 99, 1, 1, '电导率，原始值 ×0.1 nS/m'),
    ('hlb1400', 'hlb1400-01', 'conductivity_change_pct', '电导率相对变化', 'number', '%', 'i-ep:trend-charts', -100.0, 200.0, 0.1, 1, 1, 1, 1, 1, 1, 99, 2, 2, '相对电导率变化，原始值 ×0.1 %'),
    ('hlb1400', 'hlb1400-01', 'dc', '介电常数 DC', 'number', NULL, 'i-ep:data-line', 1.0, 10.0, 0.01, 2, 1, 1, 1, 1, 1, 99, 3, 3, 'DC，原始值 ×0.01'),
    ('hlb1400', 'hlb1400-01', 'dc_change_pct', 'DC 相对变化', 'number', '%', 'i-ep:trend-charts', -30.0, 30.0, 0.1, 1, 1, 1, 1, 1, 1, 99, 4, 4, '相对 DC 变化，原始值 ×0.1 %'),
    ('hlb1400', 'hlb1400-01', 'saturation_pct', '饱和度', 'number', '%rH', 'i-ep:drizzling', 0.0, 100.0, 1.0, 0, 1, 1, 1, 1, 1, 99, 5, 5, '饱和度 0..100 %rH'),
    ('hlb1400', 'hlb1400-01', 'temperature_c', '油液温度', 'number', '℃', 'i-ep:thermometer', -25.0, 100.0, 0.1, 1, 1, 1, 1, 1, 1, 99, 6, 6, '温度，原始值 ×0.1 ℃');

-- ========== 5. 参数模板 ×16（按 (sensor_type_id, channel_code) 判重） ==========
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
    t.id, v.channel_code, v.channel_name, v.value_kind, v.unit, v.icon,
    v.value_min, v.value_max, v.scale, 0, v.decimal_places,
    0.10, 0.30, 0.70, 0.90,
    v.alarm_enabled, v.s_le, v.s_lw, v.s_hw, v.s_he,
    jsonb_build_object(
        'address', v.address, 'message', 3, 'fieldIndex', v.field_index,
        'signed', true, 'scale', v.scale, 'offset', 0,
        'unit', v.unit, 'decimals', v.decimal_places),
    v.sort, 1, 'collector', 'collector'
FROM collector_demo_seed v
JOIN public.collector_sensor_type t
    ON t.type_code = v.type_code AND t.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM public.collector_sensor_template st
    WHERE st.sensor_type_id = t.id AND st.channel_code = v.channel_code AND st.deleted = 0
);

-- ========== 6. 通道 ×16（按 (sensor_id, channel_code) 判重） ==========
INSERT INTO public.collector_channel (
    id, device_id, sensor_id, sensor_template_id, channel_code, channel_name,
    enabled, value_type, value_kind, channel_config, scale, value_offset, unit, icon,
    decimal_places, deadband, value_min, value_max,
    threshold_low_error, threshold_low_warning, threshold_high_warning, threshold_high_error,
    threshold_low_error_enabled, threshold_low_warning_enabled,
    threshold_high_warning_enabled, threshold_high_error_enabled,
    alarm_deadband, alarm_enabled, poll_interval_ms, description, sort, creator, updater
)
SELECT
    nextval('public.collector_channel_seq'),
    d.id, s.id, st.id, v.channel_code, v.channel_name,
    1, 'number', v.value_kind,
    jsonb_build_object(
        'address', v.address, 'message', 3, 'fieldIndex', v.field_index,
        'signed', true, 'scale', v.scale, 'offset', 0,
        'unit', v.unit, 'decimals', v.decimal_places),
    v.scale, 0, v.unit, v.icon, v.decimal_places, 0,
    v.value_min, v.value_max,
    CASE WHEN v.value_kind = 'enum' THEN NULL
         ELSE round((v.value_min + 0.10 * (v.value_max - v.value_min))::numeric, 4)::double precision END,
    CASE WHEN v.value_kind = 'enum' THEN NULL
         ELSE round((v.value_min + 0.30 * (v.value_max - v.value_min))::numeric, 4)::double precision END,
    CASE WHEN v.value_kind = 'enum' THEN NULL
         ELSE round((v.value_min + 0.70 * (v.value_max - v.value_min))::numeric, 4)::double precision END,
    CASE WHEN v.value_kind = 'enum' THEN NULL
         ELSE round((v.value_min + 0.90 * (v.value_max - v.value_min))::numeric, 4)::double precision END,
    v.s_le, v.s_lw, v.s_hw, v.s_he,
    0, v.alarm_enabled, NULL, v.description, v.sort, 'collector', 'collector'
FROM collector_demo_seed v
JOIN public.collector_device d
    ON d.device_code = 'rk3568-hsi-01' AND d.deleted = 0
JOIN public.collector_sensor s
    ON s.device_id = d.id AND s.sensor_code = v.sensor_code AND s.deleted = 0
JOIN public.collector_sensor_type t
    ON t.type_code = v.type_code AND t.deleted = 0
JOIN public.collector_sensor_template st
    ON st.sensor_type_id = t.id AND st.channel_code = v.channel_code AND st.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM public.collector_channel c
    WHERE c.sensor_id = s.id AND c.channel_code = v.channel_code AND c.deleted = 0
);

COMMIT;
