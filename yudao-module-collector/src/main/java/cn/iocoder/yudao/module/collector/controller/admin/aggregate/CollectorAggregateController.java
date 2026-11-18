package cn.iocoder.yudao.module.collector.controller.admin.aggregate;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
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

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.AGGREGATE_FORCE_CLEAN_FORBIDDEN;

@Tag(name = "管理后台 - 采集聚合")
@RestController
@RequestMapping("/collector/aggregate")
public class CollectorAggregateController {

    private static final String FORCE_CLEAN_PERMISSION = "collector:aggregate:force-clean";

    @Resource
    private CollectorAggregateService aggregateService;

    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @PostMapping("/backfill")
    @Operation(summary = "同步回填聚合数据",
            description = "仅删除本轮有源覆盖的目标桶，无源桶保留旧行；second 限 3 小时/5 通道"
                    + "（按约 1.5 秒/通道·小时估算约 23 秒），minute 限 2 天/20 通道（约 16 秒），"
                    + "hour、day 限 30 天/50 通道（约 2.5 分钟）；板端更慢，请分批或分层回填，"
                    + "同步请求可能超时；fromRaw 起点早于 raw 保留期会被拒绝；"
                    + "forceClean=true 需要独立权限 collector:aggregate:force-clean，且会按区间整段清空目标")
    @PreAuthorize("@ss.hasPermission('collector:aggregate:rebuild')")
    public CommonResult<CollectorAggregateBackfillRespVO> backfill(
            @Valid @RequestBody CollectorAggregateBackfillReqVO reqVO) {
        if (Boolean.TRUE.equals(reqVO.getForceClean())
                && !securityFrameworkService.hasPermission(FORCE_CLEAN_PERMISSION)) {
            throw exception(AGGREGATE_FORCE_CLEAN_FORBIDDEN);
        }
        return success(CollectorAggregateBackfillRespVO.of(aggregateService.backfill(
                CollectorAggregateType.fromCode(reqVO.getAggType()), reqVO.getFrom(), reqVO.getTo(),
                reqVO.getChannelIds(), Boolean.TRUE.equals(reqVO.getFromRaw()),
                Boolean.TRUE.equals(reqVO.getCascade()), Boolean.TRUE.equals(reqVO.getForceClean()))));
    }

}
