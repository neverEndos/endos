package cn.iocoder.yudao.module.collector.controller.admin.trend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 趋势选择器设备 Response VO")
@Data
public class CollectorTrendDeviceRespVO {

    @Schema(description = "设备编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "设备名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "1 号采集器")
    private String deviceName;

}
