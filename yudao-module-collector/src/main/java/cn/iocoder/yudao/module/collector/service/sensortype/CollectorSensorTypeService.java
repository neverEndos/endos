package cn.iocoder.yudao.module.collector.service.sensortype;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.collector.controller.admin.sensortype.vo.CollectorSensorTypePageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.sensortype.vo.CollectorSensorTypeSaveReqVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.sensortype.CollectorSensorTypeDO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 传感器类型 Service 接口
 */
public interface CollectorSensorTypeService {

    PageResult<CollectorSensorTypeDO> getSensorTypePage(CollectorSensorTypePageReqVO pageReqVO);

    Map<Long, Long> getTemplateCountMapByTypeIds(Collection<Long> sensorTypeIds);

    Map<Long, Long> getSensorCountMapByTypeIds(Collection<Long> sensorTypeIds);

    List<CollectorSensorTypeDO> getSensorTypeList(Integer enabled);

    CollectorSensorTypeDO getSensorType(Long id);

    Long createSensorType(CollectorSensorTypeSaveReqVO createReqVO);

    void updateSensorType(CollectorSensorTypeSaveReqVO updateReqVO);

    void deleteSensorType(Long id);

    /**
     * 校验类型存在，并返回类型。
     */
    CollectorSensorTypeDO validateSensorTypeExists(Long id);

    /**
     * 校验类型存在且启用，并返回类型。
     */
    CollectorSensorTypeDO validateSensorTypeEnabled(Long id);

}
