package cn.iocoder.yudao.module.collector.controller.admin.retention.vo;

import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService.PartitionCandidate;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService.PartitionDrop;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预检与清理结果共用的分区项。
 *
 * <p>{@code bound} 是 PostgreSQL catalog 中的原始边界文本（仅预检提供）；清理结果只回传
 * 实际 DROP 的分区名与解析后的边界，因此该字段为 null。</p>
 */
@Schema(description = "管理后台 - 采集数据保留分区项")
@Data
public class CollectorRetentionPartitionVO {

    @Schema(description = "分区名", example = "collector_sample_raw_p20260101")
    private String partitionName;
    @Schema(description = "catalog 原始边界文本；清理结果不回填", example = "FOR VALUES FROM ('2026-01-01 00:00:00') TO ('2026-01-02 00:00:00')")
    private String bound;
    @Schema(description = "分区起始时间（含）")
    private LocalDateTime from;
    @Schema(description = "分区结束时间（不含）")
    private LocalDateTime to;

    /** 预检候选分区：保留 catalog 原始边界，便于前端原样展示。 */
    public static CollectorRetentionPartitionVO of(PartitionCandidate candidate) {
        CollectorRetentionPartitionVO partitionVO = new CollectorRetentionPartitionVO();
        partitionVO.setPartitionName(candidate.partitionName());
        partitionVO.setBound(candidate.bound());
        partitionVO.setFrom(candidate.from());
        partitionVO.setTo(candidate.to());
        return partitionVO;
    }

    /** 已 DROP 分区：只有名称与解析后的边界，不回读 catalog。 */
    public static CollectorRetentionPartitionVO of(PartitionDrop drop) {
        CollectorRetentionPartitionVO partitionVO = new CollectorRetentionPartitionVO();
        partitionVO.setPartitionName(drop.partitionName());
        partitionVO.setFrom(drop.from());
        partitionVO.setTo(drop.to());
        return partitionVO;
    }

}
