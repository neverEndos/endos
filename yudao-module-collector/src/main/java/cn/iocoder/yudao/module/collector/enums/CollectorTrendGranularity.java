package cn.iocoder.yudao.module.collector.enums;

import java.time.Duration;
import java.util.Locale;

/**
 * 趋势查询粒度。
 *
 * <p>auto 由通道数据画像与时间范围共同决定数据源；显式粒度只允许在该范围可承受的层上使用，
 * 超出范围返回明确错误并提示改用 auto。各粒度上限是本接口的硬约束，前端按同一规则禁用选项。</p>
 */
public enum CollectorTrendGranularity {

    AUTO("auto", Duration.ofDays(365), "自动"),
    RAW("raw", Duration.ofMinutes(10), "原始"),
    ONE_SECOND("1s", Duration.ofHours(2), "1 秒"),
    ONE_MINUTE("1m", Duration.ofHours(48), "1 分钟"),
    FIVE_MINUTES("5m", Duration.ofDays(90), "5 分钟"),
    ONE_HOUR("1h", Duration.ofDays(90), "1 小时"),
    ONE_DAY("1d", Duration.ofDays(365), "1 天");

    /** 无论何种粒度，单次趋势查询的总时间范围上限。 */
    public static final Duration MAX_TOTAL_RANGE = Duration.ofDays(365);

    private final String code;
    private final Duration maxRange;
    private final String label;

    CollectorTrendGranularity(String code, Duration maxRange, String label) {
        this.code = code;
        this.maxRange = maxRange;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public Duration getMaxRange() {
        return maxRange;
    }

    public String getLabel() {
        return label;
    }

    public boolean isExplicit() {
        return this != AUTO;
    }

    /**
     * 校验显式粒度是否承载该时间范围。入参为毫秒：范围上限按毫秒核算，
     * 2 小时 + 999ms 这类输入不会被向下取整后误判为可用。
     */
    public boolean supportsRangeMillis(long rangeMillis) {
        return rangeMillis <= maxRange.toMillis();
    }

    /** 解析 Web 入参；非法值抛出 IllegalArgumentException，由 Service 转为错误码。 */
    public static CollectorTrendGranularity fromCode(String code) {
        if (code == null || code.isBlank()) {
            return AUTO;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (CollectorTrendGranularity value : values()) {
            if (value.code.equals(normalized)) {
                return value;
            }
        }
        throw new IllegalArgumentException(code);
    }

}
