package cn.iocoder.yudao.module.collector.service.partition;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.Assume;
import org.junit.Before;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * raw 日分区维护的真实 PostgreSQL 测试基座。
 *
 * <p>未设置 {@code COLLECTOR_TEST_PG_URL} 时整体跳过，保证 CI 缺库不失败；建表属于破坏性操作，
 * 还要求 {@code COLLECTOR_TEST_PG_ALLOW_DESTRUCTIVE=true} 且库名以 {@code collector_partition_} 开头。</p>
 *
 * <p>测试自建与 {@code sql/postgresql/collector/01_schema.sql} 一致的父表结构（RANGE(collect_time)）
 * 与 DEFAULT 分区，日期固定为 {@link #BASE_DAY}，不依赖真实当天。</p>
 */
abstract class CollectorPartitionPgTestSupport {

    protected static final String URL = System.getenv("COLLECTOR_TEST_PG_URL");
    protected static final String USER = System.getenv("COLLECTOR_TEST_PG_USER");
    protected static final String PASSWORD = System.getenv("COLLECTOR_TEST_PG_PASSWORD");
    private static final String ALLOW_DESTRUCTIVE = System.getenv("COLLECTOR_TEST_PG_ALLOW_DESTRUCTIVE");

    /** 允许被破坏的库名前缀：只接受临时分区校验库。 */
    private static final String TEST_DATABASE_PREFIX = "collector_partition_";

    /** 用例固定「今天」，与真实当天解耦。 */
    protected static final LocalDate BASE_DAY = LocalDate.of(2026, 11, 20);
    protected static final LocalDate PREVIOUS_DAY = BASE_DAY.minusDays(1);
    protected static final LocalDateTime BASE_NOON = BASE_DAY.atTime(12, 0);

    protected static final String PARENT = "collector_sample_raw";
    protected static final String DEFAULT_PARTITION = PARENT + "_default";
    protected static final String PARTITION_PREFIX = PARENT + "_p";

    protected CollectorPartitionServiceImpl partitionService;
    protected UnpooledDataSource dataSource;

    @Before
    public void setUpPartitionPg() throws Exception {
        Assume.assumeTrue("未设置 COLLECTOR_TEST_PG_URL，跳过临时 PostgreSQL 分区测试",
                URL != null && !URL.isBlank());
        Assume.assumeTrue("未设置 COLLECTOR_TEST_PG_ALLOW_DESTRUCTIVE=true，跳过会重建表的测试",
                "true".equalsIgnoreCase(ALLOW_DESTRUCTIVE));
        Assume.assumeTrue("数据库名必须以 " + TEST_DATABASE_PREFIX + " 开头，拒绝在正式库执行破坏性测试",
                safeTestDatabase(URL));
        Class.forName("org.postgresql.Driver");
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + PARENT + " CASCADE");
            statement.execute("DROP TABLE IF EXISTS collector_retention_policy CASCADE");
            // 与 01_schema.sql 第 10 节一致：时间列 timestamp(3)，主键 (channel_id, collect_time)，
            // 数值有限性与 quality 取值约束一并保留，避免测试结构与生产漂移。
            statement.execute("""
                    CREATE TABLE public.collector_sample_raw (
                        sample_id bigserial NOT NULL,
                        channel_id bigint NOT NULL,
                        device_id bigint NOT NULL,
                        collect_time timestamp(3) without time zone NOT NULL,
                        value double precision,
                        raw_value bigint,
                        quality smallint DEFAULT 0 NOT NULL,
                        CONSTRAINT pk_collector_sample_raw PRIMARY KEY (channel_id, collect_time),
                        CONSTRAINT chk_collector_sample_raw_value CHECK (
                            value IS NULL OR (
                                value <> 'Infinity'::float8
                                AND value <> '-Infinity'::float8
                                AND value <> 'NaN'::float8
                            )
                        ),
                        CONSTRAINT chk_collector_sample_raw_quality CHECK (quality IN (0, 1, 2))
                    ) PARTITION BY RANGE (collect_time)
                    """);
            statement.execute("CREATE TABLE public." + DEFAULT_PARTITION
                    + " PARTITION OF public." + PARENT + " DEFAULT");
            statement.execute("""
                    CREATE TABLE public.collector_retention_policy (
                        id bigserial PRIMARY KEY,
                        data_class varchar(32) NOT NULL,
                        retention_days integer NOT NULL,
                        partition_lead_days integer DEFAULT 0 NOT NULL,
                        action varchar(32) DEFAULT 'drop_partition' NOT NULL,
                        enabled smallint DEFAULT 1 NOT NULL,
                        deleted smallint DEFAULT 0 NOT NULL
                    )
                    """);
        }
        dataSource = new UnpooledDataSource("org.postgresql.Driver", URL, USER, PASSWORD);
        partitionService = new CollectorPartitionServiceImpl(new JdbcTemplate(dataSource));
        partitionService.setClock(clockAtShanghai(BASE_NOON));
    }

    protected static Clock clockAtShanghai(LocalDateTime shanghaiWallClock) {
        return Clock.fixed(shanghaiWallClock.atZone(CollectorPartitionServiceImpl.SHANGHAI_ZONE).toInstant(),
                CollectorPartitionServiceImpl.SHANGHAI_ZONE);
    }

    /** 用固定 UTC 瞬间驱动时钟，用于验证「UTC 尚未跨天但上海已跨天」。 */
    protected void setClockAtInstant(Instant instant) {
        ZoneId shanghai = CollectorPartitionServiceImpl.SHANGHAI_ZONE;
        partitionService.setClock(Clock.fixed(instant, shanghai));
    }

    protected void insertPolicy(String dataClass, int leadDays, int enabled, int deleted) throws SQLException {
        executeSql("""
                INSERT INTO collector_retention_policy
                    (data_class, retention_days, partition_lead_days, enabled, deleted)
                VALUES ('%s', 30, %d, %d, %d)
                """.formatted(dataClass, leadDays, enabled, deleted));
    }

    protected void setLeadDays(int leadDays) throws SQLException {
        clearPolicy();
        insertPolicy("sample_raw", leadDays, 1, 0);
    }

    protected void setLeadDays(int leadDays, int enabled) throws SQLException {
        clearPolicy();
        insertPolicy("sample_raw", leadDays, enabled, 0);
    }

    protected void clearPolicy() throws SQLException {
        executeSql("DELETE FROM collector_retention_policy");
    }

    protected void executeSql(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /** 自己的子分区名（含 DEFAULT），按名字排序，便于稳定断言。 */
    protected List<String> childPartitionNames() throws SQLException {
        List<String> names = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT c.relname
                     FROM pg_inherits i
                     JOIN pg_class c ON c.oid = i.inhrelid
                     WHERE i.inhparent = 'public.%s'::regclass
                     ORDER BY c.relname
                     """.formatted(PARENT))) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }

    /** 不含 DEFAULT 的日分区名。 */
    protected List<String> dailyPartitionNames() throws SQLException {
        List<String> names = new ArrayList<>(childPartitionNames());
        names.remove(DEFAULT_PARTITION);
        return names;
    }

    protected String partitionBound(String partitionName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT pg_get_expr(c.relpartbound, c.oid)
                     FROM pg_class c
                     JOIN pg_namespace n ON n.oid = c.relnamespace
                     WHERE n.nspname = 'public' AND c.relname = ?
                     """)) {
            statement.setString(1, partitionName);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
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

    protected int countRows(String table) throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM public." + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    protected void insertRaw(LocalDateTime collectTime, double value) throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO public.%s (channel_id, device_id, collect_time, value, quality)
                     VALUES (1, 7, ?, ?, 0)
                     """.formatted(PARENT))) {
            statement.setTimestamp(1, Timestamp.valueOf(collectTime));
            statement.setDouble(2, value);
            statement.executeUpdate();
        }
    }

    /** DEFAULT 分区的行快照（含 sample_id），用于证明冲突行未被删除或移动。 */
    protected List<String> defaultPartitionRows() throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT sample_id, channel_id, device_id, collect_time, value, raw_value, quality
                     FROM public.%s ORDER BY sample_id
                     """.formatted(DEFAULT_PARTITION))) {
            while (rs.next()) {
                rows.add(rs.getLong(1) + "|" + rs.getLong(2) + "|" + rs.getLong(3) + "|"
                        + rs.getTimestamp(4) + "|" + rs.getObject(5) + "|" + rs.getObject(6)
                        + "|" + rs.getInt(7));
            }
        }
        return rows;
    }

    protected void createCustomPartition(String partitionName, LocalDate day) throws SQLException {
        executeSql("CREATE TABLE public." + partitionName + " PARTITION OF public." + PARENT
                + " FOR VALUES FROM ('" + day + " 00:00:00') TO ('" + day.plusDays(1) + " 00:00:00')");
    }

    protected void createDailyPartition(LocalDate day) throws SQLException {
        createCustomPartition(PARTITION_PREFIX + day.toString().replace("-", ""), day);
    }

    /** 打印当前分区目录与边界，输出会落在 surefire 报告里作为可复核证据。 */
    protected void echoCatalog(String label) throws SQLException {
        StringBuilder catalog = new StringBuilder();
        for (String name : childPartitionNames()) {
            catalog.append("\n  ").append(name).append(" -> ").append(partitionBound(name));
        }
        System.out.println("[证据] " + label + " 分区目录:" + catalog);
    }

    /** 打印一次维护结果，避免只依赖断言、无法复核具体汇总值。 */
    protected static void echoResult(String label,
                                     CollectorPartitionService.PartitionMaintainResult result) {
        System.out.println("[证据] " + label
                + " created=" + result.createdPartitions()
                + ", skippedExisting=" + result.skippedExistingCount()
                + ", defaultConflicts=" + result.defaultConflicts()
                + ", ddlFailures=" + result.ddlFailures()
                + ", lockNotAcquired=" + result.lockNotAcquired());
    }

    /**
     * 当前连接可见的 advisory lock 数量。
     *
     * <p>pg_advisory_lock(bigint) 在 pg_locks 中拆成高 32 位 classid 与低 32 位 objid，
     * 这里按同一拆法定位分区维护用的 key。</p>
     */
    protected int advisoryLockCount() throws SQLException {
        long key = CollectorPartitionServiceImpl.ADVISORY_LOCK_KEY;
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT COUNT(*) FROM pg_locks
                     WHERE locktype = 'advisory'
                       AND classid::bigint = (%d::bigint >> 32)
                       AND objid::bigint = (%d::bigint & 4294967295)
                     """.formatted(key, key))) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static boolean safeTestDatabase(String jdbcUrl) {
        String withoutParams = jdbcUrl.split("\\?", 2)[0];
        int slash = withoutParams.lastIndexOf('/');
        return slash >= 0 && withoutParams.substring(slash + 1).startsWith(TEST_DATABASE_PREFIX);
    }

}
