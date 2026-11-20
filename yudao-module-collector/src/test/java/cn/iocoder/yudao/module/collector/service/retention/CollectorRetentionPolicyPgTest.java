package cn.iocoder.yudao.module.collector.service.retention;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.collector.job.retention.CollectorRetentionCleanupJob;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService.PartitionCandidate;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService.RetentionClassPreview;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService.RetentionPreview;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionPolicyService.RetentionPolicySnapshot;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionPolicyService.RetentionPolicyUpdateCommand;
import org.junit.Test;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.RETENTION_CLEANUP_FAILED;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.RETENTION_POLICY_FIELD_INVALID;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.RETENTION_POLICY_LEAD_DAYS_FORBIDDEN;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.RETENTION_POLICY_NOT_EXISTS;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.RETENTION_PREVIEW_FAILED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 保留策略查询/更新、只读预检与清理任务摘要的真实 PostgreSQL 验证。
 */
public class CollectorRetentionPolicyPgTest extends CollectorRetentionPgTestSupport {

    /** 场景：只返回 8 个固定数据类且顺序固定。 */
    @Test
    public void policyListReturnsAllEightInFixedOrder() {
        List<RetentionPolicySnapshot> policies = policyService.getPolicyList();

        assertEquals(8, policies.size());
        assertEquals(List.of("sample_raw", "aggregate_second", "aggregate_minute", "aggregate_hour",
                "aggregate_day", "alarm", "heartbeat", "command"),
                policies.stream().map(RetentionPolicySnapshot::dataClass).toList());
        RetentionPolicySnapshot raw = policies.get(0);
        assertEquals("采集原始数据", raw.displayName());
        assertEquals("drop_partition", raw.action());
        assertEquals(3, raw.partitionLeadDays());
        assertTrue(raw.enabled());
        assertEquals(DEFAULT_RETENTION_DAYS, raw.retentionDays());
        System.out.println("[证据] 策略清单: " + policies);
    }

    /** 场景：更新只改四列，不动 data_class/action/last_run，也不删除任何数据。 */
    @Test
    public void updatePolicyOnlyTouchesAllowedColumns() throws Exception {
        insertAggregate("collector_aggregate_minute", 1, CUTOFF.minusDays(5));
        long id = policyId("aggregate_minute");

        policyService.updatePolicy(new RetentionPolicyUpdateCommand(id, 15, null, 0, "新的策略说明"));

        assertEquals(15, scalarLong("SELECT retention_days FROM public.collector_retention_policy"
                + " WHERE id = " + id));
        assertEquals(0, scalarLong("SELECT enabled FROM public.collector_retention_policy WHERE id = " + id));
        assertEquals("新的策略说明", scalarString("SELECT description FROM public.collector_retention_policy"
                + " WHERE id = " + id));
        assertEquals("delete_rows", scalarString("SELECT action FROM public.collector_retention_policy"
                + " WHERE id = " + id));
        assertEquals("aggregate_minute", scalarString("SELECT data_class FROM public.collector_retention_policy"
                + " WHERE id = " + id));
        assertEquals(0, scalarLong("SELECT COUNT(*) FROM public.collector_retention_policy"
                + " WHERE id = " + id + " AND (last_run_at IS NOT NULL OR last_run_result IS NOT NULL)"));
        // 保存策略不得触发任何 DELETE。
        assertEquals(1, countRows("collector_aggregate_minute"));

        // raw 允许修改分区预建天数。
        long rawId = policyId("sample_raw");
        policyService.updatePolicy(new RetentionPolicyUpdateCommand(rawId, null, 7, null, null));
        assertEquals(7, scalarLong("SELECT partition_lead_days FROM public.collector_retention_policy"
                + " WHERE id = " + rawId));
        assertEquals(DEFAULT_RETENTION_DAYS, scalarLong("SELECT retention_days FROM"
                + " public.collector_retention_policy WHERE id = " + rawId));
    }

