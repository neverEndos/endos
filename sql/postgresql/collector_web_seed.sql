-- 采集器 Web 端种子数据（PostgreSQL）
--
-- 幂等说明：
-- 1. HSI 协议按 protocol_code 判重，已存在则跳过；不会覆盖已有 Schema。
-- 2. 菜单使用固定编号 780~800，按 id 判重；角色菜单按 (role_id, menu_id) 判重。
-- 3. 本脚本只负责协议注册行、菜单与 super_admin 授权，演示设备/传感器/通道
--    由 collector/seed.py 幂等写入，避免 SQL 与 Python 两套演示数据互相覆盖。
-- 4. 菜单写入后需重启后端或清理权限缓存，管理员新权限才会生效。
--
-- 执行方式（示例，密码从环境变量读取，不要写入仓库）：
--   PGPASSWORD=... psql -h <host> -U <user> -d ruoyi-vue-pro -f sql/postgresql/collector_web_seed.sql

-- ========== HYDAC HSI 协议注册行 ==========
INSERT INTO public.collector_protocol (
    id, protocol_code, protocol_name, transport_type, driver_key,
    connection_schema, channel_schema, protocol_version, enabled, sort, description,
    creator, updater, deleted
)
SELECT
    nextval('public.collector_protocol_seq'),
    'hsi_rs485',
    'HYDAC HSI (RS485)',
    'serial',
    'hsi_rs485_driver',
    '{"type": "object"}'::jsonb,
    '{"type": "object"}'::jsonb,
    '1.0.0',
    1,
    30,
    'HYDAC HSI 总线；9600 8N1 无流控，RS485 需 TIOCSRS485 方向控制；msg=1 状态、msg=3 测量',
    '1',
    '1',
    0
WHERE NOT EXISTS (
    SELECT 1 FROM public.collector_protocol WHERE protocol_code = 'hsi_rs485' AND deleted = 0
);

-- ========== 采集器菜单（目录 + 6 个页面） ==========
WITH menu_seed(id, parent_id, name, permission, type, sort, path, icon, component, component_name) AS (
    VALUES
        (780::bigint, 0::bigint, '采集器', '', 1::smallint, 90, 'collector', 'ep:cpu', '', ''),
        (781, 780, '采集设备', 'collector:device:query', 2, 1, 'device', 'ep:monitor', 'collector/device/index', 'CollectorDevice'),
        (782, 780, '采集传感器', 'collector:sensor:query', 2, 2, 'sensor', 'ep:connection', 'collector/sensor/index', 'CollectorSensor'),
        (783, 780, '采集通道', 'collector:channel:query', 2, 3, 'channel', 'ep:data-line', 'collector/channel/index', 'CollectorChannel'),
        (784, 780, '传感器类型', 'collector:sensor-type:query', 2, 4, 'sensor-type', 'ep:collection-tag', 'collector/sensor-type/index', 'CollectorSensorType'),
        (785, 780, '参数模板', 'collector:sensor-template:query', 2, 5, 'sensor-template', 'ep:document', 'collector/sensor-template/index', 'CollectorSensorTemplate'),
        (786, 780, '采集协议', 'collector:protocol:query', 2, 6, 'protocol', 'ep:link', 'collector/protocol/index', 'CollectorProtocol')
)
INSERT INTO public.system_menu (
    id, parent_id, name, permission, type, sort, path, icon, component, component_name,
    status, visible, keep_alive, always_show, creator, updater, deleted
)
SELECT
    s.id, s.parent_id, s.name, s.permission, s.type, s.sort, s.path, s.icon, s.component, s.component_name,
    0, true, true, true, '1', '1', 0
FROM menu_seed s
WHERE NOT EXISTS (SELECT 1 FROM public.system_menu m WHERE m.id = s.id);

-- ========== 采集器按钮权限（14 个） ==========
WITH button_seed(id, parent_id, name, permission, sort) AS (
    VALUES
        (787::bigint, 781::bigint, '采集设备新增', 'collector:device:create', 1),
        (788, 781, '采集设备修改', 'collector:device:update', 2),
        (789, 781, '采集设备删除', 'collector:device:delete', 3),
        (790, 782, '采集传感器新增', 'collector:sensor:create', 1),
        (791, 782, '采集传感器修改', 'collector:sensor:update', 2),
        (792, 782, '采集传感器删除', 'collector:sensor:delete', 3),
        (793, 783, '采集通道修改', 'collector:channel:update', 1),
        (794, 783, '采集通道删除', 'collector:channel:delete', 2),
        (795, 784, '传感器类型新增', 'collector:sensor-type:create', 1),
        (796, 784, '传感器类型修改', 'collector:sensor-type:update', 2),
        (797, 784, '传感器类型删除', 'collector:sensor-type:delete', 3),
        (798, 785, '参数模板新增', 'collector:sensor-template:create', 1),
        (799, 785, '参数模板修改', 'collector:sensor-template:update', 2),
        (800, 785, '参数模板删除', 'collector:sensor-template:delete', 3)
)
INSERT INTO public.system_menu (
    id, parent_id, name, permission, type, sort, path, icon, component, component_name,
    status, visible, keep_alive, always_show, creator, updater, deleted
)
SELECT
    s.id, s.parent_id, s.name, s.permission, 3::smallint, s.sort, '', '', NULL, NULL,
    0, true, true, true, '1', '1', 0
FROM button_seed s
WHERE NOT EXISTS (SELECT 1 FROM public.system_menu m WHERE m.id = s.id);

-- ========== super_admin 授权（仅 role_id = 1） ==========
INSERT INTO public.system_role_menu (
    id, role_id, menu_id, creator, updater, deleted
)
SELECT
    nextval('public.system_role_menu_seq'), 1, m.id, '1', '1', 0
FROM public.system_menu m
WHERE m.id BETWEEN 780 AND 800
  AND m.deleted = 0
  AND EXISTS (SELECT 1 FROM public.system_role r WHERE r.id = 1 AND r.deleted = 0)
  AND NOT EXISTS (
      SELECT 1 FROM public.system_role_menu rm
      WHERE rm.role_id = 1 AND rm.menu_id = m.id AND rm.deleted = 0
  );
