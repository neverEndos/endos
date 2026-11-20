package cn.iocoder.yudao.module.collector.service.partition;

import java.time.LocalDate;
import java.util.List;

/**
 * collector raw 日分区维护服务。
 *
 * <p>当前只负责按 {@code collector_retention_policy.partition_lead_days} 预建
 * 「今天 ~ 今天 + N 天」的日分区；过期清理与 DROP PARTITION 不在本服务范围内。</p>
 */
public interface CollectorPartitionService {

    /**
     * 检查并预建缺失的 raw 日分区。
     *
     * <p>策略缺失、策略不唯一或 {@code partition_lead_days} 越界时直接抛出业务异常；
     * 单日 DDL 失败或默认分区存在冲突数据时通过返回值汇总，不中断其余日期。</p>
     *
     * @return 本次维护结果
     */
    PartitionMaintainResult maintainDailyPartitions();

    /**
     * 分区维护结果；字段均为不可变集合。
     *
     * @param from 检查起始日期（含）
     * @param to 检查结束日期（含）
     * @param leadDays 本次使用的提前天数
     * @param createdPartitions 本次新建成功的分区名
     * @param skippedExistingCount 因目标日已被完整覆盖而跳过的天数
     * @param defaultConflicts 默认分区存在冲突数据的日期
     * @param ddlFailures 单日 DDL 或创建后核验失败项
     * @param lockNotAcquired 是否因 advisory lock 被占用而整体跳过
     */
    record PartitionMaintainResult(LocalDate from, LocalDate to, int leadDays,
                                   List<String> createdPartitions, int skippedExistingCount,
                                   List<DefaultPartitionConflict> defaultConflicts,
                                   List<PartitionFailure> ddlFailures,
                                   boolean lockNotAcquired) {

        public PartitionMaintainResult {
            createdPartitions = List.copyOf(createdPartitions);
            defaultConflicts = List.copyOf(defaultConflicts);
            ddlFailures = List.copyOf(ddlFailures);
        }

        /** 是否存在需要让定时任务体现为失败的项。 */
        public boolean failed() {
            return !defaultConflicts.isEmpty() || !ddlFailures.isEmpty();
        }
    }

    /**
     * 默认分区冲突项。
     *
     * @param day受影响日期
     * @param range 该日的分区边界
     * @param reason 中文原因
     */
    record DefaultPartitionConflict(LocalDate day, String range, String reason) {
    }

    /**
     * 单日分区失败项。
     *
     * @param partitionName 期望创建的分区名
     * @param day 受影响日期
     * @param range 该日的分区边界
     * @param reason 中文原因
     */
    record PartitionFailure(String partitionName, LocalDate day, String range, String reason) {
    }

}
