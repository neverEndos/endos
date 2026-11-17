/*
 采集器报警引擎 v1 结构迁移脚本（PostgreSQL 17）

 1. 为原始采样增加全局递增 sample_id，供 (collect_time, sample_id) 游标稳定扫描；
    已存在的采样按序列补齐，新增采样仍可由采集器按原字段写入。
 2. collector_alarm 增量增加 event_type，不重建表、不清理历史事件；事件类型扩展
    changed（同级别阈值侧切换）。事件只追加，不再原地改写 event_type。
 3. 活动唯一性从 dedupe_key 收敛为通道维度：同一 channel_id 最多一条 cleared_at IS NULL
    的事件；迁移会把同通道较早的活动事件标记为已清除（保留原 event_type）。
 4. 新增 collector_alarm_state（每通道当前状态）与 collector_alarm_cursor（单行作业游标）。
 5. 游标由 CollectorAlarmEvaluateJob 首次执行时初始化到当时最新采样，不回放历史；
    作业初始化日志会记录游标位置，因此本迁移不预置游标。
 6. 本脚本幂等、失败即停，所有变更在一个事务中执行。

 容量与停机提示：sample_id 回填与索引创建均为全表操作，且事务内 CREATE INDEX 会阻塞
 表写入；生产环境请安排在维护窗口或停机期执行，避免采集写入超时。

 执行方式（密码从环境变量读取，不要写入仓库）：
   PGPASSWORD=... psql -h <host> -U <user> -d <db> -f sql/postgresql/collector_alarm_migration.sql
*/

\set ON_ERROR_STOP on
SET client_encoding = 'UTF8';
BEGIN;

-- =============================================================================
-- 1. 原始采样游标字段与索引
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS public.collector_sample_raw_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE public.collector_sample_raw
    ADD COLUMN IF NOT EXISTS sample_id bigint;

ALTER TABLE public.collector_sample_raw
    ALTER COLUMN sample_id SET DEFAULT nextval('public.collector_sample_raw_seq');

UPDATE public.collector_sample_raw
SET sample_id = nextval('public.collector_sample_raw_seq')
WHERE sample_id IS NULL;

ALTER TABLE public.collector_sample_raw
    ALTER COLUMN sample_id SET NOT NULL;

-- 序列至少从已分配的最大值之后继续，兼容曾手工写入 sample_id 的环境。
SELECT setval(
    'public.collector_sample_raw_seq',
    GREATEST(COALESCE((SELECT MAX(sample_id) FROM public.collector_sample_raw), 0), 1),
    EXISTS (SELECT 1 FROM public.collector_sample_raw)
);

CREATE INDEX IF NOT EXISTS idx_collector_sample_raw_cursor
    ON public.collector_sample_raw USING btree (collect_time, sample_id);

COMMENT ON COLUMN public.collector_sample_raw.sample_id IS '全局递增采样编号；与 collect_time 组成报警引擎游标';


-- =============================================================================
-- 2. 报警事件类型（保留现有 alarm_type 作为四档阈值类型）
-- =============================================================================

ALTER TABLE public.collector_alarm
    ADD COLUMN IF NOT EXISTS event_type character varying(16) DEFAULT 'raised'::character varying NOT NULL;

ALTER TABLE public.collector_alarm
    DROP CONSTRAINT IF EXISTS chk_collector_alarm_event_type;
ALTER TABLE public.collector_alarm
    ADD CONSTRAINT chk_collector_alarm_event_type
    CHECK (event_type IN ('raised', 'upgraded', 'downgraded', 'changed', 'cleared'));

COMMENT ON COLUMN public.collector_alarm.event_type IS '事件类型：raised 触发、upgraded 升级、downgraded 降级、changed 同级别阈值侧切换、cleared 恢复';

-- 同一通道最多一条活动事件。先收敛历史数据：关闭除最新一条外的活动事件，
-- 只改 status/cleared_at/clear_reason，不改写 event_type。
WITH active_ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY channel_id ORDER BY triggered_at DESC, id DESC) AS rn
    FROM public.collector_alarm
    WHERE cleared_at IS NULL
      AND channel_id > 0
)
UPDATE public.collector_alarm AS alarm
SET status = 2,
    cleared_at = COALESCE(alarm.last_seen_at, alarm.triggered_at, CURRENT_TIMESTAMP),
    clear_reason = '迁移收敛：同一通道仅保留最新活动事件'
FROM active_ranked
WHERE alarm.id = active_ranked.id
  AND active_ranked.rn > 1;

DROP INDEX IF EXISTS public.uk_collector_alarm_active_dedupe;

CREATE UNIQUE INDEX IF NOT EXISTS uk_collector_alarm_active_channel
    ON public.collector_alarm USING btree (channel_id)
    WHERE (cleared_at IS NULL AND channel_id > 0);

