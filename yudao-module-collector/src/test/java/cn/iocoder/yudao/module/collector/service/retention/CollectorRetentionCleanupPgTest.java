package cn.iocoder.yudao.module.collector.service.retention;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.collector.enums.CollectorRetentionDataClass;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionDdlSupport;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService.PartitionDrop;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService.RetentionClassOutcome;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService.RetentionClassResult;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService.RetentionCleanupReport;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 采集数据保留清理的真实 PostgreSQL 验证（对应任务卡验收 1~20 中属于后端核心的场景）。
 *
 * <p>运行方式见 {@link CollectorRetentionPgTestSupport}：必须使用库名前缀为
 * {@code collector_retention_} 的临时库，并显式设置 {@code COLLECTOR_TEST_PG_ALLOW_DESTRUCTIVE=true}。</p>
 */
public class CollectorRetentionCleanupPgTest extends CollectorRetentionPgTestSupport {

    /** 场景：retention_days=0 表示永久保留，raw 也不 DROP。 */
    @Test
    public void zeroRetentionDays_skipsCleanup() throws Exception {
        updatePolicyColumn("aggregate_second", "retention_days", 0);
        updatePolicyColumn("sample_raw", "retention_days", 0);
        insertAggregate("collector_aggregate_second", 1, CUTOFF.minusDays(10));

        RetentionCleanupReport report = cleanupService.cleanupAll();
        echoReport("场景1 retentionDays=0", report);

        RetentionClassResult second = resultOf(report, "aggregate_second");
        assertEquals(RetentionClassOutcome.SKIPPED_PERMANENT, second.outcome());
        assertEquals(0, second.deletedRows());
        assertEquals(1, countRows("collector_aggregate_second"));

        RetentionClassResult raw = resultOf(report, "sample_raw");
        assertEquals(RetentionClassOutcome.SKIPPED_PERMANENT, raw.outcome());
        assertTrue(childPartitionNames().containsAll(List.of(
                "collector_sample_raw_p20261018", "collector_sample_raw_p20261019",
                "collector_sample_raw_p20261020")));
        String result = scalarString("SELECT last_run_result FROM public.collector_retention_policy"
                + " WHERE data_class = 'aggregate_second'");
        assertTrue(result, result.contains("永久保留"));
    }

    /** 场景：enabled=0 停用，跳过清理且不影响其他数据类。 */
    @Test
    public void disabledPolicy_skipsCleanup() throws Exception {
        updatePolicyColumn("heartbeat", "enabled", 0);
        insertHeartbeat(1, CUTOFF.minusDays(10));

        RetentionCleanupReport report = cleanupService.cleanupAll();
        echoReport("场景2 enabled=0", report);

        RetentionClassResult heartbeat = resultOf(report, "heartbeat");
        assertEquals(RetentionClassOutcome.SKIPPED_DISABLED, heartbeat.outcome());
        assertEquals(1, countRows("collector_heartbeat"));
        assertFalse(heartbeat.failed());
        String result = scalarString("SELECT last_run_result FROM public.collector_retention_policy"
                + " WHERE data_class = 'heartbeat'");
        assertTrue(result, result.contains("停用"));
    }

    /** 场景：等于 cutoff 的数据保留，严格小于 cutoff 的数据删除。 */
    @Test
    public void cutoffBoundary_equalRetainedOlderDeleted() throws Exception {
        insertAggregate("collector_aggregate_minute", 1, CUTOFF);
        insertAggregate("collector_aggregate_minute", 1, CUTOFF.minusSeconds(1));
        insertAggregate("collector_aggregate_minute", 1, CUTOFF.minusDays(1));

        RetentionCleanupReport report = cleanupService.cleanupAll();
        echoReport("场景3 cutoff 边界", report);

        RetentionClassResult minute = resultOf(report, "aggregate_minute");
        assertEquals(RetentionClassOutcome.SUCCESS, minute.outcome());
        assertEquals(2, minute.deletedRows());
        assertEquals(1, countRows("collector_aggregate_minute"));
        assertEquals("2026-10-21 00:00:00",
                scalarString("SELECT to_char(bucket_time, 'YYYY-MM-DD HH24:MI:SS')"
                        + " FROM public.collector_aggregate_minute"));
    }

