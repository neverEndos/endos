package cn.iocoder.yudao.module.collector.service.partition;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * collector raw 日分区的共享 DDL 支持：常量、catalog 查询、边界解析与 advisory lock。
 *
 * <p>分区预建（{@code CollectorPartitionServiceImpl}）与过期清理（retention 清理服务）必须共用
 * 同一份表名常量、同一把 advisory lock 与同一套边界解析规则，否则预建与 DROP 会在并发下
 * 互相踩踏。本类不持有任何可变状态，所有方法都要求调用方传入已打开的物理连接。</p>
 *
 * <p>约定：advisory lock 的获取、DDL 执行与释放必须复用同一个物理连接
 * （{@code JdbcTemplate.execute(ConnectionCallback)}），避免连接池换连接导致锁泄漏。</p>
 */
@Slf4j
public final class CollectorPartitionDdlSupport {

    /** 父表所在 schema，固定为 public。 */
    public static final String SCHEMA = "public";
    /** 父表名（不含 schema）。 */
    public static final String TABLE_NAME = "collector_sample_raw";
    /** 父表全名。 */
    public static final String TABLE = SCHEMA + "." + TABLE_NAME;
    /** 日分区名前缀；完整名称必须匹配 {@link #PARTITION_NAME_PATTERN}。 */
    public static final String PARTITION_PREFIX = "collector_sample_raw_p";
    /** DEFAULT 分区名（不含 schema）。 */
    public static final String DEFAULT_PARTITION_NAME = TABLE_NAME + "_default";
    /** DEFAULT 分区全名。 */
    public static final String DEFAULT_PARTITION = SCHEMA + "." + DEFAULT_PARTITION_NAME;

    /**
     * 分区维护专用 PostgreSQL advisory lock key（固定值）。
     *
     * <p>分区预建与 raw 过期 DROP 共用这把锁，保证「建分区」和「删分区」不并发执行。
     * 这是跨实例互斥的唯一凭据，修改后新旧实例将无法互斥，务必保持稳定。
     */
    public static final long ADVISORY_LOCK_KEY = 2026111901L;

    /** 只允许固定前缀 + 8 位日期，分区名不来自任何外部输入。 */
    public static final Pattern PARTITION_NAME_PATTERN = Pattern.compile("^collector_sample_raw_p\\d{8}$");

    public static final DateTimeFormatter PARTITION_SUFFIX_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMdd", Locale.ROOT)
            .withChronology(IsoChronology.INSTANCE);
    public static final DateTimeFormatter BOUND_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
            .withChronology(IsoChronology.INSTANCE);

    private static final String MINVALUE = "MINVALUE";
    private static final String MAXVALUE = "MAXVALUE";

    /**
     * PostgreSQL pg_get_expr 输出的 RANGE 边界。
     *
     * <p>两端既可能是 {@code ('2026-11-20 00:00:00')}，也可能是无引号的 {@code (MINVALUE)} /
     * {@code (MAXVALUE)}；DEFAULT 分区由 {@link #parseBound} 单独识别。</p>
     */
    private static final Pattern RANGE_BOUND_PATTERN =
            Pattern.compile("^FOR VALUES FROM \\((.+)\\) TO \\((.+)\\)$", Pattern.CASE_INSENSITIVE);

    /**
     * 单个边界值：{@code '...' } 后可能带受控的类型后缀，例如
     * {@code '2026-11-20 00:00:00'::timestamp without time zone}。
     *
     * <p>只接受「字符串常量 + 可选类型名（可含括号精度）」，不解析任意表达式。</p>
     */
    private static final Pattern BOUND_VALUE_PATTERN = Pattern.compile(
            "^'([^']*)'(?:::\\s*[A-Za-z_][A-Za-z0-9_ ]*(?:\\(\\s*[0-9, ]+\\s*\\))?)?$");

    private static final String CHILD_PARTITION_SQL = """
            SELECT c.relname, c.relispartition, pg_get_expr(c.relpartbound, c.oid) AS bound
            FROM pg_inherits i
            JOIN pg_class c ON c.oid = i.inhrelid
            JOIN pg_class p ON p.oid = i.inhparent
            WHERE p.oid = '%s'::regclass
            """.formatted(TABLE);

    private static final String RELATION_SQL = """
            SELECT c.relname, c.relkind, c.relispartition, pg_get_expr(c.relpartbound, c.oid) AS bound,
                   pn.nspname AS parent_schema, p.relname AS parent_name
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            LEFT JOIN pg_inherits i ON i.inhrelid = c.oid
            LEFT JOIN pg_class p ON p.oid = i.inhparent
            LEFT JOIN pg_namespace pn ON pn.oid = p.relnamespace
            WHERE n.nspname = '%s' AND c.relname = ?
            LIMIT 1
            """.formatted(SCHEMA);

    private CollectorPartitionDdlSupport() {
    }

    /** 按日期生成标准日分区名。 */
    public static String partitionName(LocalDate day) {
        return PARTITION_PREFIX + day.format(PARTITION_SUFFIX_FORMATTER);
    }

