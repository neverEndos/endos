-- 采集器系统种子（PostgreSQL）—— 幂等、单事务
--
-- 内容（仅平台级配置数据，可在任何已初始化库上重复执行）：
--   1. 协议注册行：modbus_rtu / modbus_tcp / hsi_rs485（按 protocol_code 判重，已存在则跳过）
--   2. 保留策略初始建议：8 条（按 data_class 判重，不覆盖人工调整）
--   3. 采集器 Web 端菜单与角色授权（固定 ID 780~805，冲突预检，不覆盖他人菜单）
--   4. 报警判定定时任务（infra_job，按 handler_name 判重）
--   5. 秒/分/小时/天聚合定时任务（infra_job，按 handler_name 判重）
--
-- 注意：
-- 1. 本脚本不包含演示设备/传感器/通道（业务数据）：生产/开发库由 collector/seed.py
--    幂等写入；校验库由 collector/test/01_demo_seed.sql 写入（仅限 dbctl 校验库）。
-- 2. 定时任务只写 infra_job 记录，不会自动注册 Quartz；执行后需调用一次
--    POST /admin-api/infra/job/sync（或管理后台“定时任务”页点击“同步”）。
-- 3. 菜单写入后需重启后端或清理权限缓存，管理员新权限才会生效。
-- 4. 所有修改在一个事务内执行；通过 psql 执行时 ON_ERROR_STOP 失败即停。
-- 5. 全新库请优先使用 dbctl.sh init（会自动按序执行本种子并登记迁移版本）。
--
-- 执行方式（示例，密码从环境变量读取，不要写入仓库）：
--   PGPASSWORD=... psql -h <host> -U <user> -d <db> -f sql/postgresql/collector/02_system_seed.sql

\set ON_ERROR_STOP on
SET client_encoding = 'UTF8';
BEGIN;

-- ========== 1. 协议注册行 ==========
-- 协议 connection_schema / channel_schema 为合法 JSON Schema（Draft 2020-12），
-- 包含必填字段、枚举、默认值与示例；Service 保存与发布配置时按此校验。
-- id 使用序列生成，保证后续通过序列新增协议时编号不冲突。

-- Modbus RTU：仅首期支持的串口 Modbus 从站协议。

INSERT INTO public.collector_protocol (
    id, protocol_code, protocol_name, transport_type, driver_key,
    connection_schema, channel_schema, protocol_version, enabled, sort, description
)
SELECT
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
WHERE NOT EXISTS (
    SELECT 1 FROM public.collector_protocol WHERE protocol_code = 'modbus_rtu' AND deleted = 0
);

-- Modbus TCP：TCP Modbus 从站协议。
INSERT INTO public.collector_protocol (
    id, protocol_code, protocol_name, transport_type, driver_key,
    connection_schema, channel_schema, protocol_version, enabled, sort, description
)
SELECT
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
WHERE NOT EXISTS (
    SELECT 1 FROM public.collector_protocol WHERE protocol_code = 'modbus_tcp' AND deleted = 0
);

-- HYDAC HSI (RS485)：hsi_rs485 驱动；与 collector/seed.py 的 PROTOCOL_CODE 一致。
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

-- ========== 2. 保留策略初始建议 ==========
-- 对应设计稿容量结论；仅按 data_class 判重补齐，不覆盖人工调整过的策略。
INSERT INTO public.collector_retention_policy (
    id, data_class, retention_days, partition_lead_days, action, enabled, description
)
SELECT nextval('public.collector_retention_policy_seq'),
    'sample_raw', 30, 7, 'drop_partition', 1, '原始采样：按天分区，保留 30 天，过期分区整体 DROP'
WHERE NOT EXISTS (
    SELECT 1 FROM public.collector_retention_policy WHERE data_class = 'sample_raw' AND deleted = 0
);

INSERT INTO public.collector_retention_policy (
    id, data_class, retention_days, partition_lead_days, action, enabled, description
)
SELECT nextval('public.collector_retention_policy_seq'),
    'aggregate_second', 7, 3, 'delete_rows', 1, '秒聚合：保留 7 天，按 bucket_time 分批删除'
WHERE NOT EXISTS (
    SELECT 1 FROM public.collector_retention_policy WHERE data_class = 'aggregate_second' AND deleted = 0
);

