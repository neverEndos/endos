package cn.iocoder.yudao.module.collector.controller.admin.sensor.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 传感器精简 Response VO")
@Data
public class CollectorSensorSimpleRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "所属设备编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long deviceId;

    @Schema(description = "传感器编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "cs1000-01")
    private String sensorCode;

    @Schema(description = "传感器名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "CS1000 污染度传感器")
    private String sensorName;

    @Schema(description = "协议编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "hsi_rs485")
    private String protocolCode;

    @Schema(description = "是否启用：0 否、1 是", example = "1")
    private Integer enabled;

}
