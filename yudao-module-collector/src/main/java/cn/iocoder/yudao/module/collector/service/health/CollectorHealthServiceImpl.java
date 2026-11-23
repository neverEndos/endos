package cn.iocoder.yudao.module.collector.service.health;

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
import cn.iocoder.yudao.module.collector.enums.CollectorAggregateType;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionDdlSupport;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionDdlSupport.BoundParse;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionDdlSupport.BoundState;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionPolicyService;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionPolicyService.RetentionPolicySnapshot;
import cn.iocoder.yudao.module.collector.util.CollectorDashboardUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.DEVICE_NOT_EXISTS;

/**
 * 采集运行详情只读服务实现。
 *
 * <p>SQL 条数固定：summary 3 条（设备状态、最新心跳、报警汇总），overview 9 条
 * （3 条摘要 + 报警明细、聚合汇总、聚合异常、保留策略、raw 策略、分区目录各 1 条），
 * 均不随通道数增长。</p>
 */
@Slf4j
@Service
public class CollectorHealthServiceImpl implements CollectorHealthService {

    /** raw 分区策略正常。 */
    public static final String RAW_PARTITION_STATUS_OK = "ok";
    /** raw 分区策略缺失。 */
    public static final String RAW_PARTITION_STATUS_POLICY_MISSING = "policy_missing";
    /** raw 分区策略重复。 */
    public static final String RAW_PARTITION_STATUS_POLICY_DUPLICATE = "policy_duplicate";
    /** raw 分区策略非法（NULL 或超出 0~30）。 */
    public static final String RAW_PARTITION_STATUS_POLICY_INVALID = "policy_invalid";

    /** 与分区维护一致的 leadDays 合法区间，非法值绝不套用默认值。 */
    private static final int MIN_LEAD_DAYS = 0;
    private static final int MAX_LEAD_DAYS = 30;
    /** missingDates 上限；leadDays <= 30 时天然不超过 31 天，这里保留防御性截断。 */
    private static final int MAX_MISSING_DATES = 31;

    private final CollectorHealthMapper healthMapper;
    private final CollectorRetentionPolicyService retentionPolicyService;

    public CollectorHealthServiceImpl(CollectorHealthMapper healthMapper,
                                      CollectorRetentionPolicyService retentionPolicyService) {
        this.healthMapper = healthMapper;
        this.retentionPolicyService = retentionPolicyService;
    }

    /** 运行详情统一使用 Asia/Shanghai 墙钟；单测可注入固定时钟验证日期区间。 */
    private Clock clock = Clock.system(CollectorDashboardUtils.SHANGHAI_ZONE);

    /** 仅供测试替换时钟，避免依赖真实当前时间。 */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public CollectorHealthSummaryRespVO getSummary(Long deviceId) {
        return loadSummary(deviceId, LocalDateTime.now(clock.withZone(CollectorDashboardUtils.SHANGHAI_ZONE)));
    }

    @Override
    @Transactional(readOnly = true)
    public CollectorHealthOverviewRespVO getOverview(Long deviceId) {
        LocalDateTime serverTime = LocalDateTime.now(clock.withZone(CollectorDashboardUtils.SHANGHAI_ZONE));
        CollectorHealthSummaryRespVO summary = loadSummary(deviceId, serverTime);

        CollectorHealthOverviewRespVO overview = new CollectorHealthOverviewRespVO();
        overview.setServerTime(summary.getServerTime());
        overview.setProcess(summary.getProcess());
        overview.setDevice(summary.getDevice());
        overview.setAlarm(summary.getAlarm());

        // 报警明细与总数同源：count 来自上面的汇总 SQL，列表最多 20 条。
        overview.setAlarmTotal(summary.getAlarm().getActiveCount());
        overview.setAlarms(buildAlarmDetails(healthMapper.selectActiveAlarms(deviceId)));
        overview.setAggregate(buildAggregateSummary(healthMapper.selectAggregateSummary(deviceId)));
        overview.setAggregateAbnormal(buildAggregateAbnormal(healthMapper.selectAggregateAbnormal(deviceId)));

        List<RetentionPolicySnapshot> policies = retentionPolicyService.getPolicyList();
        overview.setRetention(buildRetention(policies));

        LocalDate today = LocalDate.now(clock.withZone(CollectorDashboardUtils.SHANGHAI_ZONE));
        overview.setRawPartition(buildRawPartition(today, healthMapper.selectRawPolicyRows(),
                healthMapper.selectRawPartitions()));
        return overview;
    }

