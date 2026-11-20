package cn.iocoder.yudao.module.collector.service.partition;

import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionDdlSupport.BoundParse;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionDdlSupport.BoundState;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionDdlSupport.ChildPartition;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionDdlSupport.PartitionBound;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionDdlSupport.RelationInfo;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.PARTITION_DEFAULT_CONFLICT;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.PARTITION_DEFAULT_PARTITION_INVALID;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.PARTITION_POLICY_DUPLICATE;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.PARTITION_POLICY_INVALID;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.PARTITION_POLICY_NOT_EXISTS;

/**
 * collector raw 日分区维护服务实现。
 *
 * <p>分区目录、边界解析与 advisory lock 统一由 {@link CollectorPartitionDdlSupport} 提供，
 * 与 retention 清理的 DROP 分区共享同一份规则与同一把锁；DDL 全部通过 {@link JdbcTemplate} 执行，
 * 锁、DDL、解锁使用同一个物理连接，避免连接池换连接导致锁泄漏。</p>
 */
@Slf4j
@Service
public class CollectorPartitionServiceImpl implements CollectorPartitionService {

    /** 业务时区固定为 Asia/Shanghai，不依赖服务器默认时区。 */
    public static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    private static final String PARENT_TABLE = CollectorPartitionDdlSupport.TABLE;
    private static final String PARENT_SCHEMA = CollectorPartitionDdlSupport.SCHEMA;
    private static final String PARENT_TABLE_NAME = CollectorPartitionDdlSupport.TABLE_NAME;
    private static final String DEFAULT_PARTITION_NAME = CollectorPartitionDdlSupport.DEFAULT_PARTITION_NAME;
    private static final String DEFAULT_PARTITION = CollectorPartitionDdlSupport.DEFAULT_PARTITION;
    private static final String RETENTION_POLICY_TABLE = PARENT_SCHEMA + ".collector_retention_policy";
    private static final String RAW_DATA_CLASS = "sample_raw";

    private static final int MIN_LEAD_DAYS = 0;
    private static final int MAX_LEAD_DAYS = 30;

    /** 限制 DDL 等待父表 ACCESS EXCLUSIVE 锁的时间，避免阻塞采集写入。 */
    private static final String DDL_LOCK_TIMEOUT = "5s";

    private static final String DEFAULT_PARTITION_HAS_DATA_SQL =
            "SELECT EXISTS (SELECT 1 FROM " + DEFAULT_PARTITION
                    + " WHERE collect_time >= ? AND collect_time < ?)";

    private static final String RETENTION_POLICY_SQL =
            "SELECT partition_lead_days FROM " + RETENTION_POLICY_TABLE
                    + " WHERE deleted = 0 AND data_class = ?";

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
            if (!CollectorPartitionDdlSupport.tryAcquireLock(connection)) {
                log.info("[maintainDailyPartitions][advisory lock({}) 已被占用，本次跳过]",
                        CollectorPartitionDdlSupport.ADVISORY_LOCK_KEY);
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
                CollectorPartitionDdlSupport.releaseLock(connection);
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
            String partitionName = CollectorPartitionDdlSupport.partitionName(day);
            if (!CollectorPartitionDdlSupport.PARTITION_NAME_PATTERN.matcher(partitionName).matches()) {
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
                BoundParse parsed = CollectorPartitionDdlSupport.parseBound(sameNamePartition.bound());
                if (parsed.state() == BoundState.UNPARSEABLE) {
                    // 不可解析与「边界不符」必须区分，便于人工介入时快速定位 catalog 异常。
                    ddlFailures.add(new PartitionFailure(partitionName, day, range,
                            "无法识别的分区边界表达式：" + parsed.raw() + "，需人工确认"));
                    continue;
                }
                if (parsed.state() != BoundState.RANGE
                        || !parsed.bound().exactly(dayStart, dayEnd)) {
                    ddlFailures.add(new PartitionFailure(partitionName, day, range,
                            "同名分区已存在但边界(" + CollectorPartitionDdlSupport.describeBound(
                                    sameNamePartition.bound())
                                    + ")与期望(" + range + ")不一致"));
                    continue;
                }
                skippedExistingCount++;
                continue;
            }

            // 2. 目标名被普通表、其他父表的分区或索引占用：CREATE 必然失败，提前记录中文原因。
            RelationInfo existingRelation = CollectorPartitionDdlSupport.loadRelation(connection, partitionName);
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
     * 读取 raw 保留策略中的预建天数；0 行、多行、NULL 与越界值都直接失败，不使用默认值兜底。
     */
    private int loadPartitionLeadDays(Connection connection) throws SQLException {
        List<Integer> leadDaysList = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(RETENTION_POLICY_SQL)) {
            statement.setString(1, RAW_DATA_CLASS);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    // getInt 会把 NULL 静默读成 0，必须显式取对象再判空。
                    Integer leadDays = resultSet.getObject(1, Integer.class);
                    if (leadDays == null) {
                        throw exception(PARTITION_POLICY_INVALID, "NULL");
                    }
                    leadDaysList.add(leadDays);
                }
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
        for (ChildPartition child : CollectorPartitionDdlSupport.loadChildPartitions(connection)) {
            children.put(child.name(), child);
        }
        return children;
    }

