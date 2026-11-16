package cn.iocoder.yudao.module.collector.service.channel;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelPageReqVO;
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

    void updateChannel(CollectorChannelUpdateReqVO updateReqVO);

    void updateChannelThreshold(CollectorChannelThresholdUpdateReqVO updateReqVO);

    CollectorChannelThresholdPreviewRespVO previewThreshold(CollectorChannelThresholdPreviewReqVO previewReqVO);

    void deleteChannel(Long id);

    CollectorChannelDO validateChannelExists(Long id);

}
