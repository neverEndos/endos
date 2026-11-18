package cn.iocoder.yudao.module.collector.service.trend;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendChannelPageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendSeriesReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendSeriesRespVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.device.CollectorDeviceDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.trend.CollectorTrendBucketRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.trend.CollectorTrendRawPointRow;
import cn.iocoder.yudao.module.collector.dal.mysql.channel.CollectorChannelMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.device.CollectorDeviceMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.trend.CollectorTrendMapper;
import cn.iocoder.yudao.module.collector.enums.CollectorTrendGranularity;
import cn.iocoder.yudao.module.collector.enums.CollectorTrendSource;
import cn.iocoder.yudao.module.collector.util.CollectorDashboardUtils;
import cn.iocoder.yudao.module.collector.util.CollectorTrendPlanUtils;
import cn.iocoder.yudao.module.collector.util.CollectorTrendPlanUtils.Plan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.CHANNEL_NOT_EXISTS;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.CHANNEL_NOT_IN_DEVICE;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.TREND_GRANULARITY_NOT_ALLOWED;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.TREND_QUERY_INVALID;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.TREND_RAW_OVERLAY_RANGE_EXCEEDED;

@Service
public class CollectorTrendServiceImpl implements CollectorTrendService {

    /** 单次查询最多 8 个通道，与前端左右两图 6+6、合计 8 的约束一致。 */
    private static final int MAX_CHANNELS = 8;
    /** 叠加原始点需要扫描 raw 行，限定在 2 小时内，避免长时间范围的全量 raw 扫描。 */
    private static final long RAW_OVERLAY_MAX_RANGE_SECONDS = Duration.ofHours(2).toSeconds();
    /**
     * 原始点叠加查询的 statement_timeout（毫秒）。叠加只是增强展示，不允许慢查询拖垮主趋势；
     * 超时后按 rawDegraded 降级返回主序列。通过 {@code SET LOCAL} 设置，随事务结束自动还原。
     */
    private static final int RAW_OVERLAY_TIMEOUT_MILLIS = 10_000;
    /** PostgreSQL query_canceled（57014）：statement_timeout 与主动取消共用该 SQLState。 */
    private static final String SQLSTATE_QUERY_CANCELED = "57014";
    private static final String KIND_AGGREGATE = "aggregate";
    private static final String KIND_RAW = "raw";
    /** 原始点叠加降级时的中文警告信息，随响应返回给前端展示。 */
    private static final String RAW_DEGRADED_WARNING = "原始点加载超时，已仅展示聚合趋势";

    private final CollectorChannelMapper channelMapper;
    private final CollectorTrendMapper trendMapper;
    private final CollectorDeviceMapper deviceMapper;
    private final int rawOverlayTimeoutMillis;

    @Autowired
    public CollectorTrendServiceImpl(CollectorChannelMapper channelMapper,
                                     CollectorTrendMapper trendMapper,
                                     CollectorDeviceMapper deviceMapper) {
        this(channelMapper, trendMapper, deviceMapper, RAW_OVERLAY_TIMEOUT_MILLIS);
    }

    /** 测试专用构造：允许注入更短的叠加查询超时，便于在临时 PostgreSQL 上真实触发降级。 */
    CollectorTrendServiceImpl(CollectorChannelMapper channelMapper,
                              CollectorTrendMapper trendMapper,
                              CollectorDeviceMapper deviceMapper,
                              int rawOverlayTimeoutMillis) {
        this.channelMapper = channelMapper;
        this.trendMapper = trendMapper;
        this.deviceMapper = deviceMapper;
        this.rawOverlayTimeoutMillis = rawOverlayTimeoutMillis;
    }

