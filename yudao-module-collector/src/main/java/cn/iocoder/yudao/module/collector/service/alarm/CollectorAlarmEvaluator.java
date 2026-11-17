package cn.iocoder.yudao.module.collector.service.alarm;

import cn.iocoder.yudao.module.collector.dal.dataobject.alarm.CollectorAlarmStateDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;

import java.util.Objects;

/**
 * 四档阈值纯判定器。
 *
 * 进入低档使用 value &lt; threshold，进入高档使用 value &gt; threshold；
 * 退出低档要求 value &gt;= threshold + deadband，退出高档要求 value &lt;= threshold - deadband。
 */
public class CollectorAlarmEvaluator {

    public static final String THRESHOLD_LOW_ERROR = "threshold_low_error";
    public static final String THRESHOLD_LOW_WARNING = "threshold_low_warning";
    public static final String THRESHOLD_HIGH_WARNING = "threshold_high_warning";
    public static final String THRESHOLD_HIGH_ERROR = "threshold_high_error";

    /**
     * 判断通道是否已被关闭报警能力；关闭时应将活动报警自动恢复，而不是静默跳过。
     *
     * @return null 表示通道可正常判定；否则返回需要写入恢复事件的原因
     */
    public String suppressionReason(CollectorChannelDO channel) {
        if (channel == null) {
            return "通道不存在或已逻辑删除";
        }
        if (!"number".equals(channel.getValueKind())) {
            return "通道已改为非数值类型";
        }
        if (!Objects.equals(channel.getAlarmEnabled(), 1)) {
            return "报警总开关已关闭";
        }
        return null;
    }

    /**
     * 判定目标状态。
     *
     * @return null 表示该样本跳过判定；否则返回目标级别及阈值
     */
    public Decision evaluate(CollectorChannelDO channel, Double value, CollectorAlarmStateDO previousState) {
        if (value == null || !Double.isFinite(value) || suppressionReason(channel) != null) {
            return null;
        }

        Decision direct = directHit(channel, value);
        int previousLevel = previousState == null || previousState.getLevel() == null
                ? 0 : previousState.getLevel();
        // 更高等级不等待旧档恢复，立即升级。
        if (direct.level() > previousLevel) {
            return direct;
        }

        // 当前档仍未回到恢复线时保持，避免边界附近反复升级、降级或恢复。
        if (previousLevel > 0 && previousState.getThresholdType() != null) {
            Threshold current = enabledThreshold(channel, previousState.getThresholdType());
            if (current != null && !recovered(channel, current, value)) {
                return new Decision(previousLevel, current.type(), current.value());
            }
        }
        return direct;
    }

    private Decision directHit(CollectorChannelDO channel, Double value) {
        // error 两档先于 warning 两档；异常上下限同时命中时取更高 severity。
        Threshold lowError = enabledThreshold(channel, THRESHOLD_LOW_ERROR);
        if (lowError != null && value < lowError.value()) {
            return new Decision(3, lowError.type(), lowError.value());
        }
        Threshold highError = enabledThreshold(channel, THRESHOLD_HIGH_ERROR);
        if (highError != null && value > highError.value()) {
            return new Decision(3, highError.type(), highError.value());
        }
        Threshold lowWarning = enabledThreshold(channel, THRESHOLD_LOW_WARNING);
        if (lowWarning != null && value < lowWarning.value()) {
            return new Decision(2, lowWarning.type(), lowWarning.value());
        }
        Threshold highWarning = enabledThreshold(channel, THRESHOLD_HIGH_WARNING);
        if (highWarning != null && value > highWarning.value()) {
            return new Decision(2, highWarning.type(), highWarning.value());
        }
        return Decision.normal();
    }

    private boolean recovered(CollectorChannelDO channel, Threshold threshold, Double value) {
        double deadband = channel.getAlarmDeadband() == null ? 0D : channel.getAlarmDeadband();
        return switch (threshold.type()) {
            case THRESHOLD_LOW_ERROR, THRESHOLD_LOW_WARNING -> value >= threshold.value() + deadband;
            case THRESHOLD_HIGH_WARNING, THRESHOLD_HIGH_ERROR -> value <= threshold.value() - deadband;
            default -> true;
        };
    }

    private Threshold enabledThreshold(CollectorChannelDO channel, String type) {
        Double value;
        Integer enabled;
        switch (type) {
            case THRESHOLD_LOW_ERROR -> {
                value = channel.getThresholdLowError();
                enabled = channel.getThresholdLowErrorEnabled();
            }
            case THRESHOLD_LOW_WARNING -> {
                value = channel.getThresholdLowWarning();
                enabled = channel.getThresholdLowWarningEnabled();
            }
            case THRESHOLD_HIGH_WARNING -> {
                value = channel.getThresholdHighWarning();
                enabled = channel.getThresholdHighWarningEnabled();
            }
            case THRESHOLD_HIGH_ERROR -> {
                value = channel.getThresholdHighError();
                enabled = channel.getThresholdHighErrorEnabled();
            }
            default -> {
                return null;
            }
        }
        if (!Objects.equals(enabled, 1) || value == null || !Double.isFinite(value)) {
            return null;
        }
        return new Threshold(type, value);
    }

    private record Threshold(String type, Double value) {
    }

    /**
     * 判定结果；level=0 时阈值字段为空。
     */
    public record Decision(Integer level, String thresholdType, Double thresholdValue) {

        public static Decision normal() {
            return new Decision(0, null, null);
        }

    }

}
