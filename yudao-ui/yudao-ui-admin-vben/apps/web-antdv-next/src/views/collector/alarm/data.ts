import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CollectorApi } from '#/api/collector';

import { getRangePickerDefaultProps } from '#/utils';

export const ALARM_LEVEL_OPTIONS = [
  { label: '异常', value: 3 },
  { label: '预警', value: 2 },
];

export const ALARM_EVENT_TYPE_OPTIONS = [
  { label: '产生', value: 'raised' },
  { label: '升级', value: 'upgraded' },
  { label: '降级', value: 'downgraded' },
  { label: '切换', value: 'changed' },
  { label: '恢复', value: 'cleared' },
];

const ALARM_TYPE_NAMES: Record<string, string> = {
  threshold_low_error: '异常下限',
  threshold_low_warning: '预警下限',
  threshold_high_warning: '预警上限',
  threshold_high_error: '异常上限',
};

export function getAlarmLevelConfig(level?: number) {
  return level === 3
    ? { color: 'error', text: '异常' }
    : level === 2
      ? { color: 'warning', text: '预警' }
      : { color: 'success', text: '正常' };
}

export function formatAlarmType(type?: string) {
  return ALARM_TYPE_NAMES[type || ''] || type || '-';
}

/**
 * 当前版本只有数值通道的阈值报警，可以按报警类型或触发/阈值数值判断是否支持趋势。
 * 判断不了时返回 true，交给趋势接口结果决定，避免误伤未来的报警类型。
 */
export function isNumericTrendAlarm(alarm: CollectorApi.Alarm): boolean {
  if (ALARM_TYPE_NAMES[alarm.alarmType]) {
    return true;
  }
  return (
    Number.isFinite(alarm.triggerValue) || Number.isFinite(alarm.thresholdValue)
  );
}

export function getAlarmEventTypeConfig(type?: string) {
  const configs: Record<string, { color: string; text: string }> = {
    raised: { color: 'error', text: '产生' },
    upgraded: { color: 'error', text: '升级' },
    downgraded: { color: 'warning', text: '降级' },
    changed: { color: 'warning', text: '切换' },
    cleared: { color: 'success', text: '恢复' },
  };
  return configs[type || ''] || { color: 'default', text: type || '-' };
}

export function getAlarmStatusConfig(status?: number, eventType?: string) {
  const configs: Record<number, { color: string; text: string }> = {
    0: { color: 'error', text: '活动' },
    1: { color: 'warning', text: '已确认' },
    2:
      eventType === 'cleared'
        ? { color: 'success', text: '已恢复' }
        : { color: 'default', text: '已结束（被接替）' },
  };
  return configs[status ?? -1] || { color: 'default', text: '-' };
}

/** 名称缺失时给出可读的已删除提示，避免只显示空白单元格。 */
export function formatAlarmEntityName(
  name: string | undefined,
  label: string,
  id?: number,
) {
  return name || `${label}已删除（ID ${id ?? '-'}）`;
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'deviceId',
      label: '设备编号',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 1,
        precision: 0,
        placeholder: '请输入设备编号',
      },
    },
    {
      fieldName: 'sensorId',
      label: '传感器编号',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 1,
        precision: 0,
        placeholder: '请输入传感器编号',
      },
    },
    {
      fieldName: 'channelId',
      label: '通道编号',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 1,
        precision: 0,
        placeholder: '请输入通道编号',
      },
    },
    {
      fieldName: 'level',
      label: '报警级别',
      component: 'Select',
      componentProps: {
        options: ALARM_LEVEL_OPTIONS,
        allowClear: true,
        placeholder: '请选择报警级别',
      },
    },
    {
      fieldName: 'eventType',
      label: '事件类型',
      component: 'Select',
      componentProps: {
        options: ALARM_EVENT_TYPE_OPTIONS,
        allowClear: true,
        placeholder: '请选择事件类型',
      },
    },
    {
      fieldName: 'triggeredAt',
      label: '触发时间',
      component: 'RangePicker',
      componentProps: {
        ...getRangePickerDefaultProps(),
        allowClear: true,
      },
    },
  ];
}

/**
 * 列表的字段。
 * showTrendAction 由页面根据 collector:trend:query 权限传入，无权限时不渲染操作列。
 */
export function useGridColumns(
  showTrendAction = true,
): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'triggeredAt',
      title: '触发时间',
      width: 170,
      fixed: 'left',
      formatter: 'formatDateTime',
    },
    {
      field: 'deviceName',
      title: '设备',
      minWidth: 140,
      slots: { default: 'deviceName' },
    },
    {
      field: 'sensorName',
      title: '传感器',
      minWidth: 140,
      slots: { default: 'sensorName' },
    },
    {
      field: 'channelName',
      title: '通道',
      minWidth: 140,
      slots: { default: 'channelName' },
    },
    {
      field: 'alarmType',
      title: '阈值类型',
      width: 110,
      formatter: ({ cellValue }) => formatAlarmType(cellValue),
    },
    {
      field: 'level',
      title: '级别',
      width: 90,
      slots: { default: 'level' },
    },
    {
      field: 'status',
      title: '状态',
      width: 90,
      slots: { default: 'status' },
    },
    { field: 'triggerValue', title: '触发值', width: 110 },
    { field: 'thresholdValue', title: '阈值', width: 110 },
    {
      field: 'eventType',
      title: '事件类型',
      width: 100,
      slots: { default: 'eventType' },
    },
    {
      field: 'clearedAt',
      title: '恢复时间',
      width: 170,
      slots: { default: 'recoveryTime' },
    },
    { field: 'message', title: '报警内容', minWidth: 200 },
    ...(showTrendAction
      ? [
          {
            title: '操作',
            width: 140,
            fixed: 'right' as const,
            slots: { default: 'actions' },
          },
        ]
      : []),
  ];
}
