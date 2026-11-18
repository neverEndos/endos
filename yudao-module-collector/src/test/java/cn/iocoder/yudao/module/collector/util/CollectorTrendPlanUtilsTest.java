package cn.iocoder.yudao.module.collector.util;

import cn.iocoder.yudao.module.collector.enums.CollectorTrendGranularity;
import cn.iocoder.yudao.module.collector.enums.CollectorTrendSource;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** 自动选源、显式粒度上限与点数放大规则。 */
public class CollectorTrendPlanUtilsTest {

    private static final long MINUTE = 60;
    private static final long HOUR = 3600;
    private static final long DAY = 86400;

    @Test
    public void resolveAuto_normalUsesRawThenMinuteHourDay() {
        assertPlan(CollectorTrendPlanUtils.resolveAuto("normal", 10 * MINUTE),
                CollectorTrendSource.RAW, 1);
        assertPlan(CollectorTrendPlanUtils.resolveAuto("normal", 10 * MINUTE + 1),
                CollectorTrendSource.MINUTE, 60);
        assertPlan(CollectorTrendPlanUtils.resolveAuto("normal", 48 * HOUR),
                CollectorTrendSource.MINUTE, 60);
        assertPlan(CollectorTrendPlanUtils.resolveAuto("normal", 48 * HOUR + 1),
                CollectorTrendSource.HOUR, 3600);
        assertPlan(CollectorTrendPlanUtils.resolveAuto("normal", 90 * DAY),
                CollectorTrendSource.HOUR, 3600);
        assertPlan(CollectorTrendPlanUtils.resolveAuto("normal", 90 * DAY + 1),
                CollectorTrendSource.DAY, 86400);
        // dataProfile 为空或未知值时按 normal 处理
        assertPlan(CollectorTrendPlanUtils.resolveAuto(null, 5 * MINUTE),
                CollectorTrendSource.RAW, 1);
        assertPlan(CollectorTrendPlanUtils.resolveAuto("FAST", 5 * MINUTE),
                CollectorTrendSource.SECOND, 1);
    }

    @Test
    public void resolveAuto_fastUsesSecondThenMinuteHourDay() {
        assertPlan(CollectorTrendPlanUtils.resolveAuto("fast", 10 * MINUTE),
                CollectorTrendSource.SECOND, 1);
        assertPlan(CollectorTrendPlanUtils.resolveAuto("fast", 10 * MINUTE + 1),
                CollectorTrendSource.SECOND, 5);
        assertPlan(CollectorTrendPlanUtils.resolveAuto("fast", 2 * HOUR),
                CollectorTrendSource.SECOND, 5);
        assertPlan(CollectorTrendPlanUtils.resolveAuto("fast", 2 * HOUR + 1),
                CollectorTrendSource.MINUTE, 60);
        assertPlan(CollectorTrendPlanUtils.resolveAuto("fast", 48 * HOUR),
                CollectorTrendSource.MINUTE, 60);
        assertPlan(CollectorTrendPlanUtils.resolveAuto("fast", 90 * DAY + 1),
                CollectorTrendSource.DAY, 86400);
    }

    @Test
    public void resolveExplicit_oneSecondFallsBackToRawForNormalProfile() {
        assertPlan(CollectorTrendPlanUtils.resolveExplicit(CollectorTrendGranularity.ONE_SECOND, "fast"),
                CollectorTrendSource.SECOND, 1);
        assertPlan(CollectorTrendPlanUtils.resolveExplicit(CollectorTrendGranularity.ONE_SECOND, "normal"),
                CollectorTrendSource.RAW, 1);
        assertPlan(CollectorTrendPlanUtils.resolveExplicit(CollectorTrendGranularity.ONE_MINUTE, "fast"),
                CollectorTrendSource.MINUTE, 60);
        assertPlan(CollectorTrendPlanUtils.resolveExplicit(CollectorTrendGranularity.FIVE_MINUTES, "normal"),
                CollectorTrendSource.MINUTE, 300);
        assertPlan(CollectorTrendPlanUtils.resolveExplicit(CollectorTrendGranularity.ONE_HOUR, "fast"),
                CollectorTrendSource.HOUR, 3600);
        assertPlan(CollectorTrendPlanUtils.resolveExplicit(CollectorTrendGranularity.ONE_DAY, "normal"),
                CollectorTrendSource.DAY, 86400);
    }

    @Test
    public void bucketSeconds_enlargesWithinLayerToStayUnderPointCap() {
        // 30 分钟：1800 秒，最坏桶数 1800/60+1=31，分钟层最小桶仍为 60 秒
        assertEquals(60, CollectorTrendPlanUtils.bucketSeconds(
                CollectorTrendPlanUtils.resolveAuto("normal", 30 * MINUTE), 30 * MINUTE));
        // 48 小时：172800 秒，180 秒桶最坏 961 点；120 秒桶最坏 1441 点不可用
        assertEquals(180, CollectorTrendPlanUtils.bucketSeconds(
                CollectorTrendPlanUtils.resolveAuto("normal", 48 * HOUR), 48 * HOUR));
        // 90 天小时层：10800 秒桶最坏 721 点；7200 秒桶最坏 1081 点不可用
        assertEquals(10800, CollectorTrendPlanUtils.bucketSeconds(
                CollectorTrendPlanUtils.resolveAuto("normal", 90 * DAY), 90 * DAY));
        // fast 2 小时秒层：10 秒桶最坏 721 点；5 秒桶最坏 1441 点不可用
        assertEquals(10, CollectorTrendPlanUtils.bucketSeconds(
                CollectorTrendPlanUtils.resolveAuto("fast", 2 * HOUR), 2 * HOUR));
    }