    /** 场景：raw 只 DROP dayEnd<=cutoff 的标准日分区；当前日/未来日/DEFAULT/非标准分区都保留。 */
    @Test
    public void rawDropsOnlyExpiredStandardDailyPartitions() throws Exception {
        // 非标准名与跨多日分区只在「需人工处理」场景下存在，避免污染其他用例的成功判定。
        createPartition("collector_sample_raw_custom_old",
                CUTOFF_DAY.minusDays(5), CUTOFF_DAY.minusDays(4));
        createPartition("collector_sample_raw_span",
                CUTOFF_DAY.minusDays(7), CUTOFF_DAY.minusDays(5));
        insertRaw(LocalDateTime.of(2026, 1, 5, 8, 0));
        assertEquals(1, countRows(DEFAULT_PARTITION));

        RetentionCleanupReport report = cleanupService.cleanupAll();
        echoReport("场景4 raw 分区 DROP", report);

        RetentionClassResult raw = resultOf(report, "sample_raw");
        assertEquals(RetentionClassOutcome.PARTIAL, raw.outcome());
        assertEquals(3, raw.droppedPartitions().size());
        assertEquals(List.of("collector_sample_raw_p20261018", "collector_sample_raw_p20261019",
                        "collector_sample_raw_p20261020"),
                raw.droppedPartitions().stream().map(PartitionDrop::partitionName).sorted().toList());
        assertEquals(3, report.droppedPartitionCount());

        assertEquals(List.of("collector_sample_raw_custom_old", "collector_sample_raw_default",
                        "collector_sample_raw_p20261021", "collector_sample_raw_p20261022",
                        "collector_sample_raw_span"),
                childPartitionNames());
        // DEFAULT 有旧数据也不删也不 DROP；同名普通表绝不误伤。
        assertEquals(1, countRows(DEFAULT_PARTITION));
        assertTrue(relationExists("collector_sample_raw_p20261010"));
        assertTrue(raw.warnings().stream().anyMatch(warning -> warning.contains("custom_old")));
        assertTrue(raw.warnings().stream().anyMatch(warning -> warning.contains("span")));
        assertTrue(report.hasFailure());
    }

    /** 场景：四张聚合表分别清理，第二次执行删除 0 行。 */
    @Test
    public void aggregateTablesCleanedIndividually_andSecondRunDeletesZero() throws Exception {
        insertAggregate("collector_aggregate_second", 1, CUTOFF.minusDays(1));
        insertAggregate("collector_aggregate_second", 1, CUTOFF.plusDays(1));
        insertAggregate("collector_aggregate_minute", 1, CUTOFF.minusDays(2));
        insertAggregate("collector_aggregate_hour", 1, CUTOFF.minusDays(3));
        insertAggregate("collector_aggregate_hour", 1, CUTOFF.minusDays(4));
        insertAggregateDay(1, CUTOFF_DAY.minusDays(1));
        insertAggregateDay(1, CUTOFF_DAY);

        RetentionCleanupReport first = cleanupService.cleanupAll();
        echoReport("场景5 首次清理", first);
        assertEquals(1, resultOf(first, "aggregate_second").deletedRows());
        assertEquals(1, resultOf(first, "aggregate_minute").deletedRows());
        assertEquals(2, resultOf(first, "aggregate_hour").deletedRows());
        assertEquals(1, resultOf(first, "aggregate_day").deletedRows());
        assertEquals(1, countRows("collector_aggregate_second"));
        assertEquals(0, countRows("collector_aggregate_minute"));
        assertEquals(0, countRows("collector_aggregate_hour"));
        assertEquals(1, countRows("collector_aggregate_day"));

        RetentionCleanupReport second = cleanupService.cleanupAll();
        echoReport("场景5 第二次清理", second);
        assertEquals(0, resultOf(second, "aggregate_second").deletedRows());
        assertEquals(0, resultOf(second, "aggregate_minute").deletedRows());
        assertEquals(0, resultOf(second, "aggregate_hour").deletedRows());
        assertEquals(0, resultOf(second, "aggregate_day").deletedRows());
        assertEquals(RetentionClassOutcome.SUCCESS, resultOf(second, "aggregate_second").outcome());
    }

