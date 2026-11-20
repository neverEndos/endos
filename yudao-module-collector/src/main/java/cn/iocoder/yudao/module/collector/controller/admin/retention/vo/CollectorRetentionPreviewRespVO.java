package cn.iocoder.yudao.module.collector.controller.admin.retention.vo;

import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService.RetentionClassPreview;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService.RetentionPreview;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 采集数据保留预检 Response VO")
@Data
public class CollectorRetentionPreviewRespVO {

    @Schema(description = "预检使用的业务日期（Asia/Shanghai），格式 yyyy-MM-dd", example = "2026-11-20")
    private LocalDate today;
    @Schema(description = "各数据类预检结果，固定 8 类顺序：raw、second、minute、hour、day、alarm、heartbeat、command")
    private List<ClassPreview> classes;

    /** 服务层预检结果整体转换。 */
    public static CollectorRetentionPreviewRespVO of(RetentionPreview preview) {
        CollectorRetentionPreviewRespVO respVO = new CollectorRetentionPreviewRespVO();
        respVO.setToday(preview.today());
        respVO.setClasses(preview.classes().stream().map(ClassPreview::of).toList());
        return respVO;
    }

    @Schema(description = "管理后台 - 单个数据类预检结果")
    @Data
    public static class ClassPreview {

        @Schema(description = "数据类编码", example = "aggregate_second")
        private String dataClass;
        @Schema(description = "数据类中文名", example = "秒聚合数据")
        private String displayName;
        @Schema(description = "保留天数；0 表示永久保留", example = "7")
        private Integer retentionDays;
        @Schema(description = "是否启用清理", example = "true")
        private Boolean enabled;
        @Schema(description = "清理动作", example = "delete_rows")
        private String action;
        @Schema(description = "本次截止时间（严格小于该时间才删除）；策略缺失或异常时可能为 null")
        private LocalDateTime cutoff;
        @Schema(description = "本次是否跳过", example = "false")
        private Boolean skipped;
        @Schema(description = "跳过原因；未跳过为 null", example = "策略已停用")
        private String skipReason;
        @Schema(description = "候选删除行数；raw 按分区清理，该字段为 null", example = "1024")
        private Long candidateRows;
        @Schema(description = "候选 DROP 分区；仅 raw 有值")
        private List<CollectorRetentionPartitionVO> candidatePartitions;
        @Schema(description = "需要人工处理或注意的事项")
        private List<String> warnings;

        /** 服务层单类预检结果转换；字段一一对应，不做二次解释。 */
        public static ClassPreview of(RetentionClassPreview preview) {
            ClassPreview classPreview = new ClassPreview();
            classPreview.setDataClass(preview.dataClass());
            classPreview.setDisplayName(preview.displayName());
            classPreview.setRetentionDays(preview.retentionDays());
            classPreview.setEnabled(preview.enabled());
            classPreview.setAction(preview.action());
            classPreview.setCutoff(preview.cutoff());
            classPreview.setSkipped(preview.skipped());
            classPreview.setSkipReason(preview.skipReason());
            classPreview.setCandidateRows(preview.candidateRows());
            classPreview.setCandidatePartitions(preview.candidatePartitions().stream()
                    .map(CollectorRetentionPartitionVO::of).toList());
            classPreview.setWarnings(preview.warnings());
            return classPreview;
        }

    }

}
