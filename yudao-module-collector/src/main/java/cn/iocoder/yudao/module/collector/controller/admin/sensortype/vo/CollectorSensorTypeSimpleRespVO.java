package cn.iocoder.yudao.module.collector.controller.admin.sensortype.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 传感器类型精简 Response VO")
@Data
public class CollectorSensorTypeSimpleRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "类型/型号编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "cs1000")
    private String typeCode;

    @Schema(description = "类型/型号名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "HYDAC CS1000 污染度传感器")
    private String typeName;

    @Schema(description = "协议编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "hsi_rs485")
    private String protocolCode;

    @Schema(description = "是否启用：0 否、1 是", example = "1")
    private Integer enabled;

}