    /** 场景：非法字段一律中文报错，且不做静默忽略。 */
    @Test
    public void updatePolicyRejectsIllegalFields() throws Exception {
        long rawId = policyId("sample_raw");
        long commandId = policyId("command");

        assertCode(RETENTION_POLICY_FIELD_INVALID, () ->
                policyService.updatePolicy(new RetentionPolicyUpdateCommand(rawId, 3651, null, null, null)));
        assertCode(RETENTION_POLICY_FIELD_INVALID, () ->
                policyService.updatePolicy(new RetentionPolicyUpdateCommand(rawId, -1, null, null, null)));
        assertCode(RETENTION_POLICY_FIELD_INVALID, () ->
                policyService.updatePolicy(new RetentionPolicyUpdateCommand(rawId, null, null, 2, null)));
        assertCode(RETENTION_POLICY_FIELD_INVALID, () ->
                policyService.updatePolicy(new RetentionPolicyUpdateCommand(rawId, null, null, null, "x".repeat(256))));
        assertCode(RETENTION_POLICY_FIELD_INVALID, () ->
                policyService.updatePolicy(new RetentionPolicyUpdateCommand(rawId, null, 31, null, null)));
        assertCode(RETENTION_POLICY_LEAD_DAYS_FORBIDDEN, () ->
                policyService.updatePolicy(new RetentionPolicyUpdateCommand(commandId, null, 3, null, null)));
        assertCode(RETENTION_POLICY_NOT_EXISTS, () ->
                policyService.updatePolicy(new RetentionPolicyUpdateCommand(999_999L, 10, null, null, null)));
        assertCode(RETENTION_POLICY_FIELD_INVALID, () ->
                policyService.updatePolicy(new RetentionPolicyUpdateCommand(null, 10, null, null, null)));

        // 校验失败不得留下任何修改。
        assertEquals(DEFAULT_RETENTION_DAYS, scalarLong("SELECT retention_days FROM"
                + " public.collector_retention_policy WHERE id = " + rawId));
        assertEquals(3, scalarLong("SELECT partition_lead_days FROM public.collector_retention_policy"
                + " WHERE id = " + rawId));
        assertEquals(1, scalarLong("SELECT enabled FROM public.collector_retention_policy WHERE id = " + rawId));

        ServiceException exception = assertThrows(ServiceException.class, () ->
                policyService.updatePolicy(new RetentionPolicyUpdateCommand(commandId, null, 3, null, null)));
        assertTrue(exception.getMessage(), exception.getMessage().contains("仅 sample_raw"));
    }

    /** 场景：预检只读、候选计数带安全条件、raw 候选只来自 catalog。 */
    @Test
    public void previewCountsCandidatesWithoutDeleting() throws Exception {
        insertAggregate("collector_aggregate_second", 1, CUTOFF.minusDays(1));
        insertAggregate("collector_aggregate_second", 1, CUTOFF);
        insertAggregate("collector_aggregate_minute", 1, CUTOFF.minusDays(2));
        insertAlarm(1, 301, 0, CUTOFF.minusDays(50), null);
        insertAlarm(2, 302, 2, CUTOFF.minusDays(50), CUTOFF.minusDays(40));
        insertHeartbeat(1, CUTOFF.minusDays(2));
        insertCommand(1, 0, CUTOFF.minusDays(40), null);
        insertCommand(2, 3, CUTOFF.minusDays(40), CUTOFF.minusDays(39));
        long secondBefore = countRows("collector_aggregate_second");
        long alarmBefore = countRows("collector_alarm");
        long commandBefore = countRows("collector_command");

        RetentionPreview preview = policyService.getPreview();

        assertEquals(BASE_DAY, preview.today());
        assertEquals(8, preview.classes().size());
        for (RetentionClassPreview clazz : preview.classes()) {
            assertEquals(CUTOFF, clazz.cutoff());
            assertFalse(clazz.dataClass(), clazz.skipped());
        }
        assertEquals(Long.valueOf(1), classOf(preview, "aggregate_second").candidateRows());
        assertEquals(Long.valueOf(1), classOf(preview, "aggregate_minute").candidateRows());
        assertEquals(Long.valueOf(0), classOf(preview, "aggregate_hour").candidateRows());
        assertEquals(Long.valueOf(0), classOf(preview, "aggregate_day").candidateRows());
        // 活动报警（cleared_at 为空）不计入候选。
        assertEquals(Long.valueOf(1), classOf(preview, "alarm").candidateRows());
        assertEquals(Long.valueOf(1), classOf(preview, "heartbeat").candidateRows());
        // status=0 的命令不计入候选。
        assertEquals(Long.valueOf(1), classOf(preview, "command").candidateRows());

        RetentionClassPreview raw = classOf(preview, "sample_raw");
        assertNull(raw.candidateRows());
        assertEquals(List.of("collector_sample_raw_p20261018", "collector_sample_raw_p20261019",
                        "collector_sample_raw_p20261020"),
                raw.candidatePartitions().stream().map(PartitionCandidate::partitionName).toList());
        assertTrue(raw.warnings().stream().anyMatch(warning -> warning.contains("DEFAULT")));
        assertEquals("FOR VALUES FROM ('2026-10-18 00:00:00') TO ('2026-10-19 00:00:00')",
                raw.candidatePartitions().get(0).bound());
        System.out.println("[证据] 预检结果: " + preview);

        // 预检不得删除任何行。
        assertEquals(secondBefore, countRows("collector_aggregate_second"));
        assertEquals(alarmBefore, countRows("collector_alarm"));
        assertEquals(commandBefore, countRows("collector_command"));
        assertEquals(0, advisoryLockCount(CollectorRetentionCleanupServiceImpl.CLEANUP_ADVISORY_LOCK_KEY));
    }

