package cn.iocoder.yudao.module.collector.controller.admin.health.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理后台 - 采集运行详情 Response VO。
 *
 * <p>在摘要之上补充活动报警明细、聚合水位、保留策略与 raw 分区预建情况；全部为只读查询结果。
 * 时间字段均由后端按 Asia/Shanghai 墙钟显式转换为 epoch millis，日期字段保留 yyyy-MM-dd 语义。</p>
 */
@Schema(description = "管理后台 - 采集运行详情 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CollectorHealthOverviewRespVO extends CollectorHealthSummaryRespVO {

    @Schema(description = "活动报警明细，按级别降序、状态更新时间降序，最多 20 条")
    private List<AlarmDetail> alarms;

    @Schema(description = "当前活动报警通道总数；alarms 只返回前 20 条，本字段为全量计数（与 alarm.activeCount 同源）")
    private Integer alarmTotal;

    @Schema(description = "聚合水位按 second / minute / hour / day 固定顺序的汇总，共 4 项")
    private List<AggregateSummary> aggregate;

    @Schema(description = "聚合水位异常明细（status <> 0），最多 20 条")
    private List<AggregateAbnormal> aggregateAbnormal;

    @Schema(description = "保留策略，按 raw、second、minute、hour、day、alarm、heartbeat、command 固定顺序")
    private List<Retention> retention;

    @Schema(description = "raw 日分区预建情况")
    private RawPartition rawPartition;

    @Schema(description = "活动报警明细")
    @Data
    public static class AlarmDetail {

        @Schema(description = "设备编号")
        private Long deviceId;

        @Schema(description = "设备名称")
        private String deviceName;

        @Schema(description = "传感器编号")
        private Long sensorId;

        @Schema(description = "传感器名称")
        private String sensorName;

        @Schema(description = "通道编号")
        private Long channelId;

        @Schema(description = "通道名称")
        private String channelName;

        @Schema(description = "当前级别：2 预警、3 异常")
        private Integer level;

        @Schema(description = "当前命中的阈值类型")
        private String thresholdType;

        @Schema(description = "当前级别开始时间（epoch millis，按 Asia/Shanghai 墙钟转换）")
        private Long since;

        @Schema(description = "最近参与判定的工程值")
        private Double lastValue;

        @Schema(description = "最近判定使用的阈值")
        private Double lastThreshold;

        @Schema(description = "最近参与判定的采样时间（epoch millis，按 Asia/Shanghai 墙钟转换）")
        private Long lastSampleTime;

    }

    @Schema(description = "聚合水位分组汇总")
    @Data
    public static class AggregateSummary {

        @Schema(description = "聚合类型：second / minute / hour / day")
        private String aggType;

        @Schema(description = "水位行数（通道数）；0 表示该类型尚未初始化")
        private Integer rowCount;

        @Schema(description = "status <> 0 的行数；status 1/2 为预留语义，当前聚合流程未写入")
        private Integer abnormalCount;

        @Schema(description = "最大重试次数；retry_count 为预留字段，当前恒为 0，前端应展示 --")
        private Integer maxRetryCount;

        @Schema(description = "最近一次成功时间（epoch millis，按 Asia/Shanghai 墙钟转换）")
        private Long lastSuccessTime;

        @Schema(description = "最小水位时间（epoch millis，按 Asia/Shanghai 墙钟转换）")
        private Long minWatermarkTime;

        @Schema(description = "最大水位时间（epoch millis，按 Asia/Shanghai 墙钟转换）")
        private Long maxWatermarkTime;

        @Schema(description = "是否存在水位行；false 表示尚未初始化，不能展示为「全部正常」")
        private Boolean initialized;

    }

    @Schema(description = "聚合水位异常明细")
    @Data
    public static class AggregateAbnormal {

        @Schema(description = "聚合类型")
        private String aggType;

        @Schema(description = "通道编号")
        private Long channelId;

        @Schema(description = "设备编号")
        private Long deviceId;

        @Schema(description = "设备名称")
        private String deviceName;

        @Schema(description = "通道名称")
        private String channelName;

        @Schema(description = "数据库现有状态值：1 运行中、2 失败（均为预留，原样展示，不据此推断作业正常）")
        private Integer status;

        @Schema(description = "重试次数（预留，当前恒为 0，原样展示）")
        private Integer retryCount;

        @Schema(description = "最近一次错误信息（预留，当前未写入，不做任何补写）")
        private String lastError;

        @Schema(description = "最新已成功提交桶的结束时间（epoch millis，按 Asia/Shanghai 墙钟转换）")
        private Long watermarkTime;

    }

    @Schema(description = "保留策略")
    @Data
    public static class Retention {

        @Schema(description = "策略编号")
        private Long id;

        @Schema(description = "数据类：sample_raw / aggregate_second / ... / command")
        private String dataClass;

        @Schema(description = "数据类中文名")
        private String displayName;

        @Schema(description = "保留天数；0 表示永久保留")
        private Integer retentionDays;

        @Schema(description = "是否启用过期清理")
        private Boolean enabled;

        @Schema(description = "最近执行时间（epoch millis，按 Asia/Shanghai 墙钟转换）；从未执行为 null")
        private Long lastRunAt;

        @Schema(description = "最近执行结果摘要；从未执行为 null")
        private String lastRunResult;

    }

    @Schema(description = "raw 日分区预建情况")
    @Data
    public static class RawPartition {

        @Schema(description = "状态：ok 正常；policy_missing 策略缺失；policy_duplicate 策略重复；"
                + "policy_invalid 策略非法（partition_lead_days 为 NULL 或超出 0~30）",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String status;

        @Schema(description = "策略中的预建天数；策略缺失或重复时为 null（绝不套用默认值）")
        private Integer leadDays;

        @Schema(description = "期望起始日期（Asia/Shanghai 当天）")
        private LocalDate expectedFrom;

        @Schema(description = "期望结束日期（expectedFrom + leadDays）；策略异常时为 null")
        private LocalDate expectedTo;

        @Schema(description = "期望分区数量（leadDays + 1）；策略异常时为 0")
        private Integer expectedCount;

        @Schema(description = "已存在且边界完全匹配的日分区数量")
        private Integer existingCount;

        @Schema(description = "缺失日期，按日期升序，最多 31 天")
        private List<LocalDate> missingDates;

    }

}
