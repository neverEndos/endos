package cn.iocoder.yudao.module.collector.service.sensor;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.collector.controller.admin.sensor.vo.CollectorGenerateChannelPreviewRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.sensor.vo.CollectorGenerateChannelsRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.sensor.vo.CollectorSensorPageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.sensor.vo.CollectorSensorSaveReqVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.sensor.CollectorSensorDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.sensortemplate.CollectorSensorTemplateDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.sensortype.CollectorSensorTypeDO;
import cn.iocoder.yudao.module.collector.dal.mysql.channel.CollectorChannelMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.reference.CollectorReferenceMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.sensor.CollectorSensorMapper;
import cn.iocoder.yudao.module.collector.service.device.CollectorDeviceService;
import cn.iocoder.yudao.module.collector.service.protocol.CollectorProtocolService;
import cn.iocoder.yudao.module.collector.service.sensortemplate.CollectorSensorTemplateService;
import cn.iocoder.yudao.module.collector.service.sensortype.CollectorSensorTypeService;
import cn.iocoder.yudao.module.collector.util.CollectorThresholdUtils;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.SENSOR_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.SENSOR_HAS_CHILDREN;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.SENSOR_NOT_EXISTS;

@Service
public class CollectorSensorServiceImpl implements CollectorSensorService {

    private static final String VALUE_KIND_ENUM = "enum";

    @Resource
    private CollectorSensorMapper sensorMapper;

    @Resource
    private CollectorChannelMapper channelMapper;

    @Resource
    private CollectorReferenceMapper referenceMapper;

    @Resource
    private CollectorDeviceService deviceService;

    @Resource
    private CollectorSensorTypeService sensorTypeService;

    @Resource
    private CollectorSensorTemplateService sensorTemplateService;

    @Resource
    private CollectorProtocolService protocolService;

    @Override
    public PageResult<CollectorSensorDO> getSensorPage(CollectorSensorPageReqVO pageReqVO) {
        return sensorMapper.selectPage(pageReqVO);
    }

    @Override
    public List<CollectorSensorDO> getSensorList(Long deviceId, Integer enabled) {
        return sensorMapper.selectListByDeviceIdAndEnabled(deviceId, enabled);
    }

