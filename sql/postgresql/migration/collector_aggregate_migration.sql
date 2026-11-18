-- 采集数据多级聚合迁移（PostgreSQL）
--
-- 1. 为通道增加 normal/fast 数据画像，新增秒、分钟聚合表，四张聚合表增加 valid_count，
--    扩展聚合水位类型与保留策略数据类，并幂等补充秒/分钟保留策略。
-- 2. 旧库新增 valid_count 时以 good_count 近似回填；升级后建议对历史区间重算一次，
--    校正 value_avg 分母与 valid_count。
--    若此前执行过中间版本迁移（列已存在但为 0），本脚本不会再次回填，需手工执行一次
--    UPDATE ... SET valid_count = good_count，或直接重算历史区间。
-- 3. 幂等、单事务；只增列、增表、增索引和必要约束，不删除或清理业务数据。
-- 4. 执行方式（密码从环境变量读取，不要写入仓库）：
--    PGPASSWORD=... psql -h <host> -U <user> -d <database> -f sql/postgresql/collector_aggregate_migration.sql

\set ON_ERROR_STOP on
SET client_encoding = 'UTF8';
BEGIN;

ALTER TABLE public.collector_channel
    ADD COLUMN IF NOT EXISTS data_profile character varying(16) DEFAULT 'normal'::character varying NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.collector_channel'::regclass
          AND conname = 'chk_collector_channel_data_profile'
    ) THEN
        ALTER TABLE public.collector_channel
            ADD CONSTRAINT chk_collector_channel_data_profile CHECK (data_profile IN ('normal', 'fast'));
    END IF;
END $$;

COMMENT ON COLUMN public.collector_channel.data_profile IS '数据画像：normal 常规（不写秒聚合）、fast 高频（写秒聚合）';

CREATE TABLE IF NOT EXISTS public.collector_aggregate_second (
    channel_id bigint NOT NULL,
    device_id bigint NOT NULL,
    bucket_time timestamp(3) without time zone NOT NULL,
    sample_count integer DEFAULT 0 NOT NULL,
    good_count integer DEFAULT 0 NOT NULL,
    valid_count integer DEFAULT 0 NOT NULL,
    bad_count integer DEFAULT 0 NOT NULL,
    value_avg double precision,
    value_min double precision,
    value_max double precision,
    value_sum double precision,
    value_first double precision,
    value_last double precision,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_collector_aggregate_second PRIMARY KEY (channel_id, bucket_time),
    CONSTRAINT chk_collector_aggregate_second_count CHECK (
        sample_count >= 0 AND good_count >= 0 AND valid_count >= 0
        AND valid_count <= good_count AND bad_count >= 0
    ),
    CONSTRAINT chk_collector_aggregate_second_finite CHECK (
        (value_avg IS NULL OR (value_avg <> 'Infinity'::float8 AND value_avg <> '-Infinity'::float8 AND value_avg <> 'NaN'::float8))
        AND (value_min IS NULL OR (value_min <> 'Infinity'::float8 AND value_min <> '-Infinity'::float8 AND value_min <> 'NaN'::float8))
        AND (value_max IS NULL OR (value_max <> 'Infinity'::float8 AND value_max <> '-Infinity'::float8 AND value_max <> 'NaN'::float8))
        AND (value_sum IS NULL OR (value_sum <> 'Infinity'::float8 AND value_sum <> '-Infinity'::float8 AND value_sum <> 'NaN'::float8))
        AND (value_first IS NULL OR (value_first <> 'Infinity'::float8 AND value_first <> '-Infinity'::float8 AND value_first <> 'NaN'::float8))
        AND (value_last IS NULL OR (value_last <> 'Infinity'::float8 AND value_last <> '-Infinity'::float8 AND value_last <> 'NaN'::float8))
    )
);

CREATE INDEX IF NOT EXISTS idx_collector_aggregate_second_device_time
    ON public.collector_aggregate_second USING btree (device_id, bucket_time);
CREATE INDEX IF NOT EXISTS idx_collector_aggregate_second_bucket
    ON public.collector_aggregate_second USING btree (bucket_time);

