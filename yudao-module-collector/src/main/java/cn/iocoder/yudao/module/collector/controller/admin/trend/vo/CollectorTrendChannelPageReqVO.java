package cn.iocoder.yudao.module.collector.controller.admin.trend.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 趋势选择器通道分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CollectorTrendChannelPageReqVO extends PageParam {

    @Schema(description = "设备编号；趋势页面必选，仅返回该设备下的通道",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "设备编号不能为空")
    private Long deviceId;

    @Schema(description = "通道名称关键字，模糊匹配", example = "油温")
    private String keyword;

}
