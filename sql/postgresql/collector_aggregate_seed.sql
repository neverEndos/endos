-- 采集数据多级聚合定时任务种子（PostgreSQL）
--
-- 1. handler_name 对应四个 Job 的 Spring Bean 名（infra_job.handler_name 为 varchar(64)，
--    且 JobHandlerInvoker 按 Bean 名加载，不能填全类名）；按未逻辑删除的 handler_name 判重。
-- 2. 不固定任务 id，不覆盖已有 cron 或启停配置；所有写入在一个事务内，失败即停。
-- 3. 本脚本只写 infra_job，不会自动注册 Quartz。部署后必须调用一次
--    POST /admin-api/infra/job/sync（或在管理后台“定时任务”页面点击“同步”）。
-- 4. 同步后确认任务已开启，并在调度日志中检查 agg_type、通道数、桶数、源行数与耗时。
--
-- PGPASSWORD=... psql -h <host> -U <user> -d <db> -f sql/postgresql/collector_aggregate_seed.sql

\set ON_ERROR_STOP on
SET client_encoding = 'UTF8';
BEGIN;

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
