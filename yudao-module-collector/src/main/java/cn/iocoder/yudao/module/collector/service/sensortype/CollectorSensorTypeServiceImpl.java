package cn.iocoder.yudao.module.collector.service.sensortype;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.collector.controller.admin.sensortype.vo.CollectorSensorTypePageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.sensortype.vo.CollectorSensorTypeSaveReqVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.sensortype.CollectorSensorTypeDO;
import cn.iocoder.yudao.module.collector.dal.mysql.sensor.CollectorSensorMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.sensortemplate.CollectorSensorTemplateMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.sensortype.CollectorSensorTypeMapper;
import cn.iocoder.yudao.module.collector.service.protocol.CollectorProtocolService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.SENSOR_TYPE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.SENSOR_TYPE_HAS_CHILDREN;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.SENSOR_TYPE_NOT_ENABLED;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.SENSOR_TYPE_NOT_EXISTS;

@Service
public class CollectorSensorTypeServiceImpl implements CollectorSensorTypeService {

    @Resource
    private CollectorSensorTypeMapper sensorTypeMapper;

    @Resource
    private CollectorSensorTemplateMapper sensorTemplateMapper;

    @Resource
    private CollectorSensorMapper sensorMapper;

    @Resource
    private CollectorProtocolService protocolService;

    @Override
    public PageResult<CollectorSensorTypeDO> getSensorTypePage(CollectorSensorTypePageReqVO pageReqVO) {
        return sensorTypeMapper.selectPage(pageReqVO);
    }

    @Override
    public List<CollectorSensorTypeDO> getSensorTypeList(Integer enabled) {
        return sensorTypeMapper.selectListByEnabled(enabled);
    }

    @Override
    public CollectorSensorTypeDO getSensorType(Long id) {
        return sensorTypeMapper.selectById(id);
    }

    @Override
    public Long createSensorType(CollectorSensorTypeSaveReqVO createReqVO) {
        protocolService.validateProtocolExists(createReqVO.getProtocolCode());
        validateTypeCodeUnique(null, createReqVO.getTypeCode());
        CollectorSensorTypeDO sensorType = BeanUtils.toBean(createReqVO, CollectorSensorTypeDO.class);
        if (sensorType.getEnabled() == null) {
            sensorType.setEnabled(1);
        }
        if (sensorType.getSort() == null) {
            sensorType.setSort(0);
        }
        sensorTypeMapper.insert(sensorType);
        return sensorType.getId();
    }

    @Override
    public void updateSensorType(CollectorSensorTypeSaveReqVO updateReqVO) {
        validateSensorTypeExists(updateReqVO.getId());
        protocolService.validateProtocolExists(updateReqVO.getProtocolCode());
        validateTypeCodeUnique(updateReqVO.getId(), updateReqVO.getTypeCode());
        CollectorSensorTypeDO updateObj = BeanUtils.toBean(updateReqVO, CollectorSensorTypeDO.class);
        sensorTypeMapper.updateById(updateObj);
    }

    @Override
    public void deleteSensorType(Long id) {
        validateSensorTypeExists(id);
        if (sensorTemplateMapper.selectCountBySensorTypeId(id) > 0
                || sensorMapper.selectCountBySensorTypeId(id) > 0) {
            throw exception(SENSOR_TYPE_HAS_CHILDREN);
        }
        sensorTypeMapper.deleteById(id);
    }

    @Override
    public CollectorSensorTypeDO validateSensorTypeExists(Long id) {
        CollectorSensorTypeDO sensorType = sensorTypeMapper.selectById(id);
        if (sensorType == null) {
            throw exception(SENSOR_TYPE_NOT_EXISTS);
        }
        return sensorType;
    }

    @Override
    public CollectorSensorTypeDO validateSensorTypeEnabled(Long id) {
        CollectorSensorTypeDO sensorType = validateSensorTypeExists(id);
        if (sensorType.getEnabled() == null || sensorType.getEnabled() != 1) {
            throw exception(SENSOR_TYPE_NOT_ENABLED, sensorType.getTypeCode());
        }
        return sensorType;
    }

    private void validateTypeCodeUnique(Long id, String typeCode) {
        CollectorSensorTypeDO sensorType = sensorTypeMapper.selectByTypeCode(typeCode);
        if (sensorType == null) {
            return;
        }
        if (id == null || !sensorType.getId().equals(id)) {
            throw exception(SENSOR_TYPE_CODE_DUPLICATE, typeCode);
        }
    }

}
