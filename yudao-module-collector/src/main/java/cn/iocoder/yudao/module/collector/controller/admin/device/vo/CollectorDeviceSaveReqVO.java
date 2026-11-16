package cn.iocoder.yudao.module.collector.controller.admin.device.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 采集设备创建/修改 Request VO")
@Data
public class CollectorDeviceSaveReqVO {

    @Schema(description = "编号，修改时必填", example = "1")
    private Long id;

    @Schema(description = "设备编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "rk3568-hsi-01")
    @NotBlank(message = "设备编码不能为空")
    @Size(max = 64, message = "设备编码长度不能超过 64 个字符")
    private String deviceCode;

    @Schema(description = "设备名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "RK3568 采集主机")
    @NotBlank(message = "设备名称不能为空")
    @Size(max = 100, message = "设备名称长度不能超过 100 个字符")
    private String deviceName;

    @Schema(description = "是否启用：0 否、1 是；默认 1", example = "1")
    private Integer enabled;

    @Schema(description = "设备默认轮询周期（毫秒），默认 5000", example = "5000")
    @Min(value = 100, message = "轮询周期不能小于 100 毫秒")
    private Integer pollIntervalMs;

    @Schema(description = "单次请求超时（毫秒），默认 1000", example = "1000")
    @Min(value = 10, message = "请求超时不能小于 10 毫秒")
    private Integer timeoutMs;

    @Schema(description = "失败重试次数，默认 2", example = "2")
    @Min(value = 0, message = "重试次数不能小于 0")
    @Max(value = 10, message = "重试次数不能大于 10")
    private Integer retries;

    @Schema(description = "断线重连间隔（毫秒），默认 5000", example = "5000")
    @Min(value = 100, message = "重连间隔不能小于 100 毫秒")
    private Integer reconnectIntervalMs;

    @Schema(description = "描述")
    @Size(max = 255, message = "描述长度不能超过 255 个字符")
    private String description;

    @Schema(description = "排序值，默认 0", example = "0")
    private Integer sort;

}
