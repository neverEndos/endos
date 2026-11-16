package cn.iocoder.yudao.module.collector.controller.admin.sensor.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 传感器分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CollectorSensorPageReqVO extends PageParam {

    @Schema(description = "设备编号", example = "1")
    private Long deviceId;

    @Schema(description = "传感器类型编号", example = "1")
    private Long sensorTypeId;

    @Schema(description = "传感器编码，模糊匹配", example = "cs1000")
    private String sensorCode;

    @Schema(description = "传感器名称，模糊匹配", example = "污染度")
    private String sensorName;

    @Schema(description = "协议编码", example = "hsi_rs485")
    private String protocolCode;

    @Schema(description = "是否启用：0 否、1 是", example = "1")
    private Integer enabled;

}
