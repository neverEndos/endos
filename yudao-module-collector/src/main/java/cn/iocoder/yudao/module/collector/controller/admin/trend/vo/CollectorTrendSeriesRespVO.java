package cn.iocoder.yudao.module.collector.controller.admin.trend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 采集趋势查询 Response VO")
@Data
public class CollectorTrendSeriesRespVO {

    @Schema(description = "主序列数据源汇总：raw|second|minute|hour|day；多通道跨层时为 mixed",
            example = "minute")
    private String sourceSummary;

    @Schema(description = "趋势序列；includeRaw=true 时每个通道额外带一条 kind=raw 的抽样序列",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Series> series;

    @Schema(description = "原始点叠加是否因超时降级；true 时仅主聚合序列有效，raw 序列为空",
            example = "false")
    private Boolean rawDegraded;

    @Schema(description = "原始点叠加降级时的中文警告信息；未降级时为空", example = "原始点加载超时，已仅展示聚合趋势")
    private String rawWarning;

    @Schema(description = "管理后台 - 趋势序列")
    @Data
    public static class Series {

        @Schema(description = "通道编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        private Long channelId;

        @Schema(description = "通道名称", example = "油温")
        private String channelName;

        @Schema(description = "工程单位", example = "℃")
        private String unit;

        @Schema(description = "数据画像：normal|fast", example = "fast")
        private String dataProfile;

        @Schema(description = "数据源：raw|second|minute|hour|day", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "minute")
        private String source;

        @Schema(description = "实际桶宽（秒）；前端用于标注时间轴刻度", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "60")
        private Integer bucketSeconds;

        @Schema(description = "序列类型：aggregate 聚合线、raw 原始点叠加", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "aggregate")
        private String kind;

        @Schema(description = "数据点", requiredMode = Schema.RequiredMode.REQUIRED)
        private List<Point> points;

    }

    @Schema(description = "管理后台 - 趋势数据点")
    @Data
    public static class Point {

        @Schema(description = "桶起点或抽样点时间（epoch millis）", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "1737000000000")
        private Long t;

        @Schema(description = "原始采样值；仅 kind=raw 的抽样序列返回", example = "23.5")
        private Double value;

        @Schema(description = "桶内有效样本均值；kind=raw 时等于 value", example = "23.5")
        private Double avg;

        @Schema(description = "桶内有效样本最小值", example = "20.1")
        private Double min;

        @Schema(description = "桶内有效样本最大值", example = "25.9")
        private Double max;

        @Schema(description = "桶内有效样本数（quality=0 且 value 非 NULL）", example = "12")
        private Integer count;

    }

}