    @Test
    public void bucketSeconds_fastFiveThousandSecondsMisalignedNeedsTenSecondBucket() {
        // 复现 Blocker 1：fast 通道 5000 秒（00:00:01~01:23:21），from 不在 5 秒桶边界上。
        // 旧公式 ceil(5000/5)=1000 会选 5 秒桶，SQL 实际返回 1001 点；
        // 新公式按最坏桶数 ceil(5000/5)+1=1001 > 1000，递增到 10 秒桶（最坏 501 点）。
        assertEquals(10, CollectorTrendPlanUtils.bucketSeconds(
                CollectorTrendPlanUtils.resolveAuto("fast", 5000), 5000));
        assertEquals(501, CollectorTrendSource.worstBucketCount(5000, 10));
    }

    @Test
    public void bucketSeconds_rangeExactlyMultipleStillReservesBoundaryBucket() {
        // 范围恰为桶宽整数倍（5000 = 1000 × 5）时，from 偏移仍会多覆盖一个边界桶
        assertEquals(1001, CollectorTrendSource.worstBucketCount(5000, 5));
        // 比整数倍少 1 秒同样可能跨 1001 个桶（首桶只覆盖 1ms 也算一个桶）
        assertEquals(1001, CollectorTrendSource.worstBucketCount(4999, 5));
        assertEquals(1000, CollectorTrendSource.worstBucketCount(999, 1));
        assertEquals(1001, CollectorTrendSource.worstBucketCount(1000, 1));
    }

    @Test
    public void bucketSeconds_neverExceedsPointCapForSupportedRanges() {
        long[] ranges = {600, 601, 3600, 7200, 7201, 172800, 172801, 7776000, 31536000};
        for (String profile : new String[]{"normal", "fast"}) {
            for (long range : ranges) {
                int bucket = CollectorTrendPlanUtils.bucketSeconds(
                        CollectorTrendPlanUtils.resolveAuto(profile, range), range);
                long worstPoints = CollectorTrendSource.worstBucketCount(range, bucket);
                assertTrue("profile=" + profile + " range=" + range + " bucket=" + bucket
                                + " worstPoints=" + worstPoints,
                        worstPoints <= CollectorTrendSource.MAX_POINTS_PER_SERIES);
            }
        }
    }

    @Test
    public void granularityRangeLimitsMatchContract() {
        long millis = 1000;
        assertTrue(CollectorTrendGranularity.AUTO.supportsRangeMillis(365 * DAY * millis));
        assertTrue(CollectorTrendGranularity.RAW.supportsRangeMillis(10 * MINUTE * millis));
        assertFalse(CollectorTrendGranularity.RAW.supportsRangeMillis(10 * MINUTE * millis + 1));
        assertTrue(CollectorTrendGranularity.ONE_SECOND.supportsRangeMillis(2 * HOUR * millis));
        assertFalse(CollectorTrendGranularity.ONE_SECOND.supportsRangeMillis(2 * HOUR * millis + 1));
        assertTrue(CollectorTrendGranularity.ONE_MINUTE.supportsRangeMillis(48 * HOUR * millis));
        assertFalse(CollectorTrendGranularity.ONE_MINUTE.supportsRangeMillis(48 * HOUR * millis + 1));
        assertTrue(CollectorTrendGranularity.FIVE_MINUTES.supportsRangeMillis(90 * DAY * millis));
        assertFalse(CollectorTrendGranularity.FIVE_MINUTES.supportsRangeMillis(90 * DAY * millis + 1));
        assertTrue(CollectorTrendGranularity.ONE_HOUR.supportsRangeMillis(90 * DAY * millis));
        assertFalse(CollectorTrendGranularity.ONE_HOUR.supportsRangeMillis(90 * DAY * millis + 1));
        assertTrue(CollectorTrendGranularity.ONE_DAY.supportsRangeMillis(365 * DAY * millis));
        assertFalse(CollectorTrendGranularity.ONE_DAY.supportsRangeMillis(365 * DAY * millis + 1));
        assertEquals(CollectorTrendGranularity.MAX_TOTAL_RANGE, Duration.ofDays(365));
    }

    @Test
    public void granularityFromCode_acceptsContractValuesAndRejectsOthers() {
        assertEquals(CollectorTrendGranularity.AUTO, CollectorTrendGranularity.fromCode(null));
        assertEquals(CollectorTrendGranularity.AUTO, CollectorTrendGranularity.fromCode("AUTO"));
        assertEquals(CollectorTrendGranularity.ONE_SECOND, CollectorTrendGranularity.fromCode("1S"));
        assertEquals(CollectorTrendGranularity.FIVE_MINUTES, CollectorTrendGranularity.fromCode("5m"));
        assertThrows(IllegalArgumentException.class,
                () -> CollectorTrendGranularity.fromCode("10s"));
    }

    private void assertPlan(CollectorTrendPlanUtils.Plan plan, CollectorTrendSource source,
                            int baseBucketSeconds) {
        assertEquals(source, plan.source());
        assertEquals(baseBucketSeconds, plan.baseBucketSeconds());
    }

}
