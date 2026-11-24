package cn.iocoder.yudao.module.collector.controller.admin.history.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 通道历史数据 CSV 导出 Request VO")
@Data
public class CollectorHistoryExportReqVO {

    @Schema(description = "通道编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "通道编号不能为空")
    private Long channelId;

    @Schema(description = "开始时间（epoch 毫秒，含），必须早于结束时间",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1767196800000")
    @NotNull(message = "开始时间不能为空")
    private Long from;

    @Schema(description = "结束时间（epoch 毫秒，不含），与开始时间跨度不得超过 365 天",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1767283200000")
    @NotNull(message = "结束时间不能为空")
    private Long to;

    @Schema(description = "数据粒度：raw 原始采样、second 秒聚合、minute 分钟聚合、"
            + "hour 小时聚合、day 天聚合", requiredMode = Schema.RequiredMode.REQUIRED, example = "minute")
    @NotBlank(message = "数据粒度不能为空")
    private String granularity;

}
