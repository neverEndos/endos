package cn.iocoder.yudao.module.collector.service.health;

import cn.iocoder.yudao.module.collector.controller.admin.health.vo.CollectorHealthOverviewRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.health.vo.CollectorHealthSummaryRespVO;

/**
 * 采集运行详情只读服务。
 *
 * <p>只做查询：心跳、设备状态、活动报警、聚合水位、保留策略与 raw 分区现状，
 * 不触发分区维护、保留清理、聚合或报警判定的任何写操作。</p>
 */
public interface CollectorHealthService {

    /**
     * 轻量摘要：心跳、当前设备状态、活动报警数量。
     *
     * @param deviceId 设备编号
     * @return 摘要响应；无心跳时 {@code process} 为 null
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 设备不存在或已逻辑删除
     */
    CollectorHealthSummaryRespVO getSummary(Long deviceId);

    /**
     * 完整详情：摘要 + 活动报警明细 + 聚合水位 + 保留策略 + raw 分区预建情况。
     *
     * @param deviceId 设备编号
     * @return 详情响应
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 设备不存在或已逻辑删除
     */
    CollectorHealthOverviewRespVO getOverview(Long deviceId);

}
