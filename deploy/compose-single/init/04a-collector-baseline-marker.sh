#!/bin/sh
# PostgreSQL 首次初始化：为全新库登记 collector 迁移基线版本。
# 与 sql/postgresql/dbctl.sh init 的登记内容保持一致（compose 空数据卷路径等价于 dbctl 新库）。
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
INSERT INTO public.schema_migration (version, filename, kind, description) VALUES
    ('20261116.1', 'collector_threshold_migration.sql', 'ddl', '全新初始化：schema 与系统种子已包含该版本全部内容'),
    ('20261116.2', 'collector_range_fix.sql', 'data_repair', '全新初始化：schema 与系统种子已包含该版本全部内容'),
    ('20261117.1', 'collector_alarm_migration.sql', 'ddl', '全新初始化：schema 与系统种子已包含该版本全部内容'),
    ('20261117.2', 'collector_latest_migration.sql', 'ddl', '全新初始化：schema 与系统种子已包含该版本全部内容'),
    ('20261117.3', 'collector_aggregate_migration.sql', 'ddl', '全新初始化：schema 与系统种子已包含该版本全部内容')
ON CONFLICT (version) DO NOTHING;
SQL
