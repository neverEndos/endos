package cn.iocoder.yudao.module.collector.service.channel;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelDeviceRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelPageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelThresholdBatchUpdateReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelThresholdPreviewReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelThresholdPreviewRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelThresholdUpdateReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelUpdateReqVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;

/**
 * 采集通道 Service 接口
 */
public interface CollectorChannelService {

    PageResult<CollectorChannelDO> getChannelPage(CollectorChannelPageReqVO pageReqVO);

    CollectorChannelDO getChannel(Long id);

    /**
     * 按设备查询阈值矩阵，通道按传感器分组。
     */
    CollectorChannelDeviceRespVO getChannelListByDevice(Long deviceId);

    void updateChannel(CollectorChannelUpdateReqVO updateReqVO);

    void updateChannelThreshold(CollectorChannelThresholdUpdateReqVO updateReqVO);

    /**
     * 在一个事务内批量修改同一设备下的通道阈值。
     *
     * @return 实际提交修改的通道数量
     */
    Integer updateChannelThresholdBatch(CollectorChannelThresholdBatchUpdateReqVO updateReqVO);

    CollectorChannelThresholdPreviewRespVO previewThreshold(CollectorChannelThresholdPreviewReqVO previewReqVO);

    void deleteChannel(Long id);

    CollectorChannelDO validateChannelExists(Long id);

}