INSERT INTO public.collector_retention_policy (
    id, data_class, retention_days, partition_lead_days, action, enabled, description
)
SELECT nextval('public.collector_retention_policy_seq'),
    'aggregate_minute', 90, 3, 'delete_rows', 1, '分钟聚合：保留 90 天，按 bucket_time 分批删除'
WHERE NOT EXISTS (
    SELECT 1 FROM public.collector_retention_policy WHERE data_class = 'aggregate_minute' AND deleted = 0
);

INSERT INTO public.collector_retention_policy (
    id, data_class, retention_days, partition_lead_days, action, enabled, description
)
SELECT nextval('public.collector_retention_policy_seq'),
    'aggregate_hour', 365, 3, 'delete_rows', 1, '小时聚合：保留 365 天，按 bucket_time 分批删除'
WHERE NOT EXISTS (
    SELECT 1 FROM public.collector_retention_policy WHERE data_class = 'aggregate_hour' AND deleted = 0
);

INSERT INTO public.collector_retention_policy (
    id, data_class, retention_days, partition_lead_days, action, enabled, description
)
SELECT nextval('public.collector_retention_policy_seq'),
    'aggregate_day', 0, 3, 'delete_rows', 1, '天聚合：永久保留（0 表示不清理）'
WHERE NOT EXISTS (
    SELECT 1 FROM public.collector_retention_policy WHERE data_class = 'aggregate_day' AND deleted = 0
);

INSERT INTO public.collector_retention_policy (
    id, data_class, retention_days, partition_lead_days, action, enabled, description
)
SELECT nextval('public.collector_retention_policy_seq'),
    'alarm', 180, 3, 'delete_rows', 1, '报警：仅清理已清除（cleared_at 非空）且超过 180 天的记录'
WHERE NOT EXISTS (
    SELECT 1 FROM public.collector_retention_policy WHERE data_class = 'alarm' AND deleted = 0
);

INSERT INTO public.collector_retention_policy (
    id, data_class, retention_days, partition_lead_days, action, enabled, description
)
SELECT nextval('public.collector_retention_policy_seq'),
    'heartbeat', 7, 3, 'delete_rows', 1, '心跳：保留 7 天'
WHERE NOT EXISTS (
    SELECT 1 FROM public.collector_retention_policy WHERE data_class = 'heartbeat' AND deleted = 0
);

INSERT INTO public.collector_retention_policy (
    id, data_class, retention_days, partition_lead_days, action, enabled, description
)
SELECT nextval('public.collector_retention_policy_seq'),
    'command', 30, 3, 'delete_rows', 1, '命令：保留 30 天'
WHERE NOT EXISTS (
    SELECT 1 FROM public.collector_retention_policy WHERE data_class = 'command' AND deleted = 0
);

-- 聚合水位不预置：空库尚无设备/通道，由聚合任务为每个 number 通道初始化
-- (hour, channel_id) 与 (day, channel_id) 水位，watermark_time 均为 NULL。

-- ========== 3. Web 端菜单与角色授权（原 collector_web_seed.sql） ==========
-- ========== 本次菜单种子（目录 + 9 个页面 + 16 个按钮） ==========
-- 803 聚合数据回填、805 聚合强制清空仅为 API 权限点，无页面入口；804 为趋势分析页面；
-- 请通过 POST /admin-api/collector/aggregate/backfill 使用，forceClean=true 需要 805 权限。
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
    (802, 780, '最新值看板', 'collector:dashboard:query', 2, 0, 'dashboard', 'ep:odometer', 'collector/dashboard/index', 'CollectorDashboard'),
    (781, 780, '采集设备', 'collector:device:query', 2, 1, 'device', 'ep:monitor', 'collector/device/index', 'CollectorDevice'),
    (782, 780, '采集传感器', 'collector:sensor:query', 2, 2, 'sensor', 'ep:connection', 'collector/sensor/index', 'CollectorSensor'),
    (783, 780, '采集通道', 'collector:channel:query', 2, 3, 'channel', 'ep:data-line', 'collector/channel/index', 'CollectorChannel'),
    (784, 780, '传感器类型', 'collector:sensor-type:query', 2, 4, 'sensor-type', 'ep:collection-tag', 'collector/sensor-type/index', 'CollectorSensorType'),
    (785, 780, '参数模板', 'collector:sensor-template:query', 2, 5, 'sensor-template', 'ep:document', 'collector/sensor-template/index', 'CollectorSensorTemplate'),
    (786, 780, '采集协议', 'collector:protocol:query', 2, 6, 'protocol', 'ep:link', 'collector/protocol/index', 'CollectorProtocol'),
    (801, 780, '报警记录', 'collector:alarm:query', 2, 7, 'alarm', 'ep:warning', 'collector/alarm/index', 'CollectorAlarm'),
    (804, 780, '趋势分析', 'collector:trend:query', 2, 9, 'trend', 'ep:trend-charts', 'collector/trend/index', 'CollectorTrend'),
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
    (800, 785, '参数模板删除', 'collector:sensor-template:delete', 3, 3, '', '', NULL, NULL),
    (803, 780, '聚合数据回填', 'collector:aggregate:rebuild', 3, 8, '', '', NULL, NULL),
    (805, 780, '聚合强制清空', 'collector:aggregate:force-clean', 3, 9, '', '', NULL, NULL);

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

