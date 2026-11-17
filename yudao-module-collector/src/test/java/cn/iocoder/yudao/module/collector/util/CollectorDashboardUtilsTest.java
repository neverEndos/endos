package cn.iocoder.yudao.module.collector.util;

import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CollectorDashboardUtilsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 12, 30, 0);

    @Test
    public void effectiveQuality_keepsNormalAndTimeoutBeforeStaleBoundary() {
        LocalDateTime boundary = NOW.minusSeconds(180);
        assertEquals(Integer.valueOf(0), CollectorDashboardUtils.effectiveQuality(0, boundary, NOW, 60_000));
        assertEquals(Integer.valueOf(1), CollectorDashboardUtils.effectiveQuality(1, NOW.minusSeconds(1), NOW, 60_000));
        assertEquals(Integer.valueOf(0), CollectorDashboardUtils.effectiveQuality(null, NOW, NOW, 60_000));
    }

    @Test
    public void effectiveQuality_marksStaleOnlyAfterThreeIntervals() {
        LocalDateTime sampleTime = NOW.minusSeconds(180).minusNanos(1_000_000);
        assertEquals(Integer.valueOf(2), CollectorDashboardUtils.effectiveQuality(0, sampleTime, NOW, 60_000));
        assertEquals(Integer.valueOf(2), CollectorDashboardUtils.effectiveQuality(1, sampleTime, NOW, 60_000));
    }

    @Test
    public void effectiveQuality_usesSixtySecondsWhenIntervalMissing() {
        LocalDateTime sampleTime = NOW.minusSeconds(181);
        assertEquals(Integer.valueOf(2), CollectorDashboardUtils.effectiveQuality(0, sampleTime, NOW, null));
        assertEquals(Integer.valueOf(2), CollectorDashboardUtils.effectiveQuality(0, sampleTime, NOW, 0));
    }

    @Test
    public void effectiveQuality_futureAndMissingSamplesAreNotStale() {
        assertEquals(Integer.valueOf(0), CollectorDashboardUtils.effectiveQuality(0, NOW.plusSeconds(1), NOW, 60_000));
        assertNull(CollectorDashboardUtils.effectiveQuality(1, null, NOW, 60_000));
    }

    @Test
    public void staleOffline_usesTenIntervalStrictBoundary() {
        assertFalse(CollectorDashboardUtils.isStaleOffline(NOW.minusSeconds(600), NOW, 60_000));
        assertTrue(CollectorDashboardUtils.isStaleOffline(
                NOW.minusSeconds(600).minusNanos(1_000_000), NOW, 60_000));
        assertFalse(CollectorDashboardUtils.isStaleOffline(null, NOW, 60_000));
    }

    @Test
    public void versionToken_isStableAndSensitiveToBothInputs() {
        LocalDateTime sampleTime = NOW.minusSeconds(1);
        LocalDateTime statusTime = NOW.minusSeconds(2);
        String token = CollectorDashboardUtils.createVersionToken(sampleTime, statusTime);

        assertEquals(64, token.length());
        assertEquals(token, CollectorDashboardUtils.createVersionToken(sampleTime, statusTime));
        assertNotEquals(token, CollectorDashboardUtils.createVersionToken(NOW, statusTime));
        assertNotEquals(token, CollectorDashboardUtils.createVersionToken(sampleTime, NOW));
    }

}
