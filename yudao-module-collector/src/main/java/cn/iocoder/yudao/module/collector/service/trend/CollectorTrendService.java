package cn.iocoder.yudao.module.collector.service.trend;

import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendSeriesReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendSeriesRespVO;

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

}
