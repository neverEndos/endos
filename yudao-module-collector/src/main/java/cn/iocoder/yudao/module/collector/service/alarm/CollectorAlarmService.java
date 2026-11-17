package cn.iocoder.yudao.module.collector.service.alarm;

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
     * 一次作业执行的统计。
     */
    record EvaluationResult(long processedCount, long transitionCount, int batchCount, boolean initialized) {
    }

}
