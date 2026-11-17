package cn.iocoder.yudao.module.collector.service.alarm;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.collector.controller.admin.alarm.vo.CollectorAlarmPageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.alarm.vo.CollectorAlarmRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.alarm.vo.CollectorAlarmStateRespVO;
import cn.iocoder.yudao.module.collector.convert.alarm.CollectorAlarmConvert;
import cn.iocoder.yudao.module.collector.dal.dataobject.alarm.CollectorAlarmCursorDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.alarm.CollectorAlarmDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.alarm.CollectorAlarmStateDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.device.CollectorDeviceDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.sample.CollectorSampleRawDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.sensor.CollectorSensorDO;
import cn.iocoder.yudao.module.collector.dal.mysql.alarm.CollectorAlarmCursorMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.alarm.CollectorAlarmMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.alarm.CollectorAlarmStateMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.channel.CollectorChannelMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.device.CollectorDeviceMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.sample.CollectorSampleRawMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.sensor.CollectorSensorMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 采集报警引擎：游标扫描、阈值判定、状态机与事件持久化。
 */
@Service
@Slf4j
public class CollectorAlarmServiceImpl implements CollectorAlarmService {

    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final int STATUS_ACTIVE = 0;
    private static final int STATUS_CLEARED = 2;

    @Resource
    private CollectorSampleRawMapper sampleRawMapper;
    @Resource
    private CollectorChannelMapper channelMapper;
    @Resource
    private CollectorDeviceMapper deviceMapper;
    @Resource
    private CollectorSensorMapper sensorMapper;
    @Resource
    private CollectorAlarmCursorMapper cursorMapper;
    @Resource
    private CollectorAlarmStateMapper stateMapper;
    @Resource
    private CollectorAlarmMapper alarmMapper;
    @Resource
    private TransactionTemplate transactionTemplate;

    private final CollectorAlarmEvaluator evaluator = new CollectorAlarmEvaluator();

    @Override
    public PageResult<CollectorAlarmRespVO> getAlarmPage(CollectorAlarmPageReqVO reqVO) {
        PageResult<CollectorAlarmDO> page = alarmMapper.selectPage(reqVO);
        PageResult<CollectorAlarmRespVO> result = CollectorAlarmConvert.INSTANCE.convertPage(page);
        Set<Long> deviceIds = result.getList().stream().map(CollectorAlarmRespVO::getDeviceId).collect(Collectors.toSet());
        Set<Long> sensorIds = result.getList().stream().map(CollectorAlarmRespVO::getSensorId).collect(Collectors.toSet());
        Set<Long> channelIds = result.getList().stream().map(CollectorAlarmRespVO::getChannelId).collect(Collectors.toSet());
        NameMaps names = loadNames(deviceIds, sensorIds, channelIds);
        result.getList().forEach(alarm -> {
            alarm.setDeviceName(names.deviceName(alarm.getDeviceId()));
            alarm.setSensorName(names.sensorName(alarm.getSensorId()));
            alarm.setChannelName(names.channelName(alarm.getChannelId()));
        });
        return result;
    }

    @Override
    public List<CollectorAlarmStateRespVO> getAlarmStateList(Long deviceId) {
        List<CollectorAlarmStateRespVO> result = CollectorAlarmConvert.INSTANCE.convertStateList(
                stateMapper.selectListByDeviceId(deviceId));
        Set<Long> deviceIds = result.stream().map(CollectorAlarmStateRespVO::getDeviceId).collect(Collectors.toSet());
        Set<Long> sensorIds = result.stream().map(CollectorAlarmStateRespVO::getSensorId).collect(Collectors.toSet());
        Set<Long> channelIds = result.stream().map(CollectorAlarmStateRespVO::getChannelId).collect(Collectors.toSet());
        NameMaps names = loadNames(deviceIds, sensorIds, channelIds);
        result.forEach(state -> {
            state.setDeviceName(names.deviceName(state.getDeviceId()));
            state.setSensorName(names.sensorName(state.getSensorId()));
            state.setChannelName(names.channelName(state.getChannelId()));
        });
        return result;
    }

    /**
     * 设备、传感器、通道各批量查询一次，避免分页名称装配的 N+1 查询。
     */
    private NameMaps loadNames(Collection<Long> deviceIds, Collection<Long> sensorIds, Collection<Long> channelIds) {
        Map<Long, String> deviceNames = deviceIds.isEmpty() ? Map.of() : deviceMapper.selectBatchIds(deviceIds).stream()
                .collect(Collectors.toMap(CollectorDeviceDO::getId, CollectorDeviceDO::getDeviceName));
        Map<Long, String> sensorNames = sensorIds.isEmpty() ? Map.of() : sensorMapper.selectBatchIds(sensorIds).stream()
                .collect(Collectors.toMap(CollectorSensorDO::getId, CollectorSensorDO::getSensorName));
        Map<Long, String> channelNames = channelIds.isEmpty() ? Map.of() : channelMapper.selectBatchIds(channelIds).stream()
                .collect(Collectors.toMap(CollectorChannelDO::getId, CollectorChannelDO::getChannelName));
        return new NameMaps(deviceNames, sensorNames, channelNames);
    }

