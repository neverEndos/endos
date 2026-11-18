package cn.iocoder.yudao.module.collector.job.aggregate;

import cn.iocoder.yudao.module.collector.service.aggregate.CollectorAggregateService;
import cn.iocoder.yudao.module.collector.service.aggregate.CollectorAggregateServiceImpl;
import jakarta.annotation.Resource;
import org.quartz.DisallowConcurrentExecution;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 每 5 分钟从分钟表上卷最近 3 小时的已闭合小时桶。 */
@DisallowConcurrentExecution
@Component("collectorAggregateHourJob")
public class CollectorAggregateHourJob extends AbstractCollectorAggregateJob {

    @Resource
    private CollectorAggregateService aggregateService;

    @Override
    public String execute(String param) {
        LocalDateTime to = LocalDateTime.now(CollectorAggregateServiceImpl.SHANGHAI_ZONE);
        return logResult(aggregateService.aggregateHour(to.minusHours(3), to, null));
    }

}