    /** 场景：batchSize 调小后仍能分批删完。 */
    @Test
    public void batchSizeSmall_deletesInMultipleBatches() throws Exception {
        cleanupService.setBatchSize(2);
        for (int i = 1; i <= 5; i++) {
            insertHeartbeat(i, CUTOFF.minusDays(1).plusMinutes(i));
        }

        RetentionCleanupReport report = cleanupService.cleanupAll();
        echoReport("场景6 batchSize=2", report);

        assertEquals(5, resultOf(report, "heartbeat").deletedRows());
        assertEquals(0, countRows("collector_heartbeat"));
        assertEquals(RetentionClassOutcome.SUCCESS, resultOf(report, "heartbeat").outcome());
    }

    /** 场景：活动报警永不删除；已恢复报警只按 cleared_at 判定。 */
    @Test
    public void alarmOnlyClearedByClearedAt() throws Exception {
        insertAlarm(1, 101, 0, CUTOFF.minusDays(60), null);
        insertAlarm(2, 102, 2, CUTOFF.minusDays(90), CUTOFF.plusDays(1));
        insertAlarm(3, 103, 2, CUTOFF.minusDays(90), CUTOFF.minusSeconds(1));

        RetentionCleanupReport report = cleanupService.cleanupAll();
        echoReport("场景7 报警", report);

        RetentionClassResult alarm = resultOf(report, "alarm");
        assertEquals(1, alarm.deletedRows());
        assertEquals(2, countRows("collector_alarm"));
        assertEquals(1, scalarLong("SELECT COUNT(*) FROM public.collector_alarm WHERE id = 1"));
        assertEquals(1, scalarLong("SELECT COUNT(*) FROM public.collector_alarm WHERE id = 2"));
        assertEquals(0, scalarLong("SELECT COUNT(*) FROM public.collector_alarm WHERE id = 3"));
    }

    /** 场景：心跳按 heartbeat_time 严格小于 cutoff 删除。 */
    @Test
    public void heartbeatBoundary() throws Exception {
        insertHeartbeat(1, CUTOFF);
        insertHeartbeat(2, CUTOFF.minusSeconds(1));

        RetentionCleanupReport report = cleanupService.cleanupAll();
        echoReport("场景8 心跳边界", report);

        assertEquals(1, resultOf(report, "heartbeat").deletedRows());
        assertEquals(1, countRows("collector_heartbeat"));
        assertEquals(1, scalarLong("SELECT COUNT(*) FROM public.collector_heartbeat WHERE id = 1"));
    }

    /** 场景：仅终态命令删除；status 0/1 永不删除；请求很早但最近完成的保留。 */
    @Test
    public void commandTerminalOnly() throws Exception {
        insertCommand(1, 0, CUTOFF.minusDays(30), null);
        insertCommand(2, 1, CUTOFF.minusDays(30), null);
        insertCommand(3, 2, CUTOFF.minusDays(30), CUTOFF.minusDays(30));
        insertCommand(4, 3, CUTOFF.minusDays(30), CUTOFF.minusDays(29));
        insertCommand(5, 4, CUTOFF.minusDays(30), null);
        insertCommand(6, 5, CUTOFF.minusDays(30), CUTOFF.minusDays(28));
        insertCommand(7, 2, CUTOFF.minusDays(30), CUTOFF.plusDays(2));

        RetentionCleanupReport report = cleanupService.cleanupAll();
        echoReport("场景9 命令", report);

        assertEquals(4, resultOf(report, "command").deletedRows());
        assertEquals(3, countRows("collector_command"));
        assertEquals(2, scalarLong("SELECT COUNT(*) FROM public.collector_command WHERE id IN (1, 2)"));
        assertEquals(1, scalarLong("SELECT COUNT(*) FROM public.collector_command WHERE id = 7"));
        // status=4 且 finished_at 为空时按 requested_at 判定，属于过期终态，应删除。
        assertEquals(0, scalarLong("SELECT COUNT(*) FROM public.collector_command WHERE id = 5"));
    }