    /** 读取本父表的直接子分区（含 DEFAULT 分区），按 catalog 返回顺序。 */
    public static List<ChildPartition> loadChildPartitions(Connection connection) throws SQLException {
        List<ChildPartition> children = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(CHILD_PARTITION_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                children.add(new ChildPartition(resultSet.getString("relname"),
                        resultSet.getBoolean("relispartition"), resultSet.getString("bound")));
            }
        }
        return children;
    }

    /** 按名字读取 public schema 下的关系信息；不存在返回 null。 */
    public static RelationInfo loadRelation(Connection connection, String relationName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(RELATION_SQL)) {
            statement.setString(1, relationName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new RelationInfo(resultSet.getString("relname"), resultSet.getString("relkind"),
                        resultSet.getBoolean("relispartition"), resultSet.getString("parent_schema"),
                        resultSet.getString("parent_name"), resultSet.getString("bound"));
            }
        }
    }

    /**
     * 解析 pg_get_expr 输出的分区边界。
     *
     * <p>不可解析时返回 {@link BoundState#UNPARSEABLE} 并保留原文，绝不当作「已存在」或「已覆盖」。</p>
     */
    public static BoundParse parseBound(String expression) {
        String raw = describeBound(expression);
        if (expression == null) {
            return BoundParse.unparseable(raw);
        }
        String trimmed = expression.trim();
        if ("DEFAULT".equalsIgnoreCase(trimmed)) {
            // DEFAULT 分区不构成某个具体自然日的覆盖。
            return BoundParse.defaultPartition();
        }
        Matcher matcher = RANGE_BOUND_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return BoundParse.unparseable(raw);
        }
        BoundValue from = parseBoundValue(matcher.group(1));
        BoundValue to = parseBoundValue(matcher.group(2));
        if (!from.parsed() || !to.parsed()) {
            return BoundParse.unparseable(raw);
        }
        return BoundParse.range(new PartitionBound(from.time(), to.time()));
    }

    /** 在指定连接上非阻塞获取分区维护 advisory lock。 */
    public static boolean tryAcquireLock(Connection connection) throws SQLException {
        return tryAcquireLock(connection, ADVISORY_LOCK_KEY);
    }

    /** 在指定连接上非阻塞获取指定 key 的 advisory lock。 */
    public static boolean tryAcquireLock(Connection connection, long key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            statement.setLong(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    /** 释放本连接持有的分区维护 advisory lock。 */
    public static void releaseLock(Connection connection) {
        releaseLock(connection, ADVISORY_LOCK_KEY);
    }

    /** 释放本连接持有的指定 key 的 advisory lock；失败只记日志，不覆盖业务结果。 */
    public static void releaseLock(Connection connection, long key) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, key);
            statement.execute();
        } catch (SQLException ex) {
            log.error("[releaseLock][advisory lock({}) 解锁失败]", key, ex);
        }
    }

    public static String describeBound(String expression) {
        return expression == null ? "NULL" : expression;
    }

    /** 该边界表达式是否为 DEFAULT 分区。 */
    public static boolean isDefaultBound(String expression) {
        return expression != null && "DEFAULT".equalsIgnoreCase(expression.trim());
    }

    /**
     * 解析单个边界值；{@code MINVALUE}/{@code MAXVALUE} 解析为「无穷」，返回的 time 为 null。
     */
    private static BoundValue parseBoundValue(String value) {
        String trimmed = value.trim();
        if (MINVALUE.equalsIgnoreCase(trimmed) || MAXVALUE.equalsIgnoreCase(trimmed)) {
            return BoundValue.infinity();
        }
        Matcher matcher = BOUND_VALUE_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return BoundValue.invalid();
        }
        LocalDateTime time = parseBoundTime(matcher.group(1));
        return time == null ? BoundValue.invalid() : BoundValue.at(time);
    }

    private static LocalDateTime parseBoundTime(String value) {
        try {
            return Timestamp.valueOf(value).toLocalDateTime();
        } catch (IllegalArgumentException ex) {
            try {
                return LocalDate.parse(value).atStartOfDay();
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    /** 本表子分区目录行。 */
    public record ChildPartition(String name, boolean partition, String bound) {
    }

    /** pg_class 中按名字查到的关系信息。 */
    public record RelationInfo(String name, String relKind, boolean partition, String parentSchema,
                               String parentName, String bound) {
    }

    /** 分区边界解析状态。 */
    public enum BoundState {
        RANGE, DEFAULT, UNPARSEABLE
    }

    /** 单个边界值：parsed=false 表示无法解析；time=null 表示 MINVALUE/MAXVALUE 无穷。 */
    private record BoundValue(boolean parsed, LocalDateTime time) {

        static BoundValue infinity() {
            return new BoundValue(true, null);
        }

        static BoundValue invalid() {
            return new BoundValue(false, null);
        }

        static BoundValue at(LocalDateTime time) {
            return new BoundValue(true, time);
        }
    }

    /** 分区边界解析结果；UNPARSEABLE 时保留 catalog 原文便于人工确认。 */
    public record BoundParse(BoundState state, PartitionBound bound, String raw) {

        public static BoundParse range(PartitionBound bound) {
            return new BoundParse(BoundState.RANGE, bound, null);
        }

        public static BoundParse defaultPartition() {
            return new BoundParse(BoundState.DEFAULT, null, "DEFAULT");
        }

        public static BoundParse unparseable(String raw) {
            return new BoundParse(BoundState.UNPARSEABLE, null, raw);
        }
    }

    /**
     * 解析后的 RANGE 边界；{@code from == null} 表示 MINVALUE（−∞），
     * {@code to == null} 表示 MAXVALUE（+∞）。
     */
    public record PartitionBound(LocalDateTime from, LocalDateTime to) {

        public boolean exactly(LocalDateTime expectedFrom, LocalDateTime expectedTo) {
            return Objects.equals(from, expectedFrom) && Objects.equals(to, expectedTo);
        }

        public boolean covers(LocalDateTime dayStart, LocalDateTime dayEnd) {
            boolean coversFrom = from == null || !from.isAfter(dayStart);
            boolean coversTo = to == null || !to.isBefore(dayEnd);
            return coversFrom && coversTo;
        }
    }

}
