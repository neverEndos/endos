package cn.iocoder.yudao.module.collector.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

/** 最新值看板的纯计算规则，便于独立验证时间边界与版本令牌。 */
public final class CollectorDashboardUtils {

    public static final int DEFAULT_POLL_INTERVAL_MS = 60_000;
    public static final int STALE_MULTIPLIER = 3;
    public static final int OFFLINE_MULTIPLIER = 10;

    private CollectorDashboardUtils() {
    }

    /** 采样超过三倍轮询周期时标记陈旧；无采样时间时不伪造质量状态。 */
    public static Integer effectiveQuality(Integer storedQuality, LocalDateTime sampleTime,
                                           LocalDateTime serverTime, Integer pollIntervalMs) {
        if (sampleTime == null) {
            return null;
        }
        if (isOlderThan(sampleTime, serverTime, pollIntervalMs, STALE_MULTIPLIER)) {
            return 2;
        }
        return storedQuality != null ? storedQuality : 0;
    }

    /** 最新采样超过十倍轮询周期时，看板将设备标注为因数据陈旧离线。 */
    public static boolean isStaleOffline(LocalDateTime maxSampleTime, LocalDateTime serverTime,
                                         Integer pollIntervalMs) {
        return maxSampleTime != null
                && isOlderThan(maxSampleTime, serverTime, pollIntervalMs, OFFLINE_MULTIPLIER);
    }

    /** 版本只由最新采样时间与设备状态更新时间决定，相同数据可安全返回 304。 */
    public static String createVersionToken(LocalDateTime maxSampleTime, LocalDateTime statusUpdateTime) {
        String source = String.valueOf(maxSampleTime) + "|" + String.valueOf(statusUpdateTime);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", ex);
        }
    }

    private static boolean isOlderThan(LocalDateTime sampleTime, LocalDateTime serverTime,
                                       Integer pollIntervalMs, int multiplier) {
        long interval = pollIntervalMs != null && pollIntervalMs > 0
                ? pollIntervalMs : DEFAULT_POLL_INTERVAL_MS;
        long ageMs = Duration.between(sampleTime, serverTime).toMillis();
        return ageMs > interval * multiplier;
    }

}
