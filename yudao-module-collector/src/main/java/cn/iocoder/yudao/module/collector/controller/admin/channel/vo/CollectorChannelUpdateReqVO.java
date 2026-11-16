package cn.iocoder.yudao.module.collector.controller.admin.channel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Schema(description = "管理后台 - 通道通用信息修改 Request VO；阈值请使用 update-threshold")
@Data
public class CollectorChannelUpdateReqVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "通道编号不能为空")
    private Long id;

    @Schema(description = "通道名称", example = "ISO 4μm 清洁度")
    @Size(max = 100, message = "通道名称长度不能超过 100 个字符")
    private String channelName;

    @Schema(description = "是否启用：0 否、1 是", example = "1")
    @Min(value = 0, message = "启用开关只能是 0 或 1")
    @Max(value = 1, message = "启用开关只能是 0 或 1")
    private Integer enabled;

    @Schema(description = "协议专用寻址/解析参数 JSON 对象")
    private Map<String, Object> channelConfig;

    @Schema(description = "数值缩放系数", example = "0.1")
    private Double scale;

    @Schema(description = "工程值偏移量", example = "0")
    private Double valueOffset;

    @Schema(description = "工程单位", example = "µm")
    @Size(max = 32, message = "单位长度不能超过 32 个字符")
    private String unit;

    @Schema(description = "图标标识", example = "i-ep:filter")
    @Size(max = 64, message = "图标长度不能超过 64 个字符")
    private String icon;

    @Schema(description = "展示小数位，0~6", example = "1")
    @Min(value = 0, message = "小数位不能小于 0")
    @Max(value = 6, message = "小数位不能大于 6")
    private Integer decimalPlaces;

    @Schema(description = "独立轮询周期（毫秒），为空继承设备", example = "5000")
    @Min(value = 100, message = "轮询周期不能小于 100 毫秒")
    private Integer pollIntervalMs;

    @Schema(description = "描述")
    @Size(max = 255, message = "描述长度不能超过 255 个字符")
    private String description;

    @Schema(description = "排序值", example = "1")
    private Integer sort;

}
