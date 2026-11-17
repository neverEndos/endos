import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { getRangePickerDefaultProps } from '#/utils';

export const ALARM_LEVEL_OPTIONS = [
  { label: '异常', value: 3 },
  { label: '预警', value: 2 },
];

export const ALARM_EVENT_TYPE_OPTIONS = [
  { label: '产生', value: 'raised' },
  { label: '升级', value: 'upgraded' },
  { label: '降级', value: 'downgraded' },
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

export function getAlarmEventTypeConfig(type?: string) {
  const configs: Record<string, { color: string; text: string }> = {
    raised: { color: 'error', text: '产生' },
    upgraded: { color: 'error', text: '升级' },
    downgraded: { color: 'warning', text: '降级' },
    cleared: { color: 'success', text: '恢复' },
  };
  return configs[type || ''] || { color: 'default', text: type || '-' };
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

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'triggeredAt',
      title: '触发时间',
      width: 170,
      fixed: 'left',
      formatter: 'formatDateTime',
    },
    { field: 'deviceName', title: '设备', minWidth: 140 },
    { field: 'sensorName', title: '传感器', minWidth: 140 },
    { field: 'channelName', title: '通道', minWidth: 140 },
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
      formatter: 'formatDateTime',
    },
    { field: 'message', title: '报警内容', minWidth: 200 },
  ];
}
