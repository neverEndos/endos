package cn.iocoder.yudao.module.collector.service.sensor;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.collector.controller.admin.sensor.vo.CollectorGenerateChannelPreviewRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.sensor.vo.CollectorGenerateChannelsRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.sensor.vo.CollectorSensorPageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.sensor.vo.CollectorSensorSaveReqVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.sensor.CollectorSensorDO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 采集传感器 Service 接口
 */
public interface CollectorSensorService {

    PageResult<CollectorSensorDO> getSensorPage(CollectorSensorPageReqVO pageReqVO);

    Map<Long, Long> getChannelCountMapBySensorIds(Collection<Long> sensorIds);

    List<CollectorSensorDO> getSensorList(Long deviceId, Integer enabled);

    CollectorSensorDO getSensor(Long id);

    Long createSensor(CollectorSensorSaveReqVO createReqVO);

    void updateSensor(CollectorSensorSaveReqVO updateReqVO);

    void deleteSensor(Long id);

    CollectorSensorDO validateSensorExists(Long id);

    /**
     * 按传感器类型下启用的模板预览将生成的通道与初始阈值。
     */
    List<CollectorGenerateChannelPreviewRespVO> generateChannelsPreview(Long sensorTypeId);

    /**
     * 为指定传感器按类型模板生成通道；已存在的通道跳过，不覆盖。
     */
    CollectorGenerateChannelsRespVO generateChannels(Long sensorId);

}
