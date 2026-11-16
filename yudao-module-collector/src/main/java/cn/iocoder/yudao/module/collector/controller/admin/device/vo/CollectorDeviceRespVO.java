package cn.iocoder.yudao.module.collector.controller.admin.device.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 采集设备 Response VO")
@Data
public class CollectorDeviceRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "设备编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "rk3568-hsi-01")
    private String deviceCode;

    @Schema(description = "设备名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "RK3568 采集主机")
    private String deviceName;

    @Schema(description = "是否启用：0 否、1 是", example = "1")
    private Integer enabled;

    @Schema(description = "设备默认轮询周期（毫秒）", example = "5000")
    private Integer pollIntervalMs;

    @Schema(description = "单次请求超时（毫秒）", example = "1000")
    private Integer timeoutMs;

    @Schema(description = "失败重试次数", example = "2")
    private Integer retries;

    @Schema(description = "断线重连间隔（毫秒）", example = "5000")
    private Integer reconnectIntervalMs;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "排序值", example = "0")
    private Integer sort;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
