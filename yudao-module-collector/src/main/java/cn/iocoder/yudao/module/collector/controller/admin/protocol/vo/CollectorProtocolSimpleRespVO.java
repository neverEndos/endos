package cn.iocoder.yudao.module.collector.controller.admin.protocol.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 采集协议精简 Response VO")
@Data
public class CollectorProtocolSimpleRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "协议编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "hsi_rs485")
    private String protocolCode;

    @Schema(description = "协议名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "HYDAC HSI (RS485)")
    private String protocolName;

    @Schema(description = "传输大类", example = "serial")
    private String transportType;

    @Schema(description = "是否允许新增传感器引用：0 否、1 是", example = "1")
    private Integer enabled;

    @Schema(description = "排序值", example = "30")
    private Integer sort;

}
