package cn.iocoder.yudao.module.collector.enums;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * 聚合粒度与单批窗口。表名和源表名只允许从这里进入动态 SQL，禁止直接使用接口入参。
 */
public enum CollectorAggregateType {

    SECOND("second", "collector_aggregate_second", "1 second", ChronoUnit.SECONDS, Duration.ofHours(1)),
    MINUTE("minute", "collector_aggregate_minute", "1 minute", ChronoUnit.MINUTES, Duration.ofDays(1)),
    HOUR("hour", "collector_aggregate_hour", "1 hour", ChronoUnit.HOURS, Duration.ofDays(7)),
    DAY("day", "collector_aggregate_day", "1 day", ChronoUnit.DAYS, Duration.ofDays(7));

    private final String code;
    private final String targetTable;
    private final String bucketInterval;
    private final ChronoUnit truncateUnit;
    private final Duration batchWindow;

    CollectorAggregateType(String code, String targetTable, String bucketInterval,
                           ChronoUnit truncateUnit, Duration batchWindow) {
        this.code = code;
        this.targetTable = targetTable;
        this.bucketInterval = bucketInterval;
        this.truncateUnit = truncateUnit;
        this.batchWindow = batchWindow;
    }

    public String getCode() {
        return code;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public String getBucketInterval() {
        return bucketInterval;
    }

    public Duration getBatchWindow() {
        return batchWindow;
    }

    /** 返回包含 from 的桶起点。 */
    public LocalDateTime floor(LocalDateTime time) {
        return time.truncatedTo(truncateUnit);
    }

    /** 返回覆盖 to 的开区间边界。 */
    public LocalDateTime ceil(LocalDateTime time) {
        LocalDateTime floor = floor(time);
        return floor.equals(time) ? floor : plus(floor);
    }

    public LocalDateTime plus(LocalDateTime bucketStart) {
        return switch (this) {
            case SECOND -> bucketStart.plusSeconds(1);
            case MINUTE -> bucketStart.plusMinutes(1);
            case HOUR -> bucketStart.plusHours(1);
            case DAY -> bucketStart.plusDays(1);
        };
    }

    public LocalDateTime plusWindow(LocalDateTime time) {
        return time.plus(batchWindow);
    }

    public static CollectorAggregateType fromCode(String code) {
        return valueOf(code.toUpperCase(Locale.ROOT));
    }

}
