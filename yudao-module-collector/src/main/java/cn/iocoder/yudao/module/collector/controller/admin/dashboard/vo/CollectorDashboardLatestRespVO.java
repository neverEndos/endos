package cn.iocoder.yudao.module.collector.controller.admin.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "管理后台 - 采集设备最新值看板 Response VO")
@Data
public class CollectorDashboardLatestRespVO {

    @Schema(description = "设备状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private Device device;

    @Schema(description = "按传感器分组的通道最新值", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Sensor> sensors;

    @Schema(description = "后端服务器时间（Asia/Shanghai，序列化为 epoch millis）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long serverTime;

    @Data
    public static class Device {

        @Schema(description = "设备编号", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long id;

        @Schema(description = "设备名称", requiredMode = Schema.RequiredMode.REQUIRED)
        private String name;

        @Schema(description = "是否在线；最新采样超过十倍轮询周期时强制离线，无采样时按状态更新时间判定",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private Boolean online;

        @Schema(description = "最近一次轮询状态更新时间（epoch millis）")
        private Long lastPollAt;

        @Schema(description = "采集器进程最近一次数据库写入延迟（毫秒，非设备级）")
        private Integer lastWriteLagMs;

        @Schema(description = "连续失败次数", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer consecutiveFailures;

        @Schema(description = "设备轮询周期（毫秒）", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer pollIntervalMs;

        @Schema(description = "是否陈旧离线：最新采样超十倍周期；无采样时按状态更新时间，均无则离线",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private Boolean staleOffline;

    }

    @Data
    public static class Sensor {

        @Schema(description = "传感器编号", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long id;

        @Schema(description = "传感器名称", requiredMode = Schema.RequiredMode.REQUIRED)
        private String name;

        @Schema(description = "传感器型号")
        private String model;

        @Schema(description = "通道最新值", requiredMode = Schema.RequiredMode.REQUIRED)
        private List<Channel> channels;

    }

    @Data
    public static class Channel {

        @Schema(description = "通道编号", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long id;

        @Schema(description = "通道名称", requiredMode = Schema.RequiredMode.REQUIRED)
        private String name;

        @Schema(description = "工程单位")
        private String unit;

        @Schema(description = "值类别：number 数值、enum 枚举", requiredMode = Schema.RequiredMode.REQUIRED)
        private String valueKind;

        @Schema(description = "数值通道最新值")
        private BigDecimal valueNum;

        @Schema(description = "枚举通道最新值")
        private String valueText;

        @Schema(description = "存储的数据质量：0 正常、1 不确定、2 坏质量；无数据时为空")
        private Integer quality;

        @Schema(description = "是否陈旧：采样时间超过三倍设备轮询周期；由接口按服务器时间派生",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private Boolean stale;

        @Schema(description = "采样时间（epoch millis）")
        private Long sampleTime;

    }

}
