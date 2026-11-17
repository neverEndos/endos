package cn.iocoder.yudao.module.collector.service.dashboard;

import cn.iocoder.yudao.module.collector.controller.admin.dashboard.vo.CollectorDashboardLatestRespVO;

/** 最新值看板服务。 */
public interface CollectorDashboardService {

    CollectorDashboardLatestRespVO getLatest(Long deviceId);

}
