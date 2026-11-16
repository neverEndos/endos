package cn.iocoder.yudao.module.collector.controller.admin.device.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 采集设备分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CollectorDevicePageReqVO extends PageParam {

    @Schema(description = "设备编码，模糊匹配", example = "rk3568")
    private String deviceCode;

    @Schema(description = "设备名称，模糊匹配", example = "采集主机")
    private String deviceName;

    @Schema(description = "是否启用：0 否、1 是", example = "1")
    private Integer enabled;

}
