package cn.iocoder.yudao.module.collector.controller.admin.retention.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保留策略更新请求。
 *
 * <p>结构上不含 {@code dataClass / action}：这两个字段是代码内固定语义，前端即使传入也不会被接收。
 * 字段取值的业务校验（如 partitionLeadDays 只允许 sample_raw）由 Service 按实际数据类判断。</p>
 */
@Schema(description = "管理后台 - 采集数据保留策略更新 Request VO")
@Data
public class CollectorRetentionUpdateReqVO {

    @Schema(description = "策略编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "策略编号不能为空")
    private Long id;

    @Schema(description = "保留天数；0 表示永久保留", requiredMode = Schema.RequiredMode.REQUIRED, example = "30")
    @NotNull(message = "保留天数不能为空")
    @Min(value = 0, message = "保留天数不能小于 0")
    @Max(value = 3650, message = "保留天数不能大于 3650")
    private Integer retentionDays;

    @Schema(description = "未来分区预建天数，仅 sample_raw 允许传值", example = "7")
    @Min(value = 0, message = "分区预建天数不能小于 0")
    @Max(value = 30, message = "分区预建天数不能大于 30")
    private Integer partitionLeadDays;

    @Schema(description = "是否启用清理：0 停用、1 启用", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "启用状态不能为空")
    @Min(value = 0, message = "启用状态只能为 0 或 1")
    @Max(value = 1, message = "启用状态只能为 0 或 1")
    private Integer enabled;

    @Schema(description = "策略说明，最长 255 字")
    @Size(max = 255, message = "策略说明不能超过 255 个字符")
    private String description;

}
