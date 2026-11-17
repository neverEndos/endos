package cn.iocoder.yudao.module.collector.controller.admin.aggregate;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.collector.controller.admin.aggregate.vo.CollectorAggregateBackfillReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.aggregate.vo.CollectorAggregateBackfillRespVO;
import cn.iocoder.yudao.module.collector.enums.CollectorAggregateType;
import cn.iocoder.yudao.module.collector.service.aggregate.CollectorAggregateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 采集聚合")
@RestController
@RequestMapping("/collector/aggregate")
public class CollectorAggregateController {

    @Resource
    private CollectorAggregateService aggregateService;

    @PostMapping("/backfill")
    @Operation(summary = "同步回填聚合数据",
            description = "先清空目标区间再重建；second 限 1 天/10 通道，minute 限 7 天/50 通道，"
                    + "hour、day 限 90 天/50 通道；大范围请分批或分层调用，同步请求可能超时")
    @PreAuthorize("@ss.hasPermission('collector:aggregate:rebuild')")
    public CommonResult<CollectorAggregateBackfillRespVO> backfill(
            @Valid @RequestBody CollectorAggregateBackfillReqVO reqVO) {
        return success(CollectorAggregateBackfillRespVO.of(aggregateService.backfill(
                CollectorAggregateType.fromCode(reqVO.getAggType()), reqVO.getFrom(), reqVO.getTo(),
                reqVO.getChannelIds(), Boolean.TRUE.equals(reqVO.getFromRaw()),
                Boolean.TRUE.equals(reqVO.getCascade()))));
    }

}