    /**
     * 摘要公共查询：固定 3 条 SQL（设备状态、最新心跳、报警汇总）。
     */
    private CollectorHealthSummaryRespVO loadSummary(Long deviceId, LocalDateTime serverTime) {
        CollectorHealthDeviceRow deviceRow = healthMapper.selectDevice(deviceId);
        if (deviceRow == null) {
            throw exception(DEVICE_NOT_EXISTS);
        }
        CollectorHealthSummaryRespVO summary = new CollectorHealthSummaryRespVO();
        summary.setServerTime(CollectorDashboardUtils.toEpochMilli(serverTime));
        summary.setProcess(buildProcess(healthMapper.selectLatestHeartbeat()));
        summary.setDevice(buildDevice(deviceRow));
        summary.setAlarm(buildAlarm(healthMapper.selectAlarmSummary(deviceId)));
        return summary;
    }

    /** 无心跳时返回 null，前端据此展示「尚未收到心跳」，绝不伪造 running。 */
    private CollectorHealthSummaryRespVO.Process buildProcess(CollectorHealthHeartbeatRow row) {
        if (row == null) {
            return null;
        }
        CollectorHealthSummaryRespVO.Process process = new CollectorHealthSummaryRespVO.Process();
        process.setInstanceId(row.getInstanceId());
        process.setStatus(row.getStatus());
        process.setHeartbeatTime(CollectorDashboardUtils.toEpochMilli(row.getHeartbeatTime()));
        process.setWriteLagMs(row.getWriteLagMs());
        process.setDeviceTotal(row.getDeviceTotal());
        process.setDeviceOnline(row.getDeviceOnline());
        process.setCollectorVersion(row.getCollectorVersion());
        return process;
    }

    private CollectorHealthSummaryRespVO.Device buildDevice(CollectorHealthDeviceRow row) {
        CollectorHealthSummaryRespVO.Device device = new CollectorHealthSummaryRespVO.Device();
        device.setDeviceId(row.getId());
        device.setDeviceName(row.getName());
        // 无设备状态行时 online 为 null（未知），不伪造在线。
        device.setOnline(row.getOnline() == null ? null : Integer.valueOf(1).equals(row.getOnline()));
        device.setLastPollAt(CollectorDashboardUtils.toEpochMilli(row.getLastPollAt()));
        device.setLastSuccessAt(CollectorDashboardUtils.toEpochMilli(row.getLastSuccessAt()));
        device.setLastLatencyMs(row.getLastLatencyMs());
        device.setLastError(row.getLastError());
        device.setConsecutiveFailures(row.getConsecutiveFailures());
        // poll_interval_ms 只来自 collector_device，关联不到时返回 null，不编造。
        device.setPollIntervalMs(row.getPollIntervalMs());
        return device;
    }

    private CollectorHealthSummaryRespVO.Alarm buildAlarm(CollectorHealthAlarmSummaryRow row) {
        CollectorHealthSummaryRespVO.Alarm alarm = new CollectorHealthSummaryRespVO.Alarm();
        alarm.setActiveCount(row != null && row.getActiveCount() != null ? row.getActiveCount() : 0);
        // 0 表示无活动报警：level 本身只有 0/2/3，0 不是报警级别。
        alarm.setHighestLevel(row != null && row.getHighestLevel() != null ? row.getHighestLevel() : 0);
        return alarm;
    }