    /** 场景：预检 SQL 失败必须抛中文异常，绝不能返回伪造的 0。 */
    @Test
    public void previewFailureThrowsInsteadOfZero() throws Exception {
        executeSql("ALTER TABLE public.collector_heartbeat RENAME TO collector_heartbeat_missing");
        try {
            ServiceException exception = assertThrows(ServiceException.class, () -> cleanupService.preview());
            assertEquals(RETENTION_PREVIEW_FAILED.getCode(), exception.getCode());
            assertTrue(exception.getMessage(), exception.getMessage().contains("预检失败"));
            assertTrue(exception.getMessage(), exception.getMessage().contains("心跳"));
        } finally {
            executeSql("ALTER TABLE public.collector_heartbeat_missing RENAME TO collector_heartbeat");
        }
    }

    /** 场景：Job 摘要受控、成功返回摘要、失败抛 ServiceException 让 infra 记为失败。 */
    @Test
    public void jobSummaryAndFailureSemantics() throws Exception {
        CollectorRetentionCleanupJob job = newJob();
        insertAggregate("collector_aggregate_second", 1, CUTOFF.minusDays(1));

        String summary = job.execute("");
        System.out.println("[证据] Job 成功摘要: " + summary);
        assertTrue(summary, summary.contains("保留清理 " + BASE_DAY));
        assertTrue(summary, summary.contains("aggregate_second=成功1 行"));
        assertTrue(summary.length() < 1000);

        updatePolicyColumn("aggregate_second", "action", "'drop_partition'");
        ServiceException exception = assertThrows(ServiceException.class, () -> job.execute(""));
        assertEquals(RETENTION_CLEANUP_FAILED.getCode(), exception.getCode());
        // 异常消息 = 错误码文案 + 摘要，infra 会把根因消息写入 infra_job_log.result。
        assertTrue(exception.getMessage(), exception.getMessage().startsWith("数据保留清理失败："));
        assertTrue(exception.getMessage(), exception.getMessage().contains("aggregate_second=失败"));
        assertTrue(exception.getMessage().length() < 1000);
    }

    /** 场景：cleanup advisory lock 被占用时整体跳过、不算失败，Job 返回跳过摘要。 */
    @Test
    public void cleanupLockBusy_skipsWholeRun() throws Exception {
        try (Connection holder = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement lock = holder.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            lock.setLong(1, CollectorRetentionCleanupServiceImpl.CLEANUP_ADVISORY_LOCK_KEY);
            try (ResultSet rs = lock.executeQuery()) {
                assertTrue(rs.next() && rs.getBoolean(1));
            }
            insertHeartbeat(1, CUTOFF.minusDays(5));

            var report = cleanupService.cleanupAll();
            assertTrue(report.lockNotAcquired());
            assertTrue(report.results().isEmpty());
            assertFalse(report.hasFailure());
            assertEquals(1, countRows("collector_heartbeat"));

            String summary = newJob().execute(null);
            System.out.println("[证据] Job 锁占用摘要: " + summary);
            assertTrue(summary, summary.contains("已有保留清理任务在执行"));
            assertEquals(1, advisoryLockCount(CollectorRetentionCleanupServiceImpl.CLEANUP_ADVISORY_LOCK_KEY));
        }
        assertEquals(0, advisoryLockCount(CollectorRetentionCleanupServiceImpl.CLEANUP_ADVISORY_LOCK_KEY));
    }

    /** 场景：修复策略后再次执行可正常清理（证明失败不会留下脏状态）。 */
    @Test
    public void rerunAfterFixWorks() throws Exception {
        updatePolicyColumn("aggregate_second", "action", "'drop_partition'");
        insertAggregate("collector_aggregate_second", 1, CUTOFF.minusDays(1));
        assertTrue(cleanupService.cleanupAll().hasFailure());
        assertEquals(1, countRows("collector_aggregate_second"));

        updatePolicyColumn("aggregate_second", "action", "'delete_rows'");
        var report = cleanupService.cleanupAll();
        assertFalse(report.hasFailure());
        assertEquals(0, countRows("collector_aggregate_second"));
    }

    private CollectorRetentionCleanupJob newJob() throws Exception {
        CollectorRetentionCleanupJob job = new CollectorRetentionCleanupJob();
        Field field = CollectorRetentionCleanupJob.class.getDeclaredField("cleanupService");
        field.setAccessible(true);
        field.set(job, cleanupService);
        return job;
    }

    private RetentionClassPreview classOf(RetentionPreview preview, String dataClass) {
        return preview.classes().stream()
                .filter(clazz -> dataClass.equals(clazz.dataClass()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("预检结果缺少数据类：" + dataClass));
    }

    private void assertCode(cn.iocoder.yudao.framework.common.exception.ErrorCode expected,
                            Runnable action) {
        ServiceException exception = assertThrows(ServiceException.class, action::run);
        assertEquals(exception.getMessage(), expected.getCode(), exception.getCode());
        assertTrue(exception.getMessage(), exception.getMessage().matches(".*[\\u4e00-\\u9fa5].*"));
    }

}
