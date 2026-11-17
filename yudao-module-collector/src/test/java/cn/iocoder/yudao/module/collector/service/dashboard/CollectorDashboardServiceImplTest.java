package cn.iocoder.yudao.module.collector.service.dashboard;

import cn.iocoder.yudao.module.collector.controller.admin.dashboard.vo.CollectorDashboardLatestRespVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.dashboard.CollectorDashboardChannelRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.dashboard.CollectorDashboardDeviceRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.dashboard.CollectorDashboardSensorRow;
import cn.iocoder.yudao.module.collector.dal.mysql.dashboard.CollectorDashboardMapper;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** 固定采样时间、只推进 serverTime，验证看板派生状态不会因数据未变化而冻结。 */
public class CollectorDashboardServiceImplTest {

    private static final LocalDateTime SAMPLE_TIME = LocalDateTime.of(2026, 11, 17, 12, 0, 0);
    private static final int POLL_INTERVAL_MS = 60_000;

    private CollectorDashboardServiceImpl service;

    @Before
    public void setUp() throws Exception {
        service = new CollectorDashboardServiceImpl();
        Field mapperField = CollectorDashboardServiceImpl.class
                .getDeclaredField("dashboardMapper");
        mapperField.setAccessible(true);
        mapperField.set(service, new FixedMapper());
    }

    @Test
    public void derivedState_changesWithServerTimeEvenWhenSampleIsFrozen() {
        LocalDateTime threeTimes = SAMPLE_TIME.plusSeconds(180);
        CollectorDashboardLatestRespVO atBoundary = service.getLatest(7L, threeTimes);
        CollectorDashboardLatestRespVO.Channel channel = firstChannel(atBoundary);
        assertFalse(channel.getStale());
        assertFalse(atBoundary.getDevice().getStaleOffline());
        assertTrue(atBoundary.getDevice().getOnline());
        assertEquals(Integer.valueOf(2), channel.getQuality());

        CollectorDashboardLatestRespVO afterThree = service.getLatest(
                7L, threeTimes.plusNanos(1_000_000));
        assertTrue(firstChannel(afterThree).getStale());
        assertFalse(afterThree.getDevice().getStaleOffline());
        assertTrue(afterThree.getDevice().getOnline());
        // 存储质量 2 表示坏质量，不因时间推移改写为“陈旧”。
        assertEquals(Integer.valueOf(2), firstChannel(afterThree).getQuality());

        LocalDateTime tenTimes = SAMPLE_TIME.plusSeconds(600);
        CollectorDashboardLatestRespVO atOfflineBoundary = service.getLatest(7L, tenTimes);
        assertFalse(atOfflineBoundary.getDevice().getStaleOffline());
        assertTrue(atOfflineBoundary.getDevice().getOnline());

        CollectorDashboardLatestRespVO afterTen = service.getLatest(
                7L, tenTimes.plusNanos(1_000_000));
        assertTrue(afterTen.getDevice().getStaleOffline());
        assertFalse(afterTen.getDevice().getOnline());
        assertTrue(firstChannel(afterTen).getStale());
    }

    @Test
    public void response_isCompleteWithoutConditionalRequestState() {
        CollectorDashboardLatestRespVO response = service.getLatest(
                7L, SAMPLE_TIME.plusSeconds(180));

        assertEquals(SAMPLE_TIME.plusSeconds(180), response.getServerTime());
        assertEquals(7L, response.getDevice().getId().longValue());
        assertEquals("测试设备", response.getDevice().getName());
        assertEquals(Integer.valueOf(POLL_INTERVAL_MS), response.getDevice().getPollIntervalMs());
        assertEquals(1, response.getSensors().size());
        assertEquals(3, response.getSensors().get(0).getChannels().size());

        CollectorDashboardLatestRespVO.Channel enumChannel =
                response.getSensors().get(0).getChannels().get(1);
        assertEquals("enum", enumChannel.getValueKind());
        assertEquals("11", enumChannel.getValueText());
        assertEquals(Integer.valueOf(0), enumChannel.getQuality());
        assertFalse(enumChannel.getStale());
    }

    @Test
    public void channelWithoutSampleHasNoQualityOrStaleState() {
        CollectorDashboardLatestRespVO response = service.getLatest(7L, SAMPLE_TIME);
        CollectorDashboardLatestRespVO.Channel empty =
                response.getSensors().get(0).getChannels().get(2);
        assertNull(empty.getQuality());
        assertNull(empty.getSampleTime());
        assertFalse(empty.getStale());
    }

    private static CollectorDashboardLatestRespVO.Channel firstChannel(
            CollectorDashboardLatestRespVO response) {
        return response.getSensors().get(0).getChannels().get(0);
    }

    /** 固定数据源：设备在线，两个通道有采样，一个通道尚无最新值。 */
    private static class FixedMapper implements CollectorDashboardMapper {

        @Override
        public CollectorDashboardDeviceRow selectDeviceState(Long deviceId) {
            CollectorDashboardDeviceRow row = new CollectorDashboardDeviceRow();
            row.setId(deviceId);
            row.setName("测试设备");
            row.setOnline(1);
            row.setLastPollAt(SAMPLE_TIME.plusSeconds(1));
            row.setLastWriteLagMs(3);
            row.setConsecutiveFailures(0);
            row.setPollIntervalMs(POLL_INTERVAL_MS);
            return row;
        }

        @Override
        public List<CollectorDashboardSensorRow> selectSensors(Long deviceId) {
            CollectorDashboardSensorRow row = new CollectorDashboardSensorRow();
            row.setId(10L);
            row.setName("CS1000");
            row.setModel("CS1000");
            return List.of(row);
        }

        @Override
        public List<CollectorDashboardChannelRow> selectChannelLatest(Long deviceId) {
            return List.of(
                    channelRow(101L, "iso_4", "number", new BigDecimal("12.5"),
                            null, 2, SAMPLE_TIME),
                    channelRow(102L, "flow_state", "enum", null, "11", 0, SAMPLE_TIME),
                    channelRow(103L, "temperature_c", "number", null, null, null, null));
        }

        private CollectorDashboardChannelRow channelRow(Long id, String name, String valueKind,
                                                        BigDecimal valueNum, String valueText,
                                                        Integer quality, LocalDateTime sampleTime) {
            CollectorDashboardChannelRow row = new CollectorDashboardChannelRow();
            row.setId(id);
            row.setSensorId(10L);
            row.setName(name);
            row.setValueKind(valueKind);
            row.setValueNum(valueNum);
            row.setValueText(valueText);
            row.setQuality(quality);
            row.setSampleTime(sampleTime);
            return row;
        }

    }

}
