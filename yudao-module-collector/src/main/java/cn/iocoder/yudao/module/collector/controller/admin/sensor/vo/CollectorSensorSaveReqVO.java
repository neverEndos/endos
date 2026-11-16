package cn.iocoder.yudao.module.collector.controller.admin.sensor.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Schema(description = "管理后台 - 传感器创建/修改 Request VO")
@Data
public class CollectorSensorSaveReqVO {

    @Schema(description = "编号，修改时必填", example = "1")
    private Long id;

    @Schema(description = "所属设备编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "设备编号不能为空")
    private Long deviceId;

    @Schema(description = "传感器类型编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "传感器类型编号不能为空")
    private Long sensorTypeId;

    @Schema(description = "传感器编码（设备内唯一）", requiredMode = Schema.RequiredMode.REQUIRED, example = "cs1000-01")
    @NotBlank(message = "传感器编码不能为空")
    @Size(max = 64, message = "传感器编码长度不能超过 64 个字符")
    private String sensorCode;

    @Schema(description = "传感器名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "CS1000 污染度传感器")
    @NotBlank(message = "传感器名称不能为空")
    @Size(max = 100, message = "传感器名称长度不能超过 100 个字符")
    private String sensorName;

    @Schema(description = "协议连接参数 JSON 对象")
    private Map<String, Object> connectionConfig;

    @Schema(description = "是否启用：0 否、1 是；默认 1", example = "1")
    @Min(value = 0, message = "启用开关只能是 0 或 1")
    @Max(value = 1, message = "启用开关只能是 0 或 1")
    private Integer enabled;

    @Schema(description = "描述")
    @Size(max = 255, message = "描述长度不能超过 255 个字符")
    private String description;

    @Schema(description = "排序值，默认 0", example = "1")
    private Integer sort;

    @Schema(description = "创建时是否按类型启用模板自动生成通道；默认 false，修改时忽略", example = "true")
    private Boolean autoGenerateChannels;

}