-- ========== 看板依赖的设备查询权限（任意已获得 802 的角色） ==========
INSERT INTO public.system_role_menu (
    id, role_id, menu_id, creator, updater, deleted
)
SELECT nextval('public.system_role_menu_seq'), rm.role_id, 781, '1', '1', 0
FROM public.system_role_menu rm
JOIN public.system_role r ON r.id = rm.role_id AND r.deleted = 0
JOIN public.system_menu dashboard ON dashboard.id = 802 AND dashboard.deleted = 0
JOIN public.system_menu device_menu ON device_menu.id = 781 AND device_menu.deleted = 0
WHERE rm.menu_id = 802 AND rm.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM public.system_role_menu granted
      WHERE granted.role_id = rm.role_id AND granted.menu_id = 781 AND granted.deleted = 0
  )
ON CONFLICT DO NOTHING;

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

-- ========== 4. 报警判定定时任务（原 collector_alarm_seed.sql） ==========
-- 每 30 秒执行一次；作业按每批 2000 行循环处理到追平。
INSERT INTO public.infra_job (
    id, name, status, handler_name, handler_param, cron_expression,
    retry_count, retry_interval, monitor_timeout, creator, updater, deleted
)
SELECT
    nextval('public.infra_job_seq'),
    '采集报警判定',
    1,
    'collectorAlarmEvaluateJob',
    '',
    '0/30 * * * * ?',
    0,
    0,
    10000,
    '1',
    '1',
    0
WHERE NOT EXISTS (
    SELECT 1 FROM public.infra_job
    WHERE handler_name = 'collectorAlarmEvaluateJob' AND deleted = 0
);

-- ========== 5. 聚合定时任务（原 collector_aggregate_seed.sql） ==========
CREATE TEMP TABLE collector_aggregate_job_seed (
    name varchar(100) NOT NULL,
    handler_name varchar(255) NOT NULL,
    cron_expression varchar(64) NOT NULL,
    monitor_timeout integer NOT NULL
) ON COMMIT DROP;

INSERT INTO collector_aggregate_job_seed (name, handler_name, cron_expression, monitor_timeout) VALUES
    ('采集秒聚合（fast 通道）', 'collectorAggregateSecondJob', '0/5 * * * * ?', 60000),
    ('采集分钟聚合', 'collectorAggregateMinuteJob', '0 * * * * ?', 120000),
    ('采集小时聚合', 'collectorAggregateHourJob', '0 0/5 * * * ?', 120000),
    ('采集天聚合', 'collectorAggregateDayJob', '0 0/30 * * * ?', 120000);

INSERT INTO public.infra_job (
    id, name, status, handler_name, handler_param, cron_expression,
    retry_count, retry_interval, monitor_timeout, creator, updater, deleted
)
SELECT nextval('public.infra_job_seq'), s.name, 1, s.handler_name, '', s.cron_expression,
       0, 0, s.monitor_timeout, '1', '1', 0
FROM collector_aggregate_job_seed s
WHERE NOT EXISTS (
    SELECT 1 FROM public.infra_job j
    WHERE j.handler_name = s.handler_name AND j.deleted = 0
);

COMMIT;
