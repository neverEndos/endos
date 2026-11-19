package cn.iocoder.yudao.module.collector.service.partition;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.PARTITION_DEFAULT_CONFLICT;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.PARTITION_POLICY_DUPLICATE;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.PARTITION_POLICY_INVALID;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.PARTITION_POLICY_NOT_EXISTS;

/**
 * collector raw 日分区维护服务实现。
 *
 * <p>分区目录、策略与 DDL 全部通过 {@link JdbcTemplate} 执行；advisory lock、DDL、解锁
 * 使用同一个物理连接，避免连接池换连接导致锁泄漏。</p>
 */
@Slf4j
@Service
public class CollectorPartitionServiceImpl implements CollectorPartitionService {

    /** 业务时区固定为 Asia/Shanghai，不依赖服务器默认时区。 */
    public static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    private static final String PARENT_SCHEMA = "public";
    private static final String PARENT_TABLE_NAME = "collector_sample_raw";
    private static final String PARENT_TABLE = PARENT_SCHEMA + "." + PARENT_TABLE_NAME;
    private static final String PARTITION_PREFIX = "collector_sample_raw_p";
    private static final String DEFAULT_PARTITION = PARENT_TABLE + "_default";
    private static final String RAW_DATA_CLASS = "sample_raw";

    private static final int MIN_LEAD_DAYS = 0;
    private static final int MAX_LEAD_DAYS = 30;

    /**
     * 分区维护专用 PostgreSQL advisory lock key（固定值）。
     *
     * <p>这是跨实例互斥的唯一凭据，修改后新旧实例将无法互斥，务必保持稳定。</p>
     */
    private static final long ADVISORY_LOCK_KEY = 2026111901L;

    /** 限制 DDL 等待父表 ACCESS EXCLUSIVE 锁的时间，避免阻塞采集写入。 */
    private static final String DDL_LOCK_TIMEOUT = "5s";

    /** 只允许固定前缀 + 8 位日期，分区名不来自任何外部输入。 */
    private static final Pattern PARTITION_NAME_PATTERN = Pattern.compile("^collector_sample_raw_p\\d{8}$");

    /** PostgreSQL pg_get_expr 输出的 RANGE 边界；DEFAULT 分区由 {@link #parseBound} 单独识别。 */
    private static final Pattern RANGE_BOUND_PATTERN =
            Pattern.compile("^FOR VALUES FROM \\('([^']+)'\\) TO \\('([^']+)'\\)$");

    private static final DateTimeFormatter PARTITION_SUFFIX_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter BOUND_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String CHILD_PARTITION_SQL = """
            SELECT c.relname, c.relispartition, pg_get_expr(c.relpartbound, c.oid) AS bound
            FROM pg_inherits i
            JOIN pg_class c ON c.oid = i.inhrelid
            JOIN pg_class p ON p.oid = i.inhparent
            WHERE p.oid = 'public.collector_sample_raw'::regclass
            """;

