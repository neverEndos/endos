package cn.iocoder.yudao.module.collector.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.CHANNEL_THRESHOLD_INVALID;

/**
 * 采集器阈值计算工具。
 *
 * 数值通道的四档阈值按「量程下限 + 比例 × 量程」计算，统一保留 4 位小数；
 * 量程缺失时返回 null，不伪造阈值。
 */
public final class CollectorThresholdUtils {

    public static final double DEFAULT_LOW_ERROR_RATIO = 0.10D;
    public static final double DEFAULT_LOW_WARNING_RATIO = 0.30D;
    public static final double DEFAULT_HIGH_WARNING_RATIO = 0.70D;
    public static final double DEFAULT_HIGH_ERROR_RATIO = 0.90D;

    private CollectorThresholdUtils() {
    }

    /**
     * 按量程与比例计算单档阈值；量程或比例缺失返回 null。
     */
    public static Double calculate(Double valueMin, Double valueMax, Double ratio) {
        if (valueMin == null || valueMax == null || ratio == null) {
            return null;
        }
        if (!Double.isFinite(valueMin) || !Double.isFinite(valueMax) || !Double.isFinite(ratio)) {
            throw exception(CHANNEL_THRESHOLD_INVALID);
        }
        BigDecimal result = BigDecimal.valueOf(valueMin).add(BigDecimal.valueOf(ratio)
                .multiply(BigDecimal.valueOf(valueMax).subtract(BigDecimal.valueOf(valueMin))));
        double threshold = result.setScale(4, RoundingMode.HALF_UP).doubleValue();
        if (!Double.isFinite(threshold)) {
            throw exception(CHANNEL_THRESHOLD_INVALID);
        }
        return threshold;
    }

    /**
     * 保留 4 位小数，四舍五入。
     */
    public static double round4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    public static boolean isFinite(Double value) {
        return value == null || Double.isFinite(value);
    }

}
