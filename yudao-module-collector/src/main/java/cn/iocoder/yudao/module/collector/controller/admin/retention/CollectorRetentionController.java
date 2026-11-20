package cn.iocoder.yudao.module.collector.controller.admin.retention;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.collector.controller.admin.retention.vo.CollectorRetentionCleanupRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.retention.vo.CollectorRetentionPreviewRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.retention.vo.CollectorRetentionRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.retention.vo.CollectorRetentionUpdateReqVO;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionPolicyService;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionPolicyService.RetentionPolicyUpdateCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 采集数据保留策略管理接口。
 *
 * <p>Controller 只做参数绑定与记录类型到 VO 的转换：字段范围、sample_raw 专用校验、
 * 数据类是否支持等全部由 Service 判断，避免两处各写一套规则而漂移。</p>
 */
@Tag(name = "管理后台 - 采集数据保留")
@RestController
@RequestMapping("/collector/retention")
public class CollectorRetentionController {

    @Resource
    private CollectorRetentionPolicyService retentionPolicyService;

    @Resource
    private CollectorRetentionCleanupService retentionCleanupService;

    @GetMapping("/list")
    @Operation(summary = "获得保留策略列表",
            description = "固定返回 8 个数据类（raw、second、minute、hour、day、alarm、heartbeat、command），不分页")
    @PreAuthorize("@ss.hasPermission('collector:retention:query')")
    public CommonResult<List<CollectorRetentionRespVO>> getPolicyList() {
        return success(retentionPolicyService.getPolicyList().stream()
                .map(CollectorRetentionRespVO::of).toList());
    }

    @PutMapping("/update")
    @Operation(summary = "更新保留策略",
            description = "只允许修改 retentionDays / partitionLeadDays / enabled / description；"
                    + "dataClass 与 action 由代码固定，保存不会触发任何 DELETE / DROP")
    @PreAuthorize("@ss.hasPermission('collector:retention:update')")
    public CommonResult<Boolean> updatePolicy(@Valid @RequestBody CollectorRetentionUpdateReqVO reqVO) {
        retentionPolicyService.updatePolicy(new RetentionPolicyUpdateCommand(reqVO.getId(),
                reqVO.getRetentionDays(), reqVO.getPartitionLeadDays(), reqVO.getEnabled(),
                reqVO.getDescription()));
        return success(true);
    }

    @GetMapping("/preview")
    @Operation(summary = "获得保留清理预检",
            description = "只读统计：各类候选删除行数与候选 DROP 分区；raw 不统计行数，只给候选分区。"
                    + "统计失败或超时直接返回错误，不会伪装成 0")
    @PreAuthorize("@ss.hasPermission('collector:retention:query')")
    public CommonResult<CollectorRetentionPreviewRespVO> getPreview() {
        return success(CollectorRetentionPreviewRespVO.of(retentionPolicyService.getPreview()));
    }

    @PostMapping("/cleanup")
    @Operation(summary = "立即执行保留清理",
            description = "同步执行一次全量清理，可能耗时较长；已有清理任务在执行时整体跳过并返回 "
                    + "lockNotAcquired=true。raw 仅 DROP 过期的标准日分区，DEFAULT 分区永不删除")
    @PreAuthorize("@ss.hasPermission('collector:retention:cleanup')")
    public CommonResult<CollectorRetentionCleanupRespVO> cleanup() {
        return success(CollectorRetentionCleanupRespVO.of(retentionCleanupService.cleanupAll()));
    }

}
