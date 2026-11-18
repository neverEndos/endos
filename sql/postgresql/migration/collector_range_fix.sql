/*
 CS1000 量程与四档阈值占位数据修正（PostgreSQL 17）

 仅修正同时满足以下条件的种子占位数据：
   1. ISO 模板量程仍为 0~100，或 SAE 模板量程仍为 0~20，且四档比例仍为默认值；
   2. 通道引用上述模板，量程与四档阈值均仍等于旧占位量程及默认比例计算值。
 任一字段已被用户修改的通道不会更新。修正后四档阈值按新量程和模板比例重算。
 enum 通道统一为四档阈值 NULL、报警与四个阈值开关 0；FlowState 的 11~55 展示量程不变。

 本脚本幂等，所有修改在一个事务内执行；通过 psql 执行时 ON_ERROR_STOP 失败即停。
 执行方式（密码从环境变量读取，不要写入仓库）：
   PGPASSWORD=... psql -h <host> -U <user> -d <db> -f sql/postgresql/collector_range_fix.sql
*/

-- ---------------------------------------------------------------------------
-- 迁移版本：20261116.2 ｜ 分类：data_repair ｜ 登记：public.schema_migration
-- 位置：sql/postgresql/migration/；全新库无需执行（collector/01_schema.sql 已包含
-- 全部 DDL 结果），由 dbctl.sh migrate / mark-baseline 管理版本；也可经 psql 单独
-- 执行（幂等、单事务）。
-- ---------------------------------------------------------------------------
\set ON_ERROR_STOP on
SET client_encoding = 'UTF8';
BEGIN;

CREATE TEMP TABLE collector_range_fix_target ON COMMIT DROP AS
SELECT
    st.id AS template_id,
    st.value_min AS old_min,
    st.value_max AS old_max,
    v.new_min,
    v.new_max,
    round((st.value_min + st.threshold_low_error_ratio
        * (st.value_max - st.value_min))::numeric, 4)::double precision AS old_low_error,
    round((st.value_min + st.threshold_low_warning_ratio
        * (st.value_max - st.value_min))::numeric, 4)::double precision AS old_low_warning,
    round((st.value_min + st.threshold_high_warning_ratio
        * (st.value_max - st.value_min))::numeric, 4)::double precision AS old_high_warning,
    round((st.value_min + st.threshold_high_error_ratio
        * (st.value_max - st.value_min))::numeric, 4)::double precision AS old_high_error
FROM public.collector_sensor_template st
JOIN public.collector_sensor_type t
    ON t.id = st.sensor_type_id AND t.deleted = 0 AND t.type_code = 'cs1000'
JOIN (VALUES
    ('iso_4', 0::float8, 100::float8, 8::float8, 25::float8),
    ('iso_6', 0::float8, 100::float8, 7::float8, 24::float8),
    ('iso_14', 0::float8, 100::float8, 6::float8, 23::float8),
    ('sae_a', 0::float8, 20::float8, 0::float8, 14::float8),
    ('sae_b', 0::float8, 20::float8, 0::float8, 14::float8),
    ('sae_c', 0::float8, 20::float8, 0::float8, 14::float8),
    ('sae_d', 0::float8, 20::float8, 0::float8, 14::float8)
) AS v(channel_code, old_min, old_max, new_min, new_max)
    ON v.channel_code = st.channel_code
   AND st.value_min = v.old_min AND st.value_max = v.old_max
WHERE st.deleted = 0
  AND st.value_kind = 'number'
  AND st.threshold_low_error_ratio = 0.10
  AND st.threshold_low_warning_ratio = 0.30
  AND st.threshold_high_warning_ratio = 0.70
  AND st.threshold_high_error_ratio = 0.90;

UPDATE public.collector_sensor_template st
SET value_min = target.new_min,
    value_max = target.new_max,
    updater = 'collector-range-fix',
    update_time = CURRENT_TIMESTAMP
FROM collector_range_fix_target target
WHERE st.id = target.template_id;

UPDATE public.collector_channel c
SET value_min = target.new_min,
    value_max = target.new_max,
    threshold_low_error = round((target.new_min + st.threshold_low_error_ratio
        * (target.new_max - target.new_min))::numeric, 4)::double precision,
    threshold_low_warning = round((target.new_min + st.threshold_low_warning_ratio
        * (target.new_max - target.new_min))::numeric, 4)::double precision,
    threshold_high_warning = round((target.new_min + st.threshold_high_warning_ratio
        * (target.new_max - target.new_min))::numeric, 4)::double precision,
    threshold_high_error = round((target.new_min + st.threshold_high_error_ratio
        * (target.new_max - target.new_min))::numeric, 4)::double precision,
    updater = 'collector-range-fix',
    update_time = CURRENT_TIMESTAMP
FROM collector_range_fix_target target
JOIN public.collector_sensor_template st ON st.id = target.template_id
WHERE c.sensor_template_id = target.template_id
  AND c.deleted = 0
  AND c.value_kind = 'number'
  AND c.value_min = target.old_min AND c.value_max = target.old_max
  AND c.threshold_low_error = target.old_low_error
  AND c.threshold_low_warning = target.old_low_warning
  AND c.threshold_high_warning = target.old_high_warning
  AND c.threshold_high_error = target.old_high_error;

-- enum 不参与阈值报警；保留枚举原始值展示量程，不保留伪造的数值阈值。
UPDATE public.collector_channel
SET threshold_low_error = NULL,
    threshold_low_warning = NULL,
    threshold_high_warning = NULL,
    threshold_high_error = NULL,
    alarm_enabled = 0,
    threshold_low_error_enabled = 0,
    threshold_low_warning_enabled = 0,
    threshold_high_warning_enabled = 0,
    threshold_high_error_enabled = 0,
    updater = 'collector-range-fix',
    update_time = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND value_kind = 'enum'
  AND (threshold_low_error IS NOT NULL
       OR threshold_low_warning IS NOT NULL
       OR threshold_high_warning IS NOT NULL
       OR threshold_high_error IS NOT NULL
       OR alarm_enabled <> 0
       OR threshold_low_error_enabled <> 0
       OR threshold_low_warning_enabled <> 0
       OR threshold_high_warning_enabled <> 0
       OR threshold_high_error_enabled <> 0);

-- 自登记迁移版本（schema_migration 表存在时生效；dbctl.sh 管理版本时可忽略）
DO $$
BEGIN
    IF to_regclass('public.schema_migration') IS NOT NULL THEN
        INSERT INTO public.schema_migration (version, filename, kind, description)
        VALUES ('20261116.2', 'collector_range_fix.sql', 'data_repair', 'CS1000 量程与四档阈值占位数据修正（纯 DML）')
        ON CONFLICT (version) DO NOTHING;
    END IF;
END $$;

COMMIT;
