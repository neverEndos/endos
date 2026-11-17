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
import java.util.Objects;
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
            CollectorAlarmDO activeAlarm = activeAlarmMap.get(sample.getChannelId());

            // 总开关关闭、enum 或通道已删除：自动恢复并写入独立 cleared 事件，而不是静默跳过。
            String suppressionReason = evaluator.suppressionReason(channel);
            if (suppressionReason != null) {
                int previousLevel = previousLevel(state, activeAlarm);
                boolean insertState = state == null;
                if (insertState) {
                    if (channel == null && activeAlarm == null) {
                        // 通道和历史状态都不存在，没有可恢复或可落库的对象。
                        continue;
                    }
                    state = newState(channel, activeAlarm, sample);
                }
                if (previousLevel > 0 || activeAlarm != null) {
                    if (activeAlarm != null) {
                        closeActiveAlarm(activeAlarm, sample.getCollectTime(), suppressionReason);
                        activeAlarmMap.remove(sample.getChannelId());
                    }
                    CollectorAlarmDO cleared = createClearedAlarm(
                            channel, state, activeAlarm, sample, previousLevel, suppressionReason);
                    alarmMapper.insert(cleared);
                    transitionCount++;
                }
                updateSuppressedState(state, sample, updatedTime);
                saveState(state, insertState);
                stateMap.put(sample.getChannelId(), state);
                continue;
            }

            CollectorAlarmEvaluator.Decision decision = evaluator.evaluate(channel, sample.getValue(), state);
            if (decision == null) {
                continue;
            }
            boolean insertState = state == null;
            if (insertState) {
                state = newState(channel, null, sample);
            }
            int previousLevel = previousLevel(state, activeAlarm);
            String previousThresholdType = state.getThresholdType();
            boolean thresholdChanged = decision.level() > 0 && decision.level() == previousLevel
                    && !Objects.equals(previousThresholdType, decision.thresholdType());
            if (previousLevel != decision.level() || thresholdChanged) {
                if (activeAlarm != null) {
                    String eventType = transitionEventType(previousLevel, decision.level(), thresholdChanged);
                    closeActiveAlarm(activeAlarm, sample.getCollectTime(), String.format(
                            "被后续 %s 事件接替（级别 %d→%d，阈值 %s→%s）", eventType, previousLevel,
                            decision.level(), previousThresholdType, decision.thresholdType()));
                    activeAlarmMap.remove(sample.getChannelId());
                }
                if (decision.level() > 0) {
                    CollectorAlarmDO alarm = createActiveAlarm(channel, sample, decision, previousLevel,
                            previousThresholdType, thresholdChanged);
                    alarmMapper.insert(alarm);
                    activeAlarmMap.put(sample.getChannelId(), alarm);
                } else {
                    String clearReason = "数值恢复正常";
                    CollectorAlarmDO cleared = createClearedAlarm(
                            channel, state, activeAlarm, sample, previousLevel, clearReason);
                    alarmMapper.insert(cleared);
                }
                transitionCount++;
            } else if (decision.level() > 0 && activeAlarm != null) {
                // 同一级别且同一阈值持续命中：只刷新活动事件，不追加事件行。
                activeAlarm.setLastSeenAt(sample.getCollectTime());
                activeAlarm.setOccurrenceCount(activeAlarm.getOccurrenceCount() == null
                        ? 2 : activeAlarm.getOccurrenceCount() + 1);
                alarmMapper.updateById(activeAlarm);
            }
            updateState(state, sample, decision, previousLevel, previousThresholdType, updatedTime);
            saveState(state, insertState);
            stateMap.put(sample.getChannelId(), state);
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
        // 唯一约束 + ON CONFLICT DO NOTHING，避免并发实例重复初始化游标。
        boolean inserted = cursorMapper.insertIgnore(cursor) > 0;
        log.info("[initializeCursor][报警游标初始化{}，历史采样不回放，lastSampleTime={}, lastSampleId={}]",
                inserted ? "成功" : "已被并发实例完成",
                cursor.getLastSampleTime(), cursor.getLastSampleId());
        return true;
    }

    private CollectorAlarmStateDO newState(CollectorChannelDO channel, CollectorAlarmDO activeAlarm,
                                           CollectorSampleRawDO sample) {
        CollectorAlarmStateDO state = new CollectorAlarmStateDO();
        state.setChannelId(sample.getChannelId());
        state.setDeviceId(channel != null ? channel.getDeviceId() : activeAlarm.getDeviceId());
        state.setSensorId(channel != null ? channel.getSensorId() : activeAlarm.getSensorId());
        state.setLevel(0);
        return state;
    }

    private int previousLevel(CollectorAlarmStateDO state, CollectorAlarmDO activeAlarm) {
        int stateLevel = state == null || state.getLevel() == null ? 0 : state.getLevel();
        int alarmLevel = activeAlarm == null || activeAlarm.getLevel() == null ? 0 : activeAlarm.getLevel();
        return Math.max(stateLevel, alarmLevel);
    }

    private void updateState(CollectorAlarmStateDO state, CollectorSampleRawDO sample,
                             CollectorAlarmEvaluator.Decision decision, int previousLevel,
                             String previousThresholdType, LocalDateTime updatedTime) {
        boolean thresholdChanged = !Objects.equals(previousThresholdType, decision.thresholdType());
        state.setLevel(decision.level());
        state.setThresholdType(decision.thresholdType());
        if (decision.level() == 0) {
            state.setSince(null);
        } else if (previousLevel != decision.level() || thresholdChanged || state.getSince() == null) {
            state.setSince(sample.getCollectTime());
        }
        state.setLastValue(sample.getValue());
        state.setLastThreshold(decision.thresholdValue());
        state.setLastSampleTime(sample.getCollectTime());
        state.setLastSampleId(sample.getSampleId());
        state.setUpdatedTime(updatedTime);
    }

    private void updateSuppressedState(CollectorAlarmStateDO state, CollectorSampleRawDO sample,
                                       LocalDateTime updatedTime) {
        state.setLevel(0);
        state.setThresholdType(null);
        state.setSince(null);
        state.setLastValue(sample.getValue());
        state.setLastThreshold(null);
        state.setLastSampleTime(sample.getCollectTime());
        state.setLastSampleId(sample.getSampleId());
        state.setUpdatedTime(updatedTime);
    }

    private void saveState(CollectorAlarmStateDO state, boolean insert) {
        if (insert) {
            stateMapper.insert(state);
        } else {
            stateMapper.updateById(state);
        }
    }

    private CollectorAlarmDO createActiveAlarm(CollectorChannelDO channel, CollectorSampleRawDO sample,
                                               CollectorAlarmEvaluator.Decision decision, int previousLevel,
                                               String previousThresholdType, boolean thresholdChanged) {
        String eventType = transitionEventType(previousLevel, decision.level(), thresholdChanged);
        CollectorAlarmDO alarm = new CollectorAlarmDO();
        alarm.setDeviceId(channel.getDeviceId());
        alarm.setSensorId(channel.getSensorId());
        alarm.setChannelId(channel.getId());
        alarm.setAlarmType(decision.thresholdType());
        alarm.setEventType(eventType);
        alarm.setLevel(decision.level());
        alarm.setDedupeKey(buildDedupeKey(channel, decision.thresholdType()));
        alarm.setStatus(STATUS_ACTIVE);
        alarm.setMessage(buildActiveMessage(sample, decision, previousLevel, previousThresholdType));
        alarm.setTriggerValue(sample.getValue());
        alarm.setThresholdValue(decision.thresholdValue());
        alarm.setTriggeredAt(sample.getCollectTime());
        alarm.setLastSeenAt(sample.getCollectTime());
        alarm.setOccurrenceCount(1);
        return alarm;
    }

    private CollectorAlarmDO createClearedAlarm(CollectorChannelDO channel, CollectorAlarmStateDO state,
                                                CollectorAlarmDO activeAlarm, CollectorSampleRawDO sample,
                                                int previousLevel, String clearReason) {
        String thresholdType = clearThresholdType(state, activeAlarm);
        // alarm_type 为非空列；极端脏数据下使用 unknown，避免恢复事件写入失败。
        thresholdType = thresholdType == null ? "unknown" : thresholdType;
        Double thresholdValue = state != null && state.getLastThreshold() != null
                ? state.getLastThreshold() : activeAlarm == null ? null : activeAlarm.getThresholdValue();
        Long deviceId = resolveDeviceId(channel, state, activeAlarm);
        Long sensorId = resolveSensorId(channel, state, activeAlarm);
        CollectorAlarmDO alarm = new CollectorAlarmDO();
        alarm.setDeviceId(deviceId);
        alarm.setSensorId(sensorId);
        alarm.setChannelId(sample.getChannelId());
        alarm.setAlarmType(thresholdType);
        alarm.setEventType("cleared");
        alarm.setLevel(previousLevel);
        alarm.setDedupeKey(buildDedupeKey(deviceId, sensorId, sample.getChannelId(), thresholdType));
        alarm.setStatus(STATUS_CLEARED);
        alarm.setMessage(String.format("通道值 %.6g 已恢复（%s）", sample.getValue(), clearReason));
        alarm.setTriggerValue(sample.getValue());
        alarm.setThresholdValue(thresholdValue);
        alarm.setTriggeredAt(sample.getCollectTime());
        alarm.setLastSeenAt(sample.getCollectTime());
        alarm.setOccurrenceCount(1);
        alarm.setClearedAt(sample.getCollectTime());
        alarm.setClearReason(clearReason);
        return alarm;
    }

    /**
     * 关闭上一活动事件：只标记结束，绝不改写其 event_type，历史事件保持原始语义。
     */
    private void closeActiveAlarm(CollectorAlarmDO alarm, LocalDateTime clearedAt, String reason) {
        alarm.setStatus(STATUS_CLEARED);
        alarm.setClearedAt(clearedAt);
        alarm.setClearReason(reason);
        alarmMapper.updateById(alarm);
    }

    private String transitionEventType(int previousLevel, int targetLevel, boolean thresholdChanged) {
        if (previousLevel == 0) {
            return "raised";
        }
        if (targetLevel > previousLevel) {
            return "upgraded";
        }
        if (targetLevel < previousLevel) {
            return "downgraded";
        }
        return thresholdChanged ? "changed" : "raised";
    }

    private String buildActiveMessage(CollectorSampleRawDO sample, CollectorAlarmEvaluator.Decision decision,
                                      int previousLevel, String previousThresholdType) {
        if (previousLevel > 0 && previousLevel == decision.level()
                && !Objects.equals(previousThresholdType, decision.thresholdType())) {
            return String.format("通道值 %.6g 阈值侧切换：%s → %s", sample.getValue(),
                    thresholdName(previousThresholdType), thresholdName(decision.thresholdType()));
        }
        return String.format("通道值 %.6g 命中%s", sample.getValue(), thresholdName(decision.thresholdType()));
    }

    private String clearThresholdType(CollectorAlarmStateDO state, CollectorAlarmDO activeAlarm) {
        if (state != null && state.getLevel() != null && state.getLevel() > 0) {
            return state.getThresholdType();
        }
        return activeAlarm == null ? null : activeAlarm.getAlarmType();
    }

    private Long resolveDeviceId(CollectorChannelDO channel, CollectorAlarmStateDO state, CollectorAlarmDO alarm) {
        if (channel != null) {
            return channel.getDeviceId();
        }
        if (state != null && state.getDeviceId() != null) {
            return state.getDeviceId();
        }
        return alarm == null ? null : alarm.getDeviceId();
    }

    private Long resolveSensorId(CollectorChannelDO channel, CollectorAlarmStateDO state, CollectorAlarmDO alarm) {
        if (channel != null) {
            return channel.getSensorId();
        }
        if (state != null && state.getSensorId() != null) {
            return state.getSensorId();
        }
        return alarm == null ? null : alarm.getSensorId();
    }

    private String buildDedupeKey(Long deviceId, Long sensorId, Long channelId, String thresholdType) {
        return String.format("dev:%s:sensor:%s:ch:%s:type:%s",
                deviceId, sensorId, channelId, thresholdType);
    }

    private String buildDedupeKey(CollectorChannelDO channel, String thresholdType) {
        return buildDedupeKey(channel.getDeviceId(), channel.getSensorId(), channel.getId(), thresholdType);
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