    @Override
    public EvaluationResult evaluatePendingSamples() {
        long processedCount = 0;
        long transitionCount = 0;
        int batchCount = 0;
        boolean initialized = false;
        while (true) {
            BatchResult batch = transactionTemplate.execute(status -> evaluateBatch());
            if (batch == null) {
                break;
            }
            if (batch.initialized()) {
                initialized = true;
                break;
            }
            processedCount += batch.processedCount();
            transitionCount += batch.transitionCount();
            if (batch.processedCount() > 0) {
                batchCount++;
            }
            if (batch.processedCount() < EVALUATE_BATCH_SIZE) {
                break;
            }
        }
        return new EvaluationResult(processedCount, transitionCount, batchCount, initialized);
    }

    /**
     * 游标、状态与事件在同一事务提交；任一步失败都会回滚，游标不会前进。
     */
    private BatchResult evaluateBatch() {
        CollectorAlarmCursorDO cursor = cursorMapper.selectSingletonForUpdate();
        if (cursor == null) {
            return new BatchResult(0, 0, initializeCursor());
        }
        List<CollectorSampleRawDO> samples = sampleRawMapper.selectListAfter(
                cursor.getLastSampleTime(), cursor.getLastSampleId(), EVALUATE_BATCH_SIZE);
        if (samples.isEmpty()) {
            return new BatchResult(0, 0, false);
        }

        Set<Long> channelIds = samples.stream().map(CollectorSampleRawDO::getChannelId).collect(Collectors.toSet());
        Map<Long, CollectorChannelDO> channelMap = channelMapper.selectBatchIds(channelIds).stream()
                .collect(Collectors.toMap(CollectorChannelDO::getId, Function.identity()));
        Map<Long, CollectorAlarmStateDO> stateMap = new HashMap<>(stateMapper.selectBatchIds(channelIds).stream()
                .collect(Collectors.toMap(CollectorAlarmStateDO::getChannelId, Function.identity())));
        Map<Long, CollectorAlarmDO> activeAlarmMap = new HashMap<>(alarmMapper.selectActiveListByChannelIds(channelIds)
                .stream().collect(Collectors.toMap(CollectorAlarmDO::getChannelId, Function.identity(),
                        (left, right) -> left.getId() > right.getId() ? left : right)));

        int transitionCount = 0;
        LocalDateTime updatedTime = LocalDateTime.now();
        for (CollectorSampleRawDO sample : samples) {
            CollectorChannelDO channel = channelMap.get(sample.getChannelId());
            CollectorAlarmStateDO state = stateMap.get(sample.getChannelId());
            CollectorAlarmEvaluator.Decision decision = evaluator.evaluate(channel, sample.getValue(), state);
            if (decision == null) {
                continue;
            }
            boolean insertState = state == null;
            if (insertState) {
                state = newState(channel);
            }
            int previousLevel = state.getLevel() == null ? 0 : state.getLevel();
            if (previousLevel != decision.level()) {
                CollectorAlarmDO activeAlarm = activeAlarmMap.get(channel.getId());
                if (previousLevel > 0 && activeAlarm != null) {
                    clearActiveAlarm(activeAlarm, sample.getCollectTime(), decision.level(), previousLevel);
                    activeAlarmMap.remove(channel.getId());
                }
                if (decision.level() > 0) {
                    CollectorAlarmDO alarm = createAlarm(channel, sample, decision, previousLevel);
                    alarmMapper.insert(alarm);
                    activeAlarmMap.put(channel.getId(), alarm);
                }
                transitionCount++;
            }
            updateState(state, sample, decision, previousLevel, updatedTime);
            if (insertState) {
                stateMapper.insert(state);
            } else {
                stateMapper.updateById(state);
            }
            stateMap.put(channel.getId(), state);
        }

        CollectorSampleRawDO lastSample = samples.get(samples.size() - 1);
        cursor.setLastSampleTime(lastSample.getCollectTime());
        cursor.setLastSampleId(lastSample.getSampleId());
        cursor.setUpdatedTime(updatedTime);
        cursorMapper.updateById(cursor);
        return new BatchResult(samples.size(), transitionCount, false);
    }

