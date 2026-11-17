package cn.iocoder.yudao.module.collector.job.aggregate;

import cn.iocoder.yudao.module.collector.service.aggregate.CollectorAggregateService;
import cn.iocoder.yudao.module.collector.service.aggregate.CollectorAggregateServiceImpl;
import jakarta.annotation.Resource;
import org.quartz.DisallowConcurrentExecution;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 每分钟聚合最近 10 分钟：fast 从秒表上卷，normal 从 raw 直算。 */
@DisallowConcurrentExecution
@Component("collectorAggregateMinuteJob")
public class CollectorAggregateMinuteJob extends AbstractCollectorAggregateJob {

    @Resource
    private CollectorAggregateService aggregateService;

    @Override
    public String execute(String param) {
        LocalDateTime to = LocalDateTime.now(CollectorAggregateServiceImpl.SHANGHAI_ZONE);
        return logResult(aggregateService.aggregateMinute(to.minusMinutes(10), to, null));
    }

}
