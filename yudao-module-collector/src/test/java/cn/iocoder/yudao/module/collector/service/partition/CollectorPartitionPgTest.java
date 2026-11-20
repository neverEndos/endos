package cn.iocoder.yudao.module.collector.service.partition;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.collector.job.partition.CollectorPartitionMaintainJob;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionService.DefaultPartitionConflict;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionService.PartitionFailure;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionService.PartitionMaintainResult;
import org.junit.Test;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.PARTITION_DEFAULT_PARTITION_INVALID;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.PARTITION_MAINTAIN_FAILED;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.PARTITION_POLICY_DUPLICATE;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.PARTITION_POLICY_INVALID;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.PARTITION_POLICY_NOT_EXISTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * raw 日分区自动预建的真实 PostgreSQL 验证，对应任务卡 13 项验收场景（11 由种子幂等测试与
 * dbctl 校验库共同覆盖）。
 *
 * <p><b>一次性运行方式</b>（必须使用临时库，破坏性测试会重建表）：</p>
 * <ol>
 *   <li>启动隔离实例，例如
 *   {@code docker run -d --name collector-partition-pg -p 55433:5432
 *   -e POSTGRES_PASSWORD=... postgres:17}；</li>
 *   <li>创建库名前缀为 {@code collector_partition_} 的临时库（如
 *   {@code collector_partition_verify}），严禁连接 {@code ruoyi-vue-pro}/{@code yudao_board}
 *   或任何正式库；</li>
 *   <li>设置环境变量 {@code COLLECTOR_TEST_PG_URL}、{@code COLLECTOR_TEST_PG_USER}、
 *   {@code COLLECTOR_TEST_PG_PASSWORD}，并显式设置
 *   {@code COLLECTOR_TEST_PG_ALLOW_DESTRUCTIVE=true}；未设置时用例按 Assume 跳过；</li>
 *   <li>执行
 *   {@code mvn -B -pl yudao-module-collector -am test -Dtest='CollectorPartition*Test'
 *   -Dsurefire.failIfNoSpecifiedTests=false}，跑完立即读取
 *   {@code yudao-module-collector/target/surefire-reports/*.xml} 的
 *   {@code tests/skipped/failures/errors} 作为证据。</li>
 * </ol>
 */
public class CollectorPartitionPgTest extends CollectorPartitionPgTestSupport {

    /** 场景 1：partition_lead_days=0 只建今天一个分区。 */
    @Test
    public void leadDaysZero_createsOnlyToday() throws Exception {
        setLeadDays(0);

        PartitionMaintainResult result = partitionService.maintainDailyPartitions();

        echoResult("场景1 leadDays=0", result);
        echoCatalog("场景1");
        assertEquals(BASE_DAY, result.from());
        assertEquals(BASE_DAY, result.to());
        assertEquals(0, result.leadDays());
        assertEquals(List.of(nameOf(BASE_DAY)), result.createdPartitions());
        assertEquals(0, result.skippedExistingCount());
        assertFalse(result.failed());
        assertEquals(List.of(DEFAULT_PARTITION, nameOf(BASE_DAY)), childPartitionNames());
        assertEquals("FOR VALUES FROM ('2026-11-20 00:00:00') TO ('2026-11-21 00:00:00')",
                partitionBound(nameOf(BASE_DAY)));
    }

    /** 场景 2：partition_lead_days=3 建今天 ~ +3 天共 4 个分区。 */
    @Test
    public void leadDaysThree_createsTodayThroughPlusThree() throws Exception {
        setLeadDays(3);

        PartitionMaintainResult result = partitionService.maintainDailyPartitions();

        echoResult("场景2 leadDays=3", result);
        echoCatalog("场景2");
        List<String> expected = List.of(nameOf(BASE_DAY), nameOf(BASE_DAY.plusDays(1)),
                nameOf(BASE_DAY.plusDays(2)), nameOf(BASE_DAY.plusDays(3)));
        assertEquals(BASE_DAY.plusDays(3), result.to());
        assertEquals(3, result.leadDays());
        assertEquals(expected, result.createdPartitions());
        assertEquals(expected.size(), dailyPartitionNames().size());

        List<String> expectedBounds = List.of(
                "FOR VALUES FROM ('2026-11-20 00:00:00') TO ('2026-11-21 00:00:00')",
                "FOR VALUES FROM ('2026-11-21 00:00:00') TO ('2026-11-22 00:00:00')",
                "FOR VALUES FROM ('2026-11-22 00:00:00') TO ('2026-11-23 00:00:00')",
                "FOR VALUES FROM ('2026-11-23 00:00:00') TO ('2026-11-24 00:00:00')");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expectedBounds.get(i), partitionBound(expected.get(i)));
        }
    }

    /** 场景 3：同一天重复执行，第二次不再创建，全部计入已存在。 */
    @Test
    public void secondRun_isIdempotent() throws Exception {
        setLeadDays(3);

        PartitionMaintainResult first = partitionService.maintainDailyPartitions();
        PartitionMaintainResult second = partitionService.maintainDailyPartitions();

        assertEquals(4, first.createdPartitions().size());
        assertEquals(List.of(), second.createdPartitions());
        assertEquals(4, second.skippedExistingCount());
        echoResult("场景3 第一次", first);
        echoResult("场景3 第二次", second);
        assertFalse(second.failed());
        assertEquals(4, dailyPartitionNames().size());
    }

    /** 场景 4：enabled=0 不得影响预建。 */
    @Test
    public void disabledPolicy_stillPrecreates() throws Exception {
        setLeadDays(1, 0);

        PartitionMaintainResult result = partitionService.maintainDailyPartitions();

        echoResult("场景4 enabled=0", result);
        assertEquals(List.of(nameOf(BASE_DAY), nameOf(BASE_DAY.plusDays(1))),
                result.createdPartitions());
        assertEquals(2, dailyPartitionNames().size());
    }

    /** 场景 5：分区边界取 Asia/Shanghai 自然日，UTC 尚未跨天时也要建上海的当天分区。 */
    @Test
    public void dateBoundary_followsShanghaiNotUtc() throws Exception {
        // 上海 2026-11-20 00:30 = UTC 2026-11-19 16:30，此时 UTC 仍是 19 日。
        setClockAtInstant(Instant.parse("2026-11-19T16:30:00Z"));
        setLeadDays(0);
        System.out.println("[场景5] JVM 默认时区=" + ZoneId.systemDefault()
                + "，当前 UTC=" + Instant.now());

        PartitionMaintainResult result = partitionService.maintainDailyPartitions();

        echoResult("场景5 上海 2026-11-20 00:30（UTC 16:30）", result);
        echoCatalog("场景5");
        assertEquals(BASE_DAY, result.from());
        assertEquals(List.of(nameOf(BASE_DAY)), result.createdPartitions());
        assertFalse("UTC 的 19 日分区不应被创建", relationExists(nameOf(PREVIOUS_DAY)));
        assertEquals("FOR VALUES FROM ('2026-11-20 00:00:00') TO ('2026-11-21 00:00:00')",
                partitionBound(nameOf(BASE_DAY)));
    }

    /** 场景 6：同范围已被其他命名的分区覆盖时跳过，不重复建 _p 分区。 */
    @Test
    public void sameRangeDifferentName_isSkipped() throws Exception {
        String custom = "collector_sample_raw_custom_20261120";
        createCustomPartition(custom, BASE_DAY);
        setLeadDays(0);

        PartitionMaintainResult result = partitionService.maintainDailyPartitions();

        echoResult("场景6 自定义同名范围分区", result);
        echoCatalog("场景6");
        assertEquals(List.of(), result.createdPartitions());
        assertEquals(1, result.skippedExistingCount());
        assertFalse(relationExists(nameOf(BASE_DAY)));
        assertTrue(relationExists(custom));
    }

    /** 场景 7：DEFAULT 分区存在但无冲突数据时正常建分区。 */
    @Test
    public void defaultPartitionWithoutData_creates() throws Exception {
        setLeadDays(0);

        PartitionMaintainResult result = partitionService.maintainDailyPartitions();

        echoResult("场景7 默认分区无数据", result);
        assertEquals(List.of(nameOf(BASE_DAY)), result.createdPartitions());
        assertEquals(List.of(), result.defaultConflicts());
        assertEquals(0, countRows(DEFAULT_PARTITION));
    }

    /** 场景 8：DEFAULT 分区有该日数据时报告冲突，且该行保持原样、不建分区。 */
    @Test
    public void defaultPartitionWithData_reportsConflictAndKeepsRow() throws Exception {
        insertRaw(BASE_NOON, 42.5D);
        List<String> before = defaultPartitionRows();
        assertEquals(1, before.size());
        setLeadDays(0);

        PartitionMaintainResult result = partitionService.maintainDailyPartitions();

        echoResult("场景8 默认分区有冲突数据", result);
        System.out.println("[证据] 场景8 默认分区行 before=" + before
                + " after=" + defaultPartitionRows());
        echoCatalog("场景8");
        assertEquals(List.of(), result.createdPartitions());
        assertEquals(1, result.defaultConflicts().size());
        DefaultPartitionConflict conflict = result.defaultConflicts().get(0);
        assertEquals(BASE_DAY, conflict.day());
        assertEquals("2026-11-20 00:00:00~2026-11-21 00:00:00", conflict.range());
        assertTrue("冲突原因应包含受控迁移提示，实际：" + conflict.reason(),
                conflict.reason().contains("默认分区存在冲突数据，需要受控迁移"));
        assertTrue(result.failed());
        assertFalse(relationExists(nameOf(BASE_DAY)));
        // 数据仍在 DEFAULT 中，count 与内容逐列一致（未被删除或移动）。
        assertEquals(before, defaultPartitionRows());
    }

    /** 场景 9①：策略缺失抛 PARTITION_POLICY_NOT_EXISTS，且不创建任何分区。 */
    @Test
    public void missingPolicy_throwsAndCreatesNothing() throws Exception {
        clearPolicy();

        ServiceException exception = assertThrows(ServiceException.class,
                () -> partitionService.maintainDailyPartitions());

        assertEquals(PARTITION_POLICY_NOT_EXISTS.getCode(), exception.getCode());
        assertEquals(List.of(), dailyPartitionNames());
    }

    /** 场景 9②：策略不唯一抛 PARTITION_POLICY_DUPLICATE。 */
    @Test
    public void duplicatePolicy_throws() throws Exception {
        insertPolicy("sample_raw", 1, 1, 0);
        insertPolicy("sample_raw", 2, 1, 0);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> partitionService.maintainDailyPartitions());

        assertEquals(PARTITION_POLICY_DUPLICATE.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("2"));
        assertEquals(List.of(), dailyPartitionNames());
    }

    /** 场景 9③：partition_lead_days 越界（31）抛 PARTITION_POLICY_INVALID。 */
    @Test
    public void outOfRangeLeadDays_throws() throws Exception {
        setLeadDays(31);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> partitionService.maintainDailyPartitions());

        assertEquals(PARTITION_POLICY_INVALID.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("31"));
        assertEquals(List.of(), dailyPartitionNames());
    }

    /** 场景 10①：其他连接持有 advisory lock 时整体跳过，摘要是「已有分区维护任务在执行」，且不残留锁。 */
    @Test
    public void advisoryLockHeldByOtherSession_skipsAndLeavesNoLock() throws Exception {
        setLeadDays(0);
        try (Connection holder = DriverManager.getConnection(URL, USER, PASSWORD)) {
            try (Statement statement = holder.createStatement()) {
                statement.execute("SELECT pg_advisory_lock(" + CollectorPartitionServiceImpl.ADVISORY_LOCK_KEY + ")");
            }

            PartitionMaintainResult result = partitionService.maintainDailyPartitions();

            echoResult("场景10① 锁被其他会话占用", result);
            System.out.println("[证据] 场景10① 持有者占用期间 pg_locks 中该 key 数量=" + advisoryLockCount());
            assertTrue(result.lockNotAcquired());
            assertEquals(List.of(), result.createdPartitions());
            assertEquals(0, result.skippedExistingCount());
            assertFalse(result.failed());
            // 只有持有者自己的一把锁；被测服务没有额外残留。
            assertEquals(1, advisoryLockCount());

            String jobMessage = executeJob(partitionService);
            assertTrue("任务摘要应说明跳过，实际：" + jobMessage,
                    jobMessage.contains("已有分区维护任务在执行"));
            assertTrue(jobMessage.contains("本次跳过"));

            try (Statement statement = holder.createStatement()) {
                statement.execute("SELECT pg_advisory_unlock("
                        + CollectorPartitionServiceImpl.ADVISORY_LOCK_KEY + ")");
            }
        }

        assertEquals("持有者释放后不应有任何遗留锁", 0, advisoryLockCount());
        System.out.println("[证据] 场景10① 释放后 pg_locks 中该 key 数量=0");
        // 锁释放后再次执行即可成功，证明上次调用没有破坏后续维护。
        assertEquals(List.of(nameOf(BASE_DAY)),
                partitionService.maintainDailyPartitions().createdPartitions());
    }

    /** 场景 10②：两个线程并发执行，分区最终不重复且结构正确。 */
    @Test
    public void concurrentRuns_doNotDuplicatePartitions() throws Exception {
        setLeadDays(3);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<PartitionMaintainResult>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return partitionService.maintainDailyPartitions();
                }));
            }
            start.countDown();

            int totalCreated = 0;
            Set<String> union = new HashSet<>();
            boolean anyLockSkipped = false;
            for (Future<PartitionMaintainResult> future : futures) {
                PartitionMaintainResult result = future.get(60, TimeUnit.SECONDS);
                totalCreated += result.createdPartitions().size();
                for (String name : result.createdPartitions()) {
                    assertTrue("分区被重复创建：" + name, union.add(name));
                }
                anyLockSkipped |= result.lockNotAcquired();
            }

            assertEquals("并发两次执行合计只应创建 4 个分区（另一次被锁跳过或全部已存在）",
                    4, totalCreated);
            assertEquals(4, union.size());
            assertEquals(4, dailyPartitionNames().size());
            for (LocalDate day = BASE_DAY; !day.isAfter(BASE_DAY.plusDays(3)); day = day.plusDays(1)) {
                assertEquals("FOR VALUES FROM ('" + day + " 00:00:00') TO ('" + day.plusDays(1) + " 00:00:00')",
                        partitionBound(nameOf(day)));
            }
            System.out.println("[场景10②] 是否发生抢锁跳过=" + anyLockSkipped
                    + "，分区=" + dailyPartitionNames());
        } finally {
            pool.shutdownNow();
        }
        assertEquals("并发结束后不应有遗留 advisory lock", 0, advisoryLockCount());
    }

    /** 场景 12：不创建历史分区，且既有历史分区数据行数不变。 */
    @Test
    public void historicalPartitionAndData_areUntouched() throws Exception {
        createDailyPartition(PREVIOUS_DAY);
        insertRaw(PREVIOUS_DAY.atTime(8, 0), 11.0D);
        assertEquals(1, countRows(nameOf(PREVIOUS_DAY)));
        setLeadDays(0);

        PartitionMaintainResult result = partitionService.maintainDailyPartitions();

        echoResult("场景12 历史分区保留", result);
        echoCatalog("场景12");
        System.out.println("[证据] 场景12 历史分区行数=" + countRows(nameOf(PREVIOUS_DAY))
                + "，父表总行数=" + countRows(PARENT));
        assertEquals(List.of(nameOf(BASE_DAY)), result.createdPartitions());
        // 历史分区与其数据原样保留，也没有被本次创建任何更早的分区覆盖。
        assertTrue(relationExists(nameOf(PREVIOUS_DAY)));
        assertEquals(1, countRows(nameOf(PREVIOUS_DAY)));
        assertEquals(1, countRows(PARENT));
        assertFalse(relationExists(nameOf(BASE_DAY.minusDays(2))));
        assertEquals(List.of(nameOf(PREVIOUS_DAY), nameOf(BASE_DAY)), dailyPartitionNames());
    }

    /** 场景 13 的补充证据：DDL 失败项应为空，避免把失败当成功。 */
    @Test
    public void maintain_hasNoDdlFailuresInNormalPath() throws Exception {
        setLeadDays(2);

        PartitionMaintainResult result = partitionService.maintainDailyPartitions();

        List<PartitionFailure> failures = result.ddlFailures();
        assertTrue("正常路径不应有 DDL 失败：" + failures, failures.isEmpty());
        assertNotNull(result.createdPartitions());
    }

    /**
     * M1：FROM (MINVALUE) 是无引号边界，必须解析为 −∞ 参与覆盖判断，
     * 不能因不可解析而误判「未覆盖」再撞 would overlap partition。
     */
    @Test
    public void minValueBound_coversDay_isSkipped() throws Exception {
        executeSql("CREATE TABLE public.collector_sample_raw_p_min PARTITION OF public.collector_sample_raw"
                + " FOR VALUES FROM (MINVALUE) TO ('2026-11-21 00:00:00')");
        setLeadDays(0);
        System.out.println("[证据] M1 MINVALUE 边界原文=" + partitionBound("collector_sample_raw_p_min"));

        PartitionMaintainResult result = partitionService.maintainDailyPartitions();

        echoResult("M1 MINVALUE 覆盖当天", result);
        assertEquals(List.of(), result.createdPartitions());
        assertEquals(1, result.skippedExistingCount());
        assertFalse("MINVALUE 覆盖不应产生失败：" + result.ddlFailures(), result.failed());
        assertFalse("不应再创建同范围分区", relationExists(nameOf(BASE_DAY)));
    }

    /** M1：TO (MAXVALUE) 是无引号边界，必须解析为 +∞ 参与覆盖判断。 */
    @Test
    public void maxValueBound_coversDay_isSkipped() throws Exception {
        executeSql("CREATE TABLE public.collector_sample_raw_p_max PARTITION OF public.collector_sample_raw"
                + " FOR VALUES FROM ('2026-11-20 00:00:00') TO (MAXVALUE)");
        setLeadDays(0);
        System.out.println("[证据] M1 MAXVALUE 边界原文=" + partitionBound("collector_sample_raw_p_max"));

        PartitionMaintainResult result = partitionService.maintainDailyPartitions();

        echoResult("M1 MAXVALUE 覆盖当天", result);
        assertEquals(List.of(), result.createdPartitions());
        assertEquals(1, result.skippedExistingCount());
        assertFalse("MAXVALUE 覆盖不应产生失败：" + result.ddlFailures(), result.failed());
        assertFalse("不应再创建同范围分区", relationExists(nameOf(BASE_DAY)));
    }

    /** M2①：同名对象是普通表（非分区）时计入 ddlFailures，中文原因可区分且不误建其他对象。 */
    @Test
    public void sameNamePlainTable_isReportedAsFailure() throws Exception {
        String partitionName = nameOf(BASE_DAY);
        executeSql("CREATE TABLE public." + partitionName + " (collect_time timestamp(3))");
        setLeadDays(0);

        PartitionMaintainResult result = partitionService.maintainDailyPartitions();

        echoResult("M2① 同名普通表", result);
        assertEquals(List.of(), result.createdPartitions());
        assertEquals(0, result.skippedExistingCount());
        assertEquals(1, result.ddlFailures().size());
        String reason = result.ddlFailures().get(0).reason();
        System.out.println("[证据] M2① 失败原因=" + reason);
        assertTrue("原因应为中文且指明不是分区，实际：" + reason, reason.contains("不是分区"));
        assertTrue("不应误报边界问题，实际：" + reason, !reason.contains("无法识别"));
        assertTrue(result.failed());
        // 同名普通表仍在，且没有创建任何日分区。
        assertEquals(List.of(DEFAULT_PARTITION), childPartitionNames());
        assertEquals(List.of(), dailyPartitionNames());
        assertTrue(relationExists(partitionName));
        assertNull("普通表没有分区边界", partitionBound(partitionName));
    }

    /** M2②：同名对象是其他父表的分区时计入 ddlFailures，原因指明实际父表。 */
    @Test
    public void sameNamePartitionOfOtherParent_isReportedAsFailure() throws Exception {
        String partitionName = nameOf(BASE_DAY);
        executeSql("CREATE TABLE public.collector_sample_raw_other_parent ("
                + " collect_time timestamp(3) NOT NULL) PARTITION BY RANGE (collect_time)");
        executeSql("CREATE TABLE public." + partitionName
                + " PARTITION OF public.collector_sample_raw_other_parent"
                + " FOR VALUES FROM ('2026-11-20 00:00:00') TO ('2026-11-21 00:00:00')");
        setLeadDays(0);

        PartitionMaintainResult result = partitionService.maintainDailyPartitions();

        echoResult("M2② 同名其他父表分区", result);
        assertEquals(List.of(), result.createdPartitions());
        assertEquals(1, result.ddlFailures().size());
        String reason = result.ddlFailures().get(0).reason();
        System.out.println("[证据] M2② 失败原因=" + reason);
        assertTrue("原因应指明其他父表，实际：" + reason,
                reason.contains("其他父表") && reason.contains("collector_sample_raw_other_parent"));
        assertTrue(result.failed());
        // 本表仍只有 DEFAULT，没有被误建。
        assertEquals(List.of(DEFAULT_PARTITION), childPartitionNames());
        assertEquals(List.of(), dailyPartitionNames());
    }

    /** M2③：同名且是本表分区，但边界覆盖两天（与期望不一致），必须失败且原因可区分。 */
    @Test
    public void sameNamePartitionWithWiderBound_isReportedAsFailure() throws Exception {
        String partitionName = nameOf(BASE_DAY);
        executeSql("CREATE TABLE public." + partitionName + " PARTITION OF public.collector_sample_raw"
                + " FOR VALUES FROM ('2026-11-20 00:00:00') TO ('2026-11-22 00:00:00')");
        setLeadDays(0);

        PartitionMaintainResult result = partitionService.maintainDailyPartitions();

        echoResult("M2③ 同名分区边界过宽", result);
        assertEquals(List.of(), result.createdPartitions());
        assertEquals(0, result.skippedExistingCount());
        assertEquals(1, result.ddlFailures().size());
        String reason = result.ddlFailures().get(0).reason();
        System.out.println("[证据] M2③ 失败原因=" + reason);
        assertTrue("原因应为边界不一致，实际：" + reason, reason.contains("与期望"));
        assertTrue("已解析边界不应提示无法识别，实际：" + reason, !reason.contains("无法识别"));
        assertTrue(result.failed());
        assertEquals(List.of(DEFAULT_PARTITION, partitionName), childPartitionNames());
        // 原分区保持不变，没有被重建或新增。
        assertEquals("FOR VALUES FROM ('2026-11-20 00:00:00') TO ('2026-11-22 00:00:00')",
                partitionBound(partitionName));
    }

    /** M2：Job 失败路径必须抛异常，且消息包含中文摘要（默认分区冲突场景）。 */
    @Test
    public void jobOnDefaultConflict_throwsWithChineseSummary() throws Exception {
        insertRaw(BASE_NOON, 7.0D);
        setLeadDays(0);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> executeJob(partitionService));

        System.out.println("[证据] M2 Job 失败摘要=" + exception.getMessage());
        assertEquals(PARTITION_MAINTAIN_FAILED.getCode(), exception.getCode());
        assertTrue("异常消息应包含失败摘要，实际：" + exception.getMessage(),
                exception.getMessage().contains("分区维护失败"));
        assertTrue("摘要应保留关键中文冲突提示，实际：" + exception.getMessage(),
                exception.getMessage().contains("默认分区存在冲突数据，需要受控迁移"));
        assertTrue("摘要长度应远小于 infra_job_log.result 上限，实际：" + exception.getMessage().length(),
                exception.getMessage().length() < 1000);
    }

    /** M2：策略异常抛 ServiceException 后 advisory lock 必须已释放（另一连接可立即获取）。 */
    @Test
    public void policyException_releasesAdvisoryLock() throws Exception {
        clearPolicy();

        ServiceException exception = assertThrows(ServiceException.class,
                () -> partitionService.maintainDailyPartitions());

        assertEquals(PARTITION_POLICY_NOT_EXISTS.getCode(), exception.getCode());
        assertEquals("抛出业务异常后不得残留 advisory lock", 0, advisoryLockCount());
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT pg_try_advisory_lock(" + CollectorPartitionServiceImpl.ADVISORY_LOCK_KEY + ")")) {
            assertTrue("另一连接应能立即获得该锁", rs.next() && rs.getBoolean(1));
            System.out.println("[证据] M2 异常后另一连接 pg_try_advisory_lock=true");
        }
        // 连接关闭即释放；再次确认 pg_locks 中该 key 计数为 0。
        assertEquals(0, advisoryLockCount());
    }

    /** Minor 2：partition_lead_days 为 NULL 时必须抛策略不合法，不能静默当 0。 */
    @Test
    public void nullLeadDays_throwsPolicyInvalid() throws Exception {
        executeSql("ALTER TABLE public.collector_retention_policy"
                + " ALTER COLUMN partition_lead_days DROP NOT NULL");
        executeSql("INSERT INTO public.collector_retention_policy"
                + " (data_class, retention_days, partition_lead_days, enabled, deleted)"
                + " VALUES ('sample_raw', 30, NULL, 1, 0)");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> partitionService.maintainDailyPartitions());

        System.out.println("[证据] Minor2 NULL 策略异常=" + exception.getMessage());
        assertEquals(PARTITION_POLICY_INVALID.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("NULL"));
        assertEquals(List.of(), dailyPartitionNames());
    }

    /** Minor 4：同名对象不是本表 DEFAULT 分区时必须显式失败，不能当成「默认分区不存在」。 */
    @Test
    public void invalidDefaultPartitionObject_failsExplicitly() throws Exception {
        executeSql("DROP TABLE public.collector_sample_raw_default");
        executeSql("CREATE TABLE public.collector_sample_raw_default (foo integer)");
        setLeadDays(0);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> partitionService.maintainDailyPartitions());

        System.out.println("[证据] Minor4 非法默认分区对象异常=" + exception.getMessage());
        assertEquals(PARTITION_DEFAULT_PARTITION_INVALID.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("不是分区表"));
        assertEquals(List.of(), dailyPartitionNames());
        assertEquals("失败路径同样不得残留 advisory lock", 0, advisoryLockCount());
    }

    private static String nameOf(LocalDate day) {
        return PARTITION_PREFIX + day.toString().replace("-", "");
    }

    /** 通过反射注入 service 执行 Job，验证面向调度器的中文摘要，不额外扩展生产 API。 */
    private static String executeJob(CollectorPartitionService service) throws Exception {
        CollectorPartitionMaintainJob job = new CollectorPartitionMaintainJob();
        Field field = CollectorPartitionMaintainJob.class.getDeclaredField("partitionService");
        field.setAccessible(true);
        field.set(job, service);
        return job.execute("");
    }

    /** 保证测试库确实是临时库，避免误连正式库。 */
    @Test
    public void testDatabase_isIsolatedTemporaryDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT current_database()")) {
            rs.next();
            assertTrue("当前测试库名必须以 collector_partition_ 开头，实际：" + rs.getString(1),
                    rs.getString(1).startsWith("collector_partition_"));
        }
    }

}
