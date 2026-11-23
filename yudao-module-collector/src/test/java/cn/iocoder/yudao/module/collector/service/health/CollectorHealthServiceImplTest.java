package cn.iocoder.yudao.module.collector.service.health;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.collector.controller.admin.health.vo.CollectorHealthOverviewRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.health.vo.CollectorHealthSummaryRespVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthAggregateAbnormalRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthAggregateSummaryRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthAlarmRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthAlarmSummaryRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthDeviceRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthHeartbeatRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthPartitionRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthRawPolicyRow;
import cn.iocoder.yudao.module.collector.dal.mysql.health.CollectorHealthMapper;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionPolicyService;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 采集运行详情只读服务单测：重点覆盖空数据场景（不伪造正常态）、报警级别过滤、分区缺失日期与固定 SQL 条数。
 */
public class CollectorHealthServiceImplTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    /** 2026-11-23 10:00 Asia/Shanghai。 */
    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-23T02:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    private CollectorHealthServiceImpl service;
    private FakeMapper mapper;
    private FakeRetentionPolicyService retentionService;

    @Before
    public void setUp() {
        mapper = new FakeMapper();
        retentionService = new FakeRetentionPolicyService();
        service = new CollectorHealthServiceImpl(mapper, retentionService);
        service.setClock(Clock.fixed(FIXED_INSTANT, SHANGHAI));
    }

    @Test
    public void summary_withoutHeartbeatOrStatusRow_returnsNullsInsteadOfFakeNormal() {
        mapper.device = deviceRow(null, null, null, null, null);
        mapper.heartbeat = null;
        mapper.alarmSummary = alarmSummary(0, 0);

        CollectorHealthSummaryRespVO summary = service.getSummary(7L);

        assertNull("无心跳必须返回 null，不能伪造 running", summary.getProcess());
        assertEquals(7L, summary.getDevice().getDeviceId().longValue());
        assertNull("无设备状态行时 online 必须为 null", summary.getDevice().getOnline());
        assertNull(summary.getDevice().getConsecutiveFailures());
        assertNull("pollIntervalMs 关联不到时必须为 null", summary.getDevice().getPollIntervalMs());
        assertNull(summary.getDevice().getLastSuccessAt());
        assertEquals(Integer.valueOf(0), summary.getAlarm().getActiveCount());
        assertEquals("无活动报警时 highestLevel 取 0", Integer.valueOf(0), summary.getAlarm().getHighestLevel());
        assertEquals(FIXED_INSTANT.toEpochMilli(), summary.getServerTime().longValue());
        assertEquals("summary 固定 3 条 SQL", 3, mapper.sqlCount);
        assertEquals("summary 不应读取保留策略", 0, retentionService.sqlCount);
    }

    @Test
    public void summary_deviceMissing_throwsDeviceNotExists() {
        mapper.device = null;
        ServiceException exception = assertThrows(ServiceException.class, () -> service.getSummary(404L));
        assertEquals(1_023_001_000, exception.getCode().intValue());
    }

    @Test
    public void summary_mapsHeartbeatAndDeviceFieldsVerbatim() {
        mapper.device = deviceRow(1, 12_000, LocalDateTime.of(2026, 11, 23, 9, 59),
                LocalDateTime.of(2026, 11, 23, 9, 58), 42);
        mapper.heartbeat = heartbeat("collector-1", "degraded", 8);
        mapper.alarmSummary = alarmSummary(2, 3);

        CollectorHealthSummaryRespVO summary = service.getSummary(7L);

        assertEquals("collector-1", summary.getProcess().getInstanceId());
        assertEquals("degraded", summary.getProcess().getStatus());
        assertEpoch("2026-11-23T01:59:30Z", summary.getProcess().getHeartbeatTime());
        assertEquals(Integer.valueOf(8), summary.getProcess().getWriteLagMs());
        assertEquals(Integer.valueOf(1), summary.getProcess().getDeviceTotal());
        assertEquals(Integer.valueOf(0), summary.getProcess().getDeviceOnline());
        assertEquals("1.0.0", summary.getProcess().getCollectorVersion());
        assertTrue(summary.getDevice().getOnline());
        assertEquals(Integer.valueOf(12_000), summary.getDevice().getPollIntervalMs());
        assertEquals(Integer.valueOf(42), summary.getDevice().getLastLatencyMs());
        assertEpoch("2026-11-23T01:59:00Z", summary.getDevice().getLastPollAt());
        assertEpoch("2026-11-23T01:58:00Z", summary.getDevice().getLastSuccessAt());
        assertEquals(Integer.valueOf(2), summary.getAlarm().getActiveCount());
        assertEquals(Integer.valueOf(3), summary.getAlarm().getHighestLevel());
    }

    @Test
    public void overview_emptyEnvironment_doesNotFakeHealthyState() {
        mapper.device = deviceRow(null, null, null, null, null);
        mapper.heartbeat = null;
        mapper.alarmSummary = alarmSummary(0, 0);
        mapper.alarms = List.of();
        mapper.aggregate = List.of();
        mapper.aggregateAbnormal = List.of();
        mapper.rawPolicies = List.of();
        mapper.partitions = List.of();

        CollectorHealthOverviewRespVO overview = service.getOverview(7L);

        assertNull(overview.getProcess());
        assertEquals(Integer.valueOf(0), overview.getAlarmTotal());
        assertTrue(overview.getAlarms().isEmpty());
        assertEquals("4 个聚合类型必须全部出现", 4, overview.getAggregate().size());
        for (CollectorHealthOverviewRespVO.AggregateSummary summary : overview.getAggregate()) {
            assertEquals(Integer.valueOf(0), summary.getRowCount());
            assertFalse("无水位行时不能标记为已初始化", summary.getInitialized());
            assertNull(summary.getMaxRetryCount());
        }
        assertEquals(CollectorHealthServiceImpl.RAW_PARTITION_STATUS_POLICY_MISSING,
                overview.getRawPartition().getStatus());
        assertNull("策略缺失时绝不套用默认 leadDays", overview.getRawPartition().getLeadDays());
        assertEquals(Integer.valueOf(0), overview.getRawPartition().getExpectedCount());
        assertTrue(overview.getRawPartition().getMissingDates().isEmpty());
        assertEquals("overview 固定 9 条 SQL（mapper 8 条 + 保留策略 1 条）",
                9, mapper.sqlCount + retentionService.sqlCount);
    }

    @Test
    public void overview_activeAlarmTotalIsFullCountAndDetailsOrdered() {
        mapper.device = deviceRow(1, 5_000, null, null, null);
        mapper.alarmSummary = alarmSummary(25, 3);
        mapper.alarms = List.of(alarmRow(103L, 3, "channel-c"), alarmRow(101L, 2, "channel-a"));

        CollectorHealthOverviewRespVO overview = service.getOverview(7L);

        assertEquals(Integer.valueOf(25), overview.getAlarmTotal());
        assertEquals(Integer.valueOf(25), overview.getAlarm().getActiveCount());
        assertEquals(2, overview.getAlarms().size());
        assertEquals(Integer.valueOf(3), overview.getAlarms().get(0).getLevel());
        assertEquals("channel-c", overview.getAlarms().get(0).getChannelName());
        assertEquals("测试设备", overview.getAlarms().get(0).getDeviceName());
    }

    @Test
    public void overview_aggregateFillsMissingTypesInFixedOrder() {
        CollectorHealthAggregateSummaryRow second = aggregateSummary("second", 3, 1, 0);
        mapper.device = deviceRow(1, null, null, null, null);
        mapper.aggregate = List.of(second);

        CollectorHealthOverviewRespVO overview = service.getOverview(7L);

        List<CollectorHealthOverviewRespVO.AggregateSummary> items = overview.getAggregate();
        assertEquals(List.of("second", "minute", "hour", "day"),
                List.of(items.get(0).getAggType(), items.get(1).getAggType(),
                        items.get(2).getAggType(), items.get(3).getAggType()));
        assertTrue(items.get(0).getInitialized());
        assertEquals(Integer.valueOf(3), items.get(0).getRowCount());
        assertEquals(Integer.valueOf(1), items.get(0).getAbnormalCount());
        assertEquals(Integer.valueOf(0), items.get(0).getMaxRetryCount());
        assertFalse(items.get(1).getInitialized());
    }

    @Test
    public void overview_aggregateAbnormalKeepsDatabaseValues() {
        CollectorHealthAggregateAbnormalRow row = new CollectorHealthAggregateAbnormalRow();
        row.setAggType("hour");
        row.setChannelId(11L);
        row.setDeviceId(7L);
        row.setStatus(2);
        row.setRetryCount(0);
        row.setLastError(null);
        mapper.device = deviceRow(1, null, null, null, null);
        mapper.aggregateAbnormal = List.of(row);

        CollectorHealthOverviewRespVO overview = service.getOverview(7L);

        assertEquals(1, overview.getAggregateAbnormal().size());
        CollectorHealthOverviewRespVO.AggregateAbnormal item = overview.getAggregateAbnormal().get(0);
        assertEquals(Integer.valueOf(2), item.getStatus());
        assertEquals("retry_count 恒 0 原样返回", Integer.valueOf(0), item.getRetryCount());
        assertNull("不得伪造 last_error 文案", item.getLastError());
    }

    @Test
    public void overview_timeFieldsUseShanghaiEpochAndStableJsonWhenJvmDefaultsToUtc() throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            mapper.device = deviceRow(1, 5_000, LocalDateTime.of(2026, 11, 23, 9, 59),
                    LocalDateTime.of(2026, 11, 23, 9, 58), 42);
            mapper.heartbeat = heartbeat("collector-1", "running", 8);
            mapper.alarms = List.of(alarmRow(101L, 2, "channel-a"));

            CollectorHealthAggregateSummaryRow aggregate = aggregateSummary("second", 1, 0, 0);
            aggregate.setLastSuccessTime(LocalDateTime.of(2026, 11, 23, 9, 30));
            aggregate.setMinWatermarkTime(LocalDateTime.of(2026, 11, 23, 8, 0));
            aggregate.setMaxWatermarkTime(LocalDateTime.of(2026, 11, 23, 9, 0));
            mapper.aggregate = List.of(aggregate);
            CollectorHealthAggregateAbnormalRow abnormal = new CollectorHealthAggregateAbnormalRow();
            abnormal.setAggType("second");
            abnormal.setChannelId(101L);
            abnormal.setDeviceId(7L);
            abnormal.setWatermarkTime(LocalDateTime.of(2026, 11, 23, 9, 15));
            mapper.aggregateAbnormal = List.of(abnormal);
            retentionService.lastRunAt = LocalDateTime.of(2026, 11, 23, 9, 45);

            CollectorHealthOverviewRespVO overview = service.getOverview(7L);

            assertEpoch("2026-11-23T02:00:00Z", overview.getServerTime());
            assertEpoch("2026-11-23T01:59:30Z", overview.getProcess().getHeartbeatTime());
            assertEpoch("2026-11-23T01:59:00Z", overview.getDevice().getLastPollAt());
            assertEpoch("2026-11-23T01:58:00Z", overview.getDevice().getLastSuccessAt());
            assertEpoch("2026-11-23T00:00:00Z", overview.getAlarms().get(0).getSince());
            assertEpoch("2026-11-23T01:59:00Z", overview.getAlarms().get(0).getLastSampleTime());
            assertEpoch("2026-11-23T01:30:00Z", overview.getAggregate().get(0).getLastSuccessTime());
            assertEpoch("2026-11-23T00:00:00Z", overview.getAggregate().get(0).getMinWatermarkTime());
            assertEpoch("2026-11-23T01:00:00Z", overview.getAggregate().get(0).getMaxWatermarkTime());
            assertEpoch("2026-11-23T01:15:00Z", overview.getAggregateAbnormal().get(0).getWatermarkTime());
            assertEpoch("2026-11-23T01:45:00Z", overview.getRetention().get(0).getLastRunAt());

            // 序列化结果同样是固定数字，不再经过依赖 JVM 默认时区的 LocalDateTime 序列化器。
            JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(overview));
            assertJsonEpoch(json, "/serverTime", "2026-11-23T02:00:00Z");
            assertJsonEpoch(json, "/process/heartbeatTime", "2026-11-23T01:59:30Z");
            assertJsonEpoch(json, "/device/lastPollAt", "2026-11-23T01:59:00Z");
            assertJsonEpoch(json, "/device/lastSuccessAt", "2026-11-23T01:58:00Z");
            assertJsonEpoch(json, "/alarms/0/since", "2026-11-23T00:00:00Z");
            assertJsonEpoch(json, "/alarms/0/lastSampleTime", "2026-11-23T01:59:00Z");
            assertJsonEpoch(json, "/aggregate/0/lastSuccessTime", "2026-11-23T01:30:00Z");
            assertJsonEpoch(json, "/aggregate/0/minWatermarkTime", "2026-11-23T00:00:00Z");
            assertJsonEpoch(json, "/aggregate/0/maxWatermarkTime", "2026-11-23T01:00:00Z");
            assertJsonEpoch(json, "/aggregateAbnormal/0/watermarkTime", "2026-11-23T01:15:00Z");
            assertJsonEpoch(json, "/retention/0/lastRunAt", "2026-11-23T01:45:00Z");
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    public void overview_rawPartition_duplicatePolicyIsExplicit() {
        mapper.device = deviceRow(1, null, null, null, null);
        mapper.rawPolicies = List.of(rawPolicy(1L, 2), rawPolicy(2L, 3));

        CollectorHealthOverviewRespVO overview = service.getOverview(7L);

        assertEquals(CollectorHealthServiceImpl.RAW_PARTITION_STATUS_POLICY_DUPLICATE,
                overview.getRawPartition().getStatus());
        assertNull(overview.getRawPartition().getLeadDays());
        assertNull(overview.getRawPartition().getExpectedTo());
    }

    @Test
    public void overview_rawPartition_invalidLeadDaysIsExplicit() {
        mapper.device = deviceRow(1, null, null, null, null);
        mapper.rawPolicies = List.of(rawPolicy(1L, -1));

        CollectorHealthOverviewRespVO overview = service.getOverview(7L);

        assertEquals(CollectorHealthServiceImpl.RAW_PARTITION_STATUS_POLICY_INVALID,
                overview.getRawPartition().getStatus());
        assertEquals(Integer.valueOf(-1), overview.getRawPartition().getLeadDays());
        assertEquals(Integer.valueOf(0), overview.getRawPartition().getExpectedCount());
        assertTrue(overview.getRawPartition().getMissingDates().isEmpty());
    }

    @Test
    public void overview_rawPartition_missingDatesAscendingAndCappedAt31() {
        mapper.device = deviceRow(1, null, null, null, null);
        mapper.rawPolicies = List.of(rawPolicy(1L, 30));
        mapper.partitions = List.of(
                partition(TODAY, true),
                partition(TODAY.plusDays(2), true),
                // 同名但边界错误的分区不得算作已存在
                wrongBoundPartition(TODAY.plusDays(3)));

        CollectorHealthOverviewRespVO overview = service.getOverview(7L);
        CollectorHealthOverviewRespVO.RawPartition raw = overview.getRawPartition();

        assertEquals(CollectorHealthServiceImpl.RAW_PARTITION_STATUS_OK, raw.getStatus());
        assertEquals(TODAY, raw.getExpectedFrom());
        assertEquals(TODAY.plusDays(30), raw.getExpectedTo());
        assertEquals(Integer.valueOf(31), raw.getExpectedCount());
        assertEquals(Integer.valueOf(2), raw.getExistingCount());
        assertEquals("leadDays=30 时缺失 29 天（31 - 2 天已存在）", 29, raw.getMissingDates().size());
        assertTrue(raw.getMissingDates().size() <= 31);
        assertEquals(TODAY.plusDays(1), raw.getMissingDates().get(0));
        assertEquals(TODAY.plusDays(3), raw.getMissingDates().get(1));
        assertEquals("缺失日期必须升序", TODAY.plusDays(30), raw.getMissingDates().get(28));
        for (int i = 1; i < raw.getMissingDates().size(); i++) {
            assertTrue(raw.getMissingDates().get(i).isAfter(raw.getMissingDates().get(i - 1)));
        }
    }

    @Test
    public void overview_retentionKeepsFixedOrderAndFields() {
        mapper.device = deviceRow(1, null, null, null, null);

        CollectorHealthOverviewRespVO overview = service.getOverview(7L);

        assertEquals(8, overview.getRetention().size());
        CollectorHealthOverviewRespVO.Retention raw = overview.getRetention().get(0);
        assertEquals("sample_raw", raw.getDataClass());
        assertEquals(Integer.valueOf(7), raw.getRetentionDays());
        assertTrue(raw.getEnabled());
        assertNull("从未执行过的策略 lastRunAt 必须为 null", raw.getLastRunAt());
        assertNull(raw.getLastRunResult());
        assertEquals("command", overview.getRetention().get(7).getDataClass());
    }

    private static void assertEpoch(String expectedInstant, Long actual) {
        assertEquals(Instant.parse(expectedInstant).toEpochMilli(), actual.longValue());
    }

    private static void assertJsonEpoch(JsonNode json, String pointer, String expectedInstant) {
        JsonNode value = json.at(pointer);
        assertTrue(pointer + " 必须是 JSON 数字", value.isIntegralNumber());
        assertEquals(pointer, Instant.parse(expectedInstant).toEpochMilli(), value.asLong());
    }

    private static CollectorHealthDeviceRow deviceRow(Integer online, Integer pollIntervalMs,
                                                      LocalDateTime lastPollAt, LocalDateTime lastSuccessAt,
                                                      Integer lastLatencyMs) {
        CollectorHealthDeviceRow row = new CollectorHealthDeviceRow();
        row.setId(7L);
        row.setName("测试设备");
        row.setOnline(online);
        row.setPollIntervalMs(pollIntervalMs);
        row.setLastPollAt(lastPollAt != null ? lastPollAt : LocalDateTime.of(2026, 11, 23, 9, 0));
        row.setLastSuccessAt(lastSuccessAt);
        row.setLastLatencyMs(lastLatencyMs);
        return row;
    }

    private static CollectorHealthHeartbeatRow heartbeat(String instanceId, String status, int writeLagMs) {
        CollectorHealthHeartbeatRow row = new CollectorHealthHeartbeatRow();
        row.setInstanceId(instanceId);
        row.setStatus(status);
        row.setHeartbeatTime(LocalDateTime.of(2026, 11, 23, 9, 59, 30));
        row.setWriteLagMs(writeLagMs);
        row.setDeviceTotal(1);
        row.setDeviceOnline(0);
        row.setCollectorVersion("1.0.0");
        return row;
    }

    private static CollectorHealthAlarmSummaryRow alarmSummary(int activeCount, int highestLevel) {
        CollectorHealthAlarmSummaryRow row = new CollectorHealthAlarmSummaryRow();
        row.setActiveCount(activeCount);
        row.setHighestLevel(highestLevel);
        return row;
    }

    private static CollectorHealthAlarmRow alarmRow(Long channelId, int level, String channelName) {
        CollectorHealthAlarmRow row = new CollectorHealthAlarmRow();
        row.setChannelId(channelId);
        row.setDeviceId(7L);
        row.setSensorId(10L);
        row.setLevel(level);
        row.setThresholdType("threshold_high_warning");
        row.setSince(LocalDateTime.of(2026, 11, 23, 8, 0));
        row.setLastValue(88.5D);
        row.setLastThreshold(80D);
        row.setLastSampleTime(LocalDateTime.of(2026, 11, 23, 9, 59));
        row.setDeviceName("测试设备");
        row.setSensorName("传感器");
        row.setChannelName(channelName);
        return row;
    }

    private static CollectorHealthAggregateSummaryRow aggregateSummary(String aggType, int rowCount,
                                                                      int abnormalCount, int maxRetryCount) {
        CollectorHealthAggregateSummaryRow row = new CollectorHealthAggregateSummaryRow();
        row.setAggType(aggType);
        row.setRowCount(rowCount);
        row.setAbnormalCount(abnormalCount);
        row.setMaxRetryCount(maxRetryCount);
        return row;
    }

    private static CollectorHealthRawPolicyRow rawPolicy(Long id, Integer leadDays) {
        CollectorHealthRawPolicyRow row = new CollectorHealthRawPolicyRow();
        row.setId(id);
        row.setPartitionLeadDays(leadDays);
        return row;
    }

    private static CollectorHealthPartitionRow partition(LocalDate day, boolean partition) {
        CollectorHealthPartitionRow row = new CollectorHealthPartitionRow();
        row.setName("collector_sample_raw_p" + day.toString().replace("-", ""));
        row.setPartitionFlag(partition);
        row.setBound("FOR VALUES FROM ('" + day + " 00:00:00') TO ('" + day.plusDays(1) + " 00:00:00')");
        return row;
    }

    private static CollectorHealthPartitionRow wrongBoundPartition(LocalDate day) {
        CollectorHealthPartitionRow row = new CollectorHealthPartitionRow();
        row.setName("collector_sample_raw_p" + day.toString().replace("-", ""));
        row.setPartitionFlag(true);
        row.setBound("FOR VALUES FROM ('" + day.plusDays(1) + " 00:00:00') TO ('"
                + day.plusDays(2) + " 00:00:00')");
        return row;
    }

    /** 固定数据源，同时统计被调用的 SQL 方法次数，用于断言固定条数。 */
    private static class FakeMapper implements CollectorHealthMapper {

        private int sqlCount;
        private CollectorHealthDeviceRow device;
        private CollectorHealthHeartbeatRow heartbeat;
        private CollectorHealthAlarmSummaryRow alarmSummary = alarmSummary(0, 0);
        private List<CollectorHealthAlarmRow> alarms = List.of();
        private List<CollectorHealthAggregateSummaryRow> aggregate = List.of();
        private List<CollectorHealthAggregateAbnormalRow> aggregateAbnormal = List.of();
        private List<CollectorHealthRawPolicyRow> rawPolicies = List.of(rawPolicy(1L, 2));
        private List<CollectorHealthPartitionRow> partitions = List.of();

        @Override
        public CollectorHealthDeviceRow selectDevice(Long deviceId) {
            sqlCount++;
            return device;
        }

        @Override
        public CollectorHealthHeartbeatRow selectLatestHeartbeat() {
            sqlCount++;
            return heartbeat;
        }

        @Override
        public CollectorHealthAlarmSummaryRow selectAlarmSummary(Long deviceId) {
            sqlCount++;
            return alarmSummary;
        }

        @Override
        public List<CollectorHealthAlarmRow> selectActiveAlarms(Long deviceId) {
            sqlCount++;
            return alarms;
        }

        @Override
        public List<CollectorHealthAggregateSummaryRow> selectAggregateSummary(Long deviceId) {
            sqlCount++;
            return aggregate;
        }

        @Override
        public List<CollectorHealthAggregateAbnormalRow> selectAggregateAbnormal(Long deviceId) {
            sqlCount++;
            return aggregateAbnormal;
        }

        @Override
        public List<CollectorHealthRawPolicyRow> selectRawPolicyRows() {
            sqlCount++;
            return rawPolicies;
        }

        @Override
        public List<CollectorHealthPartitionRow> selectRawPartitions() {
            sqlCount++;
            return partitions;
        }

    }

    /** 固定 8 条保留策略快照，顺序与生产一致。 */
    private static class FakeRetentionPolicyService implements CollectorRetentionPolicyService {

        private static final String[] DATA_CLASSES = {"sample_raw", "aggregate_second", "aggregate_minute",
                "aggregate_hour", "aggregate_day", "alarm", "heartbeat", "command"};
        private static final String[] DISPLAY_NAMES = {"采集原始数据", "秒聚合数据", "分钟聚合数据",
                "小时聚合数据", "天聚合数据", "报警事件", "采集器心跳", "采集命令记录"};

        private int sqlCount;
        private LocalDateTime lastRunAt;

        @Override
        public List<RetentionPolicySnapshot> getPolicyList() {
            sqlCount++;
            List<RetentionPolicySnapshot> snapshots = new ArrayList<>();
            for (int i = 0; i < DATA_CLASSES.length; i++) {
                snapshots.add(new RetentionPolicySnapshot((long) (i + 1), DATA_CLASSES[i], DISPLAY_NAMES[i],
                        7 * (i + 1), 2, "sample_raw".equals(DATA_CLASSES[i]) ? "drop_partition" : "delete_rows",
                        true, null, lastRunAt, null));
            }
            return snapshots;
        }

        @Override
        public void updatePolicy(RetentionPolicyUpdateCommand command) {
            throw new UnsupportedOperationException("只读接口不应更新策略");
        }

        @Override
        public CollectorRetentionCleanupService.RetentionPreview getPreview() {
            throw new UnsupportedOperationException("只读接口不应调用清理预检");
        }

    }

}