    /**
     * 确认 DEFAULT 分区存在，且确实是本父表的 DEFAULT 分区。
     *
     * <p>只看名字会把「同名普通表」或「其他父表的同名分区」误判成默认分区仍存在，
     * 从而静默跳过冲突保护；这里改为 catalog 校验并显式失败。</p>
     */
    private boolean defaultPartitionExists(Connection connection) throws SQLException {
        RelationInfo relation = CollectorPartitionDdlSupport.loadRelation(connection, DEFAULT_PARTITION_NAME);
        if (relation == null) {
            return false;
        }
        if (!relation.partition()) {
            throw exception(PARTITION_DEFAULT_PARTITION_INVALID, DEFAULT_PARTITION,
                    "同名对象不是分区表（relkind=" + relation.relKind() + "）");
        }
        if (!PARENT_SCHEMA.equals(relation.parentSchema())
                || !PARENT_TABLE_NAME.equals(relation.parentName())) {
            throw exception(PARTITION_DEFAULT_PARTITION_INVALID, DEFAULT_PARTITION,
                    "实际为 " + relation.parentSchema() + "." + relation.parentName()
                            + " 的分区，边界=" + CollectorPartitionDdlSupport.describeBound(relation.bound()));
        }
        if (!CollectorPartitionDdlSupport.isDefaultBound(relation.bound())) {
            throw exception(PARTITION_DEFAULT_PARTITION_INVALID, DEFAULT_PARTITION,
                    "边界为 " + CollectorPartitionDdlSupport.describeBound(relation.bound()) + "，不是 DEFAULT");
        }
        return true;
    }

    private boolean defaultPartitionHasData(Connection connection, LocalDateTime dayStart,
                                            LocalDateTime dayEnd) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DEFAULT_PARTITION_HAS_DATA_SQL)) {
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
            BoundParse parsed = CollectorPartitionDdlSupport.parseBound(child.bound());
            // MINVALUE/MAXVALUE 已解析为无穷，可正确判断覆盖；DEFAULT 与不可解析均不代表具体自然日覆盖。
            if (parsed.state() == BoundState.RANGE && parsed.bound().covers(dayStart, dayEnd)) {
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
                + " FOR VALUES FROM ('" + dayStart.format(CollectorPartitionDdlSupport.BOUND_TIME_FORMATTER) + "')"
                + " TO ('" + dayEnd.format(CollectorPartitionDdlSupport.BOUND_TIME_FORMATTER) + "')";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String verifyCreatedPartition(Connection connection, String partitionName,
                                          LocalDateTime dayStart, LocalDateTime dayEnd,
                                          String range) throws SQLException {
        RelationInfo relation = CollectorPartitionDdlSupport.loadRelation(connection, partitionName);
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
        BoundParse parsed = CollectorPartitionDdlSupport.parseBound(relation.bound());
        if (parsed.state() == BoundState.UNPARSEABLE) {
            return "无法识别的分区边界表达式：" + parsed.raw();
        }
        if (parsed.state() != BoundState.RANGE || !parsed.bound().exactly(dayStart, dayEnd)) {
            return "边界为 " + CollectorPartitionDdlSupport.describeBound(relation.bound())
                    + "，期望为 " + range;
        }
        return null;
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
     * PostgreSQL 默认分区约束冲突。
     *
     * <p>{@code CREATE ... PARTITION OF} 期间的 check_violation(SQLState 23514) 只可能来自默认分区校验，
     * 因此不再依赖英文错误文本，避免中文 {@code lc_messages} 环境下漏判。</p>
     */
    private boolean isDefaultPartitionConflict(SQLException exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException
                    && "23514".equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private String describeNameConflict(RelationInfo relation) {
        if (!relation.partition()) {
            return "同名对象已存在但不是分区（relkind=" + relation.relKind() + "，relispartition=false）";
        }
        return "同名对象已存在但是其他父表(" + relation.parentSchema() + "." + relation.parentName() + ")的分区";
    }

    private String formatRange(LocalDateTime dayStart, LocalDateTime dayEnd) {
        return dayStart.format(CollectorPartitionDdlSupport.BOUND_TIME_FORMATTER)
                + "~" + dayEnd.format(CollectorPartitionDdlSupport.BOUND_TIME_FORMATTER);
    }

    private String sqlMessage(Throwable exception) {
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

}
