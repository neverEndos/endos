package cn.iocoder.yudao.module.collector.service.trend;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendSeriesReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendSeriesRespVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.trend.CollectorTrendBucketRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.trend.CollectorTrendRawPointRow;
import cn.iocoder.yudao.module.collector.dal.mysql.channel.CollectorChannelMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.trend.CollectorTrendMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.CHANNEL_NOT_IN_DEVICE;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.TREND_GRANULARITY_NOT_ALLOWED;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.TREND_QUERY_INVALID;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.TREND_RAW_OVERLAY_RANGE_EXCEEDED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 趋势查询的选源、SQL 分组、点数放大、原始点叠加与参数校验。
 * Mapper 使用 Mockito 替代，真实 PostgreSQL SQL 由 CollectorTrendPgTest 验证。
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class CollectorTrendServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long BASE_MILLIS = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();

    @Mock
    private CollectorChannelMapper channelMapper;
    @Mock
    private CollectorTrendMapper trendMapper;
    @InjectMocks
    private CollectorTrendServiceImpl trendService;

    @Test
    public void getSeries_autoSelectsSourcePerProfileAndQueriesByLayer() {
        CollectorChannelDO normal = channel(1L, 7L, "normal");
        CollectorChannelDO fast = channel(2L, 7L, "fast");
        when(channelMapper.selectBatchIds(Arrays.asList(1L, 2L))).thenReturn(Arrays.asList(normal, fast));
        when(trendMapper.selectAggregateBuckets(eq("collector_aggregate_minute"), eq(60), any(), any(), any()))
                .thenReturn(List.of(bucketRow(1L, BASE_MILLIS, 10D, 8D, 12D, 3)));
        when(trendMapper.selectAggregateBuckets(eq("collector_aggregate_second"), eq(5), any(), any(), any()))
                .thenReturn(List.of(bucketRow(2L, BASE_MILLIS, 20D, 19D, 21D, 5)));

        CollectorTrendSeriesRespVO resp = trendService.getSeries(request(
                Arrays.asList(1L, 2L), 7L, 30 * 60, "auto", false));

        assertEquals("mixed", resp.getSourceSummary());
        assertEquals(2, resp.getSeries().size());
        assertEquals("minute", resp.getSeries().get(0).getSource());
        assertEquals(Integer.valueOf(60), resp.getSeries().get(0).getBucketSeconds());
        assertEquals("second", resp.getSeries().get(1).getSource());
        assertEquals(Integer.valueOf(5), resp.getSeries().get(1).getBucketSeconds());
        assertEquals(1, resp.getSeries().get(0).getPoints().size());
        assertEquals(1, resp.getSeries().get(1).getPoints().size());
        verify(trendMapper).selectAggregateBuckets(eq("collector_aggregate_minute"), eq(60), any(), any(), any());
        verify(trendMapper).selectAggregateBuckets(eq("collector_aggregate_second"), eq(5), any(), any(), any());
        verify(trendMapper, never()).selectRawBuckets(anyInt(), any(), any(), any());
    }

    @Test
    public void getSeries_normalShortRangeUsesRawBuckets() {
        CollectorChannelDO channel = channel(1L, 7L, "normal");
        when(channelMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(channel));
        when(trendMapper.selectRawBuckets(eq(1), any(), any(), any()))
                .thenReturn(List.of(bucketRow(1L, BASE_MILLIS, 25D, 24D, 26D, 10)));

        CollectorTrendSeriesRespVO resp = trendService.getSeries(request(
                List.of(1L), 7L, 5 * 60, "auto", false));

        assertEquals("raw", resp.getSourceSummary());
        assertEquals("raw", resp.getSeries().get(0).getSource());
        assertEquals(Integer.valueOf(1), resp.getSeries().get(0).getBucketSeconds());
        assertEquals("aggregate", resp.getSeries().get(0).getKind());
        assertEquals(25D, resp.getSeries().get(0).getPoints().get(0).getAvg(), 0D);
        assertEquals(Integer.valueOf(10), resp.getSeries().get(0).getPoints().get(0).getCount());
        verify(trendMapper, never()).selectAggregateBuckets(anyString(), anyInt(), any(), any(), any());
    }

    @Test
    public void getSeries_emptyDataReturnsEmptyPointsWithoutError() {
        CollectorChannelDO channel = channel(1L, 7L, "normal");
        when(channelMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(channel));
        when(trendMapper.selectAggregateBuckets(anyString(), anyInt(), any(), any(), any()))
                .thenReturn(List.of());

        CollectorTrendSeriesRespVO resp = trendService.getSeries(request(
                List.of(1L), 7L, 24 * 60 * 60, "auto", false));

        assertEquals("minute", resp.getSourceSummary());
        assertEquals(1, resp.getSeries().size());
        assertTrue(resp.getSeries().get(0).getPoints().isEmpty());
    }

    @Test
    public void getSeries_includeRawAddsStepSampledRawSeries() {
        CollectorChannelDO normal = channel(1L, 7L, "normal");
        CollectorChannelDO fast = channel(2L, 7L, "fast");
        when(channelMapper.selectBatchIds(Arrays.asList(1L, 2L))).thenReturn(Arrays.asList(normal, fast));
        when(trendMapper.selectAggregateBuckets(anyString(), anyInt(), any(), any(), any()))
                .thenReturn(List.of());
        when(trendMapper.selectRawPoints(eq(2), any(), any(), any()))
                .thenReturn(List.of(rawPoint(1L, BASE_MILLIS, 10D), rawPoint(2L, BASE_MILLIS, 20D)));

        CollectorTrendSeriesRespVO resp = trendService.getSeries(request(
                Arrays.asList(1L, 2L), 7L, 30 * 60, "auto", true));

        assertEquals(4, resp.getSeries().size());
        assertEquals("raw", resp.getSeries().get(1).getKind());
        assertEquals("raw", resp.getSeries().get(1).getSource());
        assertEquals(Integer.valueOf(2), resp.getSeries().get(1).getBucketSeconds());
        assertEquals(10D, resp.getSeries().get(1).getPoints().get(0).getValue(), 0D);
        assertEquals("raw", resp.getSeries().get(3).getKind());
        verify(trendMapper).selectRawPoints(eq(2), any(), any(), any());
    }

    @Test
    public void getSeries_explicitOneSecondEnlargesFastBucketWithinSecondLayer() {
        CollectorChannelDO normal = channel(1L, 7L, "normal");
        CollectorChannelDO fast = channel(2L, 7L, "fast");
        when(channelMapper.selectBatchIds(Arrays.asList(1L, 2L))).thenReturn(Arrays.asList(normal, fast));
        when(trendMapper.selectRawBuckets(eq(10), any(), any(), any())).thenReturn(List.of());
        when(trendMapper.selectAggregateBuckets(eq("collector_aggregate_second"), eq(10), any(), any(), any()))
                .thenReturn(List.of());

        CollectorTrendSeriesRespVO resp = trendService.getSeries(request(
                Arrays.asList(1L, 2L), 7L, 2 * 60 * 60, "1s", false));

        assertEquals("mixed", resp.getSourceSummary());
        assertEquals("raw", resp.getSeries().get(0).getSource());
        assertEquals(Integer.valueOf(10), resp.getSeries().get(0).getBucketSeconds());
        assertEquals("second", resp.getSeries().get(1).getSource());
        assertEquals(Integer.valueOf(10), resp.getSeries().get(1).getBucketSeconds());
    }

    @Test
    public void getSeries_explicitGranularityOutsideRangeReturnsError() {
        CollectorChannelDO channel = channel(1L, 7L, "normal");
        when(channelMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(channel));

        ServiceException error = assertThrows(ServiceException.class,
                () -> trendService.getSeries(request(List.of(1L), 7L, 20 * 60, "raw", false)));
        assertEquals(TREND_GRANULARITY_NOT_ALLOWED.getCode(), error.getCode());
    }

    @Test
    public void getSeries_invalidGranularityReturnsParamError() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> trendService.getSeries(request(List.of(1L), 7L, 10 * 60, "10s", false)));
        assertEquals(TREND_QUERY_INVALID.getCode(), error.getCode());
    }

    @Test
    public void getSeries_invalidTimeRangeReturnsParamError() {
        ServiceException reversed = assertThrows(ServiceException.class,
                () -> trendService.getSeries(fixedRequest(List.of(1L), 60_000L, 0L, "auto", false)));
        assertEquals(TREND_QUERY_INVALID.getCode(), reversed.getCode());

        ServiceException tooLong = assertThrows(ServiceException.class,
                () -> trendService.getSeries(fixedRequest(List.of(1L), 0L,
                        365L * 24 * 60 * 60 * 1000 + 1000, "auto", false)));
        assertEquals(TREND_QUERY_INVALID.getCode(), tooLong.getCode());
    }

    @Test
    public void getSeries_tooManyChannelsReturnsParamError() {
        List<Long> ids = new ArrayList<>();
        for (long i = 1; i <= 9; i++) {
            ids.add(i);
        }
        ServiceException error = assertThrows(ServiceException.class,
                () -> trendService.getSeries(request(ids, 7L, 10 * 60, "auto", false)));
        assertEquals(TREND_QUERY_INVALID.getCode(), error.getCode());
    }

    @Test
    public void getSeries_channelNotInDeviceReturnsChannelError() {
        CollectorChannelDO otherDevice = channel(1L, 8L, "normal");
        when(channelMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(otherDevice));

        ServiceException error = assertThrows(ServiceException.class,
                () -> trendService.getSeries(request(List.of(1L), 7L, 10 * 60, "auto", false)));
        assertEquals(CHANNEL_NOT_IN_DEVICE.getCode(), error.getCode());
    }

    @Test
    public void getSeries_rawOverlayOverTwoHoursReturnsRangeError() {
        CollectorChannelDO channel = channel(1L, 7L, "normal");
        when(channelMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(channel));

        ServiceException error = assertThrows(ServiceException.class,
                () -> trendService.getSeries(request(List.of(1L), 7L, 3 * 60 * 60, "auto", true)));
        assertEquals(TREND_RAW_OVERLAY_RANGE_EXCEEDED.getCode(), error.getCode());
    }

    private CollectorTrendSeriesReqVO request(List<Long> channelIds, Long deviceId, long rangeSeconds,
                                              String granularity, boolean includeRaw) {
        return fixedRequest(channelIds, BASE_MILLIS, BASE_MILLIS + rangeSeconds * 1000,
                granularity, includeRaw, deviceId);
    }

    private CollectorTrendSeriesReqVO fixedRequest(List<Long> channelIds, long from, long to,
                                                   String granularity, boolean includeRaw) {
        return fixedRequest(channelIds, from, to, granularity, includeRaw, 7L);
    }

    private CollectorTrendSeriesReqVO fixedRequest(List<Long> channelIds, long from, long to,
                                                   String granularity, boolean includeRaw, Long deviceId) {
        CollectorTrendSeriesReqVO reqVO = new CollectorTrendSeriesReqVO();
        reqVO.setChannelIds(channelIds);
        reqVO.setDeviceId(deviceId);
        reqVO.setFrom(from);
        reqVO.setTo(to);
        reqVO.setGranularity(granularity);
        reqVO.setIncludeRaw(includeRaw);
        return reqVO;
    }

    private CollectorChannelDO channel(Long id, Long deviceId, String dataProfile) {
        CollectorChannelDO channel = new CollectorChannelDO();
        channel.setId(id);
        channel.setDeviceId(deviceId);
        channel.setChannelName("通道" + id);
        channel.setUnit("u" + id);
        channel.setDataProfile(dataProfile);
        return channel;
    }

    private CollectorTrendBucketRow bucketRow(Long channelId, long bucketMillis, Double avg,
                                              Double min, Double max, Integer count) {
        CollectorTrendBucketRow row = new CollectorTrendBucketRow();
        row.setChannelId(channelId);
        row.setBucketTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(bucketMillis), ZONE));
        row.setAvg(avg);
        row.setMin(min);
        row.setMax(max);
        row.setCount(count);
        return row;
    }

    private CollectorTrendRawPointRow rawPoint(Long channelId, long timeMillis, Double value) {
        CollectorTrendRawPointRow row = new CollectorTrendRawPointRow();
        row.setChannelId(channelId);
        row.setBucketTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(timeMillis), ZONE));
        row.setValue(value);
        return row;
    }

}
