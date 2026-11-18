package cn.iocoder.yudao.module.collector.controller.admin.aggregate.vo;

import cn.iocoder.yudao.module.collector.service.aggregate.CollectorAggregateService.AggregateResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 采集聚合手工回填 Response VO")
@Data
public class CollectorAggregateBackfillRespVO {

    @Schema(description = "聚合粒度")
    private String aggType;
    @Schema(description = "实际处理通道数")
    private Integer channelCount;
    @Schema(description = "写入或更新的聚合桶数")
    private Long bucketCount;
    @Schema(description = "聚合源行数；上卷时由 sample_count 求和")
    private Long rowCount;
    @Schema(description = "整批源为空且未开启 forceClean 而跳过删除/写入的批次数")
    private Long skippedEmptyBatches;
    @Schema(description = "本轮无源而保留的旧目标桶数")
    private Long preservedBuckets;
    @Schema(description = "同步执行耗时（毫秒）")
    private Long durationMillis;
    @Schema(description = "各层结果；首项为根层，cascade 时依次为上层")
    private List<CollectorAggregateBackfillLevelRespVO> levels;

    public static CollectorAggregateBackfillRespVO of(AggregateResult result) {
        CollectorAggregateBackfillRespVO respVO = new CollectorAggregateBackfillRespVO();
        respVO.setAggType(result.aggType());
        respVO.setChannelCount(result.channelCount());
        respVO.setBucketCount(result.bucketCount());
        respVO.setRowCount(result.rowCount());
        respVO.setSkippedEmptyBatches(result.skippedEmptyBatches());
        respVO.setPreservedBuckets(result.preservedBuckets());
        respVO.setDurationMillis(result.durationMillis());
        respVO.setLevels(result.levels().stream()
                .map(CollectorAggregateBackfillLevelRespVO::of).toList());
        return respVO;
    }

}
