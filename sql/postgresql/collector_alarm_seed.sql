-- 采集报警引擎定时任务种子（PostgreSQL）
--
-- 1. handler_name 对应 CollectorAlarmEvaluateJob 的 Spring Bean 名 collectorAlarmEvaluateJob；
--    调度与执行日志由 infra 定时任务提供，判定业务位于 collector 模块。
-- 2. 按未逻辑删除的 handler_name 判重，不固定任务 id，不覆盖已有 cron 或启停配置。
-- 3. 每 30 秒执行一次；作业会按每批 2000 行循环处理到追平。
-- 4. 本脚本幂等、失败即停，所有修改在一个事务中执行。
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
