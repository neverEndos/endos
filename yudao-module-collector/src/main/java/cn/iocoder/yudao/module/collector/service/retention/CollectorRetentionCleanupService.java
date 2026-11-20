package cn.iocoder.yudao.module.collector.service.retention;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集数据保留清理服务：定时任务与手动清理的唯一入口。
 *
 * <p>清理按 {@code collector_retention_policy} 的 8 个固定数据类逐类执行，单类失败不影响其他类。
 * 所有删除条件都是「严格小于 cutoff」，等于 cutoff 的数据保留。</p>
 */
public interface CollectorRetentionCleanupService {

    /**
     * 执行一次全量清理。
     *
     * <p>整体 cleanup advisory lock 被占用时不执行任何清理并返回 {@code lockNotAcquired=true} 的报告；
     * raw 的分区 DROP 还额外需要 PR #29 的分区维护锁，取不到时该数据类记为跳过。
     */
    RetentionCleanupReport cleanupAll();

    /**
     * 只读预检：统计各类候选删除行数与候选 DROP 分区，不执行任何写操作。
     *
     * <p>SQL 失败或超过 statement_timeout 时抛出中文业务异常，绝不返回伪造的 0。</p>
     */
    RetentionPreview preview();

    /** 单类清理结果。 */
    enum RetentionClassOutcome {

        /** 全部成功（无失败、无待人工处理项）。 */
        SUCCESS,
        /** 部分成功：已有删除动作，但仍存在失败或待人工处理项。 */
        PARTIAL,
        /** 失败：未完成任何删除，或删除过程中出错。 */
        FAILED,
        /** 跳过：策略已停用。 */
        SKIPPED_DISABLED,
        /** 跳过：retention_days=0，永久保留。 */
        SKIPPED_PERMANENT,
        /** 跳过：分区锁被占用，待下次调度。 */
        SKIPPED_LOCKED
    }

    /** 单个被 DROP 的日分区。 */
    record PartitionDrop(String partitionName, LocalDateTime from, LocalDateTime to) {
    }

    /**
     * 单个数据类的清理结果。
     *
     * @param dataClass 数据类编码
     * @param displayName 中文名
     * @param action 策略声明的清理动作
     * @param cutoff 本次使用的截止时间（严格小于该时间才删除）
     * @param outcome 结果分类
     * @param deletedRows 已删除行数（raw 恒为 0）
     * @param droppedPartitions 已 DROP 的日分区
     * @param warnings 待人工处理或需要注意的事项
     * @param message 面向 {@code last_run_result} 的中文摘要
     */
    record RetentionClassResult(String dataClass, String displayName, String action, LocalDateTime cutoff,
                                RetentionClassOutcome outcome, long deletedRows,
                                List<PartitionDrop> droppedPartitions, List<String> warnings, String message) {

        public RetentionClassResult {
            droppedPartitions = List.copyOf(droppedPartitions);
            warnings = List.copyOf(warnings);
        }

        /** 是否让整体报告标记为失败。 */
        public boolean failed() {
            return outcome == RetentionClassOutcome.FAILED || outcome == RetentionClassOutcome.PARTIAL;
        }
    }

    /**
     * 一次清理的汇总报告。
     *
     * @param today 本次执行使用的业务日期（Asia/Shanghai）
     * @param lockNotAcquired 是否因 cleanup advisory lock 被占用而整体跳过
     * @param results 各类结果（固定 8 类顺序 + 未知策略行）
     */
    record RetentionCleanupReport(LocalDate today, boolean lockNotAcquired, List<RetentionClassResult> results) {

        public RetentionCleanupReport {
            results = List.copyOf(results);
        }

        /** 是否存在需要让定时任务体现为失败的项。 */
        public boolean hasFailure() {
            return results.stream().anyMatch(RetentionClassResult::failed);
        }

        public long totalDeletedRows() {
            return results.stream().mapToLong(RetentionClassResult::deletedRows).sum();
        }

        public int droppedPartitionCount() {
            return results.stream().mapToInt(result -> result.droppedPartitions().size()).sum();
        }
    }

    /** 预检中的候选分区（名称 + catalog 原始边界）。 */
    record PartitionCandidate(String partitionName, String bound, LocalDateTime from, LocalDateTime to) {
    }

    /**
     * 单个数据类的预检结果。
     *
     * @param candidateRows 候选删除行数；raw 不计数（为 null，只给候选分区）
     */
    record RetentionClassPreview(String dataClass, String displayName, int retentionDays, boolean enabled,
                                 String action, LocalDateTime cutoff, boolean skipped, String skipReason,
                                 Long candidateRows, List<PartitionCandidate> candidatePartitions,
                                 List<String> warnings) {

        public RetentionClassPreview {
            candidatePartitions = List.copyOf(candidatePartitions);
            warnings = List.copyOf(warnings);
        }
    }

    /** 预检结果。 */
    record RetentionPreview(LocalDate today, List<RetentionClassPreview> classes) {

        public RetentionPreview {
            classes = List.copyOf(classes);
        }
    }

}
