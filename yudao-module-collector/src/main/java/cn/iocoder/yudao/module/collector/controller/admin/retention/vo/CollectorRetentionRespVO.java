package cn.iocoder.yudao.module.collector.controller.admin.retention.vo;

import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionPolicyService.RetentionPolicySnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 采集数据保留策略 Response VO")
@Data
public class CollectorRetentionRespVO {

    @Schema(description = "策略编号", example = "1")
    private Long id;
    @Schema(description = "数据类编码（代码内固定，不可修改）", example = "sample_raw")
    private String dataClass;
    @Schema(description = "数据类中文名", example = "采集原始数据")
    private String displayName;
    @Schema(description = "保留天数；0 表示永久保留", example = "30")
    private Integer retentionDays;
    @Schema(description = "未来分区预建天数，仅 sample_raw 有意义", example = "7")
    private Integer partitionLeadDays;
    @Schema(description = "清理动作（代码内固定，不可修改）：drop_partition / delete_rows", example = "drop_partition")
    private String action;
    @Schema(description = "是否启用清理：0 停用、1 启用", example = "1")
    private Integer enabled;
    @Schema(description = "策略说明")
    private String description;
    @Schema(description = "最近一次清理执行时间")
    private LocalDateTime lastRunAt;
    @Schema(description = "最近一次清理结果摘要")
    private String lastRunResult;

    /** 由服务层快照转换；enabled 布尔值统一映射为 0/1 供前端直接使用。 */
    public static CollectorRetentionRespVO of(RetentionPolicySnapshot snapshot) {
        CollectorRetentionRespVO respVO = new CollectorRetentionRespVO();
        respVO.setId(snapshot.id());
        respVO.setDataClass(snapshot.dataClass());
        respVO.setDisplayName(snapshot.displayName());
        respVO.setRetentionDays(snapshot.retentionDays());
        respVO.setPartitionLeadDays(snapshot.partitionLeadDays());
        respVO.setAction(snapshot.action());
        respVO.setEnabled(snapshot.enabled() ? 1 : 0);
        respVO.setDescription(snapshot.description());
        respVO.setLastRunAt(snapshot.lastRunAt());
        respVO.setLastRunResult(snapshot.lastRunResult());
        return respVO;
    }

}
