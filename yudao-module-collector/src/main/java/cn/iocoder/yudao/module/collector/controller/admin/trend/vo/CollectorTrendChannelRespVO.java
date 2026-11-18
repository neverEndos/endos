package cn.iocoder.yudao.module.collector.controller.admin.trend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 趋势选择器通道 Response VO")
@Data
public class CollectorTrendChannelRespVO {

    @Schema(description = "通道编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "所属设备编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long deviceId;

    @Schema(description = "通道名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "油温")
    private String channelName;

    @Schema(description = "工程单位", example = "℃")
    private String unit;

    @Schema(description = "数据画像：normal|fast", example = "fast")
    private String dataProfile;

    @Schema(description = "值类别：number 数值、enum 枚举", example = "number")
    private String valueKind;

}
