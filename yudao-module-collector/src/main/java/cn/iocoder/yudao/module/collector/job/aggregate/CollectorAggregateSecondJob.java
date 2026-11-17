package cn.iocoder.yudao.module.collector.job.aggregate;

import cn.iocoder.yudao.module.collector.service.aggregate.CollectorAggregateService;
import cn.iocoder.yudao.module.collector.service.aggregate.CollectorAggregateServiceImpl;
import jakarta.annotation.Resource;
import org.quartz.DisallowConcurrentExecution;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 每 5 秒聚合 fast 通道最近 60 秒的已闭合秒桶。
 * 60 秒窗口可降低“桶已存在但内容陈旧”的漏检概率，代价是重复 upsert 的写放大；
 * 超过窗口的迟到数据不会被自动重算，需要手工回填对应区间。
 */
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
