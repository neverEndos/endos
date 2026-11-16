package cn.iocoder.yudao.module.collector.service.sensortemplate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.collector.controller.admin.sensortemplate.vo.CollectorSensorTemplatePageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.sensortemplate.vo.CollectorSensorTemplateSaveReqVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.sensortemplate.CollectorSensorTemplateDO;
import cn.iocoder.yudao.module.collector.dal.mysql.channel.CollectorChannelMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.sensortemplate.CollectorSensorTemplateMapper;
import cn.iocoder.yudao.module.collector.service.sensortype.CollectorSensorTypeService;
import cn.iocoder.yudao.module.collector.util.CollectorThresholdUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.SENSOR_TEMPLATE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.SENSOR_TEMPLATE_DECIMAL_INVALID;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.SENSOR_TEMPLATE_HAS_CHILDREN;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.SENSOR_TEMPLATE_NOT_EXISTS;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.SENSOR_TEMPLATE_RANGE_INVALID;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.SENSOR_TEMPLATE_RATIO_INVALID;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.SENSOR_TEMPLATE_SCALE_INVALID;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.SENSOR_TEMPLATE_SWITCH_INVALID;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.SENSOR_TEMPLATE_VALUE_KIND_INVALID;

@Service
public class CollectorSensorTemplateServiceImpl implements CollectorSensorTemplateService {

    private static final String VALUE_KIND_NUMBER = "number";
    private static final String VALUE_KIND_ENUM = "enum";

    @Resource
    private CollectorSensorTemplateMapper sensorTemplateMapper;

    @Resource
    private CollectorChannelMapper channelMapper;

    @Resource
    private CollectorSensorTypeService sensorTypeService;

    @Override
    public PageResult<CollectorSensorTemplateDO> getTemplatePage(CollectorSensorTemplatePageReqVO pageReqVO) {
        return sensorTemplateMapper.selectPage(pageReqVO);
    }

    @Override
    public List<CollectorSensorTemplateDO> getTemplateListByType(Long sensorTypeId, Integer enabled) {
        sensorTypeService.validateSensorTypeExists(sensorTypeId);
        return sensorTemplateMapper.selectListBySensorTypeId(sensorTypeId, enabled);
    }

    @Override
    public List<CollectorSensorTemplateDO> getEnabledTemplateListByType(Long sensorTypeId) {
        return sensorTemplateMapper.selectListBySensorTypeId(sensorTypeId, 1);
    }

    @Override
    public CollectorSensorTemplateDO getTemplate(Long id) {
        return sensorTemplateMapper.selectById(id);
    }

    @Override
    public Long createTemplate(CollectorSensorTemplateSaveReqVO createReqVO) {
        sensorTypeService.validateSensorTypeExists(createReqVO.getSensorTypeId());
        validateChannelCodeUnique(null, createReqVO.getSensorTypeId(), createReqVO.getChannelCode());
        CollectorSensorTemplateDO template = BeanUtils.toBean(createReqVO, CollectorSensorTemplateDO.class);
        fillCreateDefaults(template);
        validateAndNormalize(template);
        sensorTemplateMapper.insert(template);
        return template.getId();
    }

    @Override
    public void updateTemplate(CollectorSensorTemplateSaveReqVO updateReqVO) {
        CollectorSensorTemplateDO existing = validateTemplateExists(updateReqVO.getId());
        sensorTypeService.validateSensorTypeExists(updateReqVO.getSensorTypeId());
        validateChannelCodeUnique(updateReqVO.getId(), updateReqVO.getSensorTypeId(), updateReqVO.getChannelCode());
        CollectorSensorTemplateDO updateObj = BeanUtils.toBean(updateReqVO, CollectorSensorTemplateDO.class);
        fillMissingFromExisting(updateObj, existing);
        validateAndNormalize(updateObj);
        sensorTemplateMapper.updateById(updateObj);
    }

    @Override
    public void deleteTemplate(Long id) {
        validateTemplateExists(id);
        if (channelMapper.selectCountBySensorTemplateId(id) > 0) {
            throw exception(SENSOR_TEMPLATE_HAS_CHILDREN);
        }
        sensorTemplateMapper.deleteById(id);
    }

