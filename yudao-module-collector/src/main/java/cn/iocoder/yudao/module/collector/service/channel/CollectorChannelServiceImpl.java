package cn.iocoder.yudao.module.collector.service.channel;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelDeviceRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelPageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelThresholdBatchUpdateReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelThresholdPreviewReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelThresholdPreviewRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelThresholdUpdateReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelUpdateReqVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.protocol.CollectorProtocolDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.sensor.CollectorSensorDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.sensortype.CollectorSensorTypeDO;
import cn.iocoder.yudao.module.collector.dal.mysql.channel.CollectorChannelMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.reference.CollectorReferenceMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.sensortype.CollectorSensorTypeMapper;
import cn.iocoder.yudao.module.collector.service.device.CollectorDeviceService;
import cn.iocoder.yudao.module.collector.service.protocol.CollectorProtocolService;
import cn.iocoder.yudao.module.collector.service.sensor.CollectorSensorService;
import cn.iocoder.yudao.module.collector.util.CollectorJsonSchemaValidator;
import cn.iocoder.yudao.module.collector.util.CollectorThresholdUtils;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.CHANNEL_ENUM_THRESHOLD_FORBIDDEN;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.CHANNEL_HAS_CHILDREN;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.CHANNEL_NOT_EXISTS;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.CHANNEL_NOT_IN_DEVICE;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.CHANNEL_THRESHOLD_INVALID;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.CHANNEL_VALUE_INVALID;

@Service
public class CollectorChannelServiceImpl implements CollectorChannelService {

    private static final String VALUE_KIND_ENUM = "enum";

    @Resource
    private CollectorChannelMapper channelMapper;

    @Resource
    private CollectorReferenceMapper referenceMapper;

    @Resource
    private CollectorSensorTypeMapper sensorTypeMapper;

    @Resource
    private CollectorDeviceService deviceService;

    @Resource
    private CollectorSensorService sensorService;

    @Resource
    private CollectorProtocolService protocolService;

    @Resource
    private CollectorJsonSchemaValidator jsonSchemaValidator;

    @Override
    public PageResult<CollectorChannelDO> getChannelPage(CollectorChannelPageReqVO pageReqVO) {
        return channelMapper.selectPage(pageReqVO);
    }

    @Override
    public CollectorChannelDO getChannel(Long id) {
        return channelMapper.selectById(id);
    }

