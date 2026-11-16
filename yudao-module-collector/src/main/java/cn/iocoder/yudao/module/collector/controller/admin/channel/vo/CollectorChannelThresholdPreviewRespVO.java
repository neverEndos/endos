package cn.iocoder.yudao.module.collector.controller.admin.channel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 通道阈值预览 Response VO")
@Data
public class CollectorChannelThresholdPreviewRespVO {

    @Schema(description = "量程下限", example = "0")
    private Double valueMin;

    @Schema(description = "量程上限", example = "100")
    private Double valueMax;

    @Schema(description = "下严重阈值", example = "10")
    private Double thresholdLowError;

    @Schema(description = "下警告阈值", example = "30")
    private Double thresholdLowWarning;

    @Schema(description = "上警告阈值", example = "70")
    private Double thresholdHighWarning;

    @Schema(description = "上严重阈值", example = "90")
    private Double thresholdHighError;

}
