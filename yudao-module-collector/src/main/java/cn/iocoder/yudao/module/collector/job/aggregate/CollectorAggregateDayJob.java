package cn.iocoder.yudao.module.collector.job.aggregate;

import cn.iocoder.yudao.module.collector.service.aggregate.CollectorAggregateService;
import cn.iocoder.yudao.module.collector.service.aggregate.CollectorAggregateServiceImpl;
import jakarta.annotation.Resource;
import org.quartz.DisallowConcurrentExecution;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 每 30 分钟从小时表上卷今天与昨天；服务层会排除尚未闭合的今天桶。 */
@DisallowConcurrentExecution
@Component("collectorAggregateDayJob")
public class CollectorAggregateDayJob extends AbstractCollectorAggregateJob {

    @Resource
    private CollectorAggregateService aggregateService;

    @Override
    public String execute(String param) {
        LocalDateTime now = LocalDateTime.now(CollectorAggregateServiceImpl.SHANGHAI_ZONE);
        LocalDateTime today = now.toLocalDate().atStartOfDay();
        return logResult(aggregateService.aggregateDay(today.minusDays(1), today.plusDays(1), null));
    }

}