    /** 场景：一类失败不影响其他类，整体报告标记失败。 */
    @Test
    public void classFailureDoesNotBlockOthers() throws Exception {
        updatePolicyColumn("aggregate_second", "action", "'drop_partition'");
        insertAggregate("collector_aggregate_second", 1, CUTOFF.minusDays(5));
        insertHeartbeat(1, CUTOFF.minusDays(5));

        RetentionCleanupReport report = cleanupService.cleanupAll();
        echoReport("场景10 单类失败隔离", report);

        assertEquals(RetentionClassOutcome.FAILED, resultOf(report, "aggregate_second").outcome());
        assertEquals(1, countRows("collector_aggregate_second"));
        assertEquals(RetentionClassOutcome.SUCCESS, resultOf(report, "heartbeat").outcome());
        assertEquals(0, countRows("collector_heartbeat"));
        assertTrue(report.hasFailure());
        String result = scalarString("SELECT last_run_result FROM public.collector_retention_policy"
                + " WHERE data_class = 'aggregate_second'");
        assertTrue(result, result.startsWith("失败："));
    }

    /** 场景：单批失败时记录部分成功与已删除行数，且不死循环。 */
    @Test
    public void partialSuccessWhenBatchFails() throws Exception {
        cleanupService.setBatchSize(1);
        executeSql("""
                CREATE OR REPLACE FUNCTION retention_fail_hour() RETURNS trigger AS $$
                BEGIN
                    IF OLD.channel_id = 2 THEN
                        RAISE EXCEPTION '模拟删除失败 channel %', OLD.channel_id;
                    END IF;
                    RETURN OLD;
                END; $$ LANGUAGE plpgsql
                """);
        executeSql("CREATE TRIGGER trg_retention_fail_hour BEFORE DELETE ON public.collector_aggregate_hour"
                + " FOR EACH ROW EXECUTE FUNCTION retention_fail_hour()");
        insertAggregate("collector_aggregate_hour", 1, CUTOFF.minusDays(2));
        insertAggregate("collector_aggregate_hour", 2, CUTOFF.minusDays(1));

        RetentionCleanupReport report = cleanupService.cleanupAll();
        echoReport("场景11 部分成功", report);

        RetentionClassResult hour = resultOf(report, "aggregate_hour");
        assertEquals(RetentionClassOutcome.PARTIAL, hour.outcome());
        assertEquals(1, hour.deletedRows());
        assertEquals(1, countRows("collector_aggregate_hour"));
        assertEquals(2, scalarLong("SELECT channel_id FROM public.collector_aggregate_hour"));
        assertTrue(hour.message(), hour.message().startsWith("部分成功：已删除 1 行；失败原因："));
        String result = scalarString("SELECT last_run_result FROM public.collector_retention_policy"
                + " WHERE data_class = 'aggregate_hour'");
        assertTrue(result, result.startsWith("部分成功：已删除 1 行"));
        assertTrue(result.length() <= 255);
    }

    /** 场景：SQL 异常后无残留 advisory lock，且整体报告标记失败。 */
    @Test
    public void sqlFailure_releasesAdvisoryLocks() throws Exception {
        executeSql("ALTER TABLE public.collector_heartbeat RENAME TO collector_heartbeat_broken");
        try {
            RetentionCleanupReport report = cleanupService.cleanupAll();
            echoReport("场景12 SQL 异常", report);

            assertEquals(RetentionClassOutcome.FAILED, resultOf(report, "heartbeat").outcome());
            assertTrue(report.hasFailure());
            assertEquals(0, advisoryLockCount(CollectorRetentionCleanupServiceImpl.CLEANUP_ADVISORY_LOCK_KEY));
            assertEquals(0, advisoryLockCount(CollectorPartitionDdlSupport.ADVISORY_LOCK_KEY));
        } finally {
            executeSql("ALTER TABLE public.collector_heartbeat_broken RENAME TO collector_heartbeat");
        }
    }

