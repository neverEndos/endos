package cn.iocoder.yudao.module.collector.service.retention;

import cn.iocoder.yudao.module.collector.enums.CollectorRetentionDataClass;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionCleanupService.RetentionPreview;
import cn.iocoder.yudao.module.collector.service.retention.CollectorRetentionPolicyRows.PolicyRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.RETENTION_POLICY_FIELD_INVALID;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.RETENTION_POLICY_LEAD_DAYS_FORBIDDEN;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.RETENTION_POLICY_NOT_EXISTS;

/**
 * 采集数据保留策略服务实现。
 */
@Slf4j
@Service
public class CollectorRetentionPolicyServiceImpl implements CollectorRetentionPolicyService {

    private static final int MIN_RETENTION_DAYS = 0;
    private static final int MAX_RETENTION_DAYS = 3650;
    private static final int MIN_LEAD_DAYS = 0;
    private static final int MAX_LEAD_DAYS = 30;
    private static final int MAX_DESCRIPTION_LENGTH = 255;

    private final JdbcTemplate jdbcTemplate;
    private final CollectorRetentionCleanupService cleanupService;

    public CollectorRetentionPolicyServiceImpl(JdbcTemplate jdbcTemplate,
                                               CollectorRetentionCleanupService cleanupService) {
        this.jdbcTemplate = jdbcTemplate;
        this.cleanupService = cleanupService;
    }

    @Override
    public List<RetentionPolicySnapshot> getPolicyList() {
        Map<String, List<PolicyRow>> rowsByClass = new LinkedHashMap<>();
        for (PolicyRow row : CollectorRetentionPolicyRows.loadAll(jdbcTemplate)) {
            rowsByClass.computeIfAbsent(row.dataClass(), key -> new ArrayList<>()).add(row);
        }
        List<RetentionPolicySnapshot> snapshots = new ArrayList<>();
        for (CollectorRetentionDataClass dataClass : CollectorRetentionDataClass.values()) {
            List<PolicyRow> rows = rowsByClass.get(dataClass.getCode());
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            // 唯一索引保证正常只有一条；异常多行时取第一条展示，清理/预检会把重复明确标为失败。
            PolicyRow row = rows.get(0);
            snapshots.add(new RetentionPolicySnapshot(row.id(), dataClass.getCode(), dataClass.getDisplayName(),
                    row.retentionDays(), row.partitionLeadDays(), row.action(), row.enabled() == 1,
                    row.description(), row.lastRunAt(), row.lastRunResult()));
        }
        return snapshots;
    }

    @Override
    public void updatePolicy(RetentionPolicyUpdateCommand command) {
        if (command == null || command.id() == null) {
            throw exception(RETENTION_POLICY_FIELD_INVALID, "策略 id 不能为空");
        }
        PolicyRow policy = CollectorRetentionPolicyRows.loadById(jdbcTemplate, command.id());
        if (policy == null) {
            throw exception(RETENTION_POLICY_NOT_EXISTS);
        }
        CollectorRetentionDataClass dataClass = CollectorRetentionDataClass.of(policy.dataClass());
        if (dataClass == null) {
            throw exception(RETENTION_POLICY_FIELD_INVALID, "data_class(" + policy.dataClass() + ")不受支持");
        }

        int retentionDays = policy.retentionDays();
        if (command.retentionDays() != null) {
            retentionDays = command.retentionDays();
            if (retentionDays < MIN_RETENTION_DAYS || retentionDays > MAX_RETENTION_DAYS) {
                throw exception(RETENTION_POLICY_FIELD_INVALID,
                        "retention_days 必须在 " + MIN_RETENTION_DAYS + "~" + MAX_RETENTION_DAYS
                                + " 之间，当前为 " + retentionDays);
            }
        }
        int enabled = policy.enabled();
        if (command.enabled() != null) {
            enabled = command.enabled();
            if (enabled != 0 && enabled != 1) {
                throw exception(RETENTION_POLICY_FIELD_INVALID, "enabled 只能为 0 或 1，当前为 " + enabled);
            }
        }
        String description = policy.description();
        if (command.description() != null) {
            description = command.description();
            if (description.length() > MAX_DESCRIPTION_LENGTH) {
                throw exception(RETENTION_POLICY_FIELD_INVALID,
                        "description 长度不能超过 " + MAX_DESCRIPTION_LENGTH + " 个字符，当前为 "
                                + description.length());
            }
        }
        int partitionLeadDays = policy.partitionLeadDays();
        if (command.partitionLeadDays() != null) {
            // 分区预建只作用于 raw：其他数据类传入该字段一律拒绝，不做静默忽略。
            if (dataClass != CollectorRetentionDataClass.SAMPLE_RAW) {
                throw exception(RETENTION_POLICY_LEAD_DAYS_FORBIDDEN, dataClass.getCode());
            }
            partitionLeadDays = command.partitionLeadDays();
            if (partitionLeadDays < MIN_LEAD_DAYS || partitionLeadDays > MAX_LEAD_DAYS) {
                throw exception(RETENTION_POLICY_FIELD_INVALID,
                        "partition_lead_days 必须在 " + MIN_LEAD_DAYS + "~" + MAX_LEAD_DAYS
                                + " 之间，当前为 " + partitionLeadDays);
            }
        }

        // 只更新这四列；data_class / action / last_run_* 均保持不变，也不触发任何数据清理。
        jdbcTemplate.update("""
                UPDATE public.collector_retention_policy
                SET retention_days = ?, partition_lead_days = ?, enabled = ?, description = ?
                WHERE id = ?
                """, retentionDays, partitionLeadDays, enabled, description, policy.id());
        log.info("[updatePolicy][id({}) data_class({}) retentionDays={} leadDays={} enabled={}]",
                policy.id(), dataClass.getCode(), retentionDays, partitionLeadDays, enabled);
    }

    @Override
    public RetentionPreview getPreview() {
        return cleanupService.preview();
    }

}
