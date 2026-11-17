-- 采集通道最新值看板迁移（PostgreSQL）
--
-- 幂等、单事务；可重复执行。只新增最新值表及索引，不修改既有采样与报警表。
-- 执行方式（密码从环境变量读取，不要写入仓库）：
--   PGPASSWORD=... psql -h <host> -U <user> -d <database> -f sql/postgresql/collector_latest_migration.sql

\set ON_ERROR_STOP on
SET client_encoding = 'UTF8';
BEGIN;

CREATE TABLE IF NOT EXISTS public.collector_channel_latest (
    channel_id bigint NOT NULL,
    device_id bigint NOT NULL,
    sensor_id bigint NOT NULL,
    value_num numeric,
    value_text character varying(64),
    quality smallint DEFAULT 0 NOT NULL,
    sample_time timestamp(3) without time zone NOT NULL,
    updated_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_collector_channel_latest PRIMARY KEY (channel_id),
    CONSTRAINT fk_collector_channel_latest_channel
        FOREIGN KEY (channel_id) REFERENCES public.collector_channel(id) ON DELETE CASCADE,
    CONSTRAINT chk_collector_channel_latest_quality CHECK (quality IN (0, 1, 2))
);

CREATE INDEX IF NOT EXISTS idx_collector_channel_latest_device
    ON public.collector_channel_latest USING btree (device_id);

COMMENT ON TABLE public.collector_channel_latest IS '采集通道最新值表；每通道一行，由采集器与原始采样同事务 upsert';
COMMENT ON COLUMN public.collector_channel_latest.channel_id IS '通道编号（主键，级联跟随通道删除）';
COMMENT ON COLUMN public.collector_channel_latest.device_id IS '所属设备编号（冗余，用于看板查询）';
COMMENT ON COLUMN public.collector_channel_latest.sensor_id IS '所属传感器编号（冗余，用于看板分组）';
COMMENT ON COLUMN public.collector_channel_latest.value_num IS '数值通道最新工程值';
COMMENT ON COLUMN public.collector_channel_latest.value_text IS '枚举通道最新值';
COMMENT ON COLUMN public.collector_channel_latest.quality IS '数据质量：0 正常、1 超时/异常、2 陈旧（由后端按时间判定）';
COMMENT ON COLUMN public.collector_channel_latest.sample_time IS '最近一次有效采样时间';
COMMENT ON COLUMN public.collector_channel_latest.updated_time IS '最近一次落库时间';

COMMIT;
