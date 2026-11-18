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
        // 30 分钟：1800 秒，目标 1.8 秒，分钟层最小桶仍为 60 秒
        assertEquals(60, CollectorTrendPlanUtils.bucketSeconds(
                CollectorTrendPlanUtils.resolveAuto("normal", 30 * MINUTE), 30 * MINUTE));
        // 48 小时：172800 秒，目标 172.8 秒，放大到 180 秒（960 点）
        assertEquals(180, CollectorTrendPlanUtils.bucketSeconds(
                CollectorTrendPlanUtils.resolveAuto("normal", 48 * HOUR), 48 * HOUR));
        // 90 天小时层：目标 7776 秒，放大到 10800 秒（720 点）
        assertEquals(10800, CollectorTrendPlanUtils.bucketSeconds(
                CollectorTrendPlanUtils.resolveAuto("normal", 90 * DAY), 90 * DAY));
        // fast 2 小时秒层：目标 7.2 秒，放大到 10 秒（720 点）
        assertEquals(10, CollectorTrendPlanUtils.bucketSeconds(
                CollectorTrendPlanUtils.resolveAuto("fast", 2 * HOUR), 2 * HOUR));
    }

    @Test
    public void bucketSeconds_neverExceedsPointCapForSupportedRanges() {
        long[] ranges = {600, 601, 3600, 7200, 7201, 172800, 172801, 7776000, 31536000};
        for (String profile : new String[]{"normal", "fast"}) {
            for (long range : ranges) {
                int bucket = CollectorTrendPlanUtils.bucketSeconds(
                        CollectorTrendPlanUtils.resolveAuto(profile, range), range);
                long points = (range + bucket - 1) / bucket;
                assertTrue("profile=" + profile + " range=" + range + " bucket=" + bucket + " points=" + points,
                        points <= CollectorTrendSource.MAX_POINTS_PER_SERIES);
            }
        }
    }

    @Test
    public void granularityRangeLimitsMatchContract() {
        assertTrue(CollectorTrendGranularity.AUTO.supportsRange(365 * DAY));
        assertTrue(CollectorTrendGranularity.RAW.supportsRange(10 * MINUTE));
        assertFalse(CollectorTrendGranularity.RAW.supportsRange(10 * MINUTE + 1));
        assertTrue(CollectorTrendGranularity.ONE_SECOND.supportsRange(2 * HOUR));
        assertFalse(CollectorTrendGranularity.ONE_SECOND.supportsRange(2 * HOUR + 1));
        assertTrue(CollectorTrendGranularity.ONE_MINUTE.supportsRange(48 * HOUR));
        assertFalse(CollectorTrendGranularity.ONE_MINUTE.supportsRange(48 * HOUR + 1));
        assertTrue(CollectorTrendGranularity.FIVE_MINUTES.supportsRange(90 * DAY));
        assertFalse(CollectorTrendGranularity.FIVE_MINUTES.supportsRange(90 * DAY + 1));
        assertTrue(CollectorTrendGranularity.ONE_HOUR.supportsRange(90 * DAY));
        assertFalse(CollectorTrendGranularity.ONE_HOUR.supportsRange(90 * DAY + 1));
        assertTrue(CollectorTrendGranularity.ONE_DAY.supportsRange(365 * DAY));
        assertFalse(CollectorTrendGranularity.ONE_DAY.supportsRange(365 * DAY + 1));
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
