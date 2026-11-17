package cn.iocoder.yudao.module.collector.util;

import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CollectorDashboardUtilsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 12, 30, 0);
    private static final int ONE_MINUTE = 60_000;

    @Test
    public void storedQuality_keepsStoredBadCodeInsteadOfReusingItForStale() {
        // 存储的 2 表示坏质量；陈旧使用独立的 stale 标记，不能覆盖质量码。
        assertEquals(Integer.valueOf(0), CollectorDashboardUtils.storedQuality(0, NOW));
        assertEquals(Integer.valueOf(1), CollectorDashboardUtils.storedQuality(1, NOW));
        assertEquals(Integer.valueOf(2), CollectorDashboardUtils.storedQuality(2, NOW));
        assertEquals(Integer.valueOf(0), CollectorDashboardUtils.storedQuality(null, NOW));
        assertNull(CollectorDashboardUtils.storedQuality(1, null));
    }

    @Test
    public void isStale_marksOnlyAfterThreeIntervals() {
        LocalDateTime boundary = NOW.minusSeconds(180);
        assertFalse(CollectorDashboardUtils.isStale(boundary, NOW, ONE_MINUTE));
        assertTrue(CollectorDashboardUtils.isStale(
                boundary.minusNanos(1_000_000), NOW, ONE_MINUTE));
        assertFalse(CollectorDashboardUtils.isStale(null, NOW, ONE_MINUTE));
        assertFalse(CollectorDashboardUtils.isStale(NOW.plusSeconds(1), NOW, ONE_MINUTE));
    }

    @Test
    public void isStale_usesSixtySecondsWhenIntervalMissing() {
        LocalDateTime sampleTime = NOW.minusSeconds(181);
        assertTrue(CollectorDashboardUtils.isStale(sampleTime, NOW, null));
        assertTrue(CollectorDashboardUtils.isStale(sampleTime, NOW, 0));
    }

    @Test
    public void isStaleOffline_usesTenIntervalStrictBoundary() {
        assertFalse(CollectorDashboardUtils.isStaleOffline(NOW.minusSeconds(600), NOW, ONE_MINUTE));
        assertTrue(CollectorDashboardUtils.isStaleOffline(
                NOW.minusSeconds(600).minusNanos(1_000_000), NOW, ONE_MINUTE));
        assertFalse(CollectorDashboardUtils.isStaleOffline(null, NOW, ONE_MINUTE));
    }

    @Test
    public void derivedState_changesWithServerTimeWhenSampleTimeIsUnchanged() {
        // 同一采样时间不动，只推进服务器时间：3 倍边界出现陈旧，10 倍边界出现离线。
        LocalDateTime sampleTime = NOW.minusSeconds(180).minusNanos(1_000_000);
        assertTrue(CollectorDashboardUtils.isStale(sampleTime, NOW, ONE_MINUTE));
        assertFalse(CollectorDashboardUtils.isStaleOffline(sampleTime, NOW, ONE_MINUTE));

        LocalDateTime later = NOW.plusSeconds(420);
        assertTrue(CollectorDashboardUtils.isStale(sampleTime, later, ONE_MINUTE));
        assertTrue(CollectorDashboardUtils.isStaleOffline(sampleTime, later, ONE_MINUTE));
    }

}