    @Override
    public CollectorSensorTemplateDO validateTemplateExists(Long id) {
        CollectorSensorTemplateDO template = sensorTemplateMapper.selectById(id);
        if (template == null) {
            throw exception(SENSOR_TEMPLATE_NOT_EXISTS);
        }
        return template;
    }

    private void validateChannelCodeUnique(Long id, Long sensorTypeId, String channelCode) {
        CollectorSensorTemplateDO template = sensorTemplateMapper
                .selectBySensorTypeIdAndChannelCode(sensorTypeId, channelCode);
        if (template == null) {
            return;
        }
        if (id == null || !template.getId().equals(id)) {
            throw exception(SENSOR_TEMPLATE_CODE_DUPLICATE, channelCode);
        }
    }

    private void fillCreateDefaults(CollectorSensorTemplateDO template) {
        if (template.getValueKind() == null) {
            template.setValueKind(VALUE_KIND_NUMBER);
        }
        if (template.getScale() == null) {
            template.setScale(1D);
        }
        if (template.getValueOffset() == null) {
            template.setValueOffset(0D);
        }
        if (template.getDecimalPlaces() == null) {
            template.setDecimalPlaces(0);
        }
        if (template.getThresholdLowErrorRatio() == null) {
            template.setThresholdLowErrorRatio(CollectorThresholdUtils.DEFAULT_LOW_ERROR_RATIO);
        }
        if (template.getThresholdLowWarningRatio() == null) {
            template.setThresholdLowWarningRatio(CollectorThresholdUtils.DEFAULT_LOW_WARNING_RATIO);
        }
        if (template.getThresholdHighWarningRatio() == null) {
            template.setThresholdHighWarningRatio(CollectorThresholdUtils.DEFAULT_HIGH_WARNING_RATIO);
        }
        if (template.getThresholdHighErrorRatio() == null) {
            template.setThresholdHighErrorRatio(CollectorThresholdUtils.DEFAULT_HIGH_ERROR_RATIO);
        }
        if (template.getAlarmEnabled() == null) {
            template.setAlarmEnabled(1);
        }
        if (template.getThresholdLowErrorEnabled() == null) {
            template.setThresholdLowErrorEnabled(1);
        }
        if (template.getThresholdLowWarningEnabled() == null) {
            template.setThresholdLowWarningEnabled(1);
        }
        if (template.getThresholdHighWarningEnabled() == null) {
            template.setThresholdHighWarningEnabled(1);
        }
        if (template.getThresholdHighErrorEnabled() == null) {
            template.setThresholdHighErrorEnabled(1);
        }
        if (template.getChannelConfig() == null) {
            template.setChannelConfig(new LinkedHashMap<>());
        }
        if (template.getSort() == null) {
            template.setSort(0);
        }
        if (template.getEnabled() == null) {
            template.setEnabled(1);
        }
    }

    private void fillMissingFromExisting(CollectorSensorTemplateDO updateObj, CollectorSensorTemplateDO existing) {
        if (updateObj.getValueKind() == null) {
            updateObj.setValueKind(existing.getValueKind());
        }
        if (updateObj.getValueMin() == null) {
            updateObj.setValueMin(existing.getValueMin());
        }
        if (updateObj.getValueMax() == null) {
            updateObj.setValueMax(existing.getValueMax());
        }
        if (updateObj.getScale() == null) {
            updateObj.setScale(existing.getScale());
        }
        if (updateObj.getValueOffset() == null) {
            updateObj.setValueOffset(existing.getValueOffset());
        }
        if (updateObj.getDecimalPlaces() == null) {
            updateObj.setDecimalPlaces(existing.getDecimalPlaces());
        }
        if (updateObj.getThresholdLowErrorRatio() == null) {
            updateObj.setThresholdLowErrorRatio(existing.getThresholdLowErrorRatio());
        }
        if (updateObj.getThresholdLowWarningRatio() == null) {
            updateObj.setThresholdLowWarningRatio(existing.getThresholdLowWarningRatio());
        }
        if (updateObj.getThresholdHighWarningRatio() == null) {
            updateObj.setThresholdHighWarningRatio(existing.getThresholdHighWarningRatio());
        }
        if (updateObj.getThresholdHighErrorRatio() == null) {
            updateObj.setThresholdHighErrorRatio(existing.getThresholdHighErrorRatio());
        }
        if (updateObj.getAlarmEnabled() == null) {
            updateObj.setAlarmEnabled(existing.getAlarmEnabled());
        }
        if (updateObj.getThresholdLowErrorEnabled() == null) {
            updateObj.setThresholdLowErrorEnabled(existing.getThresholdLowErrorEnabled());
        }
        if (updateObj.getThresholdLowWarningEnabled() == null) {
            updateObj.setThresholdLowWarningEnabled(existing.getThresholdLowWarningEnabled());
        }
        if (updateObj.getThresholdHighWarningEnabled() == null) {
            updateObj.setThresholdHighWarningEnabled(existing.getThresholdHighWarningEnabled());
        }
        if (updateObj.getThresholdHighErrorEnabled() == null) {
            updateObj.setThresholdHighErrorEnabled(existing.getThresholdHighErrorEnabled());
        }
        if (updateObj.getChannelConfig() == null) {
            updateObj.setChannelConfig(existing.getChannelConfig());
        }
        if (updateObj.getSort() == null) {
            updateObj.setSort(existing.getSort());
        }
        if (updateObj.getEnabled() == null) {
            updateObj.setEnabled(existing.getEnabled());
        }
    }