    private boolean initializeCursor() {
        CollectorSampleRawDO latest = sampleRawMapper.selectLatest();
        CollectorAlarmCursorDO cursor = new CollectorAlarmCursorDO();
        cursor.setId(1);
        cursor.setLastSampleTime(latest == null ? EPOCH : latest.getCollectTime());
        cursor.setLastSampleId(latest == null ? 0L : latest.getSampleId());
        cursor.setUpdatedTime(LocalDateTime.now());
        cursorMapper.insert(cursor);
        log.info("[initializeCursor][报警游标已初始化到最新采样，历史采样不回放，lastSampleTime={}, lastSampleId={}]",
                cursor.getLastSampleTime(), cursor.getLastSampleId());
        return true;
    }

    private CollectorAlarmStateDO newState(CollectorChannelDO channel) {
        CollectorAlarmStateDO state = new CollectorAlarmStateDO();
        state.setChannelId(channel.getId());
        state.setDeviceId(channel.getDeviceId());
        state.setSensorId(channel.getSensorId());
        state.setLevel(0);
        return state;
    }

    private void updateState(CollectorAlarmStateDO state, CollectorSampleRawDO sample,
                             CollectorAlarmEvaluator.Decision decision, int previousLevel,
                             LocalDateTime updatedTime) {
        state.setLevel(decision.level());
        state.setThresholdType(decision.thresholdType());
        if (decision.level() == 0) {
            state.setSince(null);
        } else if (previousLevel != decision.level() || state.getSince() == null) {
            state.setSince(sample.getCollectTime());
        }
        state.setLastValue(sample.getValue());
        state.setLastThreshold(decision.thresholdValue());
        state.setLastSampleTime(sample.getCollectTime());
        state.setLastSampleId(sample.getSampleId());
        state.setUpdatedTime(updatedTime);
    }

    private CollectorAlarmDO createAlarm(CollectorChannelDO channel, CollectorSampleRawDO sample,
                                         CollectorAlarmEvaluator.Decision decision, int previousLevel) {
        CollectorAlarmDO alarm = new CollectorAlarmDO();
        alarm.setDeviceId(channel.getDeviceId());
        alarm.setSensorId(channel.getSensorId());
        alarm.setChannelId(channel.getId());
        alarm.setAlarmType(decision.thresholdType());
        alarm.setEventType(previousLevel == 0 ? "raised" : decision.level() > previousLevel ? "upgraded" : "downgraded");
        alarm.setLevel(decision.level());
        alarm.setDedupeKey(buildDedupeKey(channel, decision.thresholdType()));
        alarm.setStatus(STATUS_ACTIVE);
        alarm.setMessage(String.format("通道值 %.6g 命中%s", sample.getValue(), thresholdName(decision.thresholdType())));
        alarm.setTriggerValue(sample.getValue());
        alarm.setThresholdValue(decision.thresholdValue());
        alarm.setTriggeredAt(sample.getCollectTime());
        alarm.setLastSeenAt(sample.getCollectTime());
        alarm.setOccurrenceCount(1);
        return alarm;
    }

    private void clearActiveAlarm(CollectorAlarmDO alarm, LocalDateTime clearedAt,
                                  int targetLevel, int previousLevel) {
        alarm.setStatus(STATUS_CLEARED);
        alarm.setClearedAt(clearedAt);
        alarm.setClearReason(targetLevel == 0 ? "数值恢复正常" : String.format("报警等级由 %d 跃迁为 %d", previousLevel, targetLevel));
        if (targetLevel == 0) {
            alarm.setEventType("cleared");
        }
        alarmMapper.updateById(alarm);
    }

    private String buildDedupeKey(CollectorChannelDO channel, String thresholdType) {
        return String.format("dev:%d:sensor:%d:ch:%d:type:%s",
                channel.getDeviceId(), channel.getSensorId(), channel.getId(), thresholdType);
    }

    private String thresholdName(String thresholdType) {
        return switch (thresholdType) {
            case CollectorAlarmEvaluator.THRESHOLD_LOW_ERROR -> "异常下限";
            case CollectorAlarmEvaluator.THRESHOLD_LOW_WARNING -> "预警下限";
            case CollectorAlarmEvaluator.THRESHOLD_HIGH_WARNING -> "预警上限";
            case CollectorAlarmEvaluator.THRESHOLD_HIGH_ERROR -> "异常上限";
            default -> "阈值";
        };
    }

    private record NameMaps(Map<Long, String> deviceNames, Map<Long, String> sensorNames,
                            Map<Long, String> channelNames) {

        String deviceName(Long id) {
            return deviceNames.get(id);
        }

        String sensorName(Long id) {
            return sensorNames.get(id);
        }

        String channelName(Long id) {
            return channelNames.get(id);
        }

    }

    private record BatchResult(int processedCount, int transitionCount, boolean initialized) {
    }

}
