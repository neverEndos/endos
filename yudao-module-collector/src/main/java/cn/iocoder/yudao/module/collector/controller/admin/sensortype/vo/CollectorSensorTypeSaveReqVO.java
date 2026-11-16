package cn.iocoder.yudao.module.collector.controller.admin.sensortype.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 传感器类型创建/修改 Request VO")
@Data
public class CollectorSensorTypeSaveReqVO {

    @Schema(description = "编号，修改时必填", example = "1")
    private Long id;

    @Schema(description = "类型/型号编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "cs1000")
    @NotBlank(message = "类型编码不能为空")
    @Size(max = 64, message = "类型编码长度不能超过 64 个字符")
    private String typeCode;

    @Schema(description = "类型/型号名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "HYDAC CS1000 污染度传感器")
    @NotBlank(message = "类型名称不能为空")
    @Size(max = 100, message = "类型名称长度不能超过 100 个字符")
    private String typeName;

    @Schema(description = "协议编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "hsi_rs485")
    @NotBlank(message = "协议编码不能为空")
    @Size(max = 64, message = "协议编码长度不能超过 64 个字符")
    private String protocolCode;

    @Schema(description = "制造商", example = "HYDAC")
    @Size(max = 100, message = "制造商长度不能超过 100 个字符")
    private String manufacturer;

    @Schema(description = "型号说明、固件差异与字段映射备注")
    @Size(max = 255, message = "描述长度不能超过 255 个字符")
    private String description;

    @Schema(description = "是否启用：0 否、1 是；默认 1", example = "1")
    @Min(value = 0, message = "启用开关只能是 0 或 1")
    @Max(value = 1, message = "启用开关只能是 0 或 1")
    private Integer enabled;

    @Schema(description = "排序值，默认 0", example = "1")
    private Integer sort;

}
