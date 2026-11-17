package cn.iocoder.yudao.module.collector.controller.admin.channel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 设备通道阈值矩阵 Response VO")
@Data
public class CollectorChannelDeviceRespVO {

    @Schema(description = "按传感器分组的通道列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Sensor> sensors;

    @Schema(description = "管理后台 - 阈值矩阵传感器分组")
    @Data
    public static class Sensor {

        @Schema(description = "传感器编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        private Long id;

        @Schema(description = "传感器名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "油液传感器 1")
        private String name;

        @Schema(description = "传感器类型/型号名称", example = "HSI-8")
        private String model;

        @Schema(description = "该传感器下的通道", requiredMode = Schema.RequiredMode.REQUIRED)
        private List<Channel> channels;

    }

    @Schema(description = "管理后台 - 阈值矩阵通道")
    @Data
    public static class Channel {

        @Schema(description = "通道编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        private Long id;

        @Schema(description = "通道编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "iso_4")
        private String channelCode;

        @Schema(description = "通道名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "ISO 4μm 清洁度")
        private String channelName;

        @Schema(description = "工程单位", example = "µm")
        private String unit;

        @Schema(description = "值类别：number 数值、enum 枚举", example = "number")
        private String valueKind;

        @Schema(description = "展示小数位", example = "1")
        private Integer decimalPlaces;

        @Schema(description = "量程下限", example = "0")
        private Double valueMin;

        @Schema(description = "量程上限", example = "100")
        private Double valueMax;

        @Schema(description = "异常下限", example = "10")
        private Double thresholdLowError;

        @Schema(description = "预警下限", example = "30")
        private Double thresholdLowWarning;

        @Schema(description = "预警上限", example = "70")
        private Double thresholdHighWarning;

        @Schema(description = "异常上限", example = "90")
        private Double thresholdHighError;

        @Schema(description = "异常下限开关：0 否、1 是", example = "1")
        private Integer thresholdLowErrorEnabled;

        @Schema(description = "预警下限开关：0 否、1 是", example = "1")
        private Integer thresholdLowWarningEnabled;

        @Schema(description = "预警上限开关：0 否、1 是", example = "1")
        private Integer thresholdHighWarningEnabled;

        @Schema(description = "异常上限开关：0 否、1 是", example = "1")
        private Integer thresholdHighErrorEnabled;

        @Schema(description = "是否启用阈值报警：0 否、1 是", example = "1")
        private Integer alarmEnabled;

        @Schema(description = "变化死区", example = "0")
        private Double deadband;

    }

}
