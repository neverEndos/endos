package cn.iocoder.yudao.module.collector.job.alarm;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.module.collector.service.alarm.CollectorAlarmService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 采集报警判定定时任务，由 infra 定时任务调度、collector 模块执行业务。
 */
@Slf4j
@Component("collectorAlarmEvaluateJob")
public class CollectorAlarmEvaluateJob implements JobHandler {

    @Resource
    private CollectorAlarmService alarmService;

    @Override
    public String execute(String param) {
        long begin = System.currentTimeMillis();
        CollectorAlarmService.EvaluationResult result = alarmService.evaluatePendingSamples();
        long duration = System.currentTimeMillis() - begin;
        String message = String.format("处理采样 %d 条，%d 个批次，产生报警跃迁 %d 次，初始化游标=%s，耗时 %d ms",
                result.processedCount(), result.batchCount(), result.transitionCount(), result.initialized(), duration);
        log.info("[execute][{}]", message);
        return message;
    }

}
