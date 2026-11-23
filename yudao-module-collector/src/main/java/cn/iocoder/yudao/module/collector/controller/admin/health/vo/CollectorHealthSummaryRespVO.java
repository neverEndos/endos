package cn.iocoder.yudao.module.collector.controller.admin.health.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 采集运行摘要 Response VO。
 *
 * <p>所有 {@link LocalDateTime} 字段沿用项目统一的 epoch millis 序列化；
 * 缺失的心跳 / 状态 / 报警信息一律返回 null 或 0，不伪造「运行正常」。</p>
 */
@Schema(description = "管理后台 - 采集运行摘要 Response VO")
@Data
public class CollectorHealthSummaryRespVO {

    @Schema(description = "后端服务器时间（Asia/Shanghai）", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime serverTime;

    @Schema(description = "采集器进程心跳摘要；从未收到心跳时为 null")
    private Process process;

    @Schema(description = "当前设备状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private Device device;

    @Schema(description = "活动报警摘要", requiredMode = Schema.RequiredMode.REQUIRED)
    private Alarm alarm;

    @Schema(description = "采集器进程心跳摘要")
    @Data
    public static class Process {

        @Schema(description = "采集器实例编号")
        private String instanceId;

        @Schema(description = "心跳状态：starting / running / degraded / stopped")
        private String status;

        @Schema(description = "心跳时间")
        private LocalDateTime heartbeatTime;

        @Schema(description = "最近一次数据库写入延迟（毫秒）")
        private Integer writeLagMs;

        @Schema(description = "配置设备总数")
        private Integer deviceTotal;

        @Schema(description = "在线设备数")
        private Integer deviceOnline;

        @Schema(description = "采集器版本")
        private String collectorVersion;

    }

    @Schema(description = "当前设备状态")
    @Data
    public static class Device {

        @Schema(description = "设备编号", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long deviceId;

        @Schema(description = "设备名称", requiredMode = Schema.RequiredMode.REQUIRED)
        private String deviceName;

        @Schema(description = "是否在线；设备从未上报状态行时为 null（未知），不伪造在线")
        private Boolean online;

        @Schema(description = "最近一次状态更新时间")
        private LocalDateTime lastPollAt;

        @Schema(description = "最近一次采集成功时间")
        private LocalDateTime lastSuccessAt;

        @Schema(description = "最近一次请求耗时（毫秒）")
        private Integer lastLatencyMs;

        @Schema(description = "最近一次错误信息；无错误时为 null")
        private String lastError;

        @Schema(description = "连续失败次数；无状态行时为 null")
        private Integer consecutiveFailures;

        @Schema(description = "设备轮询周期（毫秒），取自 collector_device.poll_interval_ms；缺失为 null")
        private Integer pollIntervalMs;

    }

    @Schema(description = "活动报警摘要")
    @Data
    public static class Alarm {

        @Schema(description = "当前活动报警通道数（collector_alarm_state 中 level > 0 的通道数）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer activeCount;

        @Schema(description = "当前活动报警最高级别：0 表示无活动报警（级别仅 0/2/3）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer highestLevel;

    }

}
