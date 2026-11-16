-- 采集器 Web 端种子数据（PostgreSQL）
--
-- 幂等与冲突说明：
-- 1. HSI 协议按 protocol_code 判重，已存在则跳过；不会覆盖已有 Schema。
-- 2. 菜单固定使用编号 780~800。写入前逐 ID 预检（包含 deleted=1 的历史行）：
--    已占用行必须与本种子的 name/permission/type 完全一致，否则立即报错并整体回滚。
-- 3. super_admin 只授权本次种子中 name/permission/type 精确匹配的 collector 菜单，
--    不再按 780~800 区间盲授权；角色菜单按 (role_id, menu_id) 判重。
-- 4. 本脚本只负责协议注册行、菜单与 super_admin 授权，演示设备/传感器/通道
--    由 collector/seed.py 幂等写入，避免 SQL 与 Python 两套演示数据互相覆盖。
-- 5. 菜单写入后需重启后端或清理权限缓存，管理员新权限才会生效。
-- 6. 所有修改在一个事务内执行；通过 psql 执行时 ON_ERROR_STOP 失败即停。
--
-- 执行方式（示例，密码从环境变量读取，不要写入仓库）：
--   PGPASSWORD=... psql -h <host> -U <user> -d ruoyi-vue-pro -f sql/postgresql/collector_web_seed.sql

\set ON_ERROR_STOP on
SET client_encoding = 'UTF8';
BEGIN;

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

-- ========== 本次菜单种子（目录 + 6 个页面 + 14 个按钮） ==========
CREATE TEMP TABLE collector_menu_seed (
    id bigint PRIMARY KEY,
    parent_id bigint NOT NULL,
    name character varying(50) NOT NULL,
    permission character varying(100) NOT NULL,
    type smallint NOT NULL,
    sort integer NOT NULL,
    path character varying(200) NOT NULL,
    icon character varying(100) NOT NULL,
    component character varying(255),
    component_name character varying(255)
) ON COMMIT DROP;

INSERT INTO collector_menu_seed (
    id, parent_id, name, permission, type, sort, path, icon, component, component_name
) VALUES
    (780, 0, '采集器', '', 1, 90, 'collector', 'ep:cpu', '', ''),
    (781, 780, '采集设备', 'collector:device:query', 2, 1, 'device', 'ep:monitor', 'collector/device/index', 'CollectorDevice'),
    (782, 780, '采集传感器', 'collector:sensor:query', 2, 2, 'sensor', 'ep:connection', 'collector/sensor/index', 'CollectorSensor'),
    (783, 780, '采集通道', 'collector:channel:query', 2, 3, 'channel', 'ep:data-line', 'collector/channel/index', 'CollectorChannel'),
    (784, 780, '传感器类型', 'collector:sensor-type:query', 2, 4, 'sensor-type', 'ep:collection-tag', 'collector/sensor-type/index', 'CollectorSensorType'),
    (785, 780, '参数模板', 'collector:sensor-template:query', 2, 5, 'sensor-template', 'ep:document', 'collector/sensor-template/index', 'CollectorSensorTemplate'),
    (786, 780, '采集协议', 'collector:protocol:query', 2, 6, 'protocol', 'ep:link', 'collector/protocol/index', 'CollectorProtocol'),
    (787, 781, '采集设备新增', 'collector:device:create', 3, 1, '', '', NULL, NULL),
    (788, 781, '采集设备修改', 'collector:device:update', 3, 2, '', '', NULL, NULL),
    (789, 781, '采集设备删除', 'collector:device:delete', 3, 3, '', '', NULL, NULL),
    (790, 782, '采集传感器新增', 'collector:sensor:create', 3, 1, '', '', NULL, NULL),
    (791, 782, '采集传感器修改', 'collector:sensor:update', 3, 2, '', '', NULL, NULL),
    (792, 782, '采集传感器删除', 'collector:sensor:delete', 3, 3, '', '', NULL, NULL),
    (793, 783, '采集通道修改', 'collector:channel:update', 3, 1, '', '', NULL, NULL),
    (794, 783, '采集通道删除', 'collector:channel:delete', 3, 2, '', '', NULL, NULL),
    (795, 784, '传感器类型新增', 'collector:sensor-type:create', 3, 1, '', '', NULL, NULL),
    (796, 784, '传感器类型修改', 'collector:sensor-type:update', 3, 2, '', '', NULL, NULL),
    (797, 784, '传感器类型删除', 'collector:sensor-type:delete', 3, 3, '', '', NULL, NULL),
    (798, 785, '参数模板新增', 'collector:sensor-template:create', 3, 1, '', '', NULL, NULL),
    (799, 785, '参数模板修改', 'collector:sensor-template:update', 3, 2, '', '', NULL, NULL),
    (800, 785, '参数模板删除', 'collector:sensor-template:delete', 3, 3, '', '', NULL, NULL);

-- 任何固定 ID 被非本种子菜单占用（包括逻辑删除行）都明确失败，不覆盖、不授权。
DO $$
DECLARE
    conflict_detail text;
BEGIN
    SELECT string_agg(
        format('id=%s, existing=(%s,%s,%s), expected=(%s,%s,%s)',
            m.id, m.name, m.permission, m.type, s.name, s.permission, s.type),
        '; ' ORDER BY m.id)
    INTO conflict_detail
    FROM public.system_menu m
    JOIN collector_menu_seed s ON s.id = m.id
    WHERE m.name IS DISTINCT FROM s.name
       OR m.permission IS DISTINCT FROM s.permission
       OR m.type IS DISTINCT FROM s.type;

    IF conflict_detail IS NOT NULL THEN
        RAISE EXCEPTION '采集器菜单固定 ID 冲突，已回滚：%', conflict_detail;
    END IF;
END $$;

INSERT INTO public.system_menu (
    id, parent_id, name, permission, type, sort, path, icon, component, component_name,
    status, visible, keep_alive, always_show, creator, updater, deleted
)
SELECT
    s.id, s.parent_id, s.name, s.permission, s.type, s.sort, s.path, s.icon,
    s.component, s.component_name, 0, true, true, true, '1', '1', 0
FROM collector_menu_seed s
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    name = EXCLUDED.name,
    permission = EXCLUDED.permission,
    type = EXCLUDED.type,
    sort = EXCLUDED.sort,
    path = EXCLUDED.path,
    icon = EXCLUDED.icon,
    component = EXCLUDED.component,
    component_name = EXCLUDED.component_name,
    status = 0,
    visible = true,
    keep_alive = true,
    always_show = true,
    updater = '1',
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

-- ========== super_admin 授权（仅 role_id = 1，精确匹配本次 collector 菜单） ==========
INSERT INTO public.system_role_menu (
    id, role_id, menu_id, creator, updater, deleted
)
SELECT
    nextval('public.system_role_menu_seq'), 1, m.id, '1', '1', 0
FROM public.system_menu m
JOIN collector_menu_seed s
    ON s.id = m.id AND s.name = m.name AND s.permission = m.permission AND s.type = m.type
WHERE m.deleted = 0
  AND EXISTS (SELECT 1 FROM public.system_role r WHERE r.id = 1 AND r.deleted = 0)
  AND NOT EXISTS (
      SELECT 1 FROM public.system_role_menu rm
      WHERE rm.role_id = 1 AND rm.menu_id = m.id AND rm.deleted = 0
  );

COMMIT;
