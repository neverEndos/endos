package cn.iocoder.yudao.module.collector.controller.admin.aggregate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 采集聚合手工回填 Request VO")
@Data
public class CollectorAggregateBackfillReqVO {

    @Schema(description = "聚合粒度：second/minute/hour/day", requiredMode = Schema.RequiredMode.REQUIRED, example = "minute")
    @NotBlank(message = "聚合粒度不能为空")
    @Pattern(regexp = "second|minute|hour|day", message = "聚合粒度只能是 second、minute、hour、day")
    private String aggType;

    @Schema(description = "开始时间（含，Asia/Shanghai 墙钟，ISO 8601）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "2026-11-16T00:00:00")
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime from;

    @Schema(description = "结束时间（不含，Asia/Shanghai 墙钟，ISO 8601）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "2026-11-17T00:00:00")
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime to;

    @Schema(description = "通道编号列表；缺省或空数组表示全部，最多 50 个；各聚合粒度有更小的实际上限")
    @Size(max = 50, message = "单次回填通道数不能超过 50")
    private List<Long> channelIds;

    @Schema(description = "是否绕过下层聚合直接从 raw 重算，默认 false；范围不能超过 raw 保留期", example = "false")
    private Boolean fromRaw = false;

    @Schema(description = "是否按 second→minute→hour→day 级联重建上层，默认 false", example = "false")
    private Boolean cascade = false;

    @Schema(description = "源为空时是否仍清空目标区间，默认 false；true 会删除旧聚合，属于高风险操作",
            example = "false")
    private Boolean forceClean = false;

}
