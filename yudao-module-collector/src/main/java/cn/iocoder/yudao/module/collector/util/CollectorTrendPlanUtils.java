package cn.iocoder.yudao.module.collector.util;

import cn.iocoder.yudao.module.collector.enums.CollectorTrendGranularity;
import cn.iocoder.yudao.module.collector.enums.CollectorTrendSource;

/**
 * 趋势查询的数据源选择规则（纯函数，便于独立验证边界）。
 *
 * <p>fast 通道持续写秒聚合；normal 通道没有秒聚合，因此短范围必须回退 raw。
 * auto 规则按“数据画像 + 范围”选层，显式粒度由 Service 先校验范围上限。</p>
 */
public final class CollectorTrendPlanUtils {

    public static final String DATA_PROFILE_FAST = "fast";

    private static final long TEN_MINUTES_SECONDS = 600;
    private static final long TWO_HOURS_SECONDS = 7200;
    private static final long FORTY_EIGHT_HOURS_SECONDS = 172_800;
    private static final long NINETY_DAYS_SECONDS = 7_776_000;

    private CollectorTrendPlanUtils() {
    }

    /** 数据源与基础桶宽；实际桶宽还需调用 {@link #bucketSeconds(Plan, long)}。 */
    public record Plan(CollectorTrendSource source, int baseBucketSeconds) {
    }

    public static boolean isFast(String dataProfile) {
        return dataProfile != null && DATA_PROFILE_FAST.equalsIgnoreCase(dataProfile.trim());
    }

    /**
     * auto 选源：
     * <ul>
     *   <li>normal：≤10 分钟 raw；≤48 小时 minute；≤90 天 hour；更长 day</li>
     *   <li>fast：≤10 分钟 second(1s)；≤2 小时 second(5s)；≤48 小时 minute；≤90 天 hour；更长 day</li>
     * </ul>
     */
    public static Plan resolveAuto(String dataProfile, long rangeSeconds) {
        if (isFast(dataProfile)) {
            if (rangeSeconds <= TEN_MINUTES_SECONDS) {
                return new Plan(CollectorTrendSource.SECOND, 1);
            }
            if (rangeSeconds <= TWO_HOURS_SECONDS) {
                return new Plan(CollectorTrendSource.SECOND, 5);
            }
        } else if (rangeSeconds <= TEN_MINUTES_SECONDS) {
            return new Plan(CollectorTrendSource.RAW, 1);
        }
        if (rangeSeconds <= FORTY_EIGHT_HOURS_SECONDS) {
            return new Plan(CollectorTrendSource.MINUTE, 60);
        }
        if (rangeSeconds <= NINETY_DAYS_SECONDS) {
            return new Plan(CollectorTrendSource.HOUR, 3600);
        }
        return new Plan(CollectorTrendSource.DAY, 86400);
    }

    /**
     * 显式粒度选源。1 秒粒度对 fast 通道读秒聚合，对 normal 通道回退 raw；
     * 其余粒度直接对应聚合层。
     */
    public static Plan resolveExplicit(CollectorTrendGranularity granularity, String dataProfile) {
        return switch (granularity) {
            case RAW -> new Plan(CollectorTrendSource.RAW, 1);
            case ONE_SECOND -> isFast(dataProfile)
                    ? new Plan(CollectorTrendSource.SECOND, 1)
                    : new Plan(CollectorTrendSource.RAW, 1);
            case ONE_MINUTE -> new Plan(CollectorTrendSource.MINUTE, 60);
            case FIVE_MINUTES -> new Plan(CollectorTrendSource.MINUTE, 300);
            case ONE_HOUR -> new Plan(CollectorTrendSource.HOUR, 3600);
            case ONE_DAY -> new Plan(CollectorTrendSource.DAY, 86400);
            case AUTO -> throw new IllegalArgumentException("auto 必须走 resolveAuto");
        };
    }

    public static Plan resolve(String dataProfile, CollectorTrendGranularity granularity, long rangeSeconds) {
        return granularity.isExplicit()
                ? resolveExplicit(granularity, dataProfile)
                : resolveAuto(dataProfile, rangeSeconds);
    }

    /** 在所选层内按点数上限放大桶宽。 */
    public static int bucketSeconds(Plan plan, long rangeSeconds) {
        return plan.source().bucketSeconds(plan.baseBucketSeconds(), rangeSeconds);
    }

    public static long rangeSeconds(long fromMillis, long toMillis) {
        return (toMillis - fromMillis) / 1000;
    }

}