    private List<CollectorHealthOverviewRespVO.AlarmDetail> buildAlarmDetails(List<CollectorHealthAlarmRow> rows) {
        List<CollectorHealthOverviewRespVO.AlarmDetail> details = new ArrayList<>(rows.size());
        for (CollectorHealthAlarmRow row : rows) {
            CollectorHealthOverviewRespVO.AlarmDetail detail = new CollectorHealthOverviewRespVO.AlarmDetail();
            detail.setDeviceId(row.getDeviceId());
            detail.setDeviceName(row.getDeviceName());
            detail.setSensorId(row.getSensorId());
            detail.setSensorName(row.getSensorName());
            detail.setChannelId(row.getChannelId());
            detail.setChannelName(row.getChannelName());
            detail.setLevel(row.getLevel());
            detail.setThresholdType(row.getThresholdType());
            detail.setSince(CollectorDashboardUtils.toEpochMilli(row.getSince()));
            detail.setLastValue(row.getLastValue());
            detail.setLastThreshold(row.getLastThreshold());
            detail.setLastSampleTime(CollectorDashboardUtils.toEpochMilli(row.getLastSampleTime()));
            details.add(detail);
        }
        return details;
    }

    /**
     * 固定 4 组汇总：缺失的聚合类型补 0 行并标记 initialized=false，避免被误读为「全部正常」。
     */
    private List<CollectorHealthOverviewRespVO.AggregateSummary> buildAggregateSummary(
            List<CollectorHealthAggregateSummaryRow> rows) {
        Map<String, CollectorHealthAggregateSummaryRow> rowsByType = new HashMap<>();
        for (CollectorHealthAggregateSummaryRow row : rows) {
            rowsByType.put(row.getAggType(), row);
        }
        List<CollectorHealthOverviewRespVO.AggregateSummary> summaries =
                new ArrayList<>(CollectorAggregateType.values().length);
        for (CollectorAggregateType type : CollectorAggregateType.values()) {
            CollectorHealthOverviewRespVO.AggregateSummary summary =
                    new CollectorHealthOverviewRespVO.AggregateSummary();
            summary.setAggType(type.getCode());
            CollectorHealthAggregateSummaryRow row = rowsByType.get(type.getCode());
            if (row == null) {
                summary.setRowCount(0);
                summary.setAbnormalCount(0);
                summary.setInitialized(false);
            } else {
                summary.setRowCount(row.getRowCount() != null ? row.getRowCount() : 0);
                summary.setAbnormalCount(row.getAbnormalCount() != null ? row.getAbnormalCount() : 0);
                summary.setMaxRetryCount(row.getMaxRetryCount());
                summary.setLastSuccessTime(CollectorDashboardUtils.toEpochMilli(row.getLastSuccessTime()));
                summary.setMinWatermarkTime(CollectorDashboardUtils.toEpochMilli(row.getMinWatermarkTime()));
                summary.setMaxWatermarkTime(CollectorDashboardUtils.toEpochMilli(row.getMaxWatermarkTime()));
                summary.setInitialized(Boolean.TRUE);
            }
            summaries.add(summary);
        }
        return summaries;
    }

    private List<CollectorHealthOverviewRespVO.AggregateAbnormal> buildAggregateAbnormal(
            List<CollectorHealthAggregateAbnormalRow> rows) {
        List<CollectorHealthOverviewRespVO.AggregateAbnormal> items = new ArrayList<>(rows.size());
        for (CollectorHealthAggregateAbnormalRow row : rows) {
            CollectorHealthOverviewRespVO.AggregateAbnormal item =
                    new CollectorHealthOverviewRespVO.AggregateAbnormal();
            item.setAggType(row.getAggType());
            item.setChannelId(row.getChannelId());
            item.setDeviceId(row.getDeviceId());
            item.setDeviceName(row.getDeviceName());
            item.setChannelName(row.getChannelName());
            // 状态 / 重试次数原样展示，不据此推断 Quartz 作业运行正常，也不补写 last_error。
            item.setStatus(row.getStatus());
            item.setRetryCount(row.getRetryCount());
            item.setLastError(row.getLastError());
            item.setWatermarkTime(CollectorDashboardUtils.toEpochMilli(row.getWatermarkTime()));
            items.add(item);
        }
        return items;
    }

