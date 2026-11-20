package cn.iocoder.yudao.module.collector.controller.admin.retention.vo;

import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService.RetentionClassResult;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService.RetentionCleanupReport;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 采集数据保留清理 Response VO")
@Data
public class CollectorRetentionCleanupRespVO {

    @Schema(description = "本次清理使用的业务日期（Asia/Shanghai），格式 yyyy-MM-dd", example = "2026-11-20")
    private LocalDate today;
    @Schema(description = "是否因已有清理任务在执行而整体跳过", example = "false")
    private Boolean lockNotAcquired;
    @Schema(description = "本次删除的总行数（raw 按分区清理，恒为 0）", example = "2048")
    private Long totalDeletedRows;
    @Schema(description = "本次 DROP 的分区总数", example = "1")
    private Integer droppedPartitionCount;
    @Schema(description = "是否存在失败或部分成功的数据类", example = "false")
    private Boolean hasFailure;
    @Schema(description = "各数据类清理结果，固定 8 类顺序 + 未知策略行")
    private List<ClassResult> results;

    /**
     * 服务层报告整体转换。
     *
     * <p>部分成功语义：{@code hasFailure=true} 表示至少一个数据类为 FAILED 或 PARTIAL，
     * 其他数据类可能已正常完成，前端需按类展示结果而不是只看整体标志。</p>
     */
    public static CollectorRetentionCleanupRespVO of(RetentionCleanupReport report) {
        CollectorRetentionCleanupRespVO respVO = new CollectorRetentionCleanupRespVO();
        respVO.setToday(report.today());
        respVO.setLockNotAcquired(report.lockNotAcquired());
        respVO.setTotalDeletedRows(report.totalDeletedRows());
        respVO.setDroppedPartitionCount(report.droppedPartitionCount());
        respVO.setHasFailure(report.hasFailure());
        respVO.setResults(report.results().stream().map(ClassResult::of).toList());
        return respVO;
    }

    @Schema(description = "管理后台 - 单个数据类清理结果")
    @Data
    public static class ClassResult {

        @Schema(description = "数据类编码", example = "heartbeat")
        private String dataClass;
        @Schema(description = "数据类中文名", example = "采集器心跳")
        private String displayName;
        @Schema(description = "清理动作", example = "delete_rows")
        private String action;
        @Schema(description = "本次截止时间（严格小于该时间才删除）")
        private LocalDateTime cutoff;
        @Schema(description = "结果分类：SUCCESS / PARTIAL / FAILED / SKIPPED_DISABLED / SKIPPED_PERMANENT / SKIPPED_LOCKED",
                example = "SUCCESS")
        private String outcome;
        @Schema(description = "已删除行数（raw 恒为 0）", example = "1024")
        private Long deletedRows;
        @Schema(description = "已 DROP 的分区")
        private List<CollectorRetentionPartitionVO> droppedPartitions;
        @Schema(description = "需要人工处理或注意的事项")
        private List<String> warnings;
        @Schema(description = "中文结果摘要，同步写入策略的 last_run_result")
        private String message;

        /** 服务层单类结果转换；outcome 直接输出枚举名，前端按固定集合匹配。 */
        public static ClassResult of(RetentionClassResult result) {
            ClassResult classResult = new ClassResult();
            classResult.setDataClass(result.dataClass());
            classResult.setDisplayName(result.displayName());
            classResult.setAction(result.action());
            classResult.setCutoff(result.cutoff());
            classResult.setOutcome(result.outcome() == null ? null : result.outcome().name());
            classResult.setDeletedRows(result.deletedRows());
            classResult.setDroppedPartitions(result.droppedPartitions().stream()
                    .map(CollectorRetentionPartitionVO::of).toList());
            classResult.setWarnings(result.warnings());
            classResult.setMessage(result.message());
            return classResult;
        }

    }

}
