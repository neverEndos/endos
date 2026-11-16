package cn.iocoder.yudao.module.collector.controller.admin.sensortype.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 传感器类型分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CollectorSensorTypePageReqVO extends PageParam {

    @Schema(description = "类型编码，模糊匹配", example = "cs1000")
    private String typeCode;

    @Schema(description = "类型名称，模糊匹配", example = "污染度")
    private String typeName;

    @Schema(description = "协议编码", example = "hsi_rs485")
    private String protocolCode;

    @Schema(description = "是否启用：0 否、1 是", example = "1")
    private Integer enabled;

}
