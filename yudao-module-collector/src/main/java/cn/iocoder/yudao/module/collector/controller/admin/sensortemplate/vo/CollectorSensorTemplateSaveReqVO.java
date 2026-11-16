package cn.iocoder.yudao.module.collector.controller.admin.sensortemplate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Schema(description = "管理后台 - 传感器参数模板创建/修改 Request VO")
@Data
public class CollectorSensorTemplateSaveReqVO {

    @Schema(description = "编号，修改时必填", example = "1")
    private Long id;

    @Schema(description = "传感器类型编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "传感器类型编号不能为空")
    private Long sensorTypeId;

    @Schema(description = "通道编码（类型内唯一）", requiredMode = Schema.RequiredMode.REQUIRED, example = "iso_4")
    @NotBlank(message = "通道编码不能为空")
    @Size(max = 64, message = "通道编码长度不能超过 64 个字符")
    private String channelCode;

    @Schema(description = "通道名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "ISO 4μm 清洁度")
    @NotBlank(message = "通道名称不能为空")
    @Size(max = 100, message = "通道名称长度不能超过 100 个字符")
    private String channelName;

    @Schema(description = "值类别：number 数值、enum 枚举；默认 number", example = "number")
    @Pattern(regexp = "number|enum", message = "值类别只能是 number 或 enum")
    private String valueKind;

    @Schema(description = "工程单位", example = "µm")
    @Size(max = 32, message = "单位长度不能超过 32 个字符")
    private String unit;

    @Schema(description = "图标标识", example = "i-ep:filter")
    @Size(max = 64, message = "图标长度不能超过 64 个字符")
    private String icon;

    @Schema(description = "量程下限", example = "0")
    private Double valueMin;

    @Schema(description = "量程上限", example = "100")
    private Double valueMax;

    @Schema(description = "数值缩放系数，默认 1", example = "0.1")
    private Double scale;

    @Schema(description = "工程值偏移量，默认 0", example = "0")
    private Double valueOffset;

    @Schema(description = "展示小数位，0~6，默认 0", example = "1")
    @Min(value = 0, message = "小数位不能小于 0")
    @Max(value = 6, message = "小数位不能大于 6")
    private Integer decimalPlaces;

    @Schema(description = "下严重阈值比例，默认 0.10", example = "0.10")
    @DecimalMin(value = "0", message = "下严重阈值比例不能小于 0")
    @DecimalMax(value = "1", message = "下严重阈值比例不能大于 1")
    private Double thresholdLowErrorRatio;

    @Schema(description = "下警告阈值比例，默认 0.30", example = "0.30")
    @DecimalMin(value = "0", message = "下警告阈值比例不能小于 0")
    @DecimalMax(value = "1", message = "下警告阈值比例不能大于 1")
    private Double thresholdLowWarningRatio;

    @Schema(description = "上警告阈值比例，默认 0.70", example = "0.70")
    @DecimalMin(value = "0", message = "上警告阈值比例不能小于 0")
    @DecimalMax(value = "1", message = "上警告阈值比例不能大于 1")
    private Double thresholdHighWarningRatio;

    @Schema(description = "上严重阈值比例，默认 0.90", example = "0.90")
    @DecimalMin(value = "0", message = "上严重阈值比例不能小于 0")
    @DecimalMax(value = "1", message = "上严重阈值比例不能大于 1")
    private Double thresholdHighErrorRatio;

    @Schema(description = "是否启用阈值报警：0 否、1 是", example = "1")
    @Min(value = 0, message = "报警开关只能是 0 或 1")
    @Max(value = 1, message = "报警开关只能是 0 或 1")
    private Integer alarmEnabled;

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

    @Schema(description = "协议专用模板参数 JSON 对象")
    private Map<String, Object> channelConfig;

    @Schema(description = "排序值，默认 0", example = "1")
    private Integer sort;

    @Schema(description = "是否启用：0 否、1 是；默认 1", example = "1")
    private Integer enabled;

}
