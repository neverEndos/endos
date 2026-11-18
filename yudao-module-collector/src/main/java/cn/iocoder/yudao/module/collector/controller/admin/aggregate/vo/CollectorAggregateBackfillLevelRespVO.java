package cn.iocoder.yudao.module.collector.controller.admin.aggregate.vo;

import cn.iocoder.yudao.module.collector.service.aggregate.CollectorAggregateService.AggregateLevelResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 采集聚合回填单层结果")
@Data
public class CollectorAggregateBackfillLevelRespVO {

    @Schema(description = "聚合粒度")
    private String aggType;
    @Schema(description = "实际处理通道数")
    private Integer channelCount;
    @Schema(description = "写入或更新的聚合桶数")
    private Long bucketCount;
    @Schema(description = "聚合源行数")
    private Long rowCount;
    @Schema(description = "整批源为空而跳过删除/写入的批次数")
    private Long skippedEmptyBatches;
    @Schema(description = "本轮无源而保留的旧目标桶数")
    private Long preservedBuckets;
    @Schema(description = "该层执行耗时（毫秒）")
    private Long durationMillis;

    public static CollectorAggregateBackfillLevelRespVO of(AggregateLevelResult result) {
        CollectorAggregateBackfillLevelRespVO respVO = new CollectorAggregateBackfillLevelRespVO();
        respVO.setAggType(result.aggType());
        respVO.setChannelCount(result.channelCount());
        respVO.setBucketCount(result.bucketCount());
        respVO.setRowCount(result.rowCount());
        respVO.setSkippedEmptyBatches(result.skippedEmptyBatches());
        respVO.setPreservedBuckets(result.preservedBuckets());
        respVO.setDurationMillis(result.durationMillis());
        return respVO;
    }

}