COMMENT ON COLUMN public.collector_alarm.dedupe_key IS '事件来源键（dev:{deviceId}:sensor:{sensorId}:ch:{channelId}:type:{alarmType}）；活动唯一性由 uk_collector_alarm_active_channel 按通道约束';


-- =============================================================================
-- 3. 通道当前报警状态
-- =============================================================================

CREATE TABLE IF NOT EXISTS public.collector_alarm_state (
    channel_id bigint NOT NULL,
    device_id bigint NOT NULL,
    sensor_id bigint NOT NULL,
    level smallint DEFAULT 0 NOT NULL,
    threshold_type character varying(32),
    since timestamp(3) without time zone,
    last_value double precision,
    last_threshold double precision,
    last_sample_time timestamp(3) without time zone,
    last_sample_id bigint,
    updated_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_collector_alarm_state PRIMARY KEY (channel_id),
    CONSTRAINT chk_collector_alarm_state_level CHECK (level IN (0, 2, 3)),
    CONSTRAINT chk_collector_alarm_state_threshold_type CHECK (
        threshold_type IS NULL OR threshold_type IN (
            'threshold_low_error', 'threshold_low_warning',
            'threshold_high_warning', 'threshold_high_error'
        )
    ),
    CONSTRAINT chk_collector_alarm_state_active CHECK (
        (level = 0 AND threshold_type IS NULL AND since IS NULL)
        OR (level > 0 AND threshold_type IS NOT NULL AND since IS NOT NULL)
    ),
    CONSTRAINT chk_collector_alarm_state_value_finite CHECK (
        (last_value IS NULL OR (
            last_value <> 'Infinity'::float8
            AND last_value <> '-Infinity'::float8
            AND last_value <> 'NaN'::float8
        ))
        AND (last_threshold IS NULL OR (
            last_threshold <> 'Infinity'::float8
            AND last_threshold <> '-Infinity'::float8
            AND last_threshold <> 'NaN'::float8
        ))
    )
);

CREATE INDEX IF NOT EXISTS idx_collector_alarm_state_device_level
    ON public.collector_alarm_state USING btree (device_id, level);

COMMENT ON TABLE public.collector_alarm_state IS '通道当前报警状态表（报警引擎重启恢复、滞回与跃迁去重依据）';
COMMENT ON COLUMN public.collector_alarm_state.channel_id IS '通道编号（主键，逻辑关联 collector_channel.id）';
COMMENT ON COLUMN public.collector_alarm_state.device_id IS '设备编号（冗余，用于设备过滤）';
COMMENT ON COLUMN public.collector_alarm_state.sensor_id IS '传感器编号';
COMMENT ON COLUMN public.collector_alarm_state.level IS '当前级别：0 正常、2 预警、3 异常';
COMMENT ON COLUMN public.collector_alarm_state.threshold_type IS '当前命中的四档阈值类型；正常时为空';
COMMENT ON COLUMN public.collector_alarm_state.since IS '当前级别开始时间；正常时为空';
COMMENT ON COLUMN public.collector_alarm_state.last_value IS '最近参与判定的工程值';
COMMENT ON COLUMN public.collector_alarm_state.last_threshold IS '最近判定使用的阈值';
COMMENT ON COLUMN public.collector_alarm_state.last_sample_time IS '最近参与判定的采样时间';
COMMENT ON COLUMN public.collector_alarm_state.last_sample_id IS '最近参与判定的采样编号';
COMMENT ON COLUMN public.collector_alarm_state.updated_time IS '状态更新时间';


-- =============================================================================
-- 4. 报警扫描游标（单板单实例，固定一行）
-- =============================================================================

CREATE TABLE IF NOT EXISTS public.collector_alarm_cursor (
    id smallint DEFAULT 1 NOT NULL,
    last_sample_time timestamp(3) without time zone DEFAULT '1970-01-01 00:00:00'::timestamp NOT NULL,
    last_sample_id bigint DEFAULT 0 NOT NULL,
    updated_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_collector_alarm_cursor PRIMARY KEY (id),
    CONSTRAINT chk_collector_alarm_cursor_singleton CHECK (id = 1),
    CONSTRAINT chk_collector_alarm_cursor_sample CHECK (last_sample_id >= 0)
);

COMMENT ON TABLE public.collector_alarm_cursor IS '报警扫描单行游标；首次执行定位最新采样，批次提交成功后推进';
COMMENT ON COLUMN public.collector_alarm_cursor.id IS '固定为 1，限制单实例单行';
COMMENT ON COLUMN public.collector_alarm_cursor.last_sample_time IS '已处理采样的 collect_time（含）';
COMMENT ON COLUMN public.collector_alarm_cursor.last_sample_id IS '同一 collect_time 下已处理的最大 sample_id（含）';
COMMENT ON COLUMN public.collector_alarm_cursor.updated_time IS '游标更新时间';

COMMIT;
