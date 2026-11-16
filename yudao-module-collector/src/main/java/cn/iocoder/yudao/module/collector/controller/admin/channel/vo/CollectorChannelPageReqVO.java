package cn.iocoder.yudao.module.collector.controller.admin.channel.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 通道分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CollectorChannelPageReqVO extends PageParam {

    @Schema(description = "设备编号", example = "1")
    private Long deviceId;

    @Schema(description = "传感器编号", example = "1")
    private Long sensorId;

    @Schema(description = "通道编码，模糊匹配", example = "iso_4")
    private String channelCode;

    @Schema(description = "通道名称，模糊匹配", example = "清洁度")
    private String channelName;

    @Schema(description = "值类别：number 数值、enum 枚举", example = "number")
    private String valueKind;

    @Schema(description = "是否启用：0 否、1 是", example = "1")
    private Integer enabled;

}
