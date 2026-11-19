-- 采集通道最新值看板迁移（PostgreSQL）
--
-- 幂等、单事务；可重复执行。只新增最新值表及索引，不修改既有采样与报警表。
-- 执行方式（密码从环境变量读取，不要写入仓库）：
--   PGPASSWORD=... psql -h <host> -U <user> -d <database> -f sql/postgresql/migration/collector_latest_migration.sql

-- ---------------------------------------------------------------------------
-- 迁移版本：20261117.2 ｜ 分类：ddl ｜ 登记：public.schema_migration
-- 位置：sql/postgresql/migration/；全新库无需执行（collector/01_schema.sql 已包含
-- 全部 DDL 结果），由 dbctl.sh migrate / mark-baseline 管理版本；也可经 psql 单独
-- 执行（幂等、单事务）。
-- ---------------------------------------------------------------------------
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
COMMENT ON COLUMN public.collector_channel_latest.quality IS '数据质量：0 正常、1 不确定、2 坏质量；陈旧由后端按采样时间派生，不写回质量码';
COMMENT ON COLUMN public.collector_channel_latest.sample_time IS '最近一次有效采样时间（Asia/Shanghai 墙钟）';
COMMENT ON COLUMN public.collector_channel_latest.updated_time IS '最近一次落库时间';

-- 自登记迁移版本（schema_migration 表存在时生效；dbctl.sh 管理版本时可忽略）
DO $$
BEGIN
    IF to_regclass('public.schema_migration') IS NOT NULL THEN
        INSERT INTO public.schema_migration (version, filename, kind, description)
        VALUES ('20261117.2', 'collector_latest_migration.sql', 'ddl', '通道最新值看板表')
        ON CONFLICT (version) DO NOTHING;
    END IF;
END $$;

COMMIT;