COMMENT ON TABLE public.collector_aggregate_second IS '秒聚合数据表（仅 fast 通道；统计值仅用 quality=0 且 value 非 NULL 的 raw 行）';
COMMENT ON COLUMN public.collector_aggregate_second.channel_id IS '通道编号';
COMMENT ON COLUMN public.collector_aggregate_second.device_id IS '设备编号（冗余）';
COMMENT ON COLUMN public.collector_aggregate_second.bucket_time IS '秒桶起始时间';
COMMENT ON COLUMN public.collector_aggregate_second.sample_count IS '该桶全部 raw 行数（含 good/uncertain/bad）';
COMMENT ON COLUMN public.collector_aggregate_second.good_count IS 'quality=0 的 raw 行数（含 value 为 NULL 的行）';
COMMENT ON COLUMN public.collector_aggregate_second.bad_count IS 'quality=2 的 raw 行数';
COMMENT ON COLUMN public.collector_aggregate_second.value_avg IS 'value_sum / valid_count；无有效统计值时为 NULL';
COMMENT ON COLUMN public.collector_aggregate_second.value_min IS '有效统计值最小值';
COMMENT ON COLUMN public.collector_aggregate_second.value_max IS '有效统计值最大值';
COMMENT ON COLUMN public.collector_aggregate_second.value_sum IS '有效统计值求和';
COMMENT ON COLUMN public.collector_aggregate_second.value_first IS '桶内首个有效统计值';
COMMENT ON COLUMN public.collector_aggregate_second.value_last IS '桶内最后有效统计值';
COMMENT ON COLUMN public.collector_aggregate_second.update_time IS '最后重算时间';

CREATE TABLE IF NOT EXISTS public.collector_aggregate_minute (
    channel_id bigint NOT NULL,
    device_id bigint NOT NULL,
    bucket_time timestamp(3) without time zone NOT NULL,
    sample_count integer DEFAULT 0 NOT NULL,
    good_count integer DEFAULT 0 NOT NULL,
    valid_count integer DEFAULT 0 NOT NULL,
    bad_count integer DEFAULT 0 NOT NULL,
    value_avg double precision,
    value_min double precision,
    value_max double precision,
    value_sum double precision,
    value_first double precision,
    value_last double precision,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_collector_aggregate_minute PRIMARY KEY (channel_id, bucket_time),
    CONSTRAINT chk_collector_aggregate_minute_count CHECK (
        sample_count >= 0 AND good_count >= 0 AND valid_count >= 0
        AND valid_count <= good_count AND bad_count >= 0
    ),
    CONSTRAINT chk_collector_aggregate_minute_finite CHECK (
        (value_avg IS NULL OR (value_avg <> 'Infinity'::float8 AND value_avg <> '-Infinity'::float8 AND value_avg <> 'NaN'::float8))
        AND (value_min IS NULL OR (value_min <> 'Infinity'::float8 AND value_min <> '-Infinity'::float8 AND value_min <> 'NaN'::float8))
        AND (value_max IS NULL OR (value_max <> 'Infinity'::float8 AND value_max <> '-Infinity'::float8 AND value_max <> 'NaN'::float8))
        AND (value_sum IS NULL OR (value_sum <> 'Infinity'::float8 AND value_sum <> '-Infinity'::float8 AND value_sum <> 'NaN'::float8))
        AND (value_first IS NULL OR (value_first <> 'Infinity'::float8 AND value_first <> '-Infinity'::float8 AND value_first <> 'NaN'::float8))
        AND (value_last IS NULL OR (value_last <> 'Infinity'::float8 AND value_last <> '-Infinity'::float8 AND value_last <> 'NaN'::float8))
    )
);

CREATE INDEX IF NOT EXISTS idx_collector_aggregate_minute_device_time
    ON public.collector_aggregate_minute USING btree (device_id, bucket_time);
CREATE INDEX IF NOT EXISTS idx_collector_aggregate_minute_bucket
    ON public.collector_aggregate_minute USING btree (bucket_time);

