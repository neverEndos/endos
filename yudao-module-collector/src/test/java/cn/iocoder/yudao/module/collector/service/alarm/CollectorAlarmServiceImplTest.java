package cn.iocoder.yudao.module.collector.service.alarm;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.collector.dal.dataobject.alarm.CollectorAlarmCursorDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.alarm.CollectorAlarmDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.alarm.CollectorAlarmStateDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.sample.CollectorSampleRawDO;
import cn.iocoder.yudao.module.collector.dal.mysql.alarm.CollectorAlarmCursorMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.alarm.CollectorAlarmMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.alarm.CollectorAlarmStateMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.channel.CollectorChannelMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.device.CollectorDeviceMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.sample.CollectorSampleRawMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.sensor.CollectorSensorMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.ALARM_ACKNOWLEDGE_STATUS_INVALID;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.ALARM_ALREADY_ACKNOWLEDGED;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.ALARM_CLEARED_EVENT_NOT_ACKNOWLEDGEABLE;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.ALARM_NOT_EXISTS;
import static cn.iocoder.yudao.module.collector.service.alarm.CollectorAlarmEvaluator.THRESHOLD_HIGH_ERROR;
import static cn.iocoder.yudao.module.collector.service.alarm.CollectorAlarmEvaluator.THRESHOLD_HIGH_WARNING;
import static cn.iocoder.yudao.module.collector.service.alarm.CollectorAlarmEvaluator.THRESHOLD_LOW_ERROR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CollectorAlarmServiceImplTest {

    private static final Long CHANNEL_ID = 101L;
    private static final Long DEVICE_ID = 11L;
    private static final Long SENSOR_ID = 21L;
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 11, 17, 10, 0);

    private CollectorSampleRawMapper sampleRawMapper;
    private CollectorChannelMapper channelMapper;
    private CollectorAlarmCursorMapper cursorMapper;
    private CollectorAlarmStateMapper stateMapper;
    private CollectorAlarmMapper alarmMapper;
    private PlatformTransactionManager transactionManager;
    private CollectorAlarmServiceImpl service;
    private CollectorChannelDO channel;
    private CollectorAlarmCursorDO cursor;

    @Before
    public void setUp() throws Exception {
        sampleRawMapper = mock(CollectorSampleRawMapper.class);
        channelMapper = mock(CollectorChannelMapper.class);
        cursorMapper = mock(CollectorAlarmCursorMapper.class);
        stateMapper = mock(CollectorAlarmStateMapper.class);
        alarmMapper = mock(CollectorAlarmMapper.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        service = new CollectorAlarmServiceImpl();
        inject("sampleRawMapper", sampleRawMapper);
        inject("channelMapper", channelMapper);
        inject("deviceMapper", mock(CollectorDeviceMapper.class));
        inject("sensorMapper", mock(CollectorSensorMapper.class));
        inject("cursorMapper", cursorMapper);
        inject("stateMapper", stateMapper);
        inject("alarmMapper", alarmMapper);
        inject("transactionTemplate", new TransactionTemplate(transactionManager));

        channel = channel();
        when(channelMapper.selectBatchIds(anyCollection())).thenReturn(List.of(channel));
        when(stateMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
        when(alarmMapper.selectActiveListByChannelIds(anyCollection())).thenReturn(List.of());
        when(alarmMapper.selectActiveChannelList()).thenReturn(List.of());
        when(stateMapper.selectListByPositiveLevel()).thenReturn(List.of());
        when(alarmMapper.insert(any(CollectorAlarmDO.class))).thenReturn(1);
        when(alarmMapper.updateById(any(CollectorAlarmDO.class))).thenReturn(1);
        when(stateMapper.insert(any(CollectorAlarmStateDO.class))).thenReturn(1);
        when(stateMapper.updateById(any(CollectorAlarmStateDO.class))).thenReturn(1);
        when(cursorMapper.insertIgnore(any())).thenReturn(1);
        when(cursorMapper.updateById(any(CollectorAlarmCursorDO.class))).thenReturn(1);
        when(sampleRawMapper.selectListAfter(any(), any(), any())).thenReturn(List.of());
        cursor = cursor(BASE_TIME.minusSeconds(1), 0L);
        when(cursorMapper.selectSingletonForUpdate()).thenReturn(cursor);
    }

    @Test
    public void evaluate_appendsRaisedUpgradedDowngradedAndClearedEvents() {
        when(sampleRawMapper.selectListAfter(any(), any(), any())).thenReturn(List.of(
                sample(1L, 75D, BASE_TIME),
                sample(2L, 95D, BASE_TIME.plusSeconds(30)),
                sample(3L, 75D, BASE_TIME.plusSeconds(60)),
                sample(4L, 50D, BASE_TIME.plusSeconds(90))));

        CollectorAlarmService.EvaluationResult result = service.evaluatePendingSamples();

        assertEquals(4, result.processedCount());
        assertEquals(4, result.transitionCount());
        ArgumentCaptor<CollectorAlarmDO> insertedCaptor = ArgumentCaptor.forClass(CollectorAlarmDO.class);
        verify(alarmMapper, times(4)).insert(insertedCaptor.capture());
        List<CollectorAlarmDO> inserted = insertedCaptor.getAllValues();
        assertEquals(List.of("raised", "upgraded", "downgraded", "cleared"),
                inserted.stream().map(CollectorAlarmDO::getEventType).toList());
        assertEquals(List.of(2, 3, 2, 2),
                inserted.stream().map(CollectorAlarmDO::getLevel).toList());

        // 前三行被后续事件接替：只标记结束，原始 event_type 不变；恢复由独立 cleared 行表达。
        for (CollectorAlarmDO history : inserted.subList(0, 3)) {
            assertNotNull(history.getClearedAt());
            assertEquals(Integer.valueOf(2), history.getStatus());
        }
        assertEquals("raised", inserted.get(0).getEventType());
        assertEquals("upgraded", inserted.get(1).getEventType());
        assertEquals("downgraded", inserted.get(2).getEventType());

        CollectorAlarmDO cleared = inserted.get(3);
        assertEquals(Integer.valueOf(2), cleared.getStatus());
        assertEquals(BASE_TIME.plusSeconds(90), cleared.getTriggeredAt());
        assertEquals(Double.valueOf(50D), cleared.getTriggerValue());
        assertEquals(BASE_TIME.plusSeconds(90), cleared.getClearedAt());
        assertEquals("数值恢复正常", cleared.getClearReason());

        ArgumentCaptor<CollectorAlarmDO> updatedCaptor = ArgumentCaptor.forClass(CollectorAlarmDO.class);
        verify(alarmMapper, times(3)).updateById(updatedCaptor.capture());
        assertEquals(List.of("raised", "upgraded", "downgraded"),
                updatedCaptor.getAllValues().stream().map(CollectorAlarmDO::getEventType).toList());
        String recoveryCloseReason = updatedCaptor.getAllValues().get(2).getClearReason();
        assertTrue(recoveryCloseReason.contains("cleared"));
        assertTrue(recoveryCloseReason.contains("数值恢复正常"));

        // 首批插入状态，后三条更新状态；最终状态归零，游标推进到批次最后一条。
        ArgumentCaptor<CollectorAlarmStateDO> stateCaptor = ArgumentCaptor.forClass(CollectorAlarmStateDO.class);
        verify(stateMapper, times(3)).updateById(stateCaptor.capture());
        CollectorAlarmStateDO finalState = stateCaptor.getValue();
        assertEquals(Integer.valueOf(0), finalState.getLevel());
        assertNull(finalState.getThresholdType());
        verify(cursorMapper, times(1)).updateById(cursor);
        assertEquals(BASE_TIME.plusSeconds(90), cursor.getLastSampleTime());
        assertEquals(Long.valueOf(4L), cursor.getLastSampleId());
    }

    @Test
    public void evaluate_refreshesOccurrenceWhileActiveAtSameLevel() {
        when(sampleRawMapper.selectListAfter(any(), any(), any())).thenReturn(List.of(
                sample(1L, 75D, BASE_TIME),
                sample(2L, 75D, BASE_TIME.plusSeconds(30)),
                sample(3L, 75D, BASE_TIME.plusSeconds(60))));

        CollectorAlarmService.EvaluationResult result = service.evaluatePendingSamples();

        assertEquals(3, result.processedCount());
        assertEquals(1, result.transitionCount());
        ArgumentCaptor<CollectorAlarmDO> insertCaptor = ArgumentCaptor.forClass(CollectorAlarmDO.class);
        verify(alarmMapper, times(1)).insert(insertCaptor.capture());
        CollectorAlarmDO active = insertCaptor.getValue();
        assertEquals(Integer.valueOf(3), active.getOccurrenceCount());
        assertEquals(BASE_TIME.plusSeconds(60), active.getLastSeenAt());
        verify(alarmMapper, times(2)).updateById(active);
    }

    @Test
    public void evaluate_writesChangedWhenSameLevelSwitchesThresholdSide() {
        when(sampleRawMapper.selectListAfter(any(), any(), any())).thenReturn(List.of(
                sample(1L, -1D, BASE_TIME),
                sample(2L, 100D, BASE_TIME.plusSeconds(30))));

        CollectorAlarmService.EvaluationResult result = service.evaluatePendingSamples();

        assertEquals(2, result.transitionCount());
        ArgumentCaptor<CollectorAlarmDO> insertedCaptor = ArgumentCaptor.forClass(CollectorAlarmDO.class);
        verify(alarmMapper, times(2)).insert(insertedCaptor.capture());
        List<CollectorAlarmDO> inserted = insertedCaptor.getAllValues();
        assertEquals("raised", inserted.get(0).getEventType());
        assertEquals(THRESHOLD_LOW_ERROR, inserted.get(0).getAlarmType());
        assertEquals("changed", inserted.get(1).getEventType());
        assertEquals(THRESHOLD_HIGH_ERROR, inserted.get(1).getAlarmType());
        assertEquals(Integer.valueOf(3), inserted.get(1).getLevel());
        assertNotNull(inserted.get(0).getClearedAt());
    }

    @Test
    public void evaluate_clearsActiveAlarmWhenTotalSwitchDisabled() {
        CollectorAlarmStateDO warning = state(2, THRESHOLD_HIGH_WARNING);
        CollectorAlarmDO active = activeAlarm(2, THRESHOLD_HIGH_WARNING);
        channel.setAlarmEnabled(0);
        when(stateMapper.selectBatchIds(anyCollection())).thenReturn(List.of(warning));
        when(alarmMapper.selectActiveListByChannelIds(anyCollection())).thenReturn(List.of(active));
        when(sampleRawMapper.selectListAfter(any(), any(), any()))
                .thenReturn(List.of(sample(1L, 75D, BASE_TIME)));

        CollectorAlarmService.EvaluationResult result = service.evaluatePendingSamples();

        assertEquals(1, result.transitionCount());
        ArgumentCaptor<CollectorAlarmDO> insertedCaptor = ArgumentCaptor.forClass(CollectorAlarmDO.class);
        verify(alarmMapper, times(1)).insert(insertedCaptor.capture());
        CollectorAlarmDO cleared = insertedCaptor.getValue();
        assertEquals("cleared", cleared.getEventType());
        assertEquals("报警总开关已关闭", cleared.getClearReason());
        assertEquals(Integer.valueOf(2), cleared.getLevel());
        assertEquals(DEVICE_ID, cleared.getDeviceId());
        assertEquals(SENSOR_ID, cleared.getSensorId());

        // 旧活动事件只被关闭，类型保持 raised。
        verify(alarmMapper, times(1)).updateById(active);
        assertEquals("raised", active.getEventType());
        assertNotNull(active.getClearedAt());

        ArgumentCaptor<CollectorAlarmStateDO> stateCaptor = ArgumentCaptor.forClass(CollectorAlarmStateDO.class);
        verify(stateMapper).updateById(stateCaptor.capture());
        assertEquals(Integer.valueOf(0), stateCaptor.getValue().getLevel());
        assertNull(stateCaptor.getValue().getThresholdType());
    }

    @Test
    public void evaluate_clearsActiveAlarmWhenChannelBecomesEnum() {
        CollectorAlarmStateDO warning = state(2, THRESHOLD_HIGH_WARNING);
        channel.setValueKind("enum");
        when(stateMapper.selectBatchIds(anyCollection())).thenReturn(List.of(warning));
        when(sampleRawMapper.selectListAfter(any(), any(), any()))
                .thenReturn(List.of(sample(1L, 75D, BASE_TIME)));

        CollectorAlarmService.EvaluationResult result = service.evaluatePendingSamples();

        assertEquals(1, result.transitionCount());
        ArgumentCaptor<CollectorAlarmDO> insertedCaptor = ArgumentCaptor.forClass(CollectorAlarmDO.class);
        verify(alarmMapper, times(1)).insert(insertedCaptor.capture());
        assertEquals("cleared", insertedCaptor.getValue().getEventType());
        assertEquals("通道已改为非数值类型", insertedCaptor.getValue().getClearReason());
    }

    @Test
    public void evaluate_clearsDeletedChannelUsingStateIdentity() {
        CollectorAlarmStateDO error = state(3, THRESHOLD_HIGH_ERROR);
        when(channelMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
        when(stateMapper.selectBatchIds(anyCollection())).thenReturn(List.of(error));
        when(sampleRawMapper.selectListAfter(any(), any(), any()))
                .thenReturn(List.of(sample(1L, 100D, BASE_TIME)));

        CollectorAlarmService.EvaluationResult result = service.evaluatePendingSamples();

        assertEquals(1, result.transitionCount());
        ArgumentCaptor<CollectorAlarmDO> insertedCaptor = ArgumentCaptor.forClass(CollectorAlarmDO.class);
        verify(alarmMapper, times(1)).insert(insertedCaptor.capture());
        CollectorAlarmDO cleared = insertedCaptor.getValue();
        assertEquals("cleared", cleared.getEventType());
        assertEquals("通道不存在或已逻辑删除", cleared.getClearReason());
        assertEquals(Integer.valueOf(3), cleared.getLevel());
        assertEquals(DEVICE_ID, cleared.getDeviceId());
        assertEquals(SENSOR_ID, cleared.getSensorId());
        assertEquals(THRESHOLD_HIGH_ERROR, cleared.getAlarmType());
    }

    @Test
    public void evaluate_doesNotInsertClearedWhenSuppressedChannelHasNoActiveAlarm() {
        channel.setAlarmEnabled(0);
        CollectorAlarmStateDO normal = state(0, null);
        when(stateMapper.selectBatchIds(anyCollection())).thenReturn(List.of(normal));
        when(sampleRawMapper.selectListAfter(any(), any(), any()))
                .thenReturn(List.of(sample(1L, 75D, BASE_TIME)));

        CollectorAlarmService.EvaluationResult result = service.evaluatePendingSamples();

        assertEquals(0, result.transitionCount());
        verify(alarmMapper, never()).insert(any(CollectorAlarmDO.class));
    }

    @Test
    public void evaluate_reconcilesLogicallyDeletedChannelWithoutNewSamples() {
        CollectorAlarmStateDO warning = state(2, THRESHOLD_HIGH_WARNING);
        warning.setLastValue(75D);
        warning.setLastSampleTime(BASE_TIME.minusSeconds(30));
        warning.setLastSampleId(41L);
        CollectorAlarmDO active = activeAlarm(2, THRESHOLD_HIGH_WARNING);
        channel.setDeleted(true);
        when(alarmMapper.selectActiveChannelList()).thenReturn(List.of(active));
        when(stateMapper.selectListByPositiveLevel()).thenReturn(List.of(warning));
        when(channelMapper.selectListIncludingDeletedByIds(anyCollection())).thenReturn(List.of(channel));

        CollectorAlarmService.EvaluationResult result = service.evaluatePendingSamples();

        assertEquals(0, result.processedCount());
        assertEquals(1, result.transitionCount());
        verify(sampleRawMapper).selectListAfter(any(), any(), any());
        ArgumentCaptor<CollectorAlarmDO> clearedCaptor = ArgumentCaptor.forClass(CollectorAlarmDO.class);
        verify(alarmMapper).insert(clearedCaptor.capture());
        CollectorAlarmDO cleared = clearedCaptor.getValue();
        assertEquals("cleared", cleared.getEventType());
        assertEquals("通道不存在或已逻辑删除", cleared.getClearReason());
        assertEquals(Double.valueOf(75D), cleared.getTriggerValue());
        assertNotNull(cleared.getTriggeredAt());
        assertEquals(cleared.getTriggeredAt(), cleared.getClearedAt());
        verify(alarmMapper).updateById(active);
        assertEquals("raised", active.getEventType());
        assertEquals(cleared.getClearedAt(), active.getClearedAt());

        ArgumentCaptor<CollectorAlarmStateDO> stateCaptor = ArgumentCaptor.forClass(CollectorAlarmStateDO.class);
        verify(stateMapper).updateById(stateCaptor.capture());
        assertEquals(Integer.valueOf(0), stateCaptor.getValue().getLevel());
        assertNull(stateCaptor.getValue().getThresholdType());
        assertEquals(Long.valueOf(41L), stateCaptor.getValue().getLastSampleId());
        verify(cursorMapper, never()).updateById(any(CollectorAlarmCursorDO.class));
    }

    @Test
    public void evaluate_reconcilesDisabledChannelWithoutNewSamples() {
        CollectorAlarmStateDO warning = state(2, THRESHOLD_HIGH_WARNING);
        warning.setLastValue(75D);
        CollectorAlarmDO active = activeAlarm(2, THRESHOLD_HIGH_WARNING);
        channel.setEnabled(0);
        when(alarmMapper.selectActiveChannelList()).thenReturn(List.of(active));
        when(stateMapper.selectListByPositiveLevel()).thenReturn(List.of(warning));
        when(channelMapper.selectListIncludingDeletedByIds(anyCollection())).thenReturn(List.of(channel));

        CollectorAlarmService.EvaluationResult result = service.evaluatePendingSamples();

        assertEquals(0, result.processedCount());
        assertEquals(1, result.transitionCount());
        ArgumentCaptor<CollectorAlarmDO> clearedCaptor = ArgumentCaptor.forClass(CollectorAlarmDO.class);
        verify(alarmMapper).insert(clearedCaptor.capture());
        assertEquals("cleared", clearedCaptor.getValue().getEventType());
        assertEquals("通道已停用", clearedCaptor.getValue().getClearReason());
        verify(alarmMapper).updateById(active);
        assertNotNull(active.getClearedAt());
        ArgumentCaptor<CollectorAlarmStateDO> stateCaptor = ArgumentCaptor.forClass(CollectorAlarmStateDO.class);
        verify(stateMapper).updateById(stateCaptor.capture());
        assertEquals(Integer.valueOf(0), stateCaptor.getValue().getLevel());
        verify(cursorMapper, never()).updateById(any(CollectorAlarmCursorDO.class));
    }

    @Test
    public void evaluate_reconciliationIsIdempotentWithoutNewSamples() {
        CollectorAlarmStateDO warning = state(2, THRESHOLD_HIGH_WARNING);
        CollectorAlarmDO active = activeAlarm(2, THRESHOLD_HIGH_WARNING);
        channel.setDeleted(true);
        when(alarmMapper.selectActiveChannelList())
                .thenReturn(List.of(active))
                .thenReturn(List.of());
        when(stateMapper.selectListByPositiveLevel())
                .thenReturn(List.of(warning))
                .thenReturn(List.of());
        when(channelMapper.selectListIncludingDeletedByIds(anyCollection())).thenReturn(List.of(channel));

        CollectorAlarmService.EvaluationResult first = service.evaluatePendingSamples();
        CollectorAlarmService.EvaluationResult second = service.evaluatePendingSamples();

        assertEquals(0, first.processedCount());
        assertEquals(1, first.transitionCount());
        assertEquals(0, second.processedCount());
        assertEquals(0, second.transitionCount());
        // 第二次查询模拟首次提交后的数据库：活动事件已关闭、状态已归零，因此不再追加事件。
        verify(alarmMapper, times(1)).insert(any(CollectorAlarmDO.class));
        verify(alarmMapper, times(1)).updateById(any(CollectorAlarmDO.class));
        verify(stateMapper, times(1)).updateById(any(CollectorAlarmStateDO.class));
        verify(sampleRawMapper, times(2)).selectListAfter(any(), any(), any());
        verify(cursorMapper, never()).updateById(any(CollectorAlarmCursorDO.class));
    }

    @Test
    public void evaluate_isIdempotentWhenCursorAlreadyAdvanced() {
        List<CollectorSampleRawDO> samples = List.of(
                sample(1L, 75D, BASE_TIME),
                sample(2L, 95D, BASE_TIME.plusSeconds(30)));
        when(sampleRawMapper.selectListAfter(any(), any(), any()))
                .thenReturn(samples)
                .thenReturn(List.of());

        CollectorAlarmService.EvaluationResult first = service.evaluatePendingSamples();
        CollectorAlarmService.EvaluationResult second = service.evaluatePendingSamples();

        assertEquals(2, first.processedCount());
        assertEquals(2, first.transitionCount());
        assertEquals(0, second.processedCount());
        verify(alarmMapper, times(2)).insert(any(CollectorAlarmDO.class));
        verify(alarmMapper, times(1)).updateById(any(CollectorAlarmDO.class));
        verify(cursorMapper, times(1)).updateById(cursor);
    }

    @Test
    public void evaluate_initializesCursorToLatestSampleWithoutReplayingHistory() {
        CollectorSampleRawDO latest = sample(99L, 50D, BASE_TIME);
        when(cursorMapper.selectSingletonForUpdate()).thenReturn(null);
        when(sampleRawMapper.selectLatest()).thenReturn(latest);
        when(cursorMapper.insertIgnore(any())).thenReturn(1);

        CollectorAlarmService.EvaluationResult result = service.evaluatePendingSamples();

        assertTrue(result.initialized());
        assertEquals(0, result.processedCount());
        ArgumentCaptor<CollectorAlarmCursorDO> cursorCaptor = ArgumentCaptor.forClass(CollectorAlarmCursorDO.class);
        verify(cursorMapper).insertIgnore(cursorCaptor.capture());
        assertEquals(BASE_TIME, cursorCaptor.getValue().getLastSampleTime());
        assertEquals(Long.valueOf(99L), cursorCaptor.getValue().getLastSampleId());
        verify(sampleRawMapper, never()).selectListAfter(any(), any(), any());
        verify(alarmMapper, never()).insert(any(CollectorAlarmDO.class));
    }

    @Test
    public void evaluate_toleratesConcurrentCursorInitialization() {
        CollectorSampleRawDO latest = sample(99L, 50D, BASE_TIME);
        when(cursorMapper.selectSingletonForUpdate()).thenReturn(null);
        when(sampleRawMapper.selectLatest()).thenReturn(latest);
        when(cursorMapper.insertIgnore(any())).thenReturn(0);

        CollectorAlarmService.EvaluationResult result = service.evaluatePendingSamples();

        assertTrue(result.initialized());
        assertEquals(0, result.processedCount());
        verify(sampleRawMapper, never()).selectListAfter(any(), any(), any());
    }

    @Test
    public void evaluate_rollsBackAndDoesNotAdvanceCursorWhenInsertFails() {
        // 这是 mocked mapper/事务管理器的装配级证据：证明异常走 rollback 且不推进游标，
        // 不等同于真实 PostgreSQL 事务回滚集成测试。

        when(sampleRawMapper.selectListAfter(any(), any(), any())).thenReturn(List.of(
                sample(1L, 75D, BASE_TIME),
                sample(2L, 95D, BASE_TIME.plusSeconds(30))));
        when(alarmMapper.insert(any(CollectorAlarmDO.class)))
                .thenReturn(1)
                .thenThrow(new IllegalStateException("模拟事件插入失败"));

        try {
            service.evaluatePendingSamples();
            fail("预期抛出异常");
        } catch (IllegalStateException expected) {
            assertEquals("模拟事件插入失败", expected.getMessage());
        }

        verify(cursorMapper, never()).updateById(any(CollectorAlarmCursorDO.class));
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    public void acknowledge_setsStatusAndOperatorWithoutChangingLevelOrClearedAt() {
        CollectorAlarmDO alarm = activeAlarm(3, THRESHOLD_HIGH_ERROR);
        when(alarmMapper.selectById(1L)).thenReturn(alarm);

        service.acknowledgeAlarm(1L, 99L);

        assertEquals(Integer.valueOf(1), alarm.getStatus());
        assertNotNull(alarm.getAcknowledgedAt());
        assertEquals(Long.valueOf(99L), alarm.getAcknowledgedBy());
        // 确认不改变报警级别、事件类型与恢复时间
        assertEquals(Integer.valueOf(3), alarm.getLevel());
        assertEquals("raised", alarm.getEventType());
        assertNull(alarm.getClearedAt());
        verify(alarmMapper).updateById(alarm);
    }

    @Test
    public void acknowledge_rejectsMissingAlarm() {
        when(alarmMapper.selectById(999L)).thenReturn(null);

        assertServiceException(() -> service.acknowledgeAlarm(999L, 99L), ALARM_NOT_EXISTS);
        verify(alarmMapper, never()).updateById(any(CollectorAlarmDO.class));
    }

    @Test
    public void acknowledge_rejectsAlreadyAcknowledgedAlarm() {
        CollectorAlarmDO alarm = activeAlarm(3, THRESHOLD_HIGH_ERROR);
        alarm.setStatus(1);
        when(alarmMapper.selectById(1L)).thenReturn(alarm);

        assertServiceException(() -> service.acknowledgeAlarm(1L, 99L), ALARM_ALREADY_ACKNOWLEDGED);
        verify(alarmMapper, never()).updateById(any(CollectorAlarmDO.class));
    }

    @Test
    public void acknowledge_rejectsClearedEvent() {
        CollectorAlarmDO cleared = activeAlarm(3, THRESHOLD_HIGH_ERROR);
        cleared.setEventType("cleared");
        cleared.setStatus(2);
        cleared.setClearedAt(BASE_TIME);
        when(alarmMapper.selectById(1L)).thenReturn(cleared);

        assertServiceException(() -> service.acknowledgeAlarm(1L, 99L),
                ALARM_CLEARED_EVENT_NOT_ACKNOWLEDGEABLE);
        verify(alarmMapper, never()).updateById(any(CollectorAlarmDO.class));
    }

    @Test
    public void acknowledge_rejectsNonActiveStatus() {
        CollectorAlarmDO alarm = activeAlarm(2, THRESHOLD_HIGH_WARNING);
        alarm.setStatus(2);
        alarm.setClearedAt(BASE_TIME);
        when(alarmMapper.selectById(1L)).thenReturn(alarm);

        assertServiceException(() -> service.acknowledgeAlarm(1L, 99L),
                ALARM_ACKNOWLEDGE_STATUS_INVALID);
        verify(alarmMapper, never()).updateById(any(CollectorAlarmDO.class));
    }

    private void assertServiceException(Runnable action, cn.iocoder.yudao.framework.common.exception.ErrorCode errorCode) {
        ServiceException exception = null;
        try {
            action.run();
        } catch (ServiceException ex) {
            exception = ex;
        }
        assertNotNull("预期抛出 ServiceException", exception);
        assertEquals(errorCode.getCode(), exception.getCode());
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = CollectorAlarmServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    private CollectorChannelDO channel() {
        CollectorChannelDO result = new CollectorChannelDO();
        result.setId(CHANNEL_ID);
        result.setDeviceId(DEVICE_ID);
        result.setSensorId(SENSOR_ID);
        result.setValueKind("number");
        result.setEnabled(1);
        result.setAlarmEnabled(1);
        result.setAlarmDeadband(0D);
        result.setThresholdLowError(10D);
        result.setThresholdLowWarning(30D);
        result.setThresholdHighWarning(70D);
        result.setThresholdHighError(90D);
        result.setThresholdLowErrorEnabled(1);
        result.setThresholdLowWarningEnabled(1);
        result.setThresholdHighWarningEnabled(1);
        result.setThresholdHighErrorEnabled(1);
        return result;
    }

    private CollectorAlarmStateDO state(int level, String thresholdType) {
        CollectorAlarmStateDO state = new CollectorAlarmStateDO();
        state.setChannelId(CHANNEL_ID);
        state.setDeviceId(DEVICE_ID);
        state.setSensorId(SENSOR_ID);
        state.setLevel(level);
        state.setThresholdType(thresholdType);
        state.setLastThreshold(thresholdType == null ? null : 70D);
        return state;
    }

    private CollectorAlarmDO activeAlarm(int level, String thresholdType) {
        CollectorAlarmDO alarm = new CollectorAlarmDO();
        alarm.setId(1L);
        alarm.setDeviceId(DEVICE_ID);
        alarm.setSensorId(SENSOR_ID);
        alarm.setChannelId(CHANNEL_ID);
        alarm.setAlarmType(thresholdType);
        alarm.setEventType("raised");
        alarm.setLevel(level);
        alarm.setStatus(0);
        alarm.setThresholdValue(70D);
        alarm.setTriggeredAt(BASE_TIME.minusSeconds(30));
        return alarm;
    }

    private CollectorAlarmCursorDO cursor(LocalDateTime lastSampleTime, Long lastSampleId) {
        CollectorAlarmCursorDO result = new CollectorAlarmCursorDO();
        result.setId(1);
        result.setLastSampleTime(lastSampleTime);
        result.setLastSampleId(lastSampleId);
        return result;
    }

    private CollectorSampleRawDO sample(Long sampleId, Double value, LocalDateTime collectTime) {
        CollectorSampleRawDO sample = new CollectorSampleRawDO();
        sample.setSampleId(sampleId);
        sample.setChannelId(CHANNEL_ID);
        sample.setDeviceId(DEVICE_ID);
        sample.setCollectTime(collectTime);
        sample.setValue(value);
        return sample;
    }

}
