package cn.iocoder.yudao.module.collector.enums;

/**
 * 趋势数据源层级。
 *
 * <p>动态表名、时间列与桶宽阶梯只从这里进入 SQL，禁止直接使用接口入参。
 * 每层的桶宽阶梯用于在点数上限内自动放大桶宽，仍保持在同一数据源层。</p>
 */
public enum CollectorTrendSource {

    RAW("raw", null, "collect_time", 1, false,
            new int[]{1, 2, 5, 10, 15, 30, 60}),
    SECOND("second", "collector_aggregate_second", "bucket_time", 1, false,
            new int[]{1, 2, 5, 10, 15, 30}),
    MINUTE("minute", "collector_aggregate_minute", "bucket_time", 60, false,
            new int[]{60, 120, 180, 300, 600, 900, 1800, 3600, 7200, 10800, 21600, 43200, 86400}),
    HOUR("hour", "collector_aggregate_hour", "bucket_time", 3600, false,
            new int[]{3600, 7200, 10800, 21600, 43200, 86400, 172800}),
    DAY("day", "collector_aggregate_day", "bucket_date", 86400, true,
            new int[]{86400});

    /** 单条序列允许返回的点数上限，超出时在当前层阶梯内放大桶宽。 */
    public static final int MAX_POINTS_PER_SERIES = 1000;

    private final String code;
    private final String table;
    private final String timeColumn;
    private final int baseBucketSeconds;
    private final boolean dateBucket;
    private final int[] bucketLadder;

    CollectorTrendSource(String code, String table, String timeColumn,
                         int baseBucketSeconds, boolean dateBucket, int[] bucketLadder) {
        this.code = code;
        this.table = table;
        this.timeColumn = timeColumn;
        this.baseBucketSeconds = baseBucketSeconds;
        this.dateBucket = dateBucket;
        this.bucketLadder = bucketLadder;
    }

    public String getCode() {
        return code;
    }

    /** 仅对聚合层有值；raw 层不写表。 */
    public String getTable() {
        return table;
    }

    public String getTimeColumn() {
        return timeColumn;
    }

    public int getBaseBucketSeconds() {
        return baseBucketSeconds;
    }

    /** day 聚合表使用 bucket_date，SQL 需按日期比较。 */
    public boolean isDateBucket() {
        return dateBucket;
    }

    /**
     * 计算实际返回的桶宽：先保证至少为层的基础桶宽，再在层内阶梯中选不小于
     * ceil(范围秒数 / 1000) 的最小值，从而把点数控制在 {@link #MAX_POINTS_PER_SERIES} 以内。
     */
    public int bucketSeconds(int baseSeconds, long rangeSeconds) {
        int base = Math.max(baseSeconds, getBaseBucketSeconds());
        long target = (rangeSeconds + MAX_POINTS_PER_SERIES - 1) / MAX_POINTS_PER_SERIES;
        if (target <= base) {
            return base;
        }
        for (int candidate : bucketLadder) {
            if (candidate >= target) {
                return Math.max(base, candidate);
            }
        }
        // 范围上限已保证不会走到这里；保留兜底以免动态 SQL 得到 0。
        return Math.max(base, bucketLadder[bucketLadder.length - 1]);
    }

}