COMMENT ON TABLE public.collector_aggregate_minute IS '分钟聚合数据表（fast 从秒表上卷、normal 从 raw 直算；可用 fromRaw 全量审计重算）';
COMMENT ON COLUMN public.collector_aggregate_minute.channel_id IS '通道编号';
COMMENT ON COLUMN public.collector_aggregate_minute.device_id IS '设备编号（冗余）';
COMMENT ON COLUMN public.collector_aggregate_minute.bucket_time IS '分钟桶起始时间';
COMMENT ON COLUMN public.collector_aggregate_minute.sample_count IS '该桶全部 raw 行数（含 good/uncertain/bad）';
COMMENT ON COLUMN public.collector_aggregate_minute.good_count IS 'quality=0 的 raw 行数（含 value 为 NULL 的行）';
COMMENT ON COLUMN public.collector_aggregate_minute.bad_count IS 'quality=2 的 raw 行数';
COMMENT ON COLUMN public.collector_aggregate_minute.value_avg IS 'value_sum / valid_count；无有效统计值时为 NULL';
COMMENT ON COLUMN public.collector_aggregate_minute.value_min IS '有效统计值最小值';
COMMENT ON COLUMN public.collector_aggregate_minute.value_max IS '有效统计值最大值';
COMMENT ON COLUMN public.collector_aggregate_minute.value_sum IS '有效统计值求和';
COMMENT ON COLUMN public.collector_aggregate_minute.value_first IS '桶内首个有效统计值';
COMMENT ON COLUMN public.collector_aggregate_minute.value_last IS '桶内最后有效统计值';
COMMENT ON COLUMN public.collector_aggregate_minute.update_time IS '最后重算时间';

-- 旧库四张聚合表补 valid_count；仅当列原本不存在时用 good_count 近似回填，
-- 避免在已升级库上把真实 valid_count=0（quality=0 且 value 全为 NULL）误改成 good_count。
DO $$
DECLARE
    target_table text;
    column_existed boolean;
BEGIN
    FOREACH target_table IN ARRAY ARRAY[
        'collector_aggregate_second', 'collector_aggregate_minute',
        'collector_aggregate_hour', 'collector_aggregate_day'
    ] LOOP
        SELECT EXISTS (
            SELECT 1 FROM information_schema.columns c
            WHERE c.table_schema = 'public' AND c.table_name = target_table AND c.column_name = 'valid_count'
        ) INTO column_existed;
        EXECUTE format(
            'ALTER TABLE public.%I ADD COLUMN IF NOT EXISTS valid_count integer DEFAULT 0 NOT NULL',
            target_table);
        IF NOT column_existed THEN
            EXECUTE format(
                'UPDATE public.%I SET valid_count = good_count WHERE valid_count = 0 AND good_count > 0',
                target_table);
        END IF;
    END LOOP;
END $$;

DO $$
DECLARE
    table_name text;
    constraint_name text;
    constraint_definition text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'collector_aggregate_second', 'collector_aggregate_minute',
        'collector_aggregate_hour', 'collector_aggregate_day'
    ] LOOP
        constraint_name := 'chk_' || table_name || '_count';
        SELECT pg_get_constraintdef(c.oid) INTO constraint_definition
        FROM pg_constraint c
        WHERE c.conrelid = to_regclass('public.' || table_name) AND c.conname = constraint_name;

        IF constraint_definition IS NOT NULL AND constraint_definition NOT LIKE '%valid_count%' THEN
            EXECUTE format('ALTER TABLE public.%I DROP CONSTRAINT %I', table_name, constraint_name);
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint c
            WHERE c.conrelid = to_regclass('public.' || table_name) AND c.conname = constraint_name
        ) THEN
            EXECUTE format(
                'ALTER TABLE public.%I ADD CONSTRAINT %I CHECK '
                '(sample_count >= 0 AND good_count >= 0 AND valid_count >= 0 '
                'AND valid_count <= good_count AND bad_count >= 0)',
                table_name, constraint_name);
        END IF;
    END LOOP;
END $$;

COMMENT ON COLUMN public.collector_aggregate_second.valid_count
    IS 'quality=0 且 value 非 NULL 的 raw 行数；value_avg 的有效分母';
COMMENT ON COLUMN public.collector_aggregate_minute.valid_count
    IS 'quality=0 且 value 非 NULL 的 raw 行数；value_avg 的有效分母';
COMMENT ON COLUMN public.collector_aggregate_hour.valid_count
    IS 'quality=0 且 value 非 NULL 的 raw 行数；上卷时为下层 valid_count 之和';
COMMENT ON COLUMN public.collector_aggregate_day.valid_count
    IS 'quality=0 且 value 非 NULL 的 raw 行数；上卷时为下层 valid_count 之和';

DO $$
DECLARE
    constraint_definition text;
