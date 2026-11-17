package cn.iocoder.yudao.module.collector.service.aggregate;

import cn.iocoder.yudao.module.collector.enums.CollectorAggregateType;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * 采集数据多级聚合服务。
 */
public interface CollectorAggregateService {

    String DATA_PROFILE_FAST = "fast";

    /**
     * @param channelCount 实际参与处理的通道数
     * @param bucketCount 写入或更新的聚合桶数
     * @param rowCount 聚合读取的 raw 源行数；从下层上卷时由 sample_count 求和得到
     * @param skippedEmptyBatches 显式回填时因源为空而跳过删除/写入的批次数
     * @param durationMillis 整次调用耗时
     */
    record AggregateResult(String aggType, int channelCount, long bucketCount, long rowCount,
                           long skippedEmptyBatches, long durationMillis) {
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
     * 手工回填，同步执行；目标桶有源数据时先清空目标区间再重建，源数据删除或修正后不会残留旧桶。
     *
     * <p>单次上限：second 6 小时/5 通道（满额约 37 秒），minute 2 天/20 通道（满额约 16 秒），
     * hour、day 30 天/50 通道（满额约 1–2 分钟，覆盖度回退时为上限）。大范围回填必须分批或
     * 按 second → minute → hour → day 分层调用，避免同步请求超时。</p>
     *
     * <p>fromRaw=true 时范围不得超过 raw 保留期（默认 30 天），否则 raw 可能已过期；
     * 过期范围只能使用分层回填。源为空且 forceClean=false 的批次会保留旧聚合并在结果中计入
     * skippedEmptyBatches；forceClean=true 才会以空结果删除目标区间，属于高风险操作。</p>
     *
     * <p>迁移升级后历史聚合的 valid_count 为近似值，建议对历史区间执行一次分层回填校正。
     * 覆盖度检测只看下层桶行数，迟到超过秒任务窗口的数据需手工回填。</p>
     */
    AggregateResult backfill(CollectorAggregateType aggType, LocalDateTime from, LocalDateTime to,
                             Collection<Long> channelIds, boolean fromRaw);

    /**
     * 手工回填并可选级联重建上层（second→minute→hour→day、minute→hour→day、hour→day）。
     * 执行前一次性校验所有级联层的通道数，避免第一层提交后上层校验失败；级联各层仍按批次提交，
     * 失败后可从失败层继续分步重跑。
     */
    AggregateResult backfill(CollectorAggregateType aggType, LocalDateTime from, LocalDateTime to,
                             Collection<Long> channelIds, boolean fromRaw, boolean cascade);

    /**
     * 手工回填完整参数。
     *
     * <p>forceClean 默认 false：源为空时保留目标区间旧数据，避免 raw 已过期或下层缺口时静默清空
     * 正确历史。只有调用方明确要求“以空结果清空目标区间”时才可置为 true。</p>
     */
    AggregateResult backfill(CollectorAggregateType aggType, LocalDateTime from, LocalDateTime to,
                             Collection<Long> channelIds, boolean fromRaw, boolean cascade, boolean forceClean);

}
