package cn.iocoder.yudao.module.collector.service.channel;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelPageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelThresholdPreviewReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelThresholdPreviewRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelThresholdUpdateReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelUpdateReqVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.protocol.CollectorProtocolDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.sensor.CollectorSensorDO;
import cn.iocoder.yudao.module.collector.dal.mysql.channel.CollectorChannelMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.reference.CollectorReferenceMapper;
import cn.iocoder.yudao.module.collector.service.protocol.CollectorProtocolService;
import cn.iocoder.yudao.module.collector.service.sensor.CollectorSensorService;
import cn.iocoder.yudao.module.collector.util.CollectorJsonSchemaValidator;
import cn.iocoder.yudao.module.collector.util.CollectorThresholdUtils;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.CHANNEL_HAS_CHILDREN;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.CHANNEL_NOT_EXISTS;
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
        if (thresholds[0] != null && thresholds[1] != null && thresholds[0] > thresholds[1]
                || thresholds[1] != null && thresholds[2] != null && thresholds[1] > thresholds[2]
                || thresholds[2] != null && thresholds[3] != null && thresholds[2] > thresholds[3]) {
            throw exception(CHANNEL_THRESHOLD_INVALID);
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