    private void validateAndNormalize(CollectorSensorTemplateDO template) {
        String valueKind = template.getValueKind();
        if (!VALUE_KIND_NUMBER.equals(valueKind) && !VALUE_KIND_ENUM.equals(valueKind)) {
            throw exception(SENSOR_TEMPLATE_VALUE_KIND_INVALID, valueKind);
        }
        if (!CollectorThresholdUtils.isFinite(template.getValueMin())
                || !CollectorThresholdUtils.isFinite(template.getValueMax())
                || (template.getValueMin() != null && template.getValueMax() != null
                && template.getValueMin() > template.getValueMax())) {
            throw exception(SENSOR_TEMPLATE_RANGE_INVALID);
        }
        if (!CollectorThresholdUtils.isFinite(template.getScale())
                || !CollectorThresholdUtils.isFinite(template.getValueOffset())) {
            throw exception(SENSOR_TEMPLATE_SCALE_INVALID);
        }
        validateRatioRange(template.getThresholdLowErrorRatio());
        validateRatioRange(template.getThresholdLowWarningRatio());
        validateRatioRange(template.getThresholdHighWarningRatio());
        validateRatioRange(template.getThresholdHighErrorRatio());
        validateRatioOrder(template.getThresholdLowErrorRatio(), template.getThresholdLowWarningRatio());
        validateRatioOrder(template.getThresholdLowWarningRatio(), template.getThresholdHighWarningRatio());
        validateRatioOrder(template.getThresholdHighWarningRatio(), template.getThresholdHighErrorRatio());
        if (template.getDecimalPlaces() == null
                || template.getDecimalPlaces() < 0 || template.getDecimalPlaces() > 6) {
            throw exception(SENSOR_TEMPLATE_DECIMAL_INVALID);
        }
        if (VALUE_KIND_ENUM.equals(valueKind)) {
            template.setAlarmEnabled(0);
            template.setThresholdLowErrorEnabled(0);
            template.setThresholdLowWarningEnabled(0);
            template.setThresholdHighWarningEnabled(0);
            template.setThresholdHighErrorEnabled(0);
        } else {
            validateSwitch(template.getAlarmEnabled());
            validateSwitch(template.getThresholdLowErrorEnabled());
            validateSwitch(template.getThresholdLowWarningEnabled());
            validateSwitch(template.getThresholdHighWarningEnabled());
            validateSwitch(template.getThresholdHighErrorEnabled());
        }
        if (template.getChannelConfig() == null) {
            template.setChannelConfig(new LinkedHashMap<>());
        }
    }

    private void validateRatioRange(Double ratio) {
        if (!CollectorThresholdUtils.isFinite(ratio) || ratio == null || ratio < 0 || ratio > 1) {
            throw exception(SENSOR_TEMPLATE_RATIO_INVALID);
        }
    }

    private void validateRatioOrder(Double current, Double next) {
        if (current != null && next != null && current > next) {
            throw exception(SENSOR_TEMPLATE_RATIO_INVALID);
        }
    }

    private void validateSwitch(Integer value) {
        if (value == null || value < 0 || value > 1) {
            throw exception(SENSOR_TEMPLATE_SWITCH_INVALID);
        }
    }

}