    @Override
    @Transactional(readOnly = true)
    public CollectorTrendSeriesRespVO getSeries(CollectorTrendSeriesReqVO reqVO) {
        // 1. 参数与范围校验；范围一律按毫秒核算并向上取整为秒，避免 365 天 + 999ms 之类
        //    的输入经 Duration.toSeconds() 向下取整后绕过上限。时间区间为 [from, to)，to 不包含在内。
        LocalDateTime from = toLocalDateTime(reqVO.getFrom(), "开始时间");
        LocalDateTime to = toLocalDateTime(reqVO.getTo(), "结束时间");
        long rangeMillis = Math.subtractExact(reqVO.getTo(), reqVO.getFrom());
        if (rangeMillis <= 0) {
            throw exception(TREND_QUERY_INVALID, "结束时间必须晚于开始时间");
        }
        if (rangeMillis < 1000) {
            throw exception(TREND_QUERY_INVALID, "时间范围至少为 1 秒");
        }
        if (rangeMillis > CollectorTrendGranularity.MAX_TOTAL_RANGE.toMillis()) {
            throw exception(TREND_QUERY_INVALID, "时间范围不能超过 365 天");
        }
        long rangeSeconds = Math.ceilDiv(rangeMillis, 1000);
        List<Long> channelIds = distinctChannelIds(reqVO.getChannelIds());
        CollectorTrendGranularity granularity = parseGranularity(reqVO.getGranularity());
        validateGranularityRange(granularity, rangeMillis);
        boolean includeRaw = Boolean.TRUE.equals(reqVO.getIncludeRaw());
        if (includeRaw && rangeSeconds > RAW_OVERLAY_MAX_RANGE_SECONDS) {
            throw exception(TREND_RAW_OVERLAY_RANGE_EXCEEDED,
                    Duration.ofSeconds(RAW_OVERLAY_MAX_RANGE_SECONDS).toHours());
        }

        // 2. 校验通道归属并解析每个通道的数据源与桶宽
        List<CollectorChannelDO> channels = loadChannels(channelIds, reqVO.getDeviceId());
        Map<Long, ChannelPlan> channelPlans = new LinkedHashMap<>();
        Map<QueryGroup, List<Long>> queryGroups = new LinkedHashMap<>();
        for (CollectorChannelDO channel : channels) {
            Plan plan = CollectorTrendPlanUtils.resolve(channel.getDataProfile(), granularity, rangeSeconds);
            int bucketSeconds = CollectorTrendPlanUtils.bucketSeconds(plan, rangeSeconds);
            ChannelPlan channelPlan = new ChannelPlan(channel, plan.source(), bucketSeconds);
            channelPlans.put(channel.getId(), channelPlan);
            queryGroups.computeIfAbsent(new QueryGroup(plan.source(), bucketSeconds),
                    key -> new ArrayList<>()).add(channel.getId());
        }

        // 3. 按数据源层分组查询：同一层同桶宽的通道共用一条 SQL，不逐通道查询
        Map<Long, List<CollectorTrendBucketRow>> bucketRows = new HashMap<>();
        for (Map.Entry<QueryGroup, List<Long>> entry : queryGroups.entrySet()) {
            List<CollectorTrendBucketRow> rows = selectBuckets(entry.getKey(), entry.getValue(), from, to);
            for (CollectorTrendBucketRow row : rows) {
                bucketRows.computeIfAbsent(row.getChannelId(), key -> new ArrayList<>()).add(row);
            }
        }

        // 4. 原始点叠加序列：所有通道共用一条抽样 SQL。叠加失败不应拖垮主趋势——
        // 仅超时/查询取消时降级为“主序列 + 空 raw 序列 + rawDegraded 警告”，其余异常照常抛出。
        Map<Long, List<CollectorTrendRawPointRow>> rawRows = Map.of();
        boolean rawDegraded = false;
        if (includeRaw) {
            try {
                rawRows = selectRawPoints(channelIds, from, to, rangeSeconds);
            } catch (RuntimeException ex) {
                if (!isQueryCanceled(ex)) {
                    throw ex;
                }
                rawDegraded = true;
            }
        }

        // 5. 组装响应；主序列按请求通道顺序，原始序列紧跟对应通道
        CollectorTrendSeriesRespVO respVO = new CollectorTrendSeriesRespVO();
        List<CollectorTrendSeriesRespVO.Series> series = new ArrayList<>();
        Set<String> sourceSummary = new LinkedHashSet<>();
        for (Long channelId : channelIds) {
            ChannelPlan channelPlan = channelPlans.get(channelId);
            sourceSummary.add(channelPlan.source().getCode());
            series.add(buildAggregateSeries(channelPlan,
                    bucketRows.getOrDefault(channelId, List.of())));
            if (includeRaw) {
                series.add(buildRawSeries(channelPlan, rawRows.getOrDefault(channelId, List.of()),
                        rawStepSeconds(rangeSeconds)));
            }
        }
        respVO.setSeries(series);
        respVO.setSourceSummary(sourceSummary.size() == 1
                ? sourceSummary.iterator().next() : "mixed");
        respVO.setRawDegraded(rawDegraded ? Boolean.TRUE : null);
        respVO.setRawWarning(rawDegraded ? RAW_DEGRADED_WARNING : null);
        return respVO;
    }