    /** 场景：分区预建锁被外部持有时 raw 记为跳过，且不 DROP、不留锁；释放后可正常 DROP。 */
    @Test
    public void rawSkipsWhenPartitionPrecreateLockHeld() throws Exception {
        try (Connection holder = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement lock = holder.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            lock.setLong(1, CollectorPartitionDdlSupport.ADVISORY_LOCK_KEY);
            try (ResultSet rs = lock.executeQuery()) {
                assertTrue(rs.next() && rs.getBoolean(1));
            }
            assertEquals(1, advisoryLockCount(CollectorPartitionDdlSupport.ADVISORY_LOCK_KEY));

            RetentionCleanupReport blocked = cleanupService.cleanupAll();
            echoReport("场景13 分区锁占用", blocked);

            RetentionClassResult raw = resultOf(blocked, "sample_raw");
            assertEquals(RetentionClassOutcome.SKIPPED_LOCKED, raw.outcome());
            assertTrue(raw.droppedPartitions().isEmpty());
            assertFalse(blocked.hasFailure());
            assertTrue(childPartitionNames().containsAll(List.of(
                    "collector_sample_raw_p20261018", "collector_sample_raw_p20261019",
                    "collector_sample_raw_p20261020")));
            assertEquals(1, advisoryLockCount(CollectorPartitionDdlSupport.ADVISORY_LOCK_KEY));
            assertEquals(0, advisoryLockCount(CollectorRetentionCleanupServiceImpl.CLEANUP_ADVISORY_LOCK_KEY));

            try (PreparedStatement unlock = holder.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                unlock.setLong(1, CollectorPartitionDdlSupport.ADVISORY_LOCK_KEY);
                try (ResultSet rs = unlock.executeQuery()) {
                    assertTrue(rs.next() && rs.getBoolean(1));
                }
            }
        }

        RetentionCleanupReport released = cleanupService.cleanupAll();
        echoReport("场景13 锁释放后", released);

        assertEquals(3, resultOf(released, "sample_raw").droppedPartitions().size());
        assertFalse(childPartitionNames().contains("collector_sample_raw_p20261018"));
        assertEquals(0, advisoryLockCount(CollectorPartitionDdlSupport.ADVISORY_LOCK_KEY));
        assertEquals(0, advisoryLockCount(CollectorRetentionCleanupServiceImpl.CLEANUP_ADVISORY_LOCK_KEY));
    }

    /** 场景：配置、latest、水位、alarm_state、alarm_cursor 不变；策略本身不被修改。 */
    @Test
    public void untouchedTablesAndPolicyColumnsNotModified() throws Exception {
        String before = untouchedSnapshot();
        insertAggregate("collector_aggregate_second", 1, CUTOFF.minusDays(2));
        insertAlarm(1, 201, 2, CUTOFF.minusDays(40), CUTOFF.minusDays(30));

        RetentionCleanupReport report = cleanupService.cleanupAll();
        echoReport("场景14 未触碰对象", report);

        assertEquals(before, untouchedSnapshot());
        assertEquals(8, scalarLong("SELECT COUNT(*) FROM public.collector_retention_policy WHERE deleted = 0"));
        assertEquals(0, scalarLong("SELECT COUNT(*) FROM public.collector_retention_policy"
                + " WHERE retention_days <> 30 OR partition_lead_days <> CASE"
                + " WHEN data_class = 'sample_raw' THEN 3 ELSE 0 END OR enabled <> 1"));
        assertEquals(0, scalarLong("SELECT COUNT(*) FROM public.collector_retention_policy"
                + " WHERE last_run_at IS NULL OR last_run_result IS NULL OR length(last_run_result) > 255"));
        assertEquals(0, scalarLong("SELECT COUNT(*) FROM public.collector_retention_policy"
                + " WHERE (data_class = 'sample_raw' AND action <> 'drop_partition')"
                + " OR (data_class <> 'sample_raw' AND action <> 'delete_rows')"));
        assertEquals("测试策略 aggregate_second",
                scalarString("SELECT description FROM public.collector_retention_policy"
                        + " WHERE data_class = 'aggregate_second'"));
    }

    /** 场景：未知 data_class 明确失败，不猜表名、不静默跳过。 */
    @Test
    public void unknownDataClassReportedAsFailure() throws Exception {
        insertPolicy("legacy_metrics", 30, "delete_rows", 1);
        insertHeartbeat(1, CUTOFF.minusDays(1));

        RetentionCleanupReport report = cleanupService.cleanupAll();
        echoReport("场景15 未知数据类", report);

        RetentionClassResult unknown = resultOf(report, "legacy_metrics");
        assertEquals(RetentionClassOutcome.FAILED, unknown.outcome());
        assertTrue(unknown.message(), unknown.message().contains("不支持的 data_class"));
        assertTrue(report.hasFailure());
        assertEquals(0, countRows("collector_heartbeat"));
    }

