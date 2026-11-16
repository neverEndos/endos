package cn.iocoder.yudao.module.collector.controller.admin.sensortype.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 传感器类型 Response VO")
@Data
public class CollectorSensorTypeRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "类型/型号编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "cs1000")
    private String typeCode;

    @Schema(description = "类型/型号名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "HYDAC CS1000 污染度传感器")
    private String typeName;

    @Schema(description = "协议编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "hsi_rs485")
    private String protocolCode;

    @Schema(description = "制造商", example = "HYDAC")
    private String manufacturer;

    @Schema(description = "型号说明、固件差异与字段映射备注")
    private String description;

    @Schema(description = "是否启用：0 否、1 是", example = "1")
    private Integer enabled;

    @Schema(description = "排序值", example = "1")
    private Integer sort;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
