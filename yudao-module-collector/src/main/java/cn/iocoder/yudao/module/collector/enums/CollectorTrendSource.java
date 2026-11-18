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
     * 计算实际返回的桶宽：在层内阶梯中，选择满足“最坏情况下桶数不超过
     * {@link #MAX_POINTS_PER_SERIES}”的最小桶宽。
     *
     * <p>SQL 的 {@code date_bin} 固定以 2000-01-01 为分桶原点，而 from/to 由调用方任意指定：
     * 当范围恰好是桶宽整数倍且 from 不在桶边界上时，首尾会各多覆盖半桶，最坏桶数为
     * {@code ceil(范围 / 桶宽) + 1}（见 {@link #worstBucketCount(long, long)}）。
     * 因此这里不能用 {@code ceil(范围 / 1000)} 直接选档，否则单序列可能返回 1001 个点。</p>
     */
    public int bucketSeconds(int baseSeconds, long rangeSeconds) {
        int base = Math.max(baseSeconds, getBaseBucketSeconds());
        for (int candidate : bucketLadder) {
            if (candidate < base) {
                continue;
            }
            if (worstBucketCount(rangeSeconds, candidate) <= MAX_POINTS_PER_SERIES) {
                return candidate;
            }
        }
        // 范围上限已保证不会走到这里；保留兜底以免动态 SQL 得到 0。
        return Math.max(base, bucketLadder[bucketLadder.length - 1]);
    }

    /**
     * 范围 [from, to) 在固定分桶原点下最多触发的桶数。
     * 对齐最坏时比 {@code ceil(range / bucket)} 恰好多 1（首尾各多覆盖半桶），
     * 用于桶宽选择与原始点抽样步长的统一核算。
     */
    public static long worstBucketCount(long rangeSeconds, long bucketSeconds) {
        if (rangeSeconds <= 0 || bucketSeconds <= 0) {
            return 0;
        }
        return Math.ceilDiv(rangeSeconds, bucketSeconds) + 1;
    }

}
