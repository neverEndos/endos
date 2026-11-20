package cn.iocoder.yudao.module.collector.service.retention;

import cn.iocoder.yudao.module.collector.enums.CollectorRetentionDataClass;
import cn.iocoder.yudao.module.collector.enums.CollectorRetentionDataClass.RetentionAction;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionDdlSupport;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionDdlSupport.BoundParse;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionDdlSupport.BoundState;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionDdlSupport.ChildPartition;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionDdlSupport.PartitionBound;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionDdlSupport.RelationInfo;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionPolicyRows.PolicyRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.RETENTION_CLEANUP_FAILED;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.RETENTION_POLICY_UNSUPPORTED;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.RETENTION_PREVIEW_FAILED;

/**
 * 采集数据保留清理服务实现。
 *
 * <p>关键约束：</p>
 * <ul>
 *   <li>整体 cleanup advisory lock 覆盖整次 {@link #cleanupAll()}，锁、raw 分区 DROP、解锁复用同一物理连接
 *       （{@link JdbcTemplate#execute(ConnectionCallback)}），finally 释放；</li>
 *   <li>raw 的 DROP 还要求 PR #29 的分区维护锁 {@link CollectorPartitionDdlSupport#ADVISORY_LOCK_KEY}，
 *       与分区预建互斥；取不到锁时 raw 类记为「跳过（分区锁被占用，待下次调度）」；</li>
 *   <li>批量 DELETE 每批独立事务，单批失败即停止该类并记录中文原因，不会死循环；</li>
 *   <li>只修改 {@code last_run_at / last_run_result}，不改策略本身。</li>
 * </ul>
 */
@Slf4j
@Service
public class CollectorRetentionCleanupServiceImpl implements CollectorRetentionCleanupService {

