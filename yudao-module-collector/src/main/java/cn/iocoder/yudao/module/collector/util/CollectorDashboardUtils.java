package cn.iocoder.yudao.module.collector.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 最新值看板的纯计算规则，便于独立验证时间边界。 */
public final class CollectorDashboardUtils {

    /** 采集器、DDL 与后端统一使用的墙钟时区。 */
    public static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    public static final int DEFAULT_POLL_INTERVAL_MS = 60_000;
    public static final int STALE_MULTIPLIER = 3;
    public static final int OFFLINE_MULTIPLIER = 10;

    private CollectorDashboardUtils() {
    }

    /** 采样超过三倍轮询周期时标记陈旧；无采样时间时不伪造陈旧状态。 */
    public static boolean isStale(LocalDateTime sampleTime, LocalDateTime serverTime,
                                  Integer pollIntervalMs) {
        return sampleTime != null
                && isOlderThan(sampleTime, serverTime, pollIntervalMs, STALE_MULTIPLIER);
    }

    /**
     * 活性时间超过十倍轮询周期时，看板将设备标注为因数据陈旧离线。
     * 活性时间由 Service 取最新采样，无采样时回退到设备状态更新时间；两者都缺失时直接离线。
     */
    public static boolean isStaleOffline(LocalDateTime activityTime, LocalDateTime serverTime,
                                         Integer pollIntervalMs) {
        return activityTime == null
                || isOlderThan(activityTime, serverTime, pollIntervalMs, OFFLINE_MULTIPLIER);
    }

    /** 看板时间字段显式按 Asia/Shanghai 转为 epoch millis，避免 JVM 默认时区影响。 */
    public static Long toEpochMilli(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.atZone(SHANGHAI_ZONE).toInstant().toEpochMilli() : null;
    }

    /**
     * 质量码原样返回：存储的 2 表示坏质量，不再与“陈旧”复用同一个码。
     * 无采样时间时返回 null；有采样但质量为空时按正常处理。
     */
    public static Integer storedQuality(Integer quality, LocalDateTime sampleTime) {
        if (sampleTime == null) {
            return null;
        }
        return quality != null ? quality : 0;
    }

    private static boolean isOlderThan(LocalDateTime sampleTime, LocalDateTime serverTime,
                                       Integer pollIntervalMs, int multiplier) {
        long interval = pollIntervalMs != null && pollIntervalMs > 0
                ? pollIntervalMs : DEFAULT_POLL_INTERVAL_MS;
        long ageMs = Duration.between(sampleTime, serverTime).toMillis();
        return ageMs > interval * multiplier;
    }

}
