-- 采集报警引擎定时任务种子（PostgreSQL）
--
-- 1. handler_name 对应 CollectorAlarmEvaluateJob 的 Spring Bean 名 collectorAlarmEvaluateJob；
--    调度与执行日志由 infra 定时任务提供，判定业务位于 collector 模块。
-- 2. 按未逻辑删除的 handler_name 判重，不固定任务 id，不覆盖已有 cron 或启停配置。
--    本脚本只写入 infra_job 记录，不会自动注册到 Quartz。
-- 3. 执行本脚本后必须调用一次 POST /admin-api/infra/job/sync（或在管理后台
--    “定时任务”页面点击“同步”），才会创建 Quartz 调度；若跳过，任务不会执行。
-- 4. 同步后确认任务存在且状态为“开启”，并在“调度日志”中看到至少一次执行记录。
-- 5. 每 30 秒执行一次；作业会按每批 2000 行循环处理到追平。
-- 6. 本脚本幂等、失败即停，所有修改在一个事务中执行。
--
-- PGPASSWORD=... psql -h <host> -U <user> -d <db> -f sql/postgresql/collector_alarm_seed.sql

\set ON_ERROR_STOP on
SET client_encoding = 'UTF8';
BEGIN;

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

COMMIT;
