package cn.iocoder.yudao.module.collector.controller.admin.trend;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendSeriesReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendSeriesRespVO;
import cn.iocoder.yudao.module.collector.service.trend.CollectorTrendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 采集趋势")
@RestController
@RequestMapping("/collector/trend")
public class CollectorTrendController {

    @Resource
    private CollectorTrendService trendService;

    @GetMapping("/series")
    @Operation(summary = "获得采集历史趋势序列",
            description = "按数据画像与时间范围自动选源（raw/秒/分钟/小时/天），超过点数上限时在层内放大桶宽")
    @PreAuthorize("@ss.hasPermission('collector:trend:query')")
    public CommonResult<CollectorTrendSeriesRespVO> getSeries(
            @Valid CollectorTrendSeriesReqVO reqVO) {
        return success(trendService.getSeries(reqVO));
    }

}