    @Override
    public CollectorChannelDeviceRespVO getChannelListByDevice(Long deviceId) {
        deviceService.validateDeviceExists(deviceId);
        List<CollectorSensorDO> sensors = sensorService.getSensorList(deviceId, null);
        Map<Long, List<CollectorChannelDO>> channelMap = channelMapper.selectListByDeviceId(deviceId).stream()
                .collect(Collectors.groupingBy(CollectorChannelDO::getSensorId));
        Set<Long> sensorTypeIds = sensors.stream().map(CollectorSensorDO::getSensorTypeId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, CollectorSensorTypeDO> sensorTypeMap = sensorTypeIds.isEmpty() ? Map.of()
                : sensorTypeMapper.selectByIds(sensorTypeIds).stream()
                .collect(Collectors.toMap(CollectorSensorTypeDO::getId, Function.identity()));

        List<CollectorChannelDeviceRespVO.Sensor> sensorList = new ArrayList<>();
        for (CollectorSensorDO sensor : sensors) {
            List<CollectorChannelDO> channels = channelMap.get(sensor.getId());
            if (channels == null || channels.isEmpty()) {
                continue;
            }
            CollectorChannelDeviceRespVO.Sensor sensorVO = new CollectorChannelDeviceRespVO.Sensor();
            sensorVO.setId(sensor.getId());
            sensorVO.setName(sensor.getSensorName());
            CollectorSensorTypeDO sensorType = sensorTypeMap.get(sensor.getSensorTypeId());
            sensorVO.setModel(sensorType != null ? sensorType.getTypeName() : null);
            sensorVO.setChannels(BeanUtils.toBean(channels, CollectorChannelDeviceRespVO.Channel.class));
            sensorList.add(sensorVO);
        }
        CollectorChannelDeviceRespVO respVO = new CollectorChannelDeviceRespVO();
        respVO.setSensors(sensorList);
        return respVO;
    }

    @Override
    public void updateChannel(CollectorChannelUpdateReqVO updateReqVO) {
        CollectorChannelDO channel = validateChannelExists(updateReqVO.getId());
        if (!CollectorThresholdUtils.isFinite(updateReqVO.getScale())
                || !CollectorThresholdUtils.isFinite(updateReqVO.getValueOffset())) {
            throw exception(CHANNEL_VALUE_INVALID);
        }
        CollectorSensorDO sensor = sensorService.validateSensorExists(channel.getSensorId());
        CollectorProtocolDO protocol = protocolService.validateProtocolExists(sensor.getProtocolCode());
        Map<String, Object> effectiveConfig = updateReqVO.getChannelConfig() != null
                ? updateReqVO.getChannelConfig() : channel.getChannelConfig();
        jsonSchemaValidator.validate(protocol.getChannelSchema(), effectiveConfig);
        CollectorChannelDO updateObj = BeanUtils.toBean(updateReqVO, CollectorChannelDO.class);
        channelMapper.updateById(updateObj);
    }

    @Override
    public void updateChannelThreshold(CollectorChannelThresholdUpdateReqVO updateReqVO) {
        CollectorChannelDO channel = validateChannelExists(updateReqVO.getId());
        validateAndUpdateThreshold(channel, updateReqVO);
    }

    /** 单通道与批量入口共用校验和写入逻辑；批量复用已加载通道，避免逐条重查。 */
    private void validateAndUpdateThreshold(CollectorChannelDO channel,
                                            CollectorChannelThresholdUpdateReqVO updateReqVO) {
        boolean enumChannel = VALUE_KIND_ENUM.equals(channel.getValueKind());

        validateFiniteRange(updateReqVO.getValueMin(), updateReqVO.getValueMax());
        validateThresholdOrder(updateReqVO);
        Double deadband = updateReqVO.getDeadband() != null
                ? updateReqVO.getDeadband() : (channel.getDeadband() != null ? channel.getDeadband() : 0D);
        Double alarmDeadband = updateReqVO.getAlarmDeadband() != null
                ? updateReqVO.getAlarmDeadband() : (channel.getAlarmDeadband() != null ? channel.getAlarmDeadband() : 0D);
        if (!isNonNegativeFinite(deadband) || !isNonNegativeFinite(alarmDeadband)) {
            throw exception(CHANNEL_THRESHOLD_INVALID);
        }
        Integer lowErrorEnabled = switchOrDefault(updateReqVO.getThresholdLowErrorEnabled(),
                channel.getThresholdLowErrorEnabled());
        Integer lowWarningEnabled = switchOrDefault(updateReqVO.getThresholdLowWarningEnabled(),
                channel.getThresholdLowWarningEnabled());
        Integer highWarningEnabled = switchOrDefault(updateReqVO.getThresholdHighWarningEnabled(),
                channel.getThresholdHighWarningEnabled());
        Integer highErrorEnabled = switchOrDefault(updateReqVO.getThresholdHighErrorEnabled(),
                channel.getThresholdHighErrorEnabled());
        Integer alarmEnabled = switchOrDefault(updateReqVO.getAlarmEnabled(), channel.getAlarmEnabled());
        if (enumChannel) {
            lowErrorEnabled = 0;
            lowWarningEnabled = 0;
            highWarningEnabled = 0;
            highErrorEnabled = 0;
            alarmEnabled = 0;
            deadband = 0D;
            alarmDeadband = 0D;
        }

        LambdaUpdateWrapper<CollectorChannelDO> updateWrapper = new LambdaUpdateWrapper<CollectorChannelDO>()
                .eq(CollectorChannelDO::getId, channel.getId())
                .set(CollectorChannelDO::getValueMin, updateReqVO.getValueMin())
                .set(CollectorChannelDO::getValueMax, updateReqVO.getValueMax())
                .set(CollectorChannelDO::getThresholdLowError, enumChannel ? null : updateReqVO.getThresholdLowError())
                .set(CollectorChannelDO::getThresholdLowWarning, enumChannel ? null : updateReqVO.getThresholdLowWarning())
                .set(CollectorChannelDO::getThresholdHighWarning, enumChannel ? null : updateReqVO.getThresholdHighWarning())
                .set(CollectorChannelDO::getThresholdHighError, enumChannel ? null : updateReqVO.getThresholdHighError())
                .set(CollectorChannelDO::getThresholdLowErrorEnabled, lowErrorEnabled)
                .set(CollectorChannelDO::getThresholdLowWarningEnabled, lowWarningEnabled)
                .set(CollectorChannelDO::getThresholdHighWarningEnabled, highWarningEnabled)
                .set(CollectorChannelDO::getThresholdHighErrorEnabled, highErrorEnabled)
                .set(CollectorChannelDO::getAlarmEnabled, alarmEnabled)
                .set(CollectorChannelDO::getDeadband, deadband)
                .set(CollectorChannelDO::getAlarmDeadband, alarmDeadband);
        channelMapper.update(null, updateWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer updateChannelThresholdBatch(CollectorChannelThresholdBatchUpdateReqVO batchReqVO) {
        deviceService.validateDeviceExists(batchReqVO.getDeviceId());
        Map<Long, CollectorChannelDO> channelMap = channelMapper.selectListByDeviceId(batchReqVO.getDeviceId()).stream()
                .collect(Collectors.toMap(CollectorChannelDO::getId, Function.identity()));
        Set<Long> channelIds = new HashSet<>();
        for (CollectorChannelThresholdBatchUpdateReqVO.Channel item : batchReqVO.getChannels()) {
            if (!channelIds.add(item.getChannelId())) {
                throw exception(CHANNEL_THRESHOLD_INVALID);
            }
            CollectorChannelDO channel = channelMap.get(item.getChannelId());
            if (channel == null) {
                // 设备归属在 Service 统一校验，禁止借助批量接口跨设备写入。
                throw exception(CHANNEL_NOT_IN_DEVICE, item.getChannelId(), batchReqVO.getDeviceId());
            }
            if (VALUE_KIND_ENUM.equals(channel.getValueKind())) {
                throw exception(CHANNEL_ENUM_THRESHOLD_FORBIDDEN, item.getChannelId());
            }

            // 批量复用已加载通道，避免逐条重查；量程和报警死区不在矩阵中修改，保持原值。
            CollectorChannelThresholdUpdateReqVO updateReqVO = BeanUtils.toBean(item,
                    CollectorChannelThresholdUpdateReqVO.class);
            updateReqVO.setId(item.getChannelId());
            updateReqVO.setValueMin(channel.getValueMin());
            updateReqVO.setValueMax(channel.getValueMax());
            updateReqVO.setAlarmDeadband(channel.getAlarmDeadband());
            validateAndUpdateThreshold(channel, updateReqVO);
        }
        return batchReqVO.getChannels().size();
    }

    @Override
    public CollectorChannelThresholdPreviewRespVO previewThreshold(CollectorChannelThresholdPreviewReqVO previewReqVO) {
        validateFiniteRange(previewReqVO.getValueMin(), previewReqVO.getValueMax());
        Double lowErrorRatio = previewReqVO.getThresholdLowErrorRatio() != null
                ? previewReqVO.getThresholdLowErrorRatio() : CollectorThresholdUtils.DEFAULT_LOW_ERROR_RATIO;
        Double lowWarningRatio = previewReqVO.getThresholdLowWarningRatio() != null
                ? previewReqVO.getThresholdLowWarningRatio() : CollectorThresholdUtils.DEFAULT_LOW_WARNING_RATIO;
        Double highWarningRatio = previewReqVO.getThresholdHighWarningRatio() != null
                ? previewReqVO.getThresholdHighWarningRatio() : CollectorThresholdUtils.DEFAULT_HIGH_WARNING_RATIO;
        Double highErrorRatio = previewReqVO.getThresholdHighErrorRatio() != null
                ? previewReqVO.getThresholdHighErrorRatio() : CollectorThresholdUtils.DEFAULT_HIGH_ERROR_RATIO;
        validateRatio(lowErrorRatio);
        validateRatio(lowWarningRatio);
        validateRatio(highWarningRatio);
        validateRatio(highErrorRatio);
        if (lowErrorRatio > lowWarningRatio || lowWarningRatio > highWarningRatio
                || highWarningRatio > highErrorRatio) {
            throw exception(CHANNEL_THRESHOLD_INVALID);
        }

        CollectorChannelThresholdPreviewRespVO respVO = new CollectorChannelThresholdPreviewRespVO();
        respVO.setValueMin(previewReqVO.getValueMin());
        respVO.setValueMax(previewReqVO.getValueMax());
        respVO.setThresholdLowError(CollectorThresholdUtils.calculate(previewReqVO.getValueMin(),
                previewReqVO.getValueMax(), lowErrorRatio));
        respVO.setThresholdLowWarning(CollectorThresholdUtils.calculate(previewReqVO.getValueMin(),
                previewReqVO.getValueMax(), lowWarningRatio));
        respVO.setThresholdHighWarning(CollectorThresholdUtils.calculate(previewReqVO.getValueMin(),
                previewReqVO.getValueMax(), highWarningRatio));
        respVO.setThresholdHighError(CollectorThresholdUtils.calculate(previewReqVO.getValueMin(),
                previewReqVO.getValueMax(), highErrorRatio));
        return respVO;
    }

    @Override
    public void deleteChannel(Long id) {
        validateChannelExists(id);
        if (referenceMapper.selectPollScheduleCountByChannelId(id) > 0) {
            throw exception(CHANNEL_HAS_CHILDREN);
        }
        channelMapper.deleteById(id);
    }

    @Override
    public CollectorChannelDO validateChannelExists(Long id) {
        CollectorChannelDO channel = channelMapper.selectById(id);
        if (channel == null) {
            throw exception(CHANNEL_NOT_EXISTS);
        }
        return channel;
    }

    private void validateFiniteRange(Double valueMin, Double valueMax) {
        if (!CollectorThresholdUtils.isFinite(valueMin) || !CollectorThresholdUtils.isFinite(valueMax)) {
            throw exception(CHANNEL_THRESHOLD_INVALID);
        }
        if (valueMin != null && valueMax != null && valueMin > valueMax) {
            throw exception(CHANNEL_THRESHOLD_INVALID);
        }
    }

    private void validateThresholdOrder(CollectorChannelThresholdUpdateReqVO reqVO) {
        Double[] thresholds = new Double[]{
                reqVO.getThresholdLowError(), reqVO.getThresholdLowWarning(),
                reqVO.getThresholdHighWarning(), reqVO.getThresholdHighError()
        };
        for (Double threshold : thresholds) {
            if (!CollectorThresholdUtils.isFinite(threshold)) {
                throw exception(CHANNEL_THRESHOLD_INVALID);
            }
        }
        Double previous = null;
        for (Double threshold : thresholds) {
            if (threshold == null) {
                continue;
            }
            if (previous != null && threshold < previous) {
                throw exception(CHANNEL_THRESHOLD_INVALID);
            }
            previous = threshold;
        }
    }

    private void validateRatio(Double ratio) {
        if (!CollectorThresholdUtils.isFinite(ratio) || ratio == null || ratio < 0 || ratio > 1) {
            throw exception(CHANNEL_THRESHOLD_INVALID);
        }
    }

    private static boolean isNonNegativeFinite(Double value) {
        return value != null && Double.isFinite(value) && value >= 0;
    }

    private static Integer switchOrDefault(Integer value, Integer existing) {
        if (value != null) {
            return value;
        }
        return existing != null ? existing : 0;
    }

}