    /** 场景：日期型 bucket_date 的 cutoff 等价判定（等于 cutoff 当天保留）。 */
    @Test
    public void aggregateDayKeepsCutoffDate() throws Exception {
        insertAggregateDay(1, CUTOFF_DAY.minusDays(1));
        insertAggregateDay(1, CUTOFF_DAY);
        insertAggregateDay(1, CUTOFF_DAY.plusDays(1));

        RetentionCleanupReport report = cleanupService.cleanupAll();
        echoReport("场景16 天聚合边界", report);

        assertEquals(1, resultOf(report, "aggregate_day").deletedRows());
        assertEquals(2, countRows("collector_aggregate_day"));
        assertEquals(1, scalarLong("SELECT COUNT(*) FROM public.collector_aggregate_day WHERE bucket_date = '"
                + CUTOFF_DAY + "'"));
    }

    /**
     * B1：statement_timeout 必须作用在真正的 COUNT 查询上。
     *
     * <p>把秒聚合表换成带 {@code pg_sleep(2)} 的视图，只让该类计数变慢；200ms 的
     * statement_timeout 必然在计数阶段触发 57014，并翻译成中文业务异常，绝不返回 0。</p>
     */
    @Test
    public void previewTimesOutOnCountQueryWithChineseError() throws Exception {
        insertAggregate("collector_aggregate_second", 1, CUTOFF.minusDays(1));
        executeSql("ALTER TABLE public.collector_aggregate_second"
                + " RENAME TO collector_aggregate_second_slow_base");
        try {
            executeSql("""
                    CREATE OR REPLACE FUNCTION retention_slow_true() RETURNS boolean AS $$
                    BEGIN
                        PERFORM pg_sleep(2);
                        RETURN true;
                    END; $$ LANGUAGE plpgsql VOLATILE
                    """);
            // VOLATILE 条件无法被计划器消除：COUNT(*) 必须逐行求值，计时段必然超过 200ms。
            executeSql("CREATE VIEW public.collector_aggregate_second AS SELECT * FROM"
                    + " public.collector_aggregate_second_slow_base WHERE retention_slow_true()");
            cleanupService.setPreviewStatementTimeout("200ms");

            long begin = System.nanoTime();
            try {
                cleanupService.preview();
                fail("预检应在秒聚合 COUNT 查询上因 statement_timeout 失败");
            } catch (ServiceException ex) {
                long elapsedMs = (System.nanoTime() - begin) / 1_000_000;
                System.out.println("[证据] B1 预检计数超时 elapsedMs=" + elapsedMs
                        + " message=" + ex.getMessage());
                assertTrue(ex.getMessage(), ex.getMessage().contains("预检超时"));
                assertTrue(ex.getMessage(), ex.getMessage().contains("statement_timeout 200ms"));
                assertTrue("超时发生在计数阶段（应≥200ms 且远小于 pg_sleep 的 2s）",
                        elapsedMs >= 150 && elapsedMs < 5000);
            }
        } finally {
            if (relationExists("collector_aggregate_second_slow_base")) {
                executeSql("DROP VIEW IF EXISTS public.collector_aggregate_second");
                executeSql("ALTER TABLE public.collector_aggregate_second_slow_base"
                        + " RENAME TO collector_aggregate_second");
            }
        }
    }

