package cn.iocoder.yudao.module.collector.controller.admin.channel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 通道阈值批量修改 Request VO")
@Data
public class CollectorChannelThresholdBatchUpdateReqVO {

    @Schema(description = "设备编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "设备编号不能为空")
    private Long deviceId;

    @Schema(description = "待修改的数值通道", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "待修改通道不能为空")
    private List<@Valid Channel> channels;

    @Schema(description = "管理后台 - 通道阈值批量修改项")
    @Data
    public static class Channel {

        @Schema(description = "通道编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        @NotNull(message = "通道编号不能为空")
        private Long channelId;

        @Schema(description = "异常下限；可为空", example = "10")
        private Double thresholdLowError;

        @Schema(description = "预警下限；可为空", example = "30")
        private Double thresholdLowWarning;

        @Schema(description = "预警上限；可为空", example = "70")
        private Double thresholdHighWarning;

        @Schema(description = "异常上限；可为空", example = "90")
        private Double thresholdHighError;

        @Schema(description = "异常下限开关：0 否、1 是", example = "1")
        @Min(value = 0, message = "阈值开关只能是 0 或 1")
        @Max(value = 1, message = "阈值开关只能是 0 或 1")
        private Integer thresholdLowErrorEnabled;

        @Schema(description = "预警下限开关：0 否、1 是", example = "1")
        @Min(value = 0, message = "阈值开关只能是 0 或 1")
        @Max(value = 1, message = "阈值开关只能是 0 或 1")
        private Integer thresholdLowWarningEnabled;

        @Schema(description = "预警上限开关：0 否、1 是", example = "1")
        @Min(value = 0, message = "阈值开关只能是 0 或 1")
        @Max(value = 1, message = "阈值开关只能是 0 或 1")
        private Integer thresholdHighWarningEnabled;

        @Schema(description = "异常上限开关：0 否、1 是", example = "1")
        @Min(value = 0, message = "阈值开关只能是 0 或 1")
        @Max(value = 1, message = "阈值开关只能是 0 或 1")
        private Integer thresholdHighErrorEnabled;

        @Schema(description = "是否启用阈值报警：0 否、1 是", example = "1")
        @Min(value = 0, message = "报警开关只能是 0 或 1")
        @Max(value = 1, message = "报警开关只能是 0 或 1")
        private Integer alarmEnabled;

        @Schema(description = "变化死区，非负", example = "0")
        private Double deadband;

    }

}
