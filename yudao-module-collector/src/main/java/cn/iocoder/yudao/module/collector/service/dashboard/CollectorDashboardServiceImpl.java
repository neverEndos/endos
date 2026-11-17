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

    @Override
    @Transactional(readOnly = true)
    public CollectorDashboardLatestRespVO getLatest(Long deviceId) {
        // 固定三次查询：设备状态/心跳、传感器/型号、全部通道/最新值。
        CollectorDashboardDeviceRow deviceRow = dashboardMapper.selectDeviceState(deviceId);
        if (deviceRow == null) {
            throw exception(DEVICE_NOT_EXISTS);
        }
        List<CollectorDashboardSensorRow> sensorRows = dashboardMapper.selectSensors(deviceId);
        List<CollectorDashboardChannelRow> channelRows = dashboardMapper.selectChannelLatest(deviceId);

        LocalDateTime serverTime = LocalDateTime.now();
        LocalDateTime maxSampleTime = channelRows.stream()
                .map(CollectorDashboardChannelRow::getSampleTime)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        int pollIntervalMs = deviceRow.getPollIntervalMs() != null && deviceRow.getPollIntervalMs() > 0
                ? deviceRow.getPollIntervalMs() : CollectorDashboardUtils.DEFAULT_POLL_INTERVAL_MS;
        boolean staleOffline = CollectorDashboardUtils.isStaleOffline(
                maxSampleTime, serverTime, pollIntervalMs);

        CollectorDashboardLatestRespVO respVO = new CollectorDashboardLatestRespVO();
        respVO.setDevice(buildDevice(deviceRow, pollIntervalMs, staleOffline));
        respVO.setSensors(buildSensors(sensorRows, channelRows, serverTime, pollIntervalMs));
        respVO.setServerTime(serverTime);
        respVO.setVersionToken(CollectorDashboardUtils.createVersionToken(
                maxSampleTime, deviceRow.getStatusUpdateTime()));
        return respVO;
    }

    private CollectorDashboardLatestRespVO.Device buildDevice(CollectorDashboardDeviceRow row,
                                                               int pollIntervalMs,
                                                               boolean staleOffline) {
        CollectorDashboardLatestRespVO.Device device = new CollectorDashboardLatestRespVO.Device();
        device.setId(row.getId());
        device.setName(row.getName());
        device.setOnline(Integer.valueOf(1).equals(row.getOnline()) && !staleOffline);
        device.setLastPollAt(row.getLastPollAt());
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
            channel.setQuality(CollectorDashboardUtils.effectiveQuality(
                    row.getQuality(), row.getSampleTime(), serverTime, pollIntervalMs));
            channel.setSampleTime(row.getSampleTime());
            sensor.getChannels().add(channel);
        }
        return new ArrayList<>(sensorMap.values());
    }

}