    /**
     * M1：DROP 遇到长事务时按 lock_timeout 放弃，raw 记为 SKIPPED_LOCKED 而不是成功。
     */
    @Test(timeout = 20000)
    public void rawDropLockTimeoutMarksClassSkippedLocked() throws Exception {
        cleanupService.setDropLockTimeout("200ms");
        try (Connection holder = DriverManager.getConnection(URL, USER, PASSWORD)) {
            holder.setAutoCommit(false);
            try (Statement lock = holder.createStatement()) {
                // ROW EXCLUSIVE 与 catalog 读取的 ACCESS SHARE 兼容，但足以阻塞 DROP 的 ACCESS EXCLUSIVE。
                lock.execute("LOCK TABLE public.collector_sample_raw_p20261018 IN ROW EXCLUSIVE MODE");
            }
            long begin = System.nanoTime();
            RetentionCleanupReport report = cleanupService.cleanupAll();
            long elapsedMs = (System.nanoTime() - begin) / 1_000_000;
            echoReport("场景17 DROP 锁等待超时", report);

            RetentionClassResult raw = resultOf(report, "sample_raw");
            assertEquals(RetentionClassOutcome.SKIPPED_LOCKED, raw.outcome());
            assertEquals(0, raw.droppedPartitions().size());
            assertTrue(raw.message(), raw.message().contains("等待分区锁超时"));
            assertTrue("锁等待应按 200ms 放弃，实际 " + elapsedMs + "ms", elapsedMs < 5000);
            assertTrue(relationExists("collector_sample_raw_p20261018"));
            assertEquals(0, advisoryLockCount(CollectorRetentionCleanupServiceImpl.CLEANUP_ADVISORY_LOCK_KEY));
            assertEquals(0, advisoryLockCount(CollectorPartitionDdlSupport.ADVISORY_LOCK_KEY));
        }
    }

    /** M1：单批 DELETE 的 statement_timeout 生效，翻译为中文且不死循环。 */
    @Test
    public void deleteStatementTimeoutStopsBatchWithChineseError() throws Exception {
        cleanupService.setDeleteStatementTimeout("200ms");
        executeSql("""
                CREATE OR REPLACE FUNCTION retention_slow_delete() RETURNS trigger AS $$
                BEGIN
                    PERFORM pg_sleep(2);
                    RETURN OLD;
                END; $$ LANGUAGE plpgsql
                """);
        executeSql("CREATE TRIGGER trg_retention_slow_delete BEFORE DELETE ON public.collector_heartbeat"
                + " FOR EACH ROW EXECUTE FUNCTION retention_slow_delete()");
        insertHeartbeat(1, CUTOFF.minusDays(1));

        RetentionCleanupReport report = cleanupService.cleanupAll();
        echoReport("场景18 删除语句超时", report);

        RetentionClassResult heartbeat = resultOf(report, "heartbeat");
        assertEquals(RetentionClassOutcome.FAILED, heartbeat.outcome());
        assertEquals(0, heartbeat.deletedRows());
        assertTrue(heartbeat.message(), heartbeat.message().contains("删除超时"));
        assertTrue(heartbeat.message(), heartbeat.message().contains("statement_timeout 200ms"));
        assertEquals(1, countRows("collector_heartbeat"));
    }

    /** M1：单批 DELETE 的 lock_timeout 生效，失败后不重试、不留数据半删。 */
    @Test
    public void deleteLockTimeoutStopsBatchWithChineseError() throws Exception {
        cleanupService.setDeleteLockTimeout("200ms");
        insertHeartbeat(1, CUTOFF.minusDays(1));
        RetentionCleanupReport report;
        try (Connection holder = DriverManager.getConnection(URL, USER, PASSWORD)) {
            holder.setAutoCommit(false);
            try (Statement lock = holder.createStatement()) {
                lock.execute("LOCK TABLE public.collector_heartbeat IN ACCESS EXCLUSIVE MODE");
            }
            long begin = System.nanoTime();
            report = cleanupService.cleanupAll();
            long elapsedMs = (System.nanoTime() - begin) / 1_000_000;
            echoReport("场景19 删除锁等待超时", report);

            RetentionClassResult heartbeat = resultOf(report, "heartbeat");
            assertEquals(RetentionClassOutcome.FAILED, heartbeat.outcome());
            assertEquals(0, heartbeat.deletedRows());
            assertTrue(heartbeat.message(), heartbeat.message().contains("等待锁超时"));
            assertTrue("锁等待应按 200ms 放弃，实际 " + elapsedMs + "ms", elapsedMs < 5000);
        }
        assertEquals(1, countRows("collector_heartbeat"));
    }

