package cn.iocoder.yudao.module.collector.service.retention;

import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService.RetentionClassResult;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService.RetentionCleanupReport;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.Assume;
import org.junit.Before;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 采集数据保留清理的真实 PostgreSQL 测试基座。
 *
 * <p>未设置 {@code COLLECTOR_TEST_PG_URL} 时整体跳过；建表/删表属于破坏性操作，还要求
 * {@code COLLECTOR_TEST_PG_ALLOW_DESTRUCTIVE=true} 且库名以 {@code collector_retention_} 开头。</p>
 *
 * <p>「今天」固定为 {@link #BASE_DAY} 12:00（Asia/Shanghai），默认保留 30 天，
 * 因此 cutoff 固定为 {@link #CUTOFF}（2026-10-21 00:00），不依赖真实当天。</p>
 */
abstract class CollectorRetentionPgTestSupport {

    protected static final String URL = System.getenv("COLLECTOR_TEST_PG_URL");
    protected static final String USER = System.getenv("COLLECTOR_TEST_PG_USER");
    protected static final String PASSWORD = System.getenv("COLLECTOR_TEST_PG_PASSWORD");
    private static final String ALLOW_DESTRUCTIVE = System.getenv("COLLECTOR_TEST_PG_ALLOW_DESTRUCTIVE");

    /** 允许被破坏的库名前缀：只接受临时保留清理校验库。 */
    private static final String TEST_DATABASE_PREFIX = "collector_retention_";

    protected static final LocalDate BASE_DAY = LocalDate.of(2026, 11, 20);
    protected static final LocalDateTime BASE_NOON = BASE_DAY.atTime(12, 0);
    protected static final int DEFAULT_RETENTION_DAYS = 30;
    protected static final LocalDate CUTOFF_DAY = BASE_DAY.minusDays(DEFAULT_RETENTION_DAYS);
    protected static final LocalDateTime CUTOFF = CUTOFF_DAY.atStartOfDay();

    protected static final String RAW = "collector_sample_raw";
    protected static final String DEFAULT_PARTITION = RAW + "_default";
    protected static final String PARTITION_PREFIX = RAW + "_p";

    protected CollectorRetentionCleanupServiceImpl cleanupService;
    protected CollectorRetentionPolicyServiceImpl policyService;
    protected UnpooledDataSource dataSource;
    protected JdbcTemplate jdbcTemplate;

    @Before
    public void setUpRetentionPg() throws Exception {
        Assume.assumeTrue("未设置 COLLECTOR_TEST_PG_URL，跳过临时 PostgreSQL 保留清理测试",
                URL != null && !URL.isBlank());
        Assume.assumeTrue("未设置 COLLECTOR_TEST_PG_ALLOW_DESTRUCTIVE=true，跳过会重建表的测试",
                "true".equalsIgnoreCase(ALLOW_DESTRUCTIVE));
        Assume.assumeTrue("数据库名必须以 " + TEST_DATABASE_PREFIX + " 开头，拒绝在正式库执行破坏性测试",
                safeTestDatabase(URL));
        Class.forName("org.postgresql.Driver");
        rebuildSchema();
        dataSource = new UnpooledDataSource("org.postgresql.Driver", URL, USER, PASSWORD);
        jdbcTemplate = new JdbcTemplate(dataSource);
        cleanupService = new CollectorRetentionCleanupServiceImpl(jdbcTemplate,
                new DataSourceTransactionManager(dataSource));
        cleanupService.setClock(clockAtShanghai(BASE_NOON));
        policyService = new CollectorRetentionPolicyServiceImpl(jdbcTemplate, cleanupService);
    }

    private void rebuildSchema() throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            // 同名前缀的非分区对象无法被父表的 DROP CASCADE 清理，先按前缀兜底，保证用例可重复执行。
            statement.execute("""
                    DO $$
                    DECLARE r record;
                    BEGIN
                        FOR r IN
                            SELECT c.relname FROM pg_class c
                            JOIN pg_namespace n ON n.oid = c.relnamespace
                            WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
                              AND c.relname LIKE 'collector_sample_raw%'
                        LOOP
                            EXECUTE format('DROP TABLE IF EXISTS public.%I CASCADE', r.relname);
                        END LOOP;
                    END $$
                    """);
            statement.execute("""
                    DROP TABLE IF EXISTS public.collector_aggregate_second,
                        public.collector_aggregate_minute, public.collector_aggregate_hour,
                        public.collector_aggregate_day, public.collector_alarm,
                        public.collector_heartbeat, public.collector_command,
                        public.collector_retention_policy, public.collector_config_version,
                        public.collector_latest, public.collector_aggregate_watermark,
                        public.collector_alarm_state, public.collector_alarm_cursor CASCADE
                    """);
            statement.execute("""
                    CREATE TABLE public.collector_sample_raw (
                        sample_id bigserial NOT NULL,
                        channel_id bigint NOT NULL,
                        device_id bigint NOT NULL,
                        collect_time timestamp(3) without time zone NOT NULL,
                        value double precision,
                        raw_value bigint,
                        quality smallint DEFAULT 0 NOT NULL,
                        CONSTRAINT pk_collector_sample_raw PRIMARY KEY (channel_id, collect_time)
                    ) PARTITION BY RANGE (collect_time)
                    """);
            statement.execute("CREATE TABLE public." + DEFAULT_PARTITION
                    + " PARTITION OF public." + RAW + " DEFAULT");
            createAggregateTable(statement, "collector_aggregate_second", "bucket_time timestamp(3)", false);
            createAggregateTable(statement, "collector_aggregate_minute", "bucket_time timestamp(3)", false);
            createAggregateTable(statement, "collector_aggregate_hour", "bucket_time timestamp(3)", false);
            createAggregateTable(statement, "collector_aggregate_day", "bucket_date date", true);
            statement.execute("""
                    CREATE TABLE public.collector_alarm (
                        id bigint NOT NULL,
                        device_id bigint NOT NULL,
                        sensor_id bigint DEFAULT 0 NOT NULL,
                        channel_id bigint DEFAULT 0 NOT NULL,
                        alarm_type varchar(32) NOT NULL,
                        event_type varchar(16) DEFAULT 'raised' NOT NULL,
                        level smallint DEFAULT 2 NOT NULL,
                        dedupe_key varchar(128) NOT NULL,
                        status smallint DEFAULT 0 NOT NULL,
                        message varchar(500),
                        triggered_at timestamp(3) without time zone NOT NULL,
                        cleared_at timestamp(3) without time zone,
                        CONSTRAINT pk_collector_alarm PRIMARY KEY (id),
                        CONSTRAINT chk_collector_alarm_cleared CHECK ((status = 2) = (cleared_at IS NOT NULL))
                    )
                    """);
            statement.execute("""
                    CREATE TABLE public.collector_heartbeat (
                        id bigint NOT NULL,
                        instance_id varchar(64) NOT NULL,
                        heartbeat_time timestamp(3) without time zone NOT NULL,
                        status varchar(16) DEFAULT 'running' NOT NULL,
                        CONSTRAINT pk_collector_heartbeat PRIMARY KEY (id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE public.collector_command (
                        id bigint NOT NULL,
                        device_id bigint NOT NULL,
                        command_type varchar(32) NOT NULL,
                        status smallint DEFAULT 0 NOT NULL,
                        requested_at timestamp(3) without time zone NOT NULL,
                        finished_at timestamp(3) without time zone,
                        CONSTRAINT pk_collector_command PRIMARY KEY (id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE public.collector_retention_policy (
                        id bigserial PRIMARY KEY,
                        data_class varchar(32) NOT NULL,
                        retention_days integer NOT NULL,
                        partition_lead_days integer DEFAULT 3 NOT NULL,
                        action varchar(16) DEFAULT 'delete_rows' NOT NULL,
                        enabled smallint DEFAULT 1 NOT NULL,
                        description varchar(255),
                        last_run_at timestamp(3) without time zone,
                        last_run_result varchar(255),
                        creator varchar(64) DEFAULT '',
                        create_time timestamp(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        updater varchar(64) DEFAULT '',
                        update_time timestamp(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        deleted smallint DEFAULT 0 NOT NULL
                    )
                    """);
            statement.execute("CREATE UNIQUE INDEX uk_collector_retention_policy_class"
                    + " ON public.collector_retention_policy (data_class) WHERE deleted = 0");
            // 本任务不允许清理的配置类表：只放一行，用于证明清理动作完全没碰它们。
            statement.execute("CREATE TABLE public.collector_config_version (id bigint PRIMARY KEY, checksum varchar(64))");
            statement.execute("CREATE TABLE public.collector_latest (channel_id bigint PRIMARY KEY, value double precision)");
            statement.execute("CREATE TABLE public.collector_aggregate_watermark ("
                    + "agg_type varchar(8) NOT NULL, channel_id bigint NOT NULL, watermark_time timestamp(3),"
                    + " PRIMARY KEY (agg_type, channel_id))");
            statement.execute("CREATE TABLE public.collector_alarm_state (id bigint PRIMARY KEY, level smallint)");
            statement.execute("CREATE TABLE public.collector_alarm_cursor (id bigint PRIMARY KEY, cursor_time timestamp(3))");
        }
        seedPolicies();
        seedPartitions();
        seedUntouchedTables();
    }

    private void createAggregateTable(Statement statement, String table, String bucketColumn,
                                      boolean dateBucket) throws SQLException {
        String columnName = dateBucket ? "bucket_date" : "bucket_time";
        statement.execute("""
                CREATE TABLE public.%s (
                    channel_id bigint NOT NULL,
                    device_id bigint NOT NULL,
                    %s NOT NULL,
                    sample_count integer DEFAULT 0 NOT NULL,
                    value_avg double precision,
                    update_time timestamp(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
                    PRIMARY KEY (channel_id, %s)
                )
                """.formatted(table, bucketColumn, columnName));
    }

    private void seedPolicies() throws SQLException {
        for (String dataClass : List.of("sample_raw", "aggregate_second", "aggregate_minute",
                "aggregate_hour", "aggregate_day", "alarm", "heartbeat", "command")) {
            String action = "sample_raw".equals(dataClass) ? "drop_partition" : "delete_rows";
            int leadDays = "sample_raw".equals(dataClass) ? 3 : 0;
            insertPolicy(dataClass, DEFAULT_RETENTION_DAYS, action, 1, leadDays);
        }
    }

    /** 建立固定的分区目录：3 个已过期标准日分区、cutoff 当日、未来日，以及一个同名普通表。 */
    private void seedPartitions() throws SQLException {
        createDailyPartition(CUTOFF_DAY.minusDays(3));
        createDailyPartition(CUTOFF_DAY.minusDays(2));
        createDailyPartition(CUTOFF_DAY.minusDays(1));
        createDailyPartition(CUTOFF_DAY);
        createDailyPartition(CUTOFF_DAY.plusDays(1));
        // 同名但不是本父表的分区：证明清理不会 DROP TABLE IF EXISTS 误伤。
        executeSql("CREATE TABLE public.collector_sample_raw_p20261010 (id bigint)");
    }

    private void seedUntouchedTables() throws SQLException {
        executeSql("INSERT INTO public.collector_config_version (id, checksum) VALUES (1, 'abc123')");
        executeSql("INSERT INTO public.collector_latest (channel_id, value) VALUES (1, 3.14)");
        executeSql("INSERT INTO public.collector_aggregate_watermark (agg_type, channel_id, watermark_time)"
                + " VALUES ('hour', 1, '2026-11-20 00:00:00')");
        executeSql("INSERT INTO public.collector_alarm_state (id, level) VALUES (1, 2)");
        executeSql("INSERT INTO public.collector_alarm_cursor (id, cursor_time) VALUES (1, '2026-11-19 00:00:00')");
    }

    protected static Clock clockAtShanghai(LocalDateTime shanghaiWallClock) {
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        return Clock.fixed(shanghaiWallClock.atZone(shanghai).toInstant(), shanghai);
    }

    protected void createDailyPartition(LocalDate day) throws SQLException {
        createPartition(PARTITION_PREFIX + day.toString().replace("-", ""), day, day.plusDays(1));
    }

    protected void createPartition(String name, LocalDate from, LocalDate to) throws SQLException {
        executeSql("CREATE TABLE public." + name + " PARTITION OF public." + RAW
                + " FOR VALUES FROM ('" + from + " 00:00:00') TO ('" + to + " 00:00:00')");
    }

    protected void insertPolicy(String dataClass, int retentionDays, String action, int enabled)
            throws SQLException {
        insertPolicy(dataClass, retentionDays, action, enabled, 0);
    }

    protected void insertPolicy(String dataClass, int retentionDays, String action, int enabled, int leadDays)
            throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO public.collector_retention_policy
                         (data_class, retention_days, partition_lead_days, action, enabled, description)
                     VALUES (?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setString(1, dataClass);
            statement.setInt(2, retentionDays);
            statement.setInt(3, leadDays);
            statement.setString(4, action);
            statement.setInt(5, enabled);
            statement.setString(6, "测试策略 " + dataClass);
            statement.executeUpdate();
        }
    }

    protected void updatePolicyColumn(String dataClass, String column, Object value) throws SQLException {
        executeSql("UPDATE public.collector_retention_policy SET " + column + " = " + value
                + " WHERE deleted = 0 AND data_class = '" + dataClass + "'");
    }

    protected long policyId(String dataClass) throws SQLException {
        return scalarLong("SELECT id FROM public.collector_retention_policy WHERE deleted = 0"
                + " AND data_class = '" + dataClass + "'");
    }

    protected void insertAggregate(String table, long channelId, LocalDateTime bucketTime) throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO public." + table + " (channel_id, device_id, bucket_time) VALUES (?, 9, ?)")) {
            statement.setLong(1, channelId);
            statement.setTimestamp(2, Timestamp.valueOf(bucketTime));
            statement.executeUpdate();
        }
    }

    protected void insertAggregateDay(long channelId, LocalDate bucketDate) throws SQLException {
        executeSql("INSERT INTO public.collector_aggregate_day (channel_id, device_id, bucket_date)"
                + " VALUES (" + channelId + ", 9, '" + bucketDate + "')");
    }

    protected void insertAlarm(long id, long channelId, int status, LocalDateTime triggeredAt,
                               LocalDateTime clearedAt) throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO public.collector_alarm
                         (id, device_id, channel_id, alarm_type, dedupe_key, status, triggered_at, cleared_at)
                     VALUES (?, 9, ?, 'threshold_high_error', ?, ?, ?, ?)
                     """)) {
            statement.setLong(1, id);
            statement.setLong(2, channelId);
            statement.setString(3, "dev:9:ch:" + channelId);
            statement.setInt(4, status);
            statement.setTimestamp(5, Timestamp.valueOf(triggeredAt));
            statement.setTimestamp(6, clearedAt == null ? null : Timestamp.valueOf(clearedAt));
            statement.executeUpdate();
        }
    }

    protected void insertHeartbeat(long id, LocalDateTime heartbeatTime) throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO public.collector_heartbeat (id, instance_id, heartbeat_time) VALUES (?, 'c1', ?)")) {
            statement.setLong(1, id);
            statement.setTimestamp(2, Timestamp.valueOf(heartbeatTime));
            statement.executeUpdate();
        }
    }

    protected void insertCommand(long id, int status, LocalDateTime requestedAt, LocalDateTime finishedAt)
            throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO public.collector_command
                         (id, device_id, command_type, status, requested_at, finished_at)
                     VALUES (?, 9, 'test_read', ?, ?, ?)
                     """)) {
            statement.setLong(1, id);
            statement.setInt(2, status);
            statement.setTimestamp(3, Timestamp.valueOf(requestedAt));
            statement.setTimestamp(4, finishedAt == null ? null : Timestamp.valueOf(finishedAt));
            statement.executeUpdate();
        }
    }

    protected void insertRaw(LocalDateTime collectTime) throws SQLException {
        executeSql("INSERT INTO public." + RAW + " (channel_id, device_id, collect_time, value, quality)"
                + " VALUES (1, 7, '" + Timestamp.valueOf(collectTime) + "', 1.5, 0)");
    }

    protected void executeSql(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    protected long countRows(String table) throws SQLException {
        return scalarLong("SELECT COUNT(*) FROM public." + table);
    }

    protected long scalarLong(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    protected String scalarString(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    protected boolean relationExists(String relationName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement("SELECT to_regclass(?)::text")) {
            statement.setString(1, "public." + relationName);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getString(1) != null;
            }
        }
    }

    /** 子分区名（含 DEFAULT），按名字排序。 */
    protected List<String> childPartitionNames() throws SQLException {
        List<String> names = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT c.relname FROM pg_inherits i
                     JOIN pg_class c ON c.oid = i.inhrelid
                     WHERE i.inhparent = 'public.%s'::regclass
                     ORDER BY c.relname
                     """.formatted(RAW))) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }

    /** 指定 key 当前被持有的 advisory lock 数量（pg_advisory_lock(bigint) 拆为 classid/objid）。 */
    protected int advisoryLockCount(long key) throws SQLException {
        return (int) scalarLong("""
                SELECT COUNT(*) FROM pg_locks
                WHERE locktype = 'advisory'
                  AND classid::bigint = (%d::bigint >> 32)
                  AND objid::bigint = (%d::bigint & 4294967295)
                """.formatted(key, key));
    }

    protected String untouchedSnapshot() throws SQLException {
        return scalarString("SELECT string_agg(x, '|') FROM ("
                + "SELECT 'cfg:' || id || ':' || checksum AS x FROM public.collector_config_version "
                + "UNION ALL SELECT 'latest:' || channel_id || ':' || value FROM public.collector_latest "
                + "UNION ALL SELECT 'wm:' || agg_type || ':' || channel_id || ':' || watermark_time"
                + " FROM public.collector_aggregate_watermark "
                + "UNION ALL SELECT 'state:' || id || ':' || level FROM public.collector_alarm_state "
                + "UNION ALL SELECT 'cursor:' || id || ':' || cursor_time FROM public.collector_alarm_cursor"
                + ") t");
    }

    protected RetentionClassResult resultOf(RetentionCleanupReport report, String dataClass) {
        return report.results().stream()
                .filter(result -> dataClass.equals(result.dataClass()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("报告中缺少数据类：" + dataClass));
    }

    /** 打印报告，作为 surefire 报告里的可复核证据。 */
    protected static void echoReport(String label, RetentionCleanupReport report) {
        StringBuilder message = new StringBuilder();
        for (RetentionClassResult result : report.results()) {
            message.append("\n  ").append(result.dataClass()).append(" -> ").append(result.outcome())
                    .append(" deleted=").append(result.deletedRows())
                    .append(" dropped=").append(result.droppedPartitions().stream()
                            .map(partition -> partition.partitionName()).toList())
                    .append(" warnings=").append(result.warnings())
                    .append(" message=").append(result.message());
        }
        System.out.println("[证据] " + label + " lockNotAcquired=" + report.lockNotAcquired()
                + " hasFailure=" + report.hasFailure() + message);
    }

    private static boolean safeTestDatabase(String jdbcUrl) {
        String withoutParams = jdbcUrl.split("\\?", 2)[0];
        int slash = withoutParams.lastIndexOf('/');
        return slash >= 0 && withoutParams.substring(slash + 1).startsWith(TEST_DATABASE_PREFIX);
    }

}
