package cn.iocoder.yudao.module.collector.service.aggregate;

import cn.iocoder.yudao.module.collector.enums.CollectorAggregateType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 采集数据多级聚合服务。
 */
public interface CollectorAggregateService {

    String DATA_PROFILE_FAST = "fast";

    /**
     * @param channelCount 实际参与处理的通道数
     * @param bucketCount 写入或更新的聚合桶数
     * @param rowCount 聚合读取的 raw 源行数；从下层上卷时由 sample_count 求和得到
     * @param skippedEmptyBatches 显式回填时因整批源为空而跳过删除/写入的批次数
     * @param preservedBuckets 显式回填时因本轮无源而保留的旧目标桶数
     * @param durationMillis 整次调用耗时
     * @param levels 本次调用各层结果；首项为根层，级联时为根层及其上层
     */
    record AggregateResult(String aggType, int channelCount, long bucketCount, long rowCount,
                           long skippedEmptyBatches, long preservedBuckets, long durationMillis,
                           List<AggregateLevelResult> levels) {

        public AggregateResult withLevels(List<AggregateLevelResult> levels) {
            return new AggregateResult(aggType, channelCount, bucketCount, rowCount, skippedEmptyBatches,
                    preservedBuckets, durationMillis, List.copyOf(levels));
        }
    }

    /**
     * 级联回填中的单层执行结果。
     */
    record AggregateLevelResult(String aggType, int channelCount, long bucketCount, long rowCount,
                                long skippedEmptyBatches, long preservedBuckets, long durationMillis) {

        public static AggregateLevelResult of(AggregateResult result) {
            return new AggregateLevelResult(result.aggType(), result.channelCount(), result.bucketCount(),
                    result.rowCount(), result.skippedEmptyBatches(), result.preservedBuckets(),
                    result.durationMillis());
        }
    }

    AggregateResult aggregateSecond(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds);

    AggregateResult aggregateSecond(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds, boolean fromRaw);

    AggregateResult aggregateMinute(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds);

    AggregateResult aggregateMinute(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds, boolean fromRaw);

    AggregateResult aggregateHour(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds);

    AggregateResult aggregateHour(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds, boolean fromRaw);

    AggregateResult aggregateDay(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds);

    AggregateResult aggregateDay(LocalDateTime from, LocalDateTime to, Collection<Long> channelIds, boolean fromRaw);

    /**
     * 手工回填，同步执行；只删除本轮确有源覆盖的目标桶，无源旧桶保留，避免源删除时静默丢失历史。
     *
     * <p>单次上限：second 3 小时/5 通道（按约 1.5 秒/通道·小时估算约 23 秒），minute 2 天/20 通道
     * （约 16 秒），hour、day 30 天/50 通道（约 2.5 分钟，覆盖度回退时为上限）。大范围回填必须分批或
     * 按 second → minute → hour → day 分层调用；板端更慢，避免同步请求超时。</p>
     *
     * <p>fromRaw=true 时起点必须不早于 raw 保留期（优先读取 collector_retention_policy 的
     * sample_raw 策略，读不到回落配置，默认 30 天），否则 raw 可能已过期；过期起点只能使用
     * 分层回填。fromRaw=false 时若某桶需要回退 raw 且 raw 已不可用，该桶保留旧聚合。</p>
     *
     * <p>整批源为空且 forceClean=false 时跳过该批并计入 skippedEmptyBatches；批内部分桶无源时
     * 仅保留这些旧桶并计入 preservedBuckets。forceClean=true 是唯一允许按区间整段清空的入口，
     * 属于高风险操作，HTTP 入口还需独立权限 collector:aggregate:force-clean。</p>
     *
     * <p>迁移升级后历史聚合的 valid_count 为近似值，建议对历史区间执行一次分层回填校正。
     * 覆盖度检测只看下层桶行数，迟到超过秒任务窗口的数据需手工回填。</p>
     */
    AggregateResult backfill(CollectorAggregateType aggType, LocalDateTime from, LocalDateTime to,
                             Collection<Long> channelIds, boolean fromRaw);

    /**
     * 手工回填并可选级联重建上层（second→minute→hour→day、minute→hour→day、hour→day）。
     * 执行前一次性校验所有级联层的通道数，避免第一层提交后上层校验失败；返回结果中的 levels
     * 汇总根层与各上层执行情况，并逐层输出日志；级联各层仍按批次提交，失败后可从失败层继续分步重跑。
     */
    AggregateResult backfill(CollectorAggregateType aggType, LocalDateTime from, LocalDateTime to,
                             Collection<Long> channelIds, boolean fromRaw, boolean cascade);

    /**
     * 手工回填完整参数。
     *
     * <p>forceClean 默认 false：无源桶保留旧数据，避免 raw 已过期或下层缺口时静默清空正确历史。
     * 只有调用方明确要求按区间整段清空时才可置为 true；forceClean 仅影响显式回填，不是定时任务
     * 的清理入口，也不能与普通覆盖式重建混淆。</p>
     */
    AggregateResult backfill(CollectorAggregateType aggType, LocalDateTime from, LocalDateTime to,
                             Collection<Long> channelIds, boolean fromRaw, boolean cascade, boolean forceClean);

}