    @Override
    public CollectorSensorDO getSensor(Long id) {
        return sensorMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSensor(CollectorSensorSaveReqVO createReqVO) {
        deviceService.validateDeviceExists(createReqVO.getDeviceId());
        CollectorSensorTypeDO sensorType = sensorTypeService.validateSensorTypeEnabled(createReqVO.getSensorTypeId());
        protocolService.validateProtocolEnabled(sensorType.getProtocolCode());
        validateSensorCodeUnique(null, createReqVO.getDeviceId(), createReqVO.getSensorCode());

        CollectorSensorDO sensor = BeanUtils.toBean(createReqVO, CollectorSensorDO.class);
        sensor.setProtocolCode(sensorType.getProtocolCode());
        sensor.setConnectionConfig(createReqVO.getConnectionConfig() != null
                ? createReqVO.getConnectionConfig() : new LinkedHashMap<>());
        if (sensor.getEnabled() == null) {
            sensor.setEnabled(1);
        }
        if (sensor.getSort() == null) {
            sensor.setSort(0);
        }
        sensorMapper.insert(sensor);

        if (Boolean.TRUE.equals(createReqVO.getAutoGenerateChannels())) {
            generateChannelsForSensor(sensor);
        }
        return sensor.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSensor(CollectorSensorSaveReqVO updateReqVO) {
        CollectorSensorDO existing = validateSensorExists(updateReqVO.getId());
        deviceService.validateDeviceExists(updateReqVO.getDeviceId());
        CollectorSensorTypeDO sensorType = sensorTypeService.validateSensorTypeExists(updateReqVO.getSensorTypeId());
        validateSensorCodeUnique(updateReqVO.getId(), updateReqVO.getDeviceId(), updateReqVO.getSensorCode());

        CollectorSensorDO updateObj = BeanUtils.toBean(updateReqVO, CollectorSensorDO.class);
        updateObj.setProtocolCode(sensorType.getProtocolCode());
        if (updateReqVO.getConnectionConfig() != null) {
            updateObj.setConnectionConfig(updateReqVO.getConnectionConfig());
        } else {
            updateObj.setConnectionConfig(null);
        }
        sensorMapper.updateById(updateObj);

        // 设备变更时同步通道的冗余 device_id，避免出现通道与传感器跨设备
        if (!existing.getDeviceId().equals(updateReqVO.getDeviceId())) {
            channelMapper.update(null, new LambdaUpdateWrapper<CollectorChannelDO>()
                    .eq(CollectorChannelDO::getSensorId, existing.getId())
                    .set(CollectorChannelDO::getDeviceId, updateReqVO.getDeviceId()));
        }
    }

    @Override
    public void deleteSensor(Long id) {
        validateSensorExists(id);
        if (channelMapper.selectCountBySensorId(id) > 0
                || referenceMapper.selectPollScheduleCountBySensorId(id) > 0) {
            throw exception(SENSOR_HAS_CHILDREN);
        }
        sensorMapper.deleteById(id);
    }

    @Override
    public CollectorSensorDO validateSensorExists(Long id) {
        CollectorSensorDO sensor = sensorMapper.selectById(id);
        if (sensor == null) {
            throw exception(SENSOR_NOT_EXISTS);
        }
        return sensor;
    }

    @Override
    public List<CollectorGenerateChannelPreviewRespVO> generateChannelsPreview(Long sensorTypeId) {
        sensorTypeService.validateSensorTypeExists(sensorTypeId);
        List<CollectorSensorTemplateDO> templates = sensorTemplateService.getEnabledTemplateListByType(sensorTypeId);
        List<CollectorGenerateChannelPreviewRespVO> result = new ArrayList<>(templates.size());
        for (CollectorSensorTemplateDO template : templates) {
            result.add(buildChannelPreview(template));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectorGenerateChannelsRespVO generateChannels(Long sensorId) {
        CollectorSensorDO sensor = validateSensorExists(sensorId);
        return generateChannelsForSensor(sensor);
    }

    /**
     * 按传感器类型下启用的模板生成通道。
     *
     * 已存在 (sensorId, channelCode) 的通道跳过，不覆盖用户已修改的配置。
     */
    private CollectorGenerateChannelsRespVO generateChannelsForSensor(CollectorSensorDO sensor) {
        if (sensor.getSensorTypeId() == null) {
            return new CollectorGenerateChannelsRespVO(0, 0, List.of());
        }
        List<CollectorSensorTemplateDO> templates =
                sensorTemplateService.getEnabledTemplateListByType(sensor.getSensorTypeId());
        int createdCount = 0;
        int skippedCount = 0;
        List<CollectorGenerateChannelPreviewRespVO> channels = new ArrayList<>(templates.size());
        for (CollectorSensorTemplateDO template : templates) {
            CollectorGenerateChannelPreviewRespVO item = buildChannelPreview(template);
            if (channelMapper.selectBySensorIdAndChannelCode(sensor.getId(), template.getChannelCode()) != null) {
                item.setSkipped(true);
                skippedCount++;
                channels.add(item);
                continue;
            }
            CollectorChannelDO channel = buildChannel(sensor, template);
            channelMapper.insert(channel);
            item.setSkipped(false);
            createdCount++;
            channels.add(item);
        }
        return new CollectorGenerateChannelsRespVO(createdCount, skippedCount, channels);
    }

    private CollectorGenerateChannelPreviewRespVO buildChannelPreview(CollectorSensorTemplateDO template) {
        CollectorGenerateChannelPreviewRespVO item = new CollectorGenerateChannelPreviewRespVO();
        item.setSensorTemplateId(template.getId());
        item.setChannelCode(template.getChannelCode());
        item.setChannelName(template.getChannelName());
        item.setValueKind(effectiveValueKind(template));
        item.setUnit(template.getUnit());
        item.setIcon(template.getIcon());
        item.setValueMin(template.getValueMin());
        item.setValueMax(template.getValueMax());
        item.setScale(template.getScale());
        item.setValueOffset(template.getValueOffset());
        item.setDecimalPlaces(template.getDecimalPlaces());
        item.setChannelConfig(copyConfig(template.getChannelConfig()));
        fillThresholdPreview(item, template);
        return item;
    }

    private CollectorChannelDO buildChannel(CollectorSensorDO sensor, CollectorSensorTemplateDO template) {
        CollectorChannelDO channel = new CollectorChannelDO();
        channel.setDeviceId(sensor.getDeviceId());
        channel.setSensorId(sensor.getId());
        channel.setSensorTemplateId(template.getId());
        channel.setChannelCode(template.getChannelCode());
        channel.setChannelName(template.getChannelName());
        channel.setEnabled(1);
        channel.setValueType("number");
        channel.setValueKind(effectiveValueKind(template));
        channel.setChannelConfig(copyConfig(template.getChannelConfig()));
        channel.setScale(template.getScale() != null ? template.getScale() : 1D);
        channel.setValueOffset(template.getValueOffset() != null ? template.getValueOffset() : 0D);
        channel.setUnit(template.getUnit());
        channel.setIcon(template.getIcon());
        channel.setDecimalPlaces(template.getDecimalPlaces() != null ? template.getDecimalPlaces() : 0);
        channel.setDeadband(0D);
        channel.setValueMin(template.getValueMin());
        channel.setValueMax(template.getValueMax());
        fillChannelThreshold(channel, template);
        channel.setAlarmDeadband(0D);
        channel.setSort(template.getSort() != null ? template.getSort() : 0);
        return channel;
    }

    private void fillThresholdPreview(CollectorGenerateChannelPreviewRespVO item,
                                      CollectorSensorTemplateDO template) {
        if (VALUE_KIND_ENUM.equals(effectiveValueKind(template))) {
            item.setThresholdLowErrorEnabled(0);
            item.setThresholdLowWarningEnabled(0);
            item.setThresholdHighWarningEnabled(0);
            item.setThresholdHighErrorEnabled(0);
            item.setAlarmEnabled(0);
            return;
        }
        item.setThresholdLowError(CollectorThresholdUtils.calculate(template.getValueMin(), template.getValueMax(),
                ratioOrDefault(template.getThresholdLowErrorRatio(), CollectorThresholdUtils.DEFAULT_LOW_ERROR_RATIO)));
        item.setThresholdLowWarning(CollectorThresholdUtils.calculate(template.getValueMin(), template.getValueMax(),
                ratioOrDefault(template.getThresholdLowWarningRatio(), CollectorThresholdUtils.DEFAULT_LOW_WARNING_RATIO)));
        item.setThresholdHighWarning(CollectorThresholdUtils.calculate(template.getValueMin(), template.getValueMax(),
                ratioOrDefault(template.getThresholdHighWarningRatio(), CollectorThresholdUtils.DEFAULT_HIGH_WARNING_RATIO)));
        item.setThresholdHighError(CollectorThresholdUtils.calculate(template.getValueMin(), template.getValueMax(),
                ratioOrDefault(template.getThresholdHighErrorRatio(), CollectorThresholdUtils.DEFAULT_HIGH_ERROR_RATIO)));
        item.setThresholdLowErrorEnabled(switchOrDefault(template.getThresholdLowErrorEnabled()));
        item.setThresholdLowWarningEnabled(switchOrDefault(template.getThresholdLowWarningEnabled()));
        item.setThresholdHighWarningEnabled(switchOrDefault(template.getThresholdHighWarningEnabled()));
        item.setThresholdHighErrorEnabled(switchOrDefault(template.getThresholdHighErrorEnabled()));
        item.setAlarmEnabled(switchOrDefault(template.getAlarmEnabled()));
    }

    private void fillChannelThreshold(CollectorChannelDO channel, CollectorSensorTemplateDO template) {
        if (VALUE_KIND_ENUM.equals(effectiveValueKind(template))) {
            channel.setThresholdLowErrorEnabled(0);
            channel.setThresholdLowWarningEnabled(0);
            channel.setThresholdHighWarningEnabled(0);
            channel.setThresholdHighErrorEnabled(0);
            channel.setAlarmEnabled(0);
            return;
        }
        channel.setThresholdLowError(CollectorThresholdUtils.calculate(template.getValueMin(), template.getValueMax(),
                ratioOrDefault(template.getThresholdLowErrorRatio(), CollectorThresholdUtils.DEFAULT_LOW_ERROR_RATIO)));
        channel.setThresholdLowWarning(CollectorThresholdUtils.calculate(template.getValueMin(), template.getValueMax(),
                ratioOrDefault(template.getThresholdLowWarningRatio(), CollectorThresholdUtils.DEFAULT_LOW_WARNING_RATIO)));
        channel.setThresholdHighWarning(CollectorThresholdUtils.calculate(template.getValueMin(), template.getValueMax(),
                ratioOrDefault(template.getThresholdHighWarningRatio(), CollectorThresholdUtils.DEFAULT_HIGH_WARNING_RATIO)));
        channel.setThresholdHighError(CollectorThresholdUtils.calculate(template.getValueMin(), template.getValueMax(),
                ratioOrDefault(template.getThresholdHighErrorRatio(), CollectorThresholdUtils.DEFAULT_HIGH_ERROR_RATIO)));
        channel.setThresholdLowErrorEnabled(switchOrDefault(template.getThresholdLowErrorEnabled()));
        channel.setThresholdLowWarningEnabled(switchOrDefault(template.getThresholdLowWarningEnabled()));
        channel.setThresholdHighWarningEnabled(switchOrDefault(template.getThresholdHighWarningEnabled()));
        channel.setThresholdHighErrorEnabled(switchOrDefault(template.getThresholdHighErrorEnabled()));
        channel.setAlarmEnabled(switchOrDefault(template.getAlarmEnabled()));
    }

    private static String effectiveValueKind(CollectorSensorTemplateDO template) {
        return VALUE_KIND_ENUM.equals(template.getValueKind()) ? VALUE_KIND_ENUM : "number";
    }

    private static Double ratioOrDefault(Double ratio, double defaultValue) {
        return ratio != null ? ratio : defaultValue;
    }

    private static Integer switchOrDefault(Integer value) {
        return value != null ? value : 1;
    }

    private static Map<String, Object> copyConfig(Map<String, Object> config) {
        return config == null ? new LinkedHashMap<>() : new LinkedHashMap<>(config);
    }

    private void validateSensorCodeUnique(Long id, Long deviceId, String sensorCode) {
        CollectorSensorDO sensor = sensorMapper.selectByDeviceIdAndSensorCode(deviceId, sensorCode);
        if (sensor == null) {
            return;
        }
        if (id == null || !sensor.getId().equals(id)) {
            throw exception(SENSOR_CODE_DUPLICATE, sensorCode);
        }
    }

}