BEGIN
    SELECT pg_get_constraintdef(oid) INTO constraint_definition
    FROM pg_constraint
    WHERE conrelid = 'public.collector_aggregate_watermark'::regclass
      AND conname = 'chk_collector_aggregate_watermark_type';

    IF constraint_definition IS NOT NULL
            AND (constraint_definition NOT LIKE '%second%' OR constraint_definition NOT LIKE '%minute%') THEN
        ALTER TABLE public.collector_aggregate_watermark
            DROP CONSTRAINT chk_collector_aggregate_watermark_type;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.collector_aggregate_watermark'::regclass
          AND conname = 'chk_collector_aggregate_watermark_type'
    ) THEN
        ALTER TABLE public.collector_aggregate_watermark
            ADD CONSTRAINT chk_collector_aggregate_watermark_type
            CHECK (agg_type IN ('second', 'minute', 'hour', 'day'));
    END IF;
END $$;

COMMENT ON TABLE public.collector_aggregate_watermark
    IS '聚合水位与最近执行审计表；运行态/锁字段预留，当前未实现';
COMMENT ON COLUMN public.collector_aggregate_watermark.agg_type IS '聚合类型：second / minute / hour / day';
COMMENT ON COLUMN public.collector_aggregate_watermark.backfill_from IS '最近一次执行区间起始时间（审计，含）；不表示待补跑范围';
COMMENT ON COLUMN public.collector_aggregate_watermark.backfill_to IS '最近一次执行区间结束时间（审计，不含）；不表示待补跑范围';
COMMENT ON COLUMN public.collector_aggregate_watermark.status IS '状态：0 空闲；1 运行中、2 失败为预留，当前聚合流程未写入';
COMMENT ON COLUMN public.collector_aggregate_watermark.retry_count IS '连续失败次数（预留，当前恒为 0）';
COMMENT ON COLUMN public.collector_aggregate_watermark.last_error IS '最近一次错误信息（预留，当前未写入）';
COMMENT ON COLUMN public.collector_aggregate_watermark.locked_until IS '任务锁到期时间（预留，当前未实现）';

DO $$
DECLARE
    constraint_definition text;
BEGIN
    SELECT pg_get_constraintdef(c.oid) INTO constraint_definition
    FROM pg_constraint c
    WHERE c.conrelid = 'public.collector_retention_policy'::regclass
      AND c.conname = 'chk_collector_retention_policy_class';

    IF constraint_definition IS NOT NULL
            AND (constraint_definition NOT LIKE '%aggregate_second%'
                 OR constraint_definition NOT LIKE '%aggregate_minute%') THEN
        ALTER TABLE public.collector_retention_policy
            DROP CONSTRAINT chk_collector_retention_policy_class;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.collector_retention_policy'::regclass
          AND conname = 'chk_collector_retention_policy_class'
    ) THEN
        ALTER TABLE public.collector_retention_policy
            ADD CONSTRAINT chk_collector_retention_policy_class CHECK (
                data_class IN ('sample_raw', 'aggregate_second', 'aggregate_minute',
                    'aggregate_hour', 'aggregate_day', 'alarm', 'heartbeat', 'command')
            );
    END IF;
END $$;

COMMENT ON COLUMN public.collector_retention_policy.data_class
    IS '数据类：sample_raw/aggregate_second/aggregate_minute/aggregate_hour/aggregate_day/alarm/heartbeat/command';

-- 幂等补齐新增聚合层的保留策略；已有策略（包括人工调整过天数）不覆盖。
INSERT INTO public.collector_retention_policy (
    id, data_class, retention_days, partition_lead_days, action, enabled, description
)
SELECT nextval('public.collector_retention_policy_seq'), 'aggregate_second', 7, 3,
    'delete_rows', 1, '秒聚合：保留 7 天，按 bucket_time 分批删除'
WHERE NOT EXISTS (
    SELECT 1 FROM public.collector_retention_policy
    WHERE data_class = 'aggregate_second' AND deleted = 0
);
INSERT INTO public.collector_retention_policy (
    id, data_class, retention_days, partition_lead_days, action, enabled, description
)
SELECT nextval('public.collector_retention_policy_seq'), 'aggregate_minute', 90, 3,
    'delete_rows', 1, '分钟聚合：保留 90 天，按 bucket_time 分批删除'
WHERE NOT EXISTS (
    SELECT 1 FROM public.collector_retention_policy
    WHERE data_class = 'aggregate_minute' AND deleted = 0
);


COMMIT;
