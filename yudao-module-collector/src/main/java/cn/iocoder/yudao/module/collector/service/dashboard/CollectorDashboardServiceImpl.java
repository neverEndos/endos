package cn.iocoder.yudao.module.collector.service.dashboard;

import cn.iocoder.yudao.module.collector.controller.admin.dashboard.vo.CollectorDashboardLatestRespVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.dashboard.CollectorDashboardChannelRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.dashboard.CollectorDashboardDeviceRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.dashboard.CollectorDashboardSensorRow;
import cn.iocoder.yudao.module.collector.dal.mysql.dashboard.CollectorDashboardMapper;
import cn.iocoder.yudao.module.collector.util.CollectorDashboardUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.DEVICE_NOT_EXISTS;

@Service
public class CollectorDashboardServiceImpl implements CollectorDashboardService {

    @Resource
    private CollectorDashboardMapper dashboardMapper;

    /** 看板统一使用 Asia/Shanghai 墙钟；单测可注入固定时钟验证派生状态随时间变化。 */
    private Clock clock = Clock.system(CollectorDashboardUtils.SHANGHAI_ZONE);

    @Override
    @Transactional(readOnly = true)
    public CollectorDashboardLatestRespVO getLatest(Long deviceId) {
        return getLatest(deviceId, LocalDateTime.now(clock));
    }

    /**
     * 固定 serverTime 的入口，供单测在不同时间点复用同一份采样数据。
     * 接口每次返回完整响应，因此 online/staleOffline/stale 会随 serverTime 变化。
     */
    CollectorDashboardLatestRespVO getLatest(Long deviceId, LocalDateTime serverTime) {
        // 固定三次查询：设备状态/心跳、传感器/型号、全部启用通道/最新值。
        CollectorDashboardDeviceRow deviceRow = dashboardMapper.selectDeviceState(deviceId);
        if (deviceRow == null) {
            throw exception(DEVICE_NOT_EXISTS);
        }
        List<CollectorDashboardSensorRow> sensorRows = dashboardMapper.selectSensors(deviceId);
        List<CollectorDashboardChannelRow> channelRows = dashboardMapper.selectChannelLatest(deviceId);

        // 设备活性采样独立查询，不受看板“仅显示启用通道”的过滤影响。
        LocalDateTime maxSampleTime = deviceRow.getLastSampleTime();
        // v1 以设备级 poll_interval_ms 判定陈旧；通道级轮询尚未落地。
        int pollIntervalMs = deviceRow.getPollIntervalMs() != null && deviceRow.getPollIntervalMs() > 0
                ? deviceRow.getPollIntervalMs() : CollectorDashboardUtils.DEFAULT_POLL_INTERVAL_MS;
        // 没有启用通道采样时，以设备状态更新时间判断采集器活性；两者都缺失则直接离线。
        LocalDateTime activityTime = maxSampleTime != null ? maxSampleTime : deviceRow.getLastPollAt();
        boolean staleOffline = CollectorDashboardUtils.isStaleOffline(
                activityTime, serverTime, pollIntervalMs);

        CollectorDashboardLatestRespVO respVO = new CollectorDashboardLatestRespVO();
        respVO.setDevice(buildDevice(deviceRow, pollIntervalMs, staleOffline));
        respVO.setSensors(buildSensors(sensorRows, channelRows, serverTime, pollIntervalMs));
        respVO.setServerTime(CollectorDashboardUtils.toEpochMilli(serverTime));
        return respVO;
    }

    private CollectorDashboardLatestRespVO.Device buildDevice(CollectorDashboardDeviceRow row,
                                                               int pollIntervalMs,
                                                               boolean staleOffline) {
        CollectorDashboardLatestRespVO.Device device = new CollectorDashboardLatestRespVO.Device();
        device.setId(row.getId());
        device.setName(row.getName());
        device.setOnline(Integer.valueOf(1).equals(row.getOnline()) && !staleOffline);
        device.setLastPollAt(CollectorDashboardUtils.toEpochMilli(row.getLastPollAt()));
        // 心跳是采集器进程级记录，不是设备级；字段名保留以兼容契约。
        device.setLastWriteLagMs(row.getLastWriteLagMs());
        device.setConsecutiveFailures(row.getConsecutiveFailures());
        device.setPollIntervalMs(pollIntervalMs);
        device.setStaleOffline(staleOffline);
        return device;
    }

    private List<CollectorDashboardLatestRespVO.Sensor> buildSensors(
            List<CollectorDashboardSensorRow> sensorRows,
            List<CollectorDashboardChannelRow> channelRows,
            LocalDateTime serverTime, int pollIntervalMs) {
        Map<Long, CollectorDashboardLatestRespVO.Sensor> sensorMap = new LinkedHashMap<>();
        for (CollectorDashboardSensorRow row : sensorRows) {
            CollectorDashboardLatestRespVO.Sensor sensor = new CollectorDashboardLatestRespVO.Sensor();
            sensor.setId(row.getId());
            sensor.setName(row.getName());
            sensor.setModel(row.getModel());
            sensor.setChannels(new ArrayList<>());
            sensorMap.put(row.getId(), sensor);
        }
        for (CollectorDashboardChannelRow row : channelRows) {
            CollectorDashboardLatestRespVO.Sensor sensor = sensorMap.get(row.getSensorId());
            if (sensor == null) {
                continue;
            }
            CollectorDashboardLatestRespVO.Channel channel = new CollectorDashboardLatestRespVO.Channel();
            channel.setId(row.getId());
            channel.setName(row.getName());
            channel.setUnit(row.getUnit());
            channel.setValueKind(row.getValueKind());
            channel.setValueNum(row.getValueNum());
            channel.setValueText(row.getValueText());
            channel.setQuality(CollectorDashboardUtils.storedQuality(
                    row.getQuality(), row.getSampleTime()));
            channel.setStale(CollectorDashboardUtils.isStale(
                    row.getSampleTime(), serverTime, pollIntervalMs));
            channel.setSampleTime(CollectorDashboardUtils.toEpochMilli(row.getSampleTime()));
            sensor.getChannels().add(channel);
        }
        return new ArrayList<>(sensorMap.values());
    }

}