    /** 业务时区固定 Asia/Shanghai，与分区预建、聚合口径一致。 */
    public static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 清理任务专用的 advisory lock key（固定值）。
     *
     * <p>与分区维护锁 {@link CollectorPartitionDdlSupport#ADVISORY_LOCK_KEY} 分开：清理与预建可以并行进行
     * （预建建未来分区、清理删过期分区），真正互斥的是 raw 分区 DDL，由分区维护锁保证。
     * 该 key 只用于防止多个实例同时跑清理，导致重复的批量 DELETE 与重复的 last_run 回写。</p>
     */
    public static final long CLEANUP_ADVISORY_LOCK_KEY = 2026112001L;

    /** 预检的只读事务超时，防止统计扫表拖垮数据库。 */
    private static final String PREVIEW_STATEMENT_TIMEOUT = "10s";

    /** {@code last_run_result} 列宽 255，摘要控制在 200 以内并保留开头结论。 */
    private static final int MAX_RESULT_LENGTH = 200;

    /** 单条告警/失败原因在报告中的最大长度，避免原始 SQL 消息占满摘要。 */
    private static final int MAX_REASON_LENGTH = 120;

    private static final String AGGREGATE_DELETE_SQL = """
            DELETE FROM %s target
            WHERE (target.channel_id, target.%s) IN (
                SELECT source.channel_id, source.%s FROM %s source
                WHERE source.%s < ?
                ORDER BY source.%s
                LIMIT ?
            )
            """;

    private static final String ALARM_DELETE_SQL = """
            DELETE FROM public.collector_alarm
            WHERE id IN (
                SELECT id FROM public.collector_alarm
                WHERE cleared_at IS NOT NULL AND cleared_at < ?
                ORDER BY id
                LIMIT ?
            )
            """;

    private static final String HEARTBEAT_DELETE_SQL = """
            DELETE FROM public.collector_heartbeat
            WHERE id IN (
                SELECT id FROM public.collector_heartbeat
                WHERE heartbeat_time < ?
                ORDER BY id
                LIMIT ?
            )
            """;

    private static final String COMMAND_DELETE_SQL = """
            DELETE FROM public.collector_command
            WHERE id IN (
                SELECT id FROM public.collector_command
                WHERE status IN (2, 3, 4, 5) AND COALESCE(finished_at, requested_at) < ?
                ORDER BY id
                LIMIT ?
            )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    /** 每批删除行数；生产默认 2000，测试可调小以验证多批循环。 */
    @Value("${yudao.collector.retention.batch-size:2000}")
    private int batchSize = 2000;

    /** 清理时钟；生产使用 Asia/Shanghai 系统时钟，测试可替换以固定 cutoff。 */
    private Clock clock = Clock.system(SHANGHAI_ZONE);

    public CollectorRetentionCleanupServiceImpl(JdbcTemplate jdbcTemplate,
                                                PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 仅供测试替换时钟与批大小。 */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** 仅供测试覆盖批大小（生产由配置注入）。 */
    void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    @Override
    public RetentionCleanupReport cleanupAll() {
        LocalDate today = LocalDate.now(clock.withZone(SHANGHAI_ZONE));
        return jdbcTemplate.execute((ConnectionCallback<RetentionCleanupReport>) connection -> {
            // 锁必须覆盖整次清理，并在 finally 释放；拿不到锁时整体跳过，不算失败。
            if (!CollectorPartitionDdlSupport.tryAcquireLock(connection, CLEANUP_ADVISORY_LOCK_KEY)) {
                log.info("[cleanupAll][cleanup advisory lock({}) 已被占用，本次跳过]",
                        CLEANUP_ADVISORY_LOCK_KEY);
                return new RetentionCleanupReport(today, true, List.of());
            }
            try {
                return doCleanupAll(connection, today);
            } finally {
                CollectorPartitionDdlSupport.releaseLock(connection, CLEANUP_ADVISORY_LOCK_KEY);
            }
        });
    }

    private RetentionCleanupReport doCleanupAll(Connection connection, LocalDate today) {
        List<PolicyRow> policyRows = CollectorRetentionPolicyRows.loadAll(jdbcTemplate);
        Map<String, List<PolicyRow>> rowsByClass = new LinkedHashMap<>();
        for (PolicyRow row : policyRows) {
            rowsByClass.computeIfAbsent(row.dataClass(), key -> new ArrayList<>()).add(row);
        }

        List<RetentionClassResult> results = new ArrayList<>();
        Map<String, PolicyRow> writtenBack = new LinkedHashMap<>();
        for (CollectorRetentionDataClass dataClass : CollectorRetentionDataClass.values()) {
            List<PolicyRow> rows = rowsByClass.getOrDefault(dataClass.getCode(), List.of());
            RetentionClassResult result;
            if (rows.isEmpty()) {
                result = missingPolicyResult(dataClass);
            } else if (rows.size() > 1) {
                result = failedResult(dataClass, null, "策略重复：data_class=" + dataClass.getCode()
                        + " 存在 " + rows.size() + " 条有效策略，需人工处理");
            } else {
                PolicyRow policy = rows.get(0);
                result = cleanupClass(connection, today, dataClass, policy);
                writtenBack.put(dataClass.getCode(), policy);
            }
            results.add(result);
        }
        // 未知 data_class 也必须出现在报告里，禁止静默跳过。
        for (PolicyRow row : policyRows) {
            if (CollectorRetentionDataClass.of(row.dataClass()) != null) {
                continue;
            }
            results.add(new RetentionClassResult(row.dataClass(), "未知数据类", row.action(), null,
                    RetentionClassOutcome.FAILED, 0, List.of(), List.of(),
                    "失败：不支持的 data_class(" + row.dataClass() + ")，需人工处理"));
            writtenBack.put(row.dataClass(), row);
        }

        writeBack(results, writtenBack);
        return new RetentionCleanupReport(today, false, results);
    }

    private RetentionClassResult cleanupClass(Connection connection, LocalDate today,
                                              CollectorRetentionDataClass dataClass, PolicyRow policy) {
        LocalDateTime cutoff = cutoffOf(today, policy.retentionDays());
        RetentionAction action = RetentionAction.of(policy.action());
        if (action != dataClass.getExpectedAction()) {
            return new RetentionClassResult(dataClass.getCode(), dataClass.getDisplayName(), policy.action(),
                    cutoff, RetentionClassOutcome.FAILED, 0, List.of(), List.of(),
                    "失败：清理动作(" + policy.action() + ")与数据类期望(" + dataClass.getExpectedAction().getCode()
                            + ")不一致，需人工处理");
        }
        if (policy.enabled() != 1) {
            return new RetentionClassResult(dataClass.getCode(), dataClass.getDisplayName(), policy.action(),
                    cutoff, RetentionClassOutcome.SKIPPED_DISABLED, 0, List.of(), List.of(),
                    "未执行：策略已停用");
        }
        if (policy.retentionDays() == 0) {
            return new RetentionClassResult(dataClass.getCode(), dataClass.getDisplayName(), policy.action(),
                    cutoff, RetentionClassOutcome.SKIPPED_PERMANENT, 0, List.of(), List.of(),
                    "未执行：retention_days=0，永久保留");
        }
        return switch (dataClass.getCondition()) {
            case DROP_PARTITION -> dropRawPartitions(connection, dataClass, cutoff);
            case AGGREGATE_TUPLE, ALARM_CLEARED_AT, HEARTBEAT_TIME, COMMAND_TERMINAL ->
                    deleteExpiredRows(dataClass, cutoff);
        };
    }

    // ==================== raw：DROP 过期日分区 ====================

    /**
     * 只 DROP 本父表的直接子分区，且必须满足：标准日分区名、边界精确为单日 [dayStart, dayEnd)、
     * {@code dayEnd <= cutoff}。DEFAULT 分区永不 DROP，也不逐行 DELETE。
     */
    private RetentionClassResult dropRawPartitions(Connection connection, CollectorRetentionDataClass dataClass,
                                                   LocalDateTime cutoff) {
        // 与 PR #29 的分区预建共用同一把锁：锁、DROP、解锁在同一物理连接。
        try {
            if (!CollectorPartitionDdlSupport.tryAcquireLock(connection)) {
                log.info("[dropRawPartitions][分区维护锁({}) 已被占用，raw 清理跳过]",
                        CollectorPartitionDdlSupport.ADVISORY_LOCK_KEY);
                return new RetentionClassResult(dataClass.getCode(), dataClass.getDisplayName(),
                        dataClass.getExpectedAction().getCode(), cutoff, RetentionClassOutcome.SKIPPED_LOCKED,
                        0, List.of(), List.of(), "跳过：分区锁被占用，待下次调度");
            }
            try {
                RawScan scan = scanRawPartitions(connection, cutoff);
                List<PartitionDrop> dropped = new ArrayList<>();
                List<String> warnings = new ArrayList<>(scan.warnings());
                String failure = null;
                for (PartitionCandidate candidate : scan.candidates()) {
                    try {
                        if (dropPartition(connection, candidate)) {
                            dropped.add(new PartitionDrop(candidate.partitionName(), candidate.from(),
                                    candidate.to()));
                        } else {
                            warnings.add(candidate.partitionName() + " DROP 前核验未通过（对象或边界已变化），需人工处理");
                        }
                    } catch (SQLException ex) {
                        failure = "DROP " + candidate.partitionName() + " 失败：" + sqlMessage(ex);
                        break;
                    }
                }
                return rawResult(dataClass, cutoff, dropped, warnings, failure, scan.notDueCount());
            } finally {
                CollectorPartitionDdlSupport.releaseLock(connection);
            }
        } catch (SQLException ex) {
            return failedResult(dataClass, cutoff, "读取分区目录失败：" + sqlMessage(ex));
        }
    }

    private RetentionClassResult rawResult(CollectorRetentionDataClass dataClass, LocalDateTime cutoff,
                                           List<PartitionDrop> dropped, List<String> warnings,
                                           String failure, int notDueCount) {
        RetentionClassOutcome outcome;
        if (failure != null) {
            outcome = dropped.isEmpty() ? RetentionClassOutcome.FAILED : RetentionClassOutcome.PARTIAL;
        } else if (!warnings.isEmpty()) {
            outcome = RetentionClassOutcome.PARTIAL;
        } else {
            outcome = RetentionClassOutcome.SUCCESS;
        }
        StringBuilder message = new StringBuilder();
        if (outcome == RetentionClassOutcome.FAILED) {
            message.append("失败：").append(failure);
        } else {
            message.append(outcome == RetentionClassOutcome.SUCCESS ? "成功" : "部分成功")
                    .append("：DROP ").append(dropped.size()).append(" 个过期分区");
            if (failure != null) {
                message.append("；").append(failure);
            }
            if (!warnings.isEmpty()) {
                message.append("；").append(warnings.size()).append(" 项需人工处理");
            }
            if (notDueCount > 0) {
                message.append("；保留 ").append(notDueCount).append(" 个未到期分区");
            }
        }
        return new RetentionClassResult(dataClass.getCode(), dataClass.getDisplayName(),
                dataClass.getExpectedAction().getCode(), cutoff, outcome, 0, dropped, warnings, message.toString());
    }

    /** 执行 DROP 前的二次核验：对象仍必须属于预期父表，且边界未变化。 */
    private boolean dropPartition(Connection connection, PartitionCandidate candidate) throws SQLException {
        String name = candidate.partitionName();
        if (!CollectorPartitionDdlSupport.PARTITION_NAME_PATTERN.matcher(name).matches()) {
            return false;
        }
        RelationInfo relation = CollectorPartitionDdlSupport.loadRelation(connection, name);
        if (relation == null || !relation.partition()
                || !CollectorPartitionDdlSupport.SCHEMA.equals(relation.parentSchema())
                || !CollectorPartitionDdlSupport.TABLE_NAME.equals(relation.parentName())) {
            return false;
        }
        BoundParse parsed = CollectorPartitionDdlSupport.parseBound(relation.bound());
        if (parsed.state() != BoundState.RANGE
                || !parsed.bound().exactly(candidate.from(), candidate.to())) {
            return false;
        }
        // 禁止 DROP TABLE IF EXISTS：只对刚复核过的具体对象执行 DROP。
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE " + CollectorPartitionDdlSupport.SCHEMA + "." + name);
        }
        log.info("[dropPartition][已 DROP 过期分区 {}，边界 {}~{}]", name, candidate.from(), candidate.to());
        return true;
    }

    /**
     * 扫描 raw 子分区目录并分类；只读 catalog，不统计 raw 行数。
     *
     * <p>当前日、未来日、跨 cutoff、非标准名、不可解析边界一律不进入候选。</p>
     */
    private RawScan scanRawPartitions(Connection connection, LocalDateTime cutoff) throws SQLException {
        List<ChildPartition> children = new ArrayList<>(
                CollectorPartitionDdlSupport.loadChildPartitions(connection));
        children.sort(Comparator.comparing(ChildPartition::name));
        List<PartitionCandidate> candidates = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean defaultPartitionExists = false;
        int notDueCount = 0;
        for (ChildPartition child : children) {
            String name = child.name();
            if (CollectorPartitionDdlSupport.DEFAULT_PARTITION_NAME.equals(name)) {
                defaultPartitionExists = true;
                continue;
            }
            if (!child.partition()) {
                warnings.add(name + " 不是分区表，需人工处理");
                continue;
            }
            BoundParse parsed = CollectorPartitionDdlSupport.parseBound(child.bound());
            if (parsed.state() == BoundState.DEFAULT) {
                defaultPartitionExists = true;
                continue;
            }
            if (parsed.state() != BoundState.RANGE) {
                warnings.add(name + " 边界无法解析(" + CollectorPartitionDdlSupport.describeBound(child.bound())
                        + ")，需人工处理");
                continue;
            }
            PartitionBound bound = parsed.bound();
            if (bound.from() == null || bound.to() == null) {
                warnings.add(name + " 边界含 MINVALUE/MAXVALUE，需人工处理");
                continue;
            }
            if (!bound.to().equals(bound.from().plusDays(1))) {
                warnings.add(name + " 不是单日分区(" + describeRange(bound) + ")，需人工处理");
                continue;
            }
            if (!CollectorPartitionDdlSupport.PARTITION_NAME_PATTERN.matcher(name).matches()) {
                warnings.add(name + " 分区名不符合 " + CollectorPartitionDdlSupport.PARTITION_PREFIX
                        + "yyyyMMdd，需人工处理");
                continue;
            }
            String expectedName = CollectorPartitionDdlSupport.partitionName(bound.from().toLocalDate());
            if (!expectedName.equals(name)) {
                warnings.add(name + " 分区名与边界日期不一致(" + describeRange(bound) + ")，需人工处理");
                continue;
            }
            if (bound.to().isAfter(cutoff)) {
                if (bound.from().isBefore(cutoff)) {
                    // 分区内部同时包含「过期」与「保留」两段数据：不 DROP，交给人工按需处理。
                    warnings.add(name + " 跨越 cutoff(" + describeRange(bound) + ")，需人工处理");
                } else {
                    notDueCount++;
                }
                continue;
            }
            candidates.add(new PartitionCandidate(name, child.bound(), bound.from(), bound.to()));
        }
        return new RawScan(candidates, warnings, notDueCount, defaultPartitionExists);
    }

    // ==================== 其余数据类：分批 DELETE ====================

    private RetentionClassResult deleteExpiredRows(CollectorRetentionDataClass dataClass, LocalDateTime cutoff) {
        String sql = deleteSqlOf(dataClass);
        if (sql == null) {
            return failedResult(dataClass, cutoff, "暂不支持的清理条件：" + dataClass.getCondition());
        }
        Object cutoffParam = dataClass.isDateColumn()
                ? Date.valueOf(cutoff.toLocalDate()) : Timestamp.valueOf(cutoff);
        long deleted = 0;
        String failure = null;
        while (true) {
            int affected;
            try {
                Integer batch = transactionTemplate.execute(status ->
                        jdbcTemplate.update(sql, cutoffParam, batchSize));
                affected = batch == null ? 0 : batch;
            } catch (RuntimeException ex) {
                // 单批失败立即停止该类，避免同一错误无限重试。
                failure = sqlMessage(ex);
                break;
            }
            if (affected <= 0) {
                break;
            }
            deleted += affected;
        }

        List<String> warnings = new ArrayList<>();
        RetentionClassOutcome outcome;
        if (failure == null) {
            outcome = RetentionClassOutcome.SUCCESS;
        } else {
            outcome = deleted > 0 ? RetentionClassOutcome.PARTIAL : RetentionClassOutcome.FAILED;
            warnings.add("批量删除中断：" + abbreviate(failure));
        }
        String message;
        if (outcome == RetentionClassOutcome.FAILED) {
            message = "失败：" + abbreviate(failure);
        } else if (outcome == RetentionClassOutcome.PARTIAL) {
            message = "部分成功：已删除 " + deleted + " 行；失败原因：" + abbreviate(failure);
        } else {
            message = "成功：删除 " + deleted + " 行";
        }
        return new RetentionClassResult(dataClass.getCode(), dataClass.getDisplayName(),
                dataClass.getExpectedAction().getCode(), cutoff, outcome, deleted, List.of(), warnings, message);
    }

    private String deleteSqlOf(CollectorRetentionDataClass dataClass) {
        return switch (dataClass.getCondition()) {
            case AGGREGATE_TUPLE -> AGGREGATE_DELETE_SQL.formatted(dataClass.getTable(),
                    dataClass.getTimeColumn(), dataClass.getTimeColumn(), dataClass.getTable(),
                    dataClass.getTimeColumn(), dataClass.getTimeColumn());
            case ALARM_CLEARED_AT -> ALARM_DELETE_SQL;
            case HEARTBEAT_TIME -> HEARTBEAT_DELETE_SQL;
            case COMMAND_TERMINAL -> COMMAND_DELETE_SQL;
            case DROP_PARTITION -> null;
        };
    }

    // ==================== 预检（只读） ====================

    @Override
    public RetentionPreview preview() {
        LocalDate today = LocalDate.now(clock.withZone(SHANGHAI_ZONE));
        try {
            return jdbcTemplate.execute((ConnectionCallback<RetentionPreview>) connection -> {
                boolean autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    try (Statement statement = connection.createStatement()) {
                        // 只读事务 + 语句超时：任何写操作都会被 PostgreSQL 拒绝，超时由上层翻译成中文异常。
                        statement.execute("SET TRANSACTION READ ONLY");
                        statement.execute("SET LOCAL statement_timeout = '" + PREVIEW_STATEMENT_TIMEOUT + "'");
                    }
                    return doPreview(connection, today);
                } finally {
                    rollbackQuietly(connection);
                    restoreAutoCommit(connection, autoCommit);
                }
            });
        } catch (DataAccessException ex) {
            throw exception(RETENTION_PREVIEW_FAILED, describe(ex));
        }
    }

    private RetentionPreview doPreview(Connection connection, LocalDate today) {
        List<PolicyRow> policyRows = CollectorRetentionPolicyRows.loadAll(jdbcTemplate);
        Map<String, List<PolicyRow>> rowsByClass = new LinkedHashMap<>();
        for (PolicyRow row : policyRows) {
            rowsByClass.computeIfAbsent(row.dataClass(), key -> new ArrayList<>()).add(row);
        }
        List<RetentionClassPreview> classes = new ArrayList<>();
        for (CollectorRetentionDataClass dataClass : CollectorRetentionDataClass.values()) {
            List<PolicyRow> rows = rowsByClass.getOrDefault(dataClass.getCode(), List.of());
            classes.add(previewClass(connection, today, dataClass, rows));
        }
        return new RetentionPreview(today, classes);
    }

    private RetentionClassPreview previewClass(Connection connection, LocalDate today,
                                               CollectorRetentionDataClass dataClass, List<PolicyRow> rows) {
        List<String> warnings = new ArrayList<>();
        if (rows.isEmpty()) {
            return new RetentionClassPreview(dataClass.getCode(), dataClass.getDisplayName(), 0, false,
                    dataClass.getExpectedAction().getCode(), null, true, "策略缺失，无法预检", null,
                    List.of(), warnings);
        }
        if (rows.size() > 1) {
            warnings.add("data_class=" + dataClass.getCode() + " 存在 " + rows.size() + " 条有效策略，需人工处理");
        }
        PolicyRow policy = rows.get(0);
        LocalDateTime cutoff = cutoffOf(today, policy.retentionDays());
        boolean enabled = policy.enabled() == 1;
        RetentionAction action = RetentionAction.of(policy.action());
        if (action != dataClass.getExpectedAction()) {
            warnings.add("清理动作(" + policy.action() + ")与数据类期望("
                    + dataClass.getExpectedAction().getCode() + ")不一致，正式执行会失败");
        }
        if (rows.size() > 1 || action != dataClass.getExpectedAction()) {
            return new RetentionClassPreview(dataClass.getCode(), dataClass.getDisplayName(),
                    policy.retentionDays(), enabled, policy.action(), cutoff, true, "策略异常，无法预检",
                    null, List.of(), warnings);
        }
        if (!enabled) {
            return skippedPreview(dataClass, policy, cutoff, "策略已停用");
        }
        if (policy.retentionDays() == 0) {
            return skippedPreview(dataClass, policy, cutoff, "retention_days=0，永久保留");
        }
        if (dataClass.getCondition() == CollectorRetentionDataClass.RetentionCondition.DROP_PARTITION) {
            RawScan scan;
            try {
                scan = scanRawPartitions(connection, cutoff);
            } catch (SQLException ex) {
                throw exception(RETENTION_PREVIEW_FAILED,
                        dataClass.getDisplayName() + " 分区目录读取失败：" + sqlMessage(ex));
            }
            warnings.addAll(scan.warnings());
            if (scan.defaultPartitionExists()) {
                warnings.add("存在 DEFAULT 分区，本次不会 DROP 也不会逐行删除其中数据");
            }
            if (scan.notDueCount() > 0) {
                warnings.add("保留 " + scan.notDueCount() + " 个未到期日分区");
            }
            return new RetentionClassPreview(dataClass.getCode(), dataClass.getDisplayName(),
                    policy.retentionDays(), true, policy.action(), cutoff, false, null, null,
                    scan.candidates(), warnings);
        }
        Long count;
        try {
            count = countCandidates(dataClass, cutoff);
        } catch (DataAccessException ex) {
            // 绝不允许把统计失败伪装成 0。
            throw exception(RETENTION_PREVIEW_FAILED,
                    dataClass.getDisplayName() + " 候选行数统计失败：" + describe(ex));
        }
        return new RetentionClassPreview(dataClass.getCode(), dataClass.getDisplayName(),
                policy.retentionDays(), true, policy.action(), cutoff, false, null, count,
                List.of(), warnings);
    }

    private RetentionClassPreview skippedPreview(CollectorRetentionDataClass dataClass, PolicyRow policy,
                                                 LocalDateTime cutoff, String reason) {
        return new RetentionClassPreview(dataClass.getCode(), dataClass.getDisplayName(),
                policy.retentionDays(), policy.enabled() == 1, policy.action(), cutoff, true, reason,
                null, List.of(), List.of());
    }

    private Long countCandidates(CollectorRetentionDataClass dataClass, LocalDateTime cutoff) {
        Object cutoffParam = dataClass.isDateColumn()
                ? Date.valueOf(cutoff.toLocalDate()) : Timestamp.valueOf(cutoff);
        String sql = switch (dataClass.getCondition()) {
            case AGGREGATE_TUPLE -> "SELECT COUNT(*) FROM " + dataClass.getTable()
                    + " WHERE " + dataClass.getTimeColumn() + " < ?";
            case ALARM_CLEARED_AT -> "SELECT COUNT(*) FROM public.collector_alarm"
                    + " WHERE cleared_at IS NOT NULL AND cleared_at < ?";
            case HEARTBEAT_TIME -> "SELECT COUNT(*) FROM public.collector_heartbeat"
                    + " WHERE heartbeat_time < ?";
            case COMMAND_TERMINAL -> "SELECT COUNT(*) FROM public.collector_command"
                    + " WHERE status IN (2, 3, 4, 5) AND COALESCE(finished_at, requested_at) < ?";
            case DROP_PARTITION -> null;
        };
        if (sql == null) {
            throw exception(RETENTION_POLICY_UNSUPPORTED, dataClass.getCode());
        }
        Long count = jdbcTemplate.queryForObject(sql, Long.class, cutoffParam);
        return count == null ? 0L : count;
    }

    // ==================== 公共辅助 ====================

    private LocalDateTime cutoffOf(LocalDate today, int retentionDays) {
        return today.minusDays(retentionDays).atStartOfDay();
    }

    private RetentionClassResult missingPolicyResult(CollectorRetentionDataClass dataClass) {
        return new RetentionClassResult(dataClass.getCode(), dataClass.getDisplayName(),
                dataClass.getExpectedAction().getCode(), null,
                RetentionClassOutcome.FAILED, 0, List.of(), List.of(),
                "失败：策略缺失（collector_retention_policy 中不存在 data_class="
                        + dataClass.getCode() + "）");
    }

    private RetentionClassResult failedResult(CollectorRetentionDataClass dataClass, LocalDateTime cutoff,
                                              String reason) {
        return new RetentionClassResult(dataClass.getCode(), dataClass.getDisplayName(),
                dataClass.getExpectedAction().getCode(), cutoff, RetentionClassOutcome.FAILED,
                0, List.of(), List.of(), "失败：" + reason);
    }

    /** 回写 last_run_at / last_run_result；失败只记日志，不影响本次清理结论。 */
    private void writeBack(List<RetentionClassResult> results, Map<String, PolicyRow> policyRows) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(SHANGHAI_ZONE));
        for (RetentionClassResult result : results) {
            PolicyRow policy = policyRows.get(result.dataClass());
            if (policy == null) {
                continue;
            }
            try {
                CollectorRetentionPolicyRows.updateLastRun(jdbcTemplate, policy.id(), now,
                        truncate(result.message(), MAX_RESULT_LENGTH));
            } catch (DataAccessException ex) {
                log.error("[writeBack][data_class({}) last_run_result 回写失败]", result.dataClass(), ex);
            }
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 1) + "…";
    }

    private String abbreviate(String reason) {
        if (reason == null) {
            return "未知原因";
        }
        String trimmed = reason.strip();
        return trimmed.length() <= MAX_REASON_LENGTH ? trimmed
                : trimmed.substring(0, MAX_REASON_LENGTH) + "…";
    }

    private String describeRange(PartitionBound bound) {
        return bound.from() + "~" + bound.to();
    }

    private String describe(DataAccessException ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root instanceof SQLException sqlException && "57014".equals(sqlException.getSQLState())) {
            return "预检超时（statement_timeout " + PREVIEW_STATEMENT_TIMEOUT + "），请缩小数据范围后重试";
        }
        return sqlMessage(root);
    }

    private String sqlMessage(Throwable exception) {
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ex) {
            log.warn("[preview][只读事务回滚失败]", ex);
        }
    }

    private void restoreAutoCommit(Connection connection, boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException ex) {
            log.warn("[preview][恢复 autoCommit 失败]", ex);
        }
    }

    /** raw 分区扫描结果。 */
    private record RawScan(List<PartitionCandidate> candidates, List<String> warnings,
                           int notDueCount, boolean defaultPartitionExists) {
    }

}
