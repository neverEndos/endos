package cn.iocoder.yudao.module.collector.controller.admin.channel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 通道阈值预览 Request VO")
@Data
public class CollectorChannelThresholdPreviewReqVO {

    @Schema(description = "量程下限；为空则返回空阈值", example = "0")
    private Double valueMin;

    @Schema(description = "量程上限；为空则返回空阈值", example = "100")
    private Double valueMax;

    @Schema(description = "下严重阈值比例，默认 0.10", example = "0.10")
    private Double thresholdLowErrorRatio;

    @Schema(description = "下警告阈值比例，默认 0.30", example = "0.30")
    private Double thresholdLowWarningRatio;

    @Schema(description = "上警告阈值比例，默认 0.70", example = "0.70")
    private Double thresholdHighWarningRatio;

    @Schema(description = "上严重阈值比例，默认 0.90", example = "0.90")
    private Double thresholdHighErrorRatio;

}
