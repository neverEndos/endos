package cn.iocoder.yudao.module.collector.enums;

import lombok.Getter;

/**
 * 采集数据保留清理的固定数据类注册表。
 *
 * <p>数据类、表名、时间列与清理方式全部写死在代码里：{@code collector_retention_policy.data_class}
 * 只允许命中这里登记的 8 类，未知 data_class 或 action 与期望不符一律判为策略异常，
 * 不允许「按前缀猜表名」后再执行 DELETE/DROP。</p>
 */
@Getter
public enum CollectorRetentionDataClass {

    SAMPLE_RAW("sample_raw", "采集原始数据", RetentionAction.DROP_PARTITION,
            "public.collector_sample_raw", "collect_time", RetentionCondition.DROP_PARTITION, false),
    AGGREGATE_SECOND("aggregate_second", "秒聚合数据", RetentionAction.DELETE_ROWS,
            "public.collector_aggregate_second", "bucket_time", RetentionCondition.AGGREGATE_TUPLE, false),
    AGGREGATE_MINUTE("aggregate_minute", "分钟聚合数据", RetentionAction.DELETE_ROWS,
            "public.collector_aggregate_minute", "bucket_time", RetentionCondition.AGGREGATE_TUPLE, false),
    AGGREGATE_HOUR("aggregate_hour", "小时聚合数据", RetentionAction.DELETE_ROWS,
            "public.collector_aggregate_hour", "bucket_time", RetentionCondition.AGGREGATE_TUPLE, false),
    AGGREGATE_DAY("aggregate_day", "天聚合数据", RetentionAction.DELETE_ROWS,
            "public.collector_aggregate_day", "bucket_date", RetentionCondition.AGGREGATE_TUPLE, true),
    ALARM("alarm", "报警事件", RetentionAction.DELETE_ROWS,
            "public.collector_alarm", "cleared_at", RetentionCondition.ALARM_CLEARED_AT, false),
    HEARTBEAT("heartbeat", "采集器心跳", RetentionAction.DELETE_ROWS,
            "public.collector_heartbeat", "heartbeat_time", RetentionCondition.HEARTBEAT_TIME, false),
    COMMAND("command", "采集命令记录", RetentionAction.DELETE_ROWS,
            "public.collector_command", "finished_at", RetentionCondition.COMMAND_TERMINAL, false);

    /** 策略表中的 data_class 取值。 */
    private final String code;
    /** 中文名，用于日志、报告与接口展示。 */
    private final String displayName;
    /** 该数据类唯一允许的清理动作。 */
    private final RetentionAction expectedAction;
    /** 物理表全名（含 schema）。 */
    private final String table;
    /** 清理判定的时间列。 */
    private final String timeColumn;
    /** 清理条件类型，决定生成哪种 SQL。 */
    private final RetentionCondition condition;
    /** 时间列是否为 date 类型（仅天聚合）。 */
    private final boolean dateColumn;

    CollectorRetentionDataClass(String code, String displayName, RetentionAction expectedAction,
                                String table, String timeColumn, RetentionCondition condition,
                                boolean dateColumn) {
        this.code = code;
        this.displayName = displayName;
        this.expectedAction = expectedAction;
        this.table = table;
        this.timeColumn = timeColumn;
        this.condition = condition;
        this.dateColumn = dateColumn;
    }

    /** 按 data_class 查注册项；未知返回 null（由调用方显式判失败）。 */
    public static CollectorRetentionDataClass of(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        for (CollectorRetentionDataClass dataClass : values()) {
            if (dataClass.code.equals(trimmed)) {
                return dataClass;
            }
        }
        return null;
    }

    /** 清理动作，取值与 {@code collector_retention_policy.action} 一致。 */
    @Getter
    public enum RetentionAction {

        DROP_PARTITION("drop_partition", "删除过期日分区"),
        DELETE_ROWS("delete_rows", "分批删除过期行");

        private final String code;
        private final String displayName;

        RetentionAction(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        /** 按 action 字符串匹配；未知返回 null。 */
        public static RetentionAction of(String code) {
            if (code == null) {
                return null;
            }
            String trimmed = code.trim();
            for (RetentionAction action : values()) {
                if (action.code.equals(trimmed)) {
                    return action;
                }
            }
            return null;
        }
    }

    /** 清理条件类型。 */
    public enum RetentionCondition {

        /** catalog 驱动的日分区 DROP，不做逐行删除。 */
        DROP_PARTITION,
        /** 按 (channel_id, 时间列) 元组分批 DELETE。 */
        AGGREGATE_TUPLE,
        /** 仅删除已恢复（cleared_at 非空）且早于 cutoff 的报警。 */
        ALARM_CLEARED_AT,
        /** 按 heartbeat_time 分批 DELETE。 */
        HEARTBEAT_TIME,
        /** 仅删除终态命令，时间取 COALESCE(finished_at, requested_at)。 */
        COMMAND_TERMINAL
    }

}
