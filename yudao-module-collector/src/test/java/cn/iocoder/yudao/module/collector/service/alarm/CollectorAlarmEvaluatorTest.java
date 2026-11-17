package cn.iocoder.yudao.module.collector.service.alarm;

import cn.iocoder.yudao.module.collector.dal.dataobject.alarm.CollectorAlarmStateDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;
import org.junit.Test;

import static cn.iocoder.yudao.module.collector.service.alarm.CollectorAlarmEvaluator.THRESHOLD_HIGH_ERROR;
import static cn.iocoder.yudao.module.collector.service.alarm.CollectorAlarmEvaluator.THRESHOLD_HIGH_WARNING;
import static cn.iocoder.yudao.module.collector.service.alarm.CollectorAlarmEvaluator.THRESHOLD_LOW_ERROR;
import static cn.iocoder.yudao.module.collector.service.alarm.CollectorAlarmEvaluator.THRESHOLD_LOW_WARNING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CollectorAlarmEvaluatorTest {

    private final CollectorAlarmEvaluator evaluator = new CollectorAlarmEvaluator();

    @Test
    public void evaluate_raisedUpgradedDowngradedAndCleared() {
        CollectorChannelDO channel = channel(2D);

        assertDecision(evaluator.evaluate(channel, 50D, null), 0, null);
        CollectorAlarmStateDO warning = state(2, THRESHOLD_HIGH_WARNING);
        assertDecision(evaluator.evaluate(channel, 75D, null), 2, THRESHOLD_HIGH_WARNING);
        assertDecision(evaluator.evaluate(channel, 95D, warning), 3, THRESHOLD_HIGH_ERROR);
        CollectorAlarmStateDO error = state(3, THRESHOLD_HIGH_ERROR);
        assertDecision(evaluator.evaluate(channel, 75D, error), 2, THRESHOLD_HIGH_WARNING);
        assertDecision(evaluator.evaluate(channel, 50D, warning), 0, null);
    }

    @Test
    public void evaluate_lowThresholdLifecycle() {
        CollectorChannelDO channel = channel(0D);
        assertDecision(evaluator.evaluate(channel, 20D, null), 2, THRESHOLD_LOW_WARNING);
        CollectorAlarmStateDO warning = state(2, THRESHOLD_LOW_WARNING);
        assertDecision(evaluator.evaluate(channel, -5D, warning), 3, THRESHOLD_LOW_ERROR);
        CollectorAlarmStateDO error = state(3, THRESHOLD_LOW_ERROR);
        assertDecision(evaluator.evaluate(channel, 15D, error), 2, THRESHOLD_LOW_WARNING);
        assertDecision(evaluator.evaluate(channel, 50D, warning), 0, null);
    }

    @Test
    public void evaluate_highHysteresisHoldsUntilRecoveryLine() {
        CollectorChannelDO channel = channel(5D);
        CollectorAlarmStateDO error = state(3, THRESHOLD_HIGH_ERROR);

        // 上异常阈值 90，恢复线为 85；仍在 (85,90] 内时保持异常。
        assertDecision(evaluator.evaluate(channel, 87D, error), 3, THRESHOLD_HIGH_ERROR);
        assertDecision(evaluator.evaluate(channel, 85D, error), 2, THRESHOLD_HIGH_WARNING);
        CollectorAlarmStateDO warning = state(2, THRESHOLD_HIGH_WARNING);
        assertDecision(evaluator.evaluate(channel, 68D, warning), 2, THRESHOLD_HIGH_WARNING);
        assertDecision(evaluator.evaluate(channel, 65D, warning), 0, null);
    }

    @Test
    public void evaluate_lowHysteresisHoldsUntilRecoveryLine() {
        CollectorChannelDO channel = channel(5D);
        CollectorAlarmStateDO error = state(3, THRESHOLD_LOW_ERROR);

        // 下异常阈值 10，恢复线为 15；仍在 [10,15) 内时保持异常。
        assertDecision(evaluator.evaluate(channel, 12D, error), 3, THRESHOLD_LOW_ERROR);
        assertDecision(evaluator.evaluate(channel, 15D, error), 2, THRESHOLD_LOW_WARNING);
        CollectorAlarmStateDO warning = state(2, THRESHOLD_LOW_WARNING);
        assertDecision(evaluator.evaluate(channel, 28D, warning), 2, THRESHOLD_LOW_WARNING);
        assertDecision(evaluator.evaluate(channel, 35D, warning), 0, null);
    }

    @Test
    public void evaluate_errorHasPriorityOverWarning() {
        CollectorChannelDO channel = channel(0D);
        CollectorAlarmStateDO warning = state(2, THRESHOLD_HIGH_WARNING);

        assertDecision(evaluator.evaluate(channel, 100D, warning), 3, THRESHOLD_HIGH_ERROR);
        assertDecision(evaluator.evaluate(channel, -1D, warning), 3, THRESHOLD_LOW_ERROR);
    }

    @Test
    public void evaluate_skipsEnumDisabledAlarmAndDisabledThreshold() {
        CollectorChannelDO channel = channel(0D);
        channel.setValueKind("enum");
        assertNull(evaluator.evaluate(channel, 100D, null));

        channel.setValueKind("number");
        channel.setAlarmEnabled(0);
        assertNull(evaluator.evaluate(channel, 100D, null));

        channel.setAlarmEnabled(1);
        channel.setThresholdHighErrorEnabled(0);
        channel.setThresholdHighWarningEnabled(0);
        assertDecision(evaluator.evaluate(channel, 100D, null), 0, null);
        assertNull(evaluator.evaluate(channel, null, null));
        assertNull(evaluator.evaluate(channel, Double.NaN, null));
    }

    @Test
    public void evaluate_zeroDeadbandRecoversAtOriginalThreshold() {
        CollectorChannelDO channel = channel(0D);
        CollectorAlarmStateDO high = state(3, THRESHOLD_HIGH_ERROR);
        assertDecision(evaluator.evaluate(channel, 89D, high), 2, THRESHOLD_HIGH_WARNING);
        CollectorAlarmStateDO low = state(3, THRESHOLD_LOW_ERROR);
        assertDecision(evaluator.evaluate(channel, 11D, low), 2, THRESHOLD_LOW_WARNING);
    }

    private CollectorChannelDO channel(double deadband) {
        CollectorChannelDO channel = new CollectorChannelDO();
        channel.setValueKind("number");
        channel.setAlarmEnabled(1);
        channel.setAlarmDeadband(deadband);
        channel.setThresholdLowError(10D);
        channel.setThresholdLowWarning(30D);
        channel.setThresholdHighWarning(70D);
        channel.setThresholdHighError(90D);
        channel.setThresholdLowErrorEnabled(1);
        channel.setThresholdLowWarningEnabled(1);
        channel.setThresholdHighWarningEnabled(1);
        channel.setThresholdHighErrorEnabled(1);
        return channel;
    }

    private CollectorAlarmStateDO state(int level, String thresholdType) {
        CollectorAlarmStateDO state = new CollectorAlarmStateDO();
        state.setLevel(level);
        state.setThresholdType(thresholdType);
        return state;
    }

    private void assertDecision(CollectorAlarmEvaluator.Decision decision, int level, String thresholdType) {
        assertEquals(level, decision.level().intValue());
        assertEquals(thresholdType, decision.thresholdType());
    }

}
