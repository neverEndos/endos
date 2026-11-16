package cn.iocoder.yudao.module.collector.service.sensortemplate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.collector.controller.admin.sensortemplate.vo.CollectorSensorTemplatePageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.sensortemplate.vo.CollectorSensorTemplateSaveReqVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.sensortemplate.CollectorSensorTemplateDO;

import java.util.List;

/**
 * 传感器参数模板 Service 接口
 */
public interface CollectorSensorTemplateService {

    PageResult<CollectorSensorTemplateDO> getTemplatePage(CollectorSensorTemplatePageReqVO pageReqVO);

    /**
     * 查询指定类型下的模板；enabled 为空时返回全部。
     */
    List<CollectorSensorTemplateDO> getTemplateListByType(Long sensorTypeId, Integer enabled);

    /**
     * 查询指定类型下启用的模板，用于生成通道。
     */
    List<CollectorSensorTemplateDO> getEnabledTemplateListByType(Long sensorTypeId);

    CollectorSensorTemplateDO getTemplate(Long id);

    Long createTemplate(CollectorSensorTemplateSaveReqVO createReqVO);

    void updateTemplate(CollectorSensorTemplateSaveReqVO updateReqVO);

    void deleteTemplate(Long id);

    CollectorSensorTemplateDO validateTemplateExists(Long id);

}
