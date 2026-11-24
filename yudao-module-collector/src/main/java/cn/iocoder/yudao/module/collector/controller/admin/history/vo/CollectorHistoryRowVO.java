package cn.iocoder.yudao.module.collector.controller.admin.history.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 通道历史数据行。
 *
 * <p>raw 与聚合层共用同一结构：raw 只填 {@code value/rawValue/quality}，
 * 聚合层只填 {@code sampleCount/goodCount/badCount/value*}，不适用的列恒为 null。
 */
@Schema(description = "管理后台 - 通道历史数据行 Response VO")
@Data
public class CollectorHistoryRowVO {

    @Schema(description = "桶时间（epoch 毫秒，Asia/Shanghai 墙钟）；raw 取 collect_time，"
            + "day 取 bucket_date 当天 00:00 换算结果。结果固定按该字段升序排列。",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1767196800000")
    private Long bucketTime;

    @Schema(description = "工程单位，取自 collector_channel.unit", example = "℃")
    private String unit;

    @Schema(description = "通道名称，取自 collector_channel.channel_name", example = "油温")
    private String channelName;

    // ========== raw 专有 ==========

    @Schema(description = "工程值（raw 粒度）", example = "23.5")
    private Double value;

    @Schema(description = "整数原始值（raw 粒度，浮点协议可为空）", example = "2350")
    private Long rawValue;

    @Schema(description = "质量码（raw 粒度）：0 good、1 uncertain、2 bad", example = "0")
    private Integer quality;

    // ========== 聚合专有 ==========

    @Schema(description = "桶内全部采样数（含 good/uncertain/bad）", example = "60")
    private Integer sampleCount;

    @Schema(description = "桶内 quality=0 的采样数", example = "58")
    private Integer goodCount;

    @Schema(description = "桶内 quality=2 的采样数", example = "1")
    private Integer badCount;

    @Schema(description = "有效样本加权均值", example = "23.42")
    private Double valueAvg;

    @Schema(description = "有效样本最小值", example = "23.10")
    private Double valueMin;

    @Schema(description = "有效样本最大值", example = "23.88")
    private Double valueMax;

    @Schema(description = "桶内首个有效值", example = "23.20")
    private Double valueFirst;

    @Schema(description = "桶内最后有效值", example = "23.51")
    private Double valueLast;

}