    private static final String RELATION_SQL = """
            SELECT c.relname, c.relkind, c.relispartition, pg_get_expr(c.relpartbound, c.oid) AS bound,
                   pn.nspname AS parent_schema, p.relname AS parent_name
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            LEFT JOIN pg_inherits i ON i.inhrelid = c.oid
            LEFT JOIN pg_class p ON p.oid = i.inhparent
            LEFT JOIN pg_namespace pn ON pn.oid = p.relnamespace
            WHERE n.nspname = 'public' AND c.relname = ?
            LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;

    /** 分区维护时钟；生产使用 Asia/Shanghai 系统时钟，测试可替换以校验日期范围。 */
    private Clock clock = Clock.system(SHANGHAI_ZONE);

    public CollectorPartitionServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 仅供测试替换时钟，避免依赖真实当前时间。 */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public PartitionMaintainResult maintainDailyPartitions() {
        LocalDate today = LocalDate.now(clock.withZone(SHANGHAI_ZONE));
        return jdbcTemplate.execute((ConnectionCallback<PartitionMaintainResult>) connection -> {
            // 锁、DDL、解锁必须复用同一个物理连接；try 取不到锁时不等待。
            if (!tryAcquireAdvisoryLock(connection)) {
                log.info("[maintainDailyPartitions][advisory lock({}) 已被占用，本次跳过]", ADVISORY_LOCK_KEY);
                return new PartitionMaintainResult(today, today, 0, List.of(), 0,
                        List.of(), List.of(), true);
            }
            String previousLockTimeout = null;
            boolean lockTimeoutChanged = false;
            try {
                previousLockTimeout = currentLockTimeout(connection);
                setLockTimeout(connection, DDL_LOCK_TIMEOUT);
                lockTimeoutChanged = true;
                return doMaintain(connection, today);
            } finally {
                if (lockTimeoutChanged) {
                    restoreLockTimeout(connection, previousLockTimeout);
                }
                releaseAdvisoryLock(connection);
            }
        });
    }

    private PartitionMaintainResult doMaintain(Connection connection, LocalDate today) throws SQLException {
        int leadDays = loadPartitionLeadDays(connection);
        LocalDate to = today.plusDays(leadDays);
        Map<String, ChildPartition> children = loadChildPartitions(connection);
        boolean defaultPartitionExists = defaultPartitionExists(connection);

        List<String> createdPartitions = new ArrayList<>();
        int skippedExistingCount = 0;
        List<DefaultPartitionConflict> defaultConflicts = new ArrayList<>();
        List<PartitionFailure> ddlFailures = new ArrayList<>();

        for (LocalDate day = today; !day.isAfter(to); day = day.plusDays(1)) {
            LocalDateTime dayStart = day.atStartOfDay();
            LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
            String range = formatRange(dayStart, dayEnd);
            String partitionName = PARTITION_PREFIX + day.format(PARTITION_SUFFIX_FORMATTER);
            if (!PARTITION_NAME_PATTERN.matcher(partitionName).matches()) {
                // 日期只来自 LocalDate，正常不可能触发；保留防御，避免任何非预期表名进入 DDL。
                throw new IllegalStateException("分区名不合法：" + partitionName);
            }

            // 1. 目标名已是本父表的分区：边界完全一致才算已存在，否则必须显式失败（不得假成功）。
            ChildPartition sameNamePartition = children.get(partitionName);
            if (sameNamePartition != null) {
                if (!sameNamePartition.partition()) {
                    ddlFailures.add(new PartitionFailure(partitionName, day, range,
                            "同名对象已存在但不是分区（relispartition=false）"));
                    continue;
                }
                PartitionBound bound = parseBound(sameNamePartition.bound());
                if (bound == null || !bound.exactly(dayStart, dayEnd)) {
                    ddlFailures.add(new PartitionFailure(partitionName, day, range,
                            "同名分区已存在但边界(" + describeBound(sameNamePartition.bound()) + ")与期望不一致"));
                    continue;
                }
                skippedExistingCount++;
                continue;
            }

            // 2. 目标名被普通表、其他父表的分区或索引占用：CREATE 必然失败，提前记录中文原因。
            RelationInfo existingRelation = loadRelation(connection, partitionName);
            if (existingRelation != null) {
                ddlFailures.add(new PartitionFailure(partitionName, day, range,
                        describeNameConflict(existingRelation)));
                continue;
            }

            // 3. 该日已被其他命名的子分区完整覆盖：跳过，不因名字不同重复创建。
            if (isCoveredByExistingPartition(children.values(), dayStart, dayEnd)) {
                skippedExistingCount++;
                continue;
            }

            // 4. 默认分区保护：存在落入本日范围的数据时绝不创建，也不迁移/删除数据。
            if (defaultPartitionExists) {
                try {
                    if (defaultPartitionHasData(connection, dayStart, dayEnd)) {
                        defaultConflicts.add(new DefaultPartitionConflict(day, range,
                                PARTITION_DEFAULT_CONFLICT.getMsg()));
                        continue;
                    }
                } catch (SQLException ex) {
                    ddlFailures.add(new PartitionFailure(partitionName, day, range,
                            "检查默认分区数据失败：" + sqlMessage(ex)));
                    continue;
                }
            }

            // 5. 创建并立刻回查 catalog 核验。
            try {
                createPartition(connection, partitionName, dayStart, dayEnd);
            } catch (SQLException ex) {
                if (isDefaultPartitionConflict(ex)) {
                    // 并发写入可能在检查后落入默认分区，CREATE 的约束报错映射为同一条中文冲突信息。
                    defaultConflicts.add(new DefaultPartitionConflict(day, range,
                            PARTITION_DEFAULT_CONFLICT.getMsg()));
                } else {
                    ddlFailures.add(new PartitionFailure(partitionName, day, range,
                            "执行 DDL 失败：" + sqlMessage(ex)));
                }
                continue;
            }
            String verifyError;
            try {
                verifyError = verifyCreatedPartition(connection, partitionName, dayStart, dayEnd, range);
            } catch (SQLException ex) {
                verifyError = "回查分区目录失败：" + sqlMessage(ex);
            }
            if (verifyError == null) {
                createdPartitions.add(partitionName);
            } else {
                ddlFailures.add(new PartitionFailure(partitionName, day, range,
                        "创建后核验失败：" + verifyError));
            }
        }
        return new PartitionMaintainResult(today, to, leadDays, createdPartitions, skippedExistingCount,
                defaultConflicts, ddlFailures, false);
    }

    /**
     * 读取 raw 保留策略中的预建天数；0 行、多行与越界值都直接失败，不使用默认值兜底。
     */
    private int loadPartitionLeadDays(Connection connection) throws SQLException {
        List<Integer> leadDaysList = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT partition_lead_days FROM public.collector_retention_policy"
                        + " WHERE deleted = 0 AND data_class = '" + RAW_DATA_CLASS + "'");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                leadDaysList.add(resultSet.getInt(1));
            }
        }
        if (leadDaysList.isEmpty()) {
            throw exception(PARTITION_POLICY_NOT_EXISTS);
        }
        if (leadDaysList.size() > 1) {
            throw exception(PARTITION_POLICY_DUPLICATE, leadDaysList.size());
        }
        int leadDays = leadDaysList.get(0);
        if (leadDays < MIN_LEAD_DAYS || leadDays > MAX_LEAD_DAYS) {
            throw exception(PARTITION_POLICY_INVALID, leadDays);
        }
        return leadDays;
    }

    private Map<String, ChildPartition> loadChildPartitions(Connection connection) throws SQLException {
        Map<String, ChildPartition> children = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(CHILD_PARTITION_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String name = resultSet.getString("relname");
                children.put(name, new ChildPartition(name, resultSet.getBoolean("relispartition"),
                        resultSet.getString("bound")));
            }
        }
        return children;
    }

    private RelationInfo loadRelation(Connection connection, String partitionName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(RELATION_SQL)) {
            statement.setString(1, partitionName);
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

    private boolean defaultPartitionExists(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT to_regclass(?)::text")) {
            statement.setString(1, DEFAULT_PARTITION);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getString(1) != null;
            }
        }
    }

    private boolean defaultPartitionHasData(Connection connection, LocalDateTime dayStart,
                                            LocalDateTime dayEnd) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM public.collector_sample_raw_default"
                        + " WHERE collect_time >= ? AND collect_time < ?)")) {
            statement.setTimestamp(1, Timestamp.valueOf(dayStart));
            statement.setTimestamp(2, Timestamp.valueOf(dayEnd));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    private boolean isCoveredByExistingPartition(Collection<ChildPartition> children,
                                                 LocalDateTime dayStart, LocalDateTime dayEnd) {
        for (ChildPartition child : children) {
            if (!child.partition()) {
                continue;
            }
            PartitionBound bound = parseBound(child.bound());
            if (bound != null && bound.covers(dayStart, dayEnd)) {
                return true;
            }
        }
        return false;
    }

    private void createPartition(Connection connection, String partitionName,
                                 LocalDateTime dayStart, LocalDateTime dayEnd) throws SQLException {
        // 表名与日期均为代码常量 / LocalDate 生成，分区名已通过正则校验。
        String sql = "CREATE TABLE " + PARENT_SCHEMA + "." + partitionName
                + " PARTITION OF " + PARENT_TABLE
                + " FOR VALUES FROM ('" + dayStart.format(BOUND_TIME_FORMATTER) + "')"
                + " TO ('" + dayEnd.format(BOUND_TIME_FORMATTER) + "')";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String verifyCreatedPartition(Connection connection, String partitionName,
                                          LocalDateTime dayStart, LocalDateTime dayEnd,
                                          String range) throws SQLException {
        RelationInfo relation = loadRelation(connection, partitionName);
        if (relation == null) {
            return "catalog 中未找到新建分区";
        }
        if (!relation.partition()) {
            return "relispartition=false";
        }
        if (!PARENT_SCHEMA.equals(relation.parentSchema())
                || !PARENT_TABLE_NAME.equals(relation.parentName())) {
            return "父表为 " + relation.parentSchema() + "." + relation.parentName()
                    + "，不是 " + PARENT_TABLE;
        }
        PartitionBound bound = parseBound(relation.bound());
        if (bound == null || !bound.exactly(dayStart, dayEnd)) {
            return "边界为 " + describeBound(relation.bound()) + "，期望为 " + range;
        }
        return null;
    }

    private boolean tryAcquireAdvisoryLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            statement.setLong(1, ADVISORY_LOCK_KEY);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    private void releaseAdvisoryLock(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, ADVISORY_LOCK_KEY);
            statement.execute();
        } catch (SQLException ex) {
            log.error("[releaseAdvisoryLock][advisory lock({}) 解锁失败]", ADVISORY_LOCK_KEY, ex);
        }
    }

    private String currentLockTimeout(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT current_setting('lock_timeout')");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getString(1) : "0";
        }
    }

    private void setLockTimeout(Connection connection, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT set_config('lock_timeout', ?, false)")) {
            statement.setString(1, value);
            statement.execute();
        }
    }

    private void restoreLockTimeout(Connection connection, String value) {
        try {
            setLockTimeout(connection, value == null ? "0" : value);
        } catch (SQLException ex) {
            log.error("[restoreLockTimeout][恢复 lock_timeout({}) 失败]", value, ex);
        }
    }

    /**
     * PostgreSQL 默认分区约束冲突：SQLState 23514，且错误信息包含 default partition / partition constraint。
     * 并发插入可能在默认分区检查后落入数据，此时需要把 SQL 异常映射成与预检查一致的中文冲突信息。
     */
    private boolean isDefaultPartitionConflict(SQLException exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (!(current instanceof SQLException sqlException)) {
                continue;
            }
            String message = sqlException.getMessage();
            String lowerMessage = message == null ? "" : message.toLowerCase(Locale.ROOT);
            if ("23514".equals(sqlException.getSQLState())
                    && (lowerMessage.contains("default partition") || lowerMessage.contains("partition constraint"))) {
                return true;
            }
        }
        return false;
    }

    private PartitionBound parseBound(String expression) {
        if (expression == null) {
            return null;
        }
        String trimmed = expression.trim();
        if ("DEFAULT".equalsIgnoreCase(trimmed)) {
            // DEFAULT 分区不构成某个具体自然日的覆盖。
            return null;
        }
        Matcher matcher = RANGE_BOUND_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return null;
        }
        LocalDateTime from = parseBoundTime(matcher.group(1));
        LocalDateTime to = parseBoundTime(matcher.group(2));
        return from == null || to == null ? null : new PartitionBound(from, to);
    }

    private LocalDateTime parseBoundTime(String value) {
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

    private String describeNameConflict(RelationInfo relation) {
        if (!relation.partition()) {
            return "同名对象已存在但不是分区（relkind=" + relation.relKind() + "，relispartition=false）";
        }
        return "同名对象已存在但是其他父表(" + relation.parentSchema() + "." + relation.parentName() + ")的分区";
    }

    private String describeBound(String expression) {
        return expression == null ? "NULL" : expression;
    }

    private String formatRange(LocalDateTime dayStart, LocalDateTime dayEnd) {
        return dayStart.format(BOUND_TIME_FORMATTER) + "~" + dayEnd.format(BOUND_TIME_FORMATTER);
    }

    private String sqlMessage(Throwable exception) {
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    /** 本表子分区目录行。 */
    private record ChildPartition(String name, boolean partition, String bound) {
    }

    /** pg_class 中按名字查到的关系信息。 */
    private record RelationInfo(String name, String relKind, boolean partition, String parentSchema,
                                String parentName, String bound) {
    }

    /** 解析后的 RANGE 边界。 */
    private record PartitionBound(LocalDateTime from, LocalDateTime to) {

        boolean exactly(LocalDateTime expectedFrom, LocalDateTime expectedTo) {
            return from.equals(expectedFrom) && to.equals(expectedTo);
        }

        boolean covers(LocalDateTime dayStart, LocalDateTime dayEnd) {
            return !from.isAfter(dayStart) && !to.isBefore(dayEnd);
        }
    }

}
