package cn.iocoder.yudao.module.collector.service.trend;

import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendSeriesReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendSeriesRespVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.trend.CollectorTrendBucketRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.trend.CollectorTrendRawPointRow;
import cn.iocoder.yudao.module.collector.dal.mysql.channel.CollectorChannelMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.trend.CollectorTrendMapper;
import cn.iocoder.yudao.module.collector.enums.CollectorTrendGranularity;
import cn.iocoder.yudao.module.collector.enums.CollectorTrendSource;
import cn.iocoder.yudao.module.collector.util.CollectorDashboardUtils;
import cn.iocoder.yudao.module.collector.util.CollectorTrendPlanUtils;
import cn.iocoder.yudao.module.collector.util.CollectorTrendPlanUtils.Plan;
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
    private static final String KIND_AGGREGATE = "aggregate";
    private static final String KIND_RAW = "raw";

    private final CollectorChannelMapper channelMapper;
    private final CollectorTrendMapper trendMapper;

    public CollectorTrendServiceImpl(CollectorChannelMapper channelMapper,
                                     CollectorTrendMapper trendMapper) {
        this.channelMapper = channelMapper;
        this.trendMapper = trendMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public CollectorTrendSeriesRespVO getSeries(CollectorTrendSeriesReqVO reqVO) {
        // 1. 参数与范围校验
        LocalDateTime from = toLocalDateTime(reqVO.getFrom(), "开始时间");
        LocalDateTime to = toLocalDateTime(reqVO.getTo(), "结束时间");
        if (!from.isBefore(to)) {
            throw exception(TREND_QUERY_INVALID, "结束时间必须晚于开始时间");
        }
        long rangeSeconds = Duration.between(from, to).toSeconds();
        if (rangeSeconds <= 0) {
            throw exception(TREND_QUERY_INVALID, "时间范围至少为 1 秒");
        }
        if (rangeSeconds > CollectorTrendGranularity.MAX_TOTAL_RANGE.toSeconds()) {
            throw exception(TREND_QUERY_INVALID, "时间范围不能超过 365 天");
        }
        List<Long> channelIds = distinctChannelIds(reqVO.getChannelIds());
        CollectorTrendGranularity granularity = parseGranularity(reqVO.getGranularity());
        validateGranularityRange(granularity, rangeSeconds);
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

        // 4. 原始点叠加序列：所有通道共用一条抽样 SQL
        Map<Long, List<CollectorTrendRawPointRow>> rawRows = includeRaw
                ? selectRawPoints(channelIds, from, to, rangeSeconds)
                : Map.of();

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

    private void validateGranularityRange(CollectorTrendGranularity granularity, long rangeSeconds) {
        if (granularity.isExplicit() && !granularity.supportsRange(rangeSeconds)) {
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

    private Map<Long, List<CollectorTrendRawPointRow>> selectRawPoints(List<Long> channelIds,
                                                                       LocalDateTime from, LocalDateTime to,
                                                                       long rangeSeconds) {
        int stepSeconds = rawStepSeconds(rangeSeconds);
        List<CollectorTrendRawPointRow> rows = trendMapper.selectRawPoints(
                stepSeconds, channelIds, from, to);
        Map<Long, List<CollectorTrendRawPointRow>> byChannel = new HashMap<>();
        for (CollectorTrendRawPointRow row : rows) {
            byChannel.computeIfAbsent(row.getChannelId(), key -> new ArrayList<>()).add(row);
        }
        return byChannel;
    }

    /** 原始点抽样步长：在 1000 点上限内尽量保留更多细节。 */
    private int rawStepSeconds(long rangeSeconds) {
        return (int) Math.max(1, (rangeSeconds + CollectorTrendSource.MAX_POINTS_PER_SERIES - 1)
                / CollectorTrendSource.MAX_POINTS_PER_SERIES);
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

    private record ChannelPlan(CollectorChannelDO channel, CollectorTrendSource source, int bucketSeconds) {
    }

    private record QueryGroup(CollectorTrendSource source, int bucketSeconds) {
    }

}