    private List<CollectorHealthOverviewRespVO.Retention> buildRetention(List<RetentionPolicySnapshot> snapshots) {
        List<CollectorHealthOverviewRespVO.Retention> items = new ArrayList<>(snapshots.size());
        for (RetentionPolicySnapshot snapshot : snapshots) {
            CollectorHealthOverviewRespVO.Retention item = new CollectorHealthOverviewRespVO.Retention();
            item.setId(snapshot.id());
            item.setDataClass(snapshot.dataClass());
            item.setDisplayName(snapshot.displayName());
            item.setRetentionDays(snapshot.retentionDays());
            item.setEnabled(snapshot.enabled());
            item.setLastRunAt(CollectorDashboardUtils.toEpochMilli(snapshot.lastRunAt()));
            item.setLastRunResult(snapshot.lastRunResult());
            items.add(item);
        }
        return items;
    }

    /**
     * raw 分区现状：策略缺失 / 重复 / 非法时返回明确状态，绝不套用默认 leadDays；
     * 只读 catalog 结果，不执行任何 DDL，也不扫描 DEFAULT 分区数据。
     */
    private CollectorHealthOverviewRespVO.RawPartition buildRawPartition(
            LocalDate today, List<CollectorHealthRawPolicyRow> policyRows,
            List<CollectorHealthPartitionRow> partitionRows) {
        CollectorHealthOverviewRespVO.RawPartition vo = new CollectorHealthOverviewRespVO.RawPartition();
        vo.setExpectedFrom(today);
        vo.setExpectedCount(0);
        vo.setExistingCount(0);
        vo.setMissingDates(List.of());

        if (policyRows == null || policyRows.isEmpty()) {
            vo.setStatus(RAW_PARTITION_STATUS_POLICY_MISSING);
            return vo;
        }
        if (policyRows.size() > 1) {
            vo.setStatus(RAW_PARTITION_STATUS_POLICY_DUPLICATE);
            return vo;
        }
        Integer leadDays = policyRows.get(0).getPartitionLeadDays();
        vo.setLeadDays(leadDays);
        if (leadDays == null || leadDays < MIN_LEAD_DAYS || leadDays > MAX_LEAD_DAYS) {
            vo.setStatus(RAW_PARTITION_STATUS_POLICY_INVALID);
            return vo;
        }
        vo.setStatus(RAW_PARTITION_STATUS_OK);
        LocalDate expectedTo = today.plusDays(leadDays);
        vo.setExpectedTo(expectedTo);
        vo.setExpectedCount(leadDays + 1);

        Map<LocalDate, Boolean> presentDays = collectPresentDays(partitionRows, today, expectedTo);
        vo.setExistingCount(presentDays.size());
        List<LocalDate> missingDates = new ArrayList<>();
        for (LocalDate day = today; !day.isAfter(expectedTo) && missingDates.size() < MAX_MISSING_DATES;
             day = day.plusDays(1)) {
            if (!presentDays.containsKey(day)) {
                missingDates.add(day);
            }
        }
        vo.setMissingDates(missingDates);
        return vo;
    }

    /**
     * 识别目标区间内「名称与边界都完全匹配」的日分区，避免同名但边界错误的分区被当作已存在。
     */
    private Map<LocalDate, Boolean> collectPresentDays(List<CollectorHealthPartitionRow> partitionRows,
                                                       LocalDate from, LocalDate to) {
        Map<LocalDate, Boolean> presentDays = new HashMap<>();
        if (partitionRows == null) {
            return presentDays;
        }
        for (CollectorHealthPartitionRow row : partitionRows) {
            if (!Boolean.TRUE.equals(row.getPartitionFlag()) || row.getName() == null
                    || !CollectorPartitionDdlSupport.PARTITION_NAME_PATTERN.matcher(row.getName()).matches()) {
                continue;
            }
            LocalDate day;
            try {
                day = LocalDate.parse(row.getName().substring(CollectorPartitionDdlSupport.PARTITION_PREFIX.length()),
                        CollectorPartitionDdlSupport.PARTITION_SUFFIX_FORMATTER);
            } catch (DateTimeParseException ex) {
                continue;
            }
            if (day.isBefore(from) || day.isAfter(to)) {
                continue;
            }
            BoundParse parsed = CollectorPartitionDdlSupport.parseBound(row.getBound());
            if (parsed.state() != BoundState.RANGE
                    || !parsed.bound().exactly(day.atStartOfDay(), day.plusDays(1).atStartOfDay())) {
                continue;
            }
            presentDays.put(day, Boolean.TRUE);
        }
        return presentDays;
    }

}
