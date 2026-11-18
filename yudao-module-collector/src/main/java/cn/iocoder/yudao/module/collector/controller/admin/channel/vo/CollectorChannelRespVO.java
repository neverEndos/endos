package cn.iocoder.yudao.module.collector.controller.admin.channel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "管理后台 - 通道 Response VO")
@Data
public class CollectorChannelRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "所属设备编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long deviceId;

    @Schema(description = "所属传感器编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long sensorId;

    @Schema(description = "生成该通道的模板编号", example = "1")
    private Long sensorTemplateId;

    @Schema(description = "通道编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "iso_4")
    private String channelCode;

    @Schema(description = "通道名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "ISO 4μm 清洁度")
    private String channelName;

    @Schema(description = "是否启用：0 否、1 是", example = "1")
    private Integer enabled;

    @Schema(description = "通用值类型", example = "number")
    private String valueType;

    @Schema(description = "值类别：number 数值、enum 枚举", example = "number")
    private String valueKind;

    @Schema(description = "数据画像：normal 常规、fast 高频", example = "normal")
    private String dataProfile;

    @Schema(description = "协议专用寻址/解析参数 JSON 对象")
    private Map<String, Object> channelConfig;

    @Schema(description = "数值缩放系数", example = "0.1")
    private Double scale;

    @Schema(description = "工程值偏移量", example = "0")
    private Double valueOffset;

    @Schema(description = "工程单位", example = "µm")
    private String unit;

    @Schema(description = "图标标识", example = "i-ep:filter")
    private String icon;

    @Schema(description = "展示小数位", example = "1")
    private Integer decimalPlaces;

    @Schema(description = "变化死区", example = "0")
    private Double deadband;

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

    @Schema(description = "下严重阈值开关", example = "1")
    private Integer thresholdLowErrorEnabled;

    @Schema(description = "下警告阈值开关", example = "1")
    private Integer thresholdLowWarningEnabled;

    @Schema(description = "上警告阈值开关", example = "1")
    private Integer thresholdHighWarningEnabled;

    @Schema(description = "上严重阈值开关", example = "1")
    private Integer thresholdHighErrorEnabled;

    @Schema(description = "报警恢复死区", example = "0")
    private Double alarmDeadband;

    @Schema(description = "是否启用阈值报警", example = "1")
    private Integer alarmEnabled;

    @Schema(description = "独立轮询周期（毫秒）")
    private Integer pollIntervalMs;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "排序值", example = "1")
    private Integer sort;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
