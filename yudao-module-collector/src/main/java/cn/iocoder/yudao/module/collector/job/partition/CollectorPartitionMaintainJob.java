package cn.iocoder.yudao.module.collector.job.partition;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionService;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionService.DefaultPartitionConflict;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionService.PartitionFailure;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionService.PartitionMaintainResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Function;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.PARTITION_MAINTAIN_FAILED;

/**
 * 采集 raw 日分区预建定时任务，由 infra 定时任务调度、collector 模块执行业务。
 */
@Slf4j
@Component("collectorPartitionMaintainJob")
public class CollectorPartitionMaintainJob implements JobHandler {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * infra_job_log.result 为 varchar(4000) 且写库异常会被 infra 静默吞掉；
     * 摘要必须显著短于该上限，避免失败状态/结果整体写不进库。
     */
    private static final int MAX_SUMMARY_LENGTH = 1000;

    /** 每类明细最多列出的条目数，超出部分只提示数量。 */
    private static final int MAX_DETAIL_ITEMS = 5;

    /** 单条明细原因的最大长度，保留开头的中文前缀，避免原始 SQL 消息占满预算。 */
    private static final int MAX_REASON_LENGTH = 120;

    /** 因预算/条数截断时的统一提示，完整内容由 log.error 输出。 */
    private static final String TRUNCATED_SUFFIX = "，详见应用日志";

    /** 为截断提示预留的字符数。 */
    private static final int TRUNCATION_RESERVE = 80;

    @Resource
    private CollectorPartitionService partitionService;

    @Override
    public String execute(String param) {
        long begin = System.currentTimeMillis();
        PartitionMaintainResult result = partitionService.maintainDailyPartitions();
        long duration = System.currentTimeMillis() - begin;
        String message = formatSummary(result, duration);
        if (result.failed()) {
            // 完整明细仍走日志，保证排障时能看到所有失败项与原始 SQL 消息。
            log.error("[execute][完整明细][{}]", formatDetail(result, duration));
            // 存在默认分区冲突或 DDL 失败时必须让 infra 任务日志/状态体现为失败。
            throw exception(PARTITION_MAINTAIN_FAILED, message);
        }
        log.info("[execute][{}]", message);
        return message;
    }

    /**
     * 面向 infra_job_log 的摘要：总长受控、每类明细最多 {@value #MAX_DETAIL_ITEMS} 项。
     */
    private String formatSummary(PartitionMaintainResult result, long duration) {
        if (result.lockNotAcquired()) {
            // 未获取锁时没有实际检查范围，不能打印编造的日期区间。
            return String.format("已有分区维护任务在执行，本次跳过，耗时 %d ms", duration);
        }
        StringBuilder message = new StringBuilder(header(result, duration));
        appendDefaultConflicts(message, result.defaultConflicts());
        appendDdlFailures(message, result.ddlFailures());
        return message.toString();
    }

    /** 不设上限的完整明细，仅用于日志。 */
    private String formatDetail(PartitionMaintainResult result, long duration) {
        if (result.lockNotAcquired()) {
            return String.format("已有分区维护任务在执行，本次跳过，耗时 %d ms", duration);
        }
        StringBuilder message = new StringBuilder(header(result, duration));
        if (!result.defaultConflicts().isEmpty()) {
            StringJoiner conflicts = new StringJoiner("；");
            for (DefaultPartitionConflict conflict : result.defaultConflicts()) {
                conflicts.add(conflict.range() + "：" + conflict.reason());
            }
            message.append("；默认分区冲突 ").append(result.defaultConflicts().size()).append(" 项：")
                    .append(conflicts);
        }
        if (!result.ddlFailures().isEmpty()) {
            StringJoiner failures = new StringJoiner("；");
            for (PartitionFailure failure : result.ddlFailures()) {
                failures.add(failure.partitionName() + "（" + failure.range() + "）：" + failure.reason());
            }
            message.append("；DDL 失败 ").append(result.ddlFailures().size()).append(" 项：").append(failures);
        }
        return message.toString();
    }

    private String header(PartitionMaintainResult result, long duration) {
        String range = result.from().format(DATE_FORMATTER) + "~" + result.to().format(DATE_FORMATTER);
        return String.format("检查 %s，新建 %d 个，已存在 %d 个，耗时 %d ms",
                range, result.createdPartitions().size(), result.skippedExistingCount(), duration);
    }

    private void appendDefaultConflicts(StringBuilder message,
                                        List<DefaultPartitionConflict> conflicts) {
        if (conflicts.isEmpty()) {
            return;
        }
        // 默认分区冲突的中文提示是受控迁移的关键线索，优先占用预算。
        int shown = appendDetails(message, "；默认分区冲突 " + conflicts.size() + " 项：",
                conflicts, conflict -> conflict.range() + "：" + conflict.reason());
        if (shown < conflicts.size()) {
            message.append("；等 ").append(conflicts.size() - shown).append(" 项").append(TRUNCATED_SUFFIX);
        }
    }

    private void appendDdlFailures(StringBuilder message, List<PartitionFailure> failures) {
        if (failures.isEmpty()) {
            return;
        }
        int shown = appendDetails(message, "；DDL 失败 " + failures.size() + " 项：",
                failures, failure -> failure.partitionName() + "（" + failure.range() + "）："
                        + abbreviate(failure.reason()));
        if (shown < failures.size()) {
            message.append("；等 ").append(failures.size() - shown).append(" 项").append(TRUNCATED_SUFFIX);
        }
    }

    /**
     * 追加一类明细，返回实际列出的条目数。
     *
     * <p>同时受「最多 {@value #MAX_DETAIL_ITEMS} 项」与「总长不超过 {@value #MAX_SUMMARY_LENGTH}」约束，
     * 预算不足时提前停止，由调用方补截断提示。</p>
     */
    private <T> int appendDetails(StringBuilder message, String prefix, List<T> items,
                                  Function<T, String> formatter) {
        int limit = Math.min(items.size(), MAX_DETAIL_ITEMS);
        StringBuilder details = new StringBuilder();
        int shown = 0;
        while (shown < limit) {
            String item = formatter.apply(items.get(shown));
            int projected = message.length() + prefix.length() + details.length() + item.length()
                    + TRUNCATION_RESERVE;
            if (projected > MAX_SUMMARY_LENGTH && shown > 0) {
                break;
            }
            if (details.length() > 0) {
                details.append("；");
            }
            details.append(item);
            shown++;
        }
        message.append(prefix).append(details);
        return shown;
    }

    private String abbreviate(String reason) {
        if (reason == null) {
            return "未知原因";
        }
        String trimmed = reason.strip();
        return trimmed.length() <= MAX_REASON_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_REASON_LENGTH) + "…";
    }

}
