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
import java.util.StringJoiner;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.PARTITION_MAINTAIN_FAILED;

/**
 * 采集 raw 日分区预建定时任务，由 infra 定时任务调度、collector 模块执行业务。
 */
@Slf4j
@Component("collectorPartitionMaintainJob")
public class CollectorPartitionMaintainJob implements JobHandler {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private CollectorPartitionService partitionService;

    @Override
    public String execute(String param) {
        long begin = System.currentTimeMillis();
        PartitionMaintainResult result = partitionService.maintainDailyPartitions();
        long duration = System.currentTimeMillis() - begin;
        String message = formatResult(result, duration);
        if (result.failed()) {
            log.error("[execute][{}]", message);
            // 存在默认分区冲突或 DDL 失败时必须让 infra 任务日志/状态体现为失败。
            throw exception(PARTITION_MAINTAIN_FAILED, message);
        }
        log.info("[execute][{}]", message);
        return message;
    }

    private String formatResult(PartitionMaintainResult result, long duration) {
        String range = result.from().format(DATE_FORMATTER) + "~" + result.to().format(DATE_FORMATTER);
        if (result.lockNotAcquired()) {
            return String.format("检查 %s，已有分区维护任务在执行，本次跳过，耗时 %d ms", range, duration);
        }
        StringBuilder message = new StringBuilder(String.format("检查 %s，新建 %d 个，已存在 %d 个，耗时 %d ms",
                range, result.createdPartitions().size(), result.skippedExistingCount(), duration));
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

}
