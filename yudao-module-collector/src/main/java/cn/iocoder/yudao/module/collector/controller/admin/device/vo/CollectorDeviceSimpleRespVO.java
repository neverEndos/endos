package cn.iocoder.yudao.module.collector.controller.admin.device.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 采集设备精简 Response VO")
@Data
public class CollectorDeviceSimpleRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "设备编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "rk3568-hsi-01")
    private String deviceCode;

    @Schema(description = "设备名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "RK3568 采集主机")
    private String deviceName;

    @Schema(description = "是否启用：0 否、1 是", example = "1")
    private Integer enabled;

}
