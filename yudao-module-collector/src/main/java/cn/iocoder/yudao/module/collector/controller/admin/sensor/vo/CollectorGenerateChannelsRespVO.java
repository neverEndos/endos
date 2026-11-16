package cn.iocoder.yudao.module.collector.controller.admin.sensor.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "管理后台 - 按模板生成通道结果 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollectorGenerateChannelsRespVO {

    @Schema(description = "新生成的通道数量", example = "10")
    private Integer createdCount;

    @Schema(description = "因通道已存在而跳过的数量", example = "0")
    private Integer skippedCount;

    @Schema(description = "逐通道结果")
    private List<CollectorGenerateChannelPreviewRespVO> channels;

}
