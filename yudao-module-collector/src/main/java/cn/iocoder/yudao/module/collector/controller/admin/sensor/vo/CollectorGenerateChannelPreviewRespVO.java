package cn.iocoder.yudao.module.collector.controller.admin.sensor.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Schema(description = "管理后台 - 按模板生成通道的预览/结果项 Response VO")
@Data
public class CollectorGenerateChannelPreviewRespVO {

    @Schema(description = "模板编号", example = "1")
    private Long sensorTemplateId;

    @Schema(description = "通道编码", example = "iso_4")
    private String channelCode;

    @Schema(description = "通道名称", example = "ISO 4μm 清洁度")
    private String channelName;

    @Schema(description = "值类别：number 数值、enum 枚举", example = "number")
    private String valueKind;

    @Schema(description = "工程单位", example = "µm")
    private String unit;

    @Schema(description = "图标标识", example = "i-ep:filter")
    private String icon;

    @Schema(description = "量程下限", example = "0")
    private Double valueMin;

    @Schema(description = "量程上限", example = "100")
    private Double valueMax;

    @Schema(description = "数值缩放系数", example = "0.1")
    private Double scale;

    @Schema(description = "工程值偏移量", example = "0")
    private Double valueOffset;

    @Schema(description = "展示小数位", example = "1")
    private Integer decimalPlaces;

    @Schema(description = "下严重阈值", example = "10")
    private Double thresholdLowError;

    @Schema(description = "下警告阈值", example = "30")
    private Double thresholdLowWarning;

    @Schema(description = "上警告阈值", example = "70")
    private Double thresholdHighWarning;

    @Schema(description = "上严重阈值", example = "90")
    private Double thresholdHighError;

    @Schema(description = "下严重阈值开关", example = "1")
    private Integer thresholdLowErrorEnabled;

    @Schema(description = "下警告阈值开关", example = "1")
    private Integer thresholdLowWarningEnabled;

    @Schema(description = "上警告阈值开关", example = "1")
    private Integer thresholdHighWarningEnabled;

    @Schema(description = "上严重阈值开关", example = "1")
    private Integer thresholdHighErrorEnabled;

    @Schema(description = "是否启用阈值报警", example = "1")
    private Integer alarmEnabled;

    @Schema(description = "协议专用通道参数 JSON 对象")
    private Map<String, Object> channelConfig;

    @Schema(description = "执行生成时是否因通道已存在而跳过；预览接口为 null", example = "false")
    private Boolean skipped;

}
