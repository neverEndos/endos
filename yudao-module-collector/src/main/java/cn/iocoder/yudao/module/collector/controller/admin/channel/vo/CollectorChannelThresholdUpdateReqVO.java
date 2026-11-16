package cn.iocoder.yudao.module.collector.controller.admin.channel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 通道阈值修改 Request VO")
@Data
public class CollectorChannelThresholdUpdateReqVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "通道编号不能为空")
    private Long id;

    @Schema(description = "量程下限；可为空", example = "0")
    private Double valueMin;

    @Schema(description = "量程上限；可为空", example = "100")
    private Double valueMax;

    @Schema(description = "下严重阈值；可为空", example = "10")
    private Double thresholdLowError;

    @Schema(description = "下警告阈值；可为空", example = "30")
    private Double thresholdLowWarning;

    @Schema(description = "上警告阈值；可为空", example = "70")
    private Double thresholdHighWarning;

    @Schema(description = "上严重阈值；可为空", example = "90")
    private Double thresholdHighError;

    @Schema(description = "下严重阈值开关：0 否、1 是", example = "1")
    @Min(value = 0, message = "阈值开关只能是 0 或 1")
    @Max(value = 1, message = "阈值开关只能是 0 或 1")
    private Integer thresholdLowErrorEnabled;

    @Schema(description = "下警告阈值开关：0 否、1 是", example = "1")
    @Min(value = 0, message = "阈值开关只能是 0 或 1")
    @Max(value = 1, message = "阈值开关只能是 0 或 1")
    private Integer thresholdLowWarningEnabled;

    @Schema(description = "上警告阈值开关：0 否、1 是", example = "1")
    @Min(value = 0, message = "阈值开关只能是 0 或 1")
    @Max(value = 1, message = "阈值开关只能是 0 或 1")
    private Integer thresholdHighWarningEnabled;

    @Schema(description = "上严重阈值开关：0 否、1 是", example = "1")
    @Min(value = 0, message = "阈值开关只能是 0 或 1")
    @Max(value = 1, message = "阈值开关只能是 0 或 1")
    private Integer thresholdHighErrorEnabled;

    @Schema(description = "是否启用阈值报警：0 否、1 是", example = "1")
    @Min(value = 0, message = "报警开关只能是 0 或 1")
    @Max(value = 1, message = "报警开关只能是 0 或 1")
    private Integer alarmEnabled;

    @Schema(description = "变化死区，非负", example = "0")
    private Double deadband;

    @Schema(description = "报警恢复死区，非负", example = "0")
    private Double alarmDeadband;

}
