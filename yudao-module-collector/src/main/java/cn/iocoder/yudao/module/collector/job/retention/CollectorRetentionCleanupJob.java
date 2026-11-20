package cn.iocoder.yudao.module.collector.job.retention;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService.PartitionDrop;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService.RetentionClassOutcome;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService.RetentionClassResult;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService.RetentionCleanupReport;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.StringJoiner;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.RETENTION_CLEANUP_FAILED;

/**
 * 采集数据保留清理定时任务，cron 由 infra_job 种子控制（每天 02:40）。
 *
 * <p>失败语义与分区预建任务一致：{@code JobHandlerInvoker} 捕获异常后把
 * {@code infra_job_log.success} 记为 false，并将根因消息写入 result；因此这里在
 * {@link RetentionCleanupReport#hasFailure()} 为 true 时抛 {@code ServiceException}，
 * 摘要本身作为异常消息，完整明细另走 {@code log.error}。</p>
 */
@Slf4j
@Component("collectorRetentionCleanupJob")
public class CollectorRetentionCleanupJob implements JobHandler {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** infra_job_log.result 为 varchar(4000)，摘要必须显著短于该上限。 */
    private static final int MAX_SUMMARY_LENGTH = 1000;

    /** 为截断提示预留的字符数。 */
    private static final int TRUNCATION_RESERVE = 60;

    private static final String TRUNCATED_SUFFIX = "；其余详见应用日志";

    /** 单条原因在摘要中的最大长度。 */
    private static final int MAX_REASON_LENGTH = 40;

    @Resource
    private CollectorRetentionCleanupService cleanupService;

    @Override
    public String execute(String param) {
        long begin = System.currentTimeMillis();
        RetentionCleanupReport report = cleanupService.cleanupAll();
        long duration = System.currentTimeMillis() - begin;
        String summary = formatSummary(report, duration);
        if (report.hasFailure()) {
            // 完整明细走日志，保证排障时能看到每类的行数、分区与失败原因。
            log.error("[execute][完整明细][{}]", formatDetail(report, duration));
            throw exception(RETENTION_CLEANUP_FAILED, summary);
        }
        log.info("[execute][{}]", summary);
        return summary;
    }

    /**
     * 面向 infra_job_log 的摘要：总长受控，每个数据类最多一行。
     */
    private String formatSummary(RetentionCleanupReport report, long duration) {
        if (report.lockNotAcquired()) {
            return String.format("已有保留清理任务在执行，本次跳过，耗时 %d ms", duration);
        }
        StringBuilder message = new StringBuilder();
        message.append("保留清理 ").append(report.today())
                .append("，cutoff ").append(cutoffRange(report.results()))
                .append("，删除 ").append(report.totalDeletedRows()).append(" 行")
                .append("，DROP ").append(report.droppedPartitionCount()).append(" 个分区")
                .append("，耗时 ").append(duration).append(" ms");
        for (RetentionClassResult result : report.results()) {
            String item = "；" + result.dataClass() + "=" + describe(result);
            if (message.length() + item.length() + TRUNCATION_RESERVE > MAX_SUMMARY_LENGTH) {
                message.append(TRUNCATED_SUFFIX);
                break;
            }
            message.append(item);
        }
        return message.toString();
    }

    /** 不设上限的完整明细，仅用于日志。 */
    private String formatDetail(RetentionCleanupReport report, long duration) {
        if (report.lockNotAcquired()) {
            return String.format("已有保留清理任务在执行，本次跳过，耗时 %d ms", duration);
        }
        StringBuilder message = new StringBuilder(formatSummary(report, duration));
        for (RetentionClassResult result : report.results()) {
            message.append("\n- ").append(result.dataClass()).append("：").append(result.outcome())
                    .append("，删除 ").append(result.deletedRows()).append(" 行，cutoff ")
                    .append(result.cutoff() == null ? "-" : result.cutoff().format(DATE_TIME_FORMATTER));
            if (!result.droppedPartitions().isEmpty()) {
                StringJoiner dropped = new StringJoiner("、");
                for (PartitionDrop partition : result.droppedPartitions()) {
                    dropped.add(partition.partitionName());
                }
                message.append("，DROP ").append(dropped);
            }
            if (!result.warnings().isEmpty()) {
                message.append("，告警 ").append(String.join("；", result.warnings()));
            }
            message.append("，摘要：").append(result.message());
        }
        return message.toString();
    }

    private String cutoffRange(List<RetentionClassResult> results) {
        LocalDateTime min = null;
        LocalDateTime max = null;
        for (RetentionClassResult result : results) {
            LocalDateTime cutoff = result.cutoff();
            if (cutoff == null) {
                continue;
            }
            if (min == null || cutoff.isBefore(min)) {
                min = cutoff;
            }
            if (max == null || cutoff.isAfter(max)) {
                max = cutoff;
            }
        }
        if (min == null) {
            return "-";
        }
        return min.format(DATE_TIME_FORMATTER) + "~" + max.format(DATE_TIME_FORMATTER);
    }

    /** 每个数据类一行内的短描述：结论 + 行数/分区数，必要时附一条失败原因。 */
    private String describe(RetentionClassResult result) {
        StringBuilder text = new StringBuilder(outcomeText(result.outcome()));
        if (result.deletedRows() > 0) {
            text.append(result.deletedRows()).append(" 行");
        }
        if (!result.droppedPartitions().isEmpty()) {
            text.append("DROP ").append(result.droppedPartitions().size()).append(" 分区");
        }
        if (result.failed() && !result.warnings().isEmpty()) {
            text.append("(").append(abbreviate(result.warnings().get(0))).append(")");
        }
        return text.toString();
    }

    private String outcomeText(RetentionClassOutcome outcome) {
        return switch (outcome) {
            case SUCCESS -> "成功";
            case PARTIAL -> "部分成功";
            case FAILED -> "失败";
            case SKIPPED_DISABLED -> "已停用跳过";
            case SKIPPED_PERMANENT -> "永久保留跳过";
            case SKIPPED_LOCKED -> "锁占用跳过";
        };
    }

    private String abbreviate(String reason) {
        if (reason == null) {
            return "未知原因";
        }
        String trimmed = reason.strip();
        return trimmed.length() <= MAX_REASON_LENGTH ? trimmed
                : trimmed.substring(0, MAX_REASON_LENGTH) + "…";
    }

}
