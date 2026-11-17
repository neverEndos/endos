package cn.iocoder.yudao.module.collector.job.aggregate;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.module.collector.service.aggregate.CollectorAggregateService.AggregateResult;
import lombok.extern.slf4j.Slf4j;

/** 聚合作业统一中文日志。 */
@Slf4j
abstract class AbstractCollectorAggregateJob implements JobHandler {

    protected String logResult(AggregateResult result) {
        String message = String.format("agg_type=%s，通道数=%d，桶数=%d，源行数=%d，耗时=%d ms",
                result.aggType(), result.channelCount(), result.bucketCount(), result.rowCount(), result.durationMillis());
        log.info("[execute][{}]", message);
        return message;
    }

}
