package cn.iocoder.yudao.module.collector.service.trend;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendChannelPageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendSeriesReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendSeriesRespVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.device.CollectorDeviceDO;

import java.util.List;

/**
 * 采集历史趋势查询服务。
 */
public interface CollectorTrendService {

    /**
     * 查询指定通道与时间范围的历史趋势；自动选源并按点数上限放大桶宽。
     *
     * @param reqVO 趋势查询参数
     * @return 主序列与可选的原始点抽样序列
     */
    CollectorTrendSeriesRespVO getSeries(CollectorTrendSeriesReqVO reqVO);

    /**
     * 趋势选择器：全部启用设备。仅返回趋势选择所需字段，供趋势页面设备下拉使用，
     * 不依赖 collector:device:query 权限。
     *
     * @return 启用设备列表，按排序值与编号升序
     */
    List<CollectorDeviceDO> getTrendDeviceList();

    /**
     * 趋势选择器：指定设备下的启用通道分页。支持通道名称关键字模糊匹配，
     * 供趋势页面通道下拉使用，不依赖 collector:channel:query 权限。
     *
     * @param reqVO 设备编号 + 关键字 + 分页参数
     * @return 启用通道分页，按排序值与编号升序
     */
    PageResult<CollectorChannelDO> getTrendChannelPage(CollectorTrendChannelPageReqVO reqVO);

}
