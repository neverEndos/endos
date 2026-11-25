package cn.iocoder.yudao.module.collector.service.alarm;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.collector.controller.admin.alarm.vo.CollectorAlarmPageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.alarm.vo.CollectorAlarmRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.alarm.vo.CollectorAlarmStateRespVO;

import java.util.List;

/**
 * 采集报警服务
 */
public interface CollectorAlarmService {

    int EVALUATE_BATCH_SIZE = 2000;

    /**
     * 扫描并处理所有尚未消费的采样，每个批次独立事务。
     */
    EvaluationResult evaluatePendingSamples();

    /**
     * 查询报警事件分页，名称通过批量查询装配。
     */
    PageResult<CollectorAlarmRespVO> getAlarmPage(CollectorAlarmPageReqVO reqVO);

    /**
     * 查询通道当前报警状态，可按设备过滤。
     */
    List<CollectorAlarmStateRespVO> getAlarmStateList(Long deviceId);

    /**
     * 确认一条活动报警事件：将状态置为已确认，保留级别、不写恢复时间。
     *
     * @param id     报警事件编号
     * @param userId 当前登录用户编号
     */
    void acknowledgeAlarm(Long id, Long userId);

    /**
     * 一次作业执行的统计。
     */
    record EvaluationResult(long processedCount, long transitionCount, int batchCount, boolean initialized) {
    }

}
