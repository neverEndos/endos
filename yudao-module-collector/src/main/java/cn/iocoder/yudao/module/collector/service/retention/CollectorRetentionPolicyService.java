package cn.iocoder.yudao.module.collector.service.retention;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集数据保留策略的查询与维护入口。
 *
 * <p>只允许修改 {@code retention_days / partition_lead_days / enabled / description}；
 * {@code data_class / action} 是代码内固定语义，任何修改都会被拒绝。保存策略不会触发
 * 任何 DELETE / DROP，清理动作只由 {@link CollectorRetentionCleanupService} 执行。</p>
 */
public interface CollectorRetentionPolicyService {

    /**
     * 读取 8 个固定数据类的有效策略，按 raw、second、minute、hour、day、alarm、heartbeat、command 顺序返回。
     */
    List<RetentionPolicySnapshot> getPolicyList();

    /**
     * 更新保留策略。字段为 null 表示保持不变。
     *
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 策略不存在或字段非法
     */
    void updatePolicy(RetentionPolicyUpdateCommand command);

    /** 清理预检（只读），委托清理服务实现，不重复写清理 SQL。 */
    CollectorRetentionCleanupService.RetentionPreview getPreview();

    /**
     * 策略快照。
     *
     * @param partitionLeadDays 未来分区预建天数（仅 sample_raw 有意义）
     */
    record RetentionPolicySnapshot(Long id, String dataClass, String displayName, int retentionDays,
                                   int partitionLeadDays, String action, boolean enabled, String description,
                                   LocalDateTime lastRunAt, String lastRunResult) {
    }

    /**
     * 策略更新命令；只包含允许修改的字段，结构上排除 data_class / action。
     */
    record RetentionPolicyUpdateCommand(Long id, Integer retentionDays, Integer partitionLeadDays,
                                        Integer enabled, String description) {
    }

}
