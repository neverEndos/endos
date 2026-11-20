package cn.iocoder.yudao.module.collector.service.retention;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code collector_retention_policy} 的读取与结果回写 SQL，供策略服务与清理服务共用。
 *
 * <p>只暴露清理需要的列，避免两个服务各写一份列名映射导致漂移。所有方法都不修改
 * {@code data_class / action / retention_days / partition_lead_days / enabled}。</p>
 */
final class CollectorRetentionPolicyRows {

    private static final String SELECT_COLUMNS = """
            SELECT id, data_class, retention_days, partition_lead_days, action, enabled, description,
                   last_run_at, last_run_result
            FROM public.collector_retention_policy
            WHERE deleted = 0
            """;

    private static final RowMapper<PolicyRow> ROW_MAPPER = (resultSet, rowNum) -> new PolicyRow(
            resultSet.getLong("id"),
            resultSet.getString("data_class"),
            resultSet.getInt("retention_days"),
            resultSet.getInt("partition_lead_days"),
            resultSet.getString("action"),
            resultSet.getInt("enabled"),
            resultSet.getString("description"),
            resultSet.getObject("last_run_at", LocalDateTime.class),
            resultSet.getString("last_run_result"));

    private CollectorRetentionPolicyRows() {
    }

    /** 读取全部未删除策略行；顺序由 data_class 决定（调用方按注册表排序）。 */
    static List<PolicyRow> loadAll(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.query(SELECT_COLUMNS, ROW_MAPPER);
    }

    /** 按主键读取未删除策略行；不存在返回 null。 */
    static PolicyRow loadById(JdbcTemplate jdbcTemplate, long id) {
        List<PolicyRow> rows = jdbcTemplate.query(SELECT_COLUMNS + " AND id = ?", ROW_MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 只回写最近执行时间与结果摘要。 */
    static int updateLastRun(JdbcTemplate jdbcTemplate, long id, LocalDateTime lastRunAt, String lastRunResult) {
        return jdbcTemplate.update("""
                UPDATE public.collector_retention_policy
                SET last_run_at = ?, last_run_result = ?
                WHERE id = ?
                """, lastRunAt, lastRunResult, id);
    }

    /** 策略行快照（原样映射，不做任何业务解释）。 */
    record PolicyRow(Long id, String dataClass, int retentionDays, int partitionLeadDays, String action,
                     int enabled, String description, LocalDateTime lastRunAt, String lastRunResult) {
    }

}