    /**
     * M2：改写后的聚合删除必须让批次选择走时间索引，且不再出现目标表的半连接。
     *
     * <p>小数据量下计划器仍可能选 Seq Scan，因此用 {@code SET LOCAL enable_seqscan=off}
     * 证明时间索引可用；默认计划同时打印在 surefire 报告中，结论不伪造。</p>
     */
    @Test
    public void aggregateDeletePlanUsesTimeIndex() throws Exception {
        executeSql("""
                INSERT INTO public.collector_aggregate_second (channel_id, device_id, bucket_time)
                SELECT (g % 4) + 1, 9, timestamp '2026-11-21 00:00:00' + (g || ' seconds')::interval
                FROM generate_series(1, 4000) g
                """);
        executeSql("ANALYZE public.collector_aggregate_second");

        String sql = cleanupService.deleteSqlOf(CollectorRetentionDataClass.AGGREGATE_SECOND);
        String natural = explainPlan(sql, Timestamp.valueOf(CUTOFF), 2000, false);
        String forced = explainPlan(sql, Timestamp.valueOf(CUTOFF), 2000, true);
        System.out.println("[证据] M2 EXPLAIN 默认计划：\n" + natural);
        System.out.println("[证据] M2 EXPLAIN enable_seqscan=off：\n" + forced);

        assertTrue(forced, forced.contains("idx_collector_aggregate_second_bucket"));
        assertTrue(forced, forced.contains("Index Scan") || forced.contains("Bitmap Index Scan"));
        assertFalse("改写后不应再把目标表放进 Hash Semi Join", forced.contains("Hash Semi Join"));
    }

    /** M2：改写后四张聚合表仍「等于 cutoff 保留、早于 cutoff 删除」，第二次执行删除 0 行。 */
    @Test
    public void aggregateRewriteKeepsCutoffBoundaryAndIsIdempotent() throws Exception {
        for (String table : List.of("collector_aggregate_second", "collector_aggregate_minute",
                "collector_aggregate_hour")) {
            insertAggregate(table, 1, CUTOFF);
            insertAggregate(table, 1, CUTOFF.minusSeconds(1));
        }
        insertAggregateDay(1, CUTOFF_DAY);
        insertAggregateDay(1, CUTOFF_DAY.minusDays(1));

        RetentionCleanupReport first = cleanupService.cleanupAll();
        echoReport("场景20 改写 SQL 边界", first);
        for (String dataClass : List.of("aggregate_second", "aggregate_minute",
                "aggregate_hour", "aggregate_day")) {
            assertEquals(dataClass + " 应只删除早于 cutoff 的 1 行",
                    1, resultOf(first, dataClass).deletedRows());
        }
        assertEquals(1, countRows("collector_aggregate_second"));
        assertEquals(1, countRows("collector_aggregate_minute"));
        assertEquals(1, countRows("collector_aggregate_hour"));
        assertEquals(1, countRows("collector_aggregate_day"));
        assertEquals(1, scalarLong("SELECT COUNT(*) FROM public.collector_aggregate_second"
                + " WHERE bucket_time = '" + Timestamp.valueOf(CUTOFF) + "'"));
        assertEquals(1, scalarLong("SELECT COUNT(*) FROM public.collector_aggregate_day"
                + " WHERE bucket_date = '" + CUTOFF_DAY + "'"));

        RetentionCleanupReport second = cleanupService.cleanupAll();
        echoReport("场景20 改写 SQL 第二次执行", second);
        for (String dataClass : List.of("aggregate_second", "aggregate_minute",
                "aggregate_hour", "aggregate_day")) {
            assertEquals(dataClass + " 第二次应删除 0 行",
                    0, resultOf(second, dataClass).deletedRows());
        }
    }

    /** 执行 EXPLAIN 并返回计划文本；disableSeqScan=true 时在事务内关闭顺序扫描以证明索引可用。 */
    private String explainPlan(String sql, Object first, Object second, boolean disableSeqScan)
            throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
            connection.setAutoCommit(false);
            if (disableSeqScan) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SET LOCAL enable_seqscan = off");
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("EXPLAIN " + sql)) {
                statement.setObject(1, first);
                statement.setObject(2, second);
                StringBuilder plan = new StringBuilder();
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        plan.append(resultSet.getString(1)).append('\n');
                    }
                }
                connection.rollback();
                return plan.toString();
            }
        }
    }

}
