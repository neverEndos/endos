package cn.iocoder.yudao.module.collector.job.aggregate;

import cn.iocoder.yudao.module.collector.service.aggregate.CollectorAggregateService;
import cn.iocoder.yudao.module.collector.service.aggregate.CollectorAggregateServiceImpl;
import jakarta.annotation.Resource;
import org.quartz.DisallowConcurrentExecution;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 每 5 秒聚合 fast 通道最近 60 秒的已闭合秒桶。 */
@DisallowConcurrentExecution
@Component("collectorAggregateSecondJob")
public class CollectorAggregateSecondJob extends AbstractCollectorAggregateJob {

    @Resource
    private CollectorAggregateService aggregateService;

    @Override
    public String execute(String param) {
        LocalDateTime to = LocalDateTime.now(CollectorAggregateServiceImpl.SHANGHAI_ZONE);
        return logResult(aggregateService.aggregateSecond(to.minusSeconds(60), to, null));
    }

}
