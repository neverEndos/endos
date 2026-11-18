package cn.iocoder.yudao.module.collector.controller.admin.trend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 采集趋势查询 Request VO")
@Data
public class CollectorTrendSeriesReqVO {

    @Schema(description = "设备编号；传入时校验通道归属", example = "1")
    private Long deviceId;

    @Schema(description = "通道编号列表，1~8 个", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "[1, 2]")
    @NotEmpty(message = "通道编号列表不能为空")
    @Size(max = 8, message = "通道编号最多 8 个")
    private List<Long> channelIds;

    @Schema(description = "开始时间（epoch millis，Asia/Shanghai 墙钟）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1737000000000")
    @NotNull(message = "开始时间不能为空")
    private Long from;

    @Schema(description = "结束时间（epoch millis，Asia/Shanghai 墙钟），必须晚于开始时间",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1737003600000")
    @NotNull(message = "结束时间不能为空")
    private Long to;

    @Schema(description = "粒度：auto|raw|1s|1m|5m|1h|1d，默认 auto", example = "auto")
    private String granularity;

    @Schema(description = "是否额外返回每个通道的原始点抽样序列", example = "false")
    private Boolean includeRaw;

}