    private LocalDateTime toLocalDateTime(Long millis, String fieldName) {
        if (millis == null) {
            throw exception(TREND_QUERY_INVALID, fieldName + "不能为空");
        }
        return Instant.ofEpochMilli(millis)
                .atZone(CollectorDashboardUtils.SHANGHAI_ZONE)
                .toLocalDateTime();
    }

    private List<Long> distinctChannelIds(Collection<Long> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            throw exception(TREND_QUERY_INVALID, "通道编号数量必须在 1~8 之间");
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long id : rawIds) {
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty() || ids.size() > MAX_CHANNELS) {
            throw exception(TREND_QUERY_INVALID, "通道编号数量必须在 1~8 之间");
        }
        return new ArrayList<>(ids);
    }

    private CollectorTrendGranularity parseGranularity(String granularity) {
        try {
            return CollectorTrendGranularity.fromCode(granularity);
        } catch (IllegalArgumentException ex) {
            throw exception(TREND_QUERY_INVALID,
                    "granularity 仅支持 auto、raw、1s、1m、5m、1h、1d");
        }
    }

    private void validateGranularityRange(CollectorTrendGranularity granularity, long rangeMillis) {
        if (granularity.isExplicit() && !granularity.supportsRangeMillis(rangeMillis)) {
            throw exception(TREND_GRANULARITY_NOT_ALLOWED, granularity.getCode(),
                    describeMaxRange(granularity));
        }
    }

    private String describeMaxRange(CollectorTrendGranularity granularity) {
        long seconds = granularity.getMaxRange().toSeconds();
        if (seconds < Duration.ofHours(1).toSeconds()) {
            return "仅支持 " + (seconds / 60) + " 分钟以内";
        }
        if (seconds < Duration.ofDays(1).toSeconds()) {
            return "仅支持 " + (seconds / 3600) + " 小时以内";
        }
        return "仅支持 " + (seconds / Duration.ofDays(1).toSeconds()) + " 天以内";
    }

    /**
     * 批量加载通道。MP 的 selectBatchIds 会自动过滤逻辑删除；缺失通道按是否指定设备分别报错。
     */
    private List<CollectorChannelDO> loadChannels(List<Long> channelIds, Long deviceId) {
        List<CollectorChannelDO> channels = channelMapper.selectBatchIds(channelIds);
        Map<Long, CollectorChannelDO> channelMap = new HashMap<>();
        for (CollectorChannelDO channel : channels) {
            channelMap.put(channel.getId(), channel);
        }
        List<CollectorChannelDO> ordered = new ArrayList<>(channelIds.size());
        for (Long channelId : channelIds) {
            CollectorChannelDO channel = channelMap.get(channelId);
            if (channel == null) {
                if (deviceId != null) {
                    throw exception(CHANNEL_NOT_IN_DEVICE, channelId, deviceId);
                }
                throw exception(CHANNEL_NOT_EXISTS);
            }
            if (deviceId != null && !deviceId.equals(channel.getDeviceId())) {
                throw exception(CHANNEL_NOT_IN_DEVICE, channelId, deviceId);
            }
            ordered.add(channel);
        }
        return ordered;
    }

    private List<CollectorTrendBucketRow> selectBuckets(QueryGroup group,
                                                        Collection<Long> channelIds,
                                                        LocalDateTime from, LocalDateTime to) {
        CollectorTrendSource source = group.source();
        if (source == CollectorTrendSource.RAW) {
            return trendMapper.selectRawBuckets(group.bucketSeconds(), channelIds, from, to);
        }
        if (source == CollectorTrendSource.DAY) {
            return trendMapper.selectDayBuckets(channelIds, from, to);
        }
        return trendMapper.selectAggregateBuckets(source.getTable(), group.bucketSeconds(),
                channelIds, from, to);
    }

    /**
     * 原始点叠加查询：先对当前连接设置明确且较短的 statement_timeout（{@code SET LOCAL}
     * 只影响本事务，事务结束自动还原，不会污染连接池），再执行抽样 SQL。
     */
    private Map<Long, List<CollectorTrendRawPointRow>> selectRawPoints(List<Long> channelIds,
                                                                       LocalDateTime from, LocalDateTime to,
                                                                       long rangeSeconds) {
        int stepSeconds = rawStepSeconds(rangeSeconds);
        trendMapper.setRawOverlayTimeout(rawOverlayTimeoutMillis);
        List<CollectorTrendRawPointRow> rows = trendMapper.selectRawPoints(
                stepSeconds, channelIds, from, to);
        Map<Long, List<CollectorTrendRawPointRow>> byChannel = new HashMap<>();
        for (CollectorTrendRawPointRow row : rows) {
            byChannel.computeIfAbsent(row.getChannelId(), key -> new ArrayList<>()).add(row);
        }
        return byChannel;
    }

    /**
     * 原始点抽样步长：按固定分桶原点下的最坏桶数（首尾各多覆盖半桶，见
     * {@link CollectorTrendSource#worstBucketCount}）选择，保证任意时间对齐下单通道
     * 抽样点严格不超过 {@link CollectorTrendSource#MAX_POINTS_PER_SERIES}。
     */
    private int rawStepSeconds(long rangeSeconds) {
        long step = Math.ceilDiv(rangeSeconds, CollectorTrendSource.MAX_POINTS_PER_SERIES - 1);
        return (int) Math.max(1, step);
    }

    /** 判断异常链中是否包含查询取消/超时（PostgreSQL 57014，含 statement_timeout）。 */
    private boolean isQueryCanceled(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof java.sql.SQLException sqlException
                    && SQLSTATE_QUERY_CANCELED.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private CollectorTrendSeriesRespVO.Series buildAggregateSeries(ChannelPlan plan,
                                                                   List<CollectorTrendBucketRow> rows) {
        CollectorTrendSeriesRespVO.Series series = newSeries(plan, KIND_AGGREGATE, plan.bucketSeconds());
        List<CollectorTrendSeriesRespVO.Point> points = new ArrayList<>(rows.size());
        for (CollectorTrendBucketRow row : rows) {
            CollectorTrendSeriesRespVO.Point point = new CollectorTrendSeriesRespVO.Point();
            point.setT(CollectorDashboardUtils.toEpochMilli(row.getBucketTime()));
            point.setAvg(row.getAvg());
            point.setMin(row.getMin());
            point.setMax(row.getMax());
            point.setCount(row.getCount());
            points.add(point);
        }
        series.setPoints(points);
        return series;
    }

    private CollectorTrendSeriesRespVO.Series buildRawSeries(ChannelPlan plan,
                                                             List<CollectorTrendRawPointRow> rows,
                                                             int stepSeconds) {
        CollectorTrendSeriesRespVO.Series series = newSeries(plan, KIND_RAW, stepSeconds);
        // 叠加序列无论主序列来自哪一层，数据源始终是 raw。
        series.setSource(CollectorTrendSource.RAW.getCode());
        List<CollectorTrendSeriesRespVO.Point> points = new ArrayList<>(rows.size());
        for (CollectorTrendRawPointRow row : rows) {
            CollectorTrendSeriesRespVO.Point point = new CollectorTrendSeriesRespVO.Point();
            point.setT(CollectorDashboardUtils.toEpochMilli(row.getBucketTime()));
            point.setValue(row.getValue());
            point.setAvg(row.getValue());
            point.setMin(row.getValue());
            point.setMax(row.getValue());
            point.setCount(1);
            points.add(point);
        }
        series.setPoints(points);
        return series;
    }

    private CollectorTrendSeriesRespVO.Series newSeries(ChannelPlan plan, String kind, int bucketSeconds) {
        CollectorTrendSeriesRespVO.Series series = new CollectorTrendSeriesRespVO.Series();
        series.setChannelId(plan.channel().getId());
        series.setChannelName(plan.channel().getChannelName());
        series.setUnit(plan.channel().getUnit());
        series.setDataProfile(plan.channel().getDataProfile());
        series.setSource(plan.source().getCode());
        series.setBucketSeconds(bucketSeconds);
        series.setKind(kind);
        return series;
    }

    @Override
    public List<CollectorDeviceDO> getTrendDeviceList() {
        return deviceMapper.selectListByEnabled(1);
    }

    @Override
    public PageResult<CollectorChannelDO> getTrendChannelPage(CollectorTrendChannelPageReqVO reqVO) {
        return channelMapper.selectEnabledPage(reqVO, reqVO.getDeviceId(), reqVO.getKeyword());
    }

    private record ChannelPlan(CollectorChannelDO channel, CollectorTrendSource source, int bucketSeconds) {
    }

    private record QueryGroup(CollectorTrendSource source, int bucketSeconds) {
    }

}
