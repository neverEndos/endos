import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import {
  getDeviceSimpleList,
  getSensorSimpleList,
} from '#/api/collector';

import { ENABLED_OPTIONS, VALUE_KIND_OPTIONS } from '../shared';

/** 查询启用的设备精简列表 */
function getEnabledDeviceList() {
  return getDeviceSimpleList({ enabled: 1 });
}

/** 查询启用的传感器精简列表 */
function getEnabledSensorList() {
  return getSensorSimpleList({ enabled: 1 });
}

/** 阈值与报警配置表单 */
export function useThresholdFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'id',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      component: 'Input',
      fieldName: 'valueKind',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'valueMin',
      label: '量程下限',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入量程下限',
      },
    },
    {
      fieldName: 'valueMax',
      label: '量程上限',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入量程上限',
      },
    },
    {
      fieldName: 'thresholdLowError',
      label: '低低限阈值',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入低低限阈值',
      },
      dependencies: {
        triggerFields: ['valueKind'],
        disabled: (values) => values.valueKind === 'enum',
      },
    },
    {
      fieldName: 'thresholdLowWarning',
      label: '低限阈值',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入低限阈值',
      },
      dependencies: {
        triggerFields: ['valueKind'],
        disabled: (values) => values.valueKind === 'enum',
      },
    },
    {
      fieldName: 'thresholdHighWarning',
      label: '高限阈值',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入高限阈值',
      },
      dependencies: {
        triggerFields: ['valueKind'],
        disabled: (values) => values.valueKind === 'enum',
      },
    },
    {
      fieldName: 'thresholdHighError',
      label: '高高限阈值',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入高高限阈值',
      },
      dependencies: {
        triggerFields: ['valueKind'],
        disabled: (values) => values.valueKind === 'enum',
      },
    },
    {
      fieldName: 'thresholdLowErrorEnabled',
      label: '低低限报警',
      component: 'Switch',
      dependencies: {
        triggerFields: ['valueKind'],
        disabled: (values) => values.valueKind === 'enum',
      },
    },
    {
      fieldName: 'thresholdLowWarningEnabled',
      label: '低限报警',
      component: 'Switch',
      dependencies: {
        triggerFields: ['valueKind'],
        disabled: (values) => values.valueKind === 'enum',
      },
    },
    {
      fieldName: 'thresholdHighWarningEnabled',
      label: '高限报警',
      component: 'Switch',
      dependencies: {
        triggerFields: ['valueKind'],
        disabled: (values) => values.valueKind === 'enum',
      },
    },
    {
      fieldName: 'thresholdHighErrorEnabled',
      label: '高高限报警',
      component: 'Switch',
      dependencies: {
        triggerFields: ['valueKind'],
        disabled: (values) => values.valueKind === 'enum',
      },
    },
    {
      fieldName: 'alarmEnabled',
      label: '启用报警',
      component: 'Switch',
      dependencies: {
        triggerFields: ['valueKind'],
        disabled: (values) => values.valueKind === 'enum',
      },
    },
    {
      fieldName: 'alarmDeadband',
      label: '报警死区',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 0,
        placeholder: '请输入报警死区',
      },
      dependencies: {
        triggerFields: ['valueKind'],
        disabled: (values) => values.valueKind === 'enum',
      },
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'deviceId',
      label: '所属设备',
      component: 'ApiSelect',
      componentProps: {
        api: getEnabledDeviceList,
        labelField: 'deviceName',
        valueField: 'id',
        placeholder: '请选择所属设备',
        allowClear: true,
      },
    },
    {
      fieldName: 'sensorId',
      label: '所属传感器',
      component: 'ApiSelect',
      componentProps: {
        api: getEnabledSensorList,
        labelField: 'sensorName',
        valueField: 'id',
        placeholder: '请选择所属传感器',
        allowClear: true,
      },
    },
    {
      fieldName: 'channelCode',
      label: '通道编码',
      component: 'Input',
      componentProps: {
        placeholder: '请输入通道编码',
        allowClear: true,
      },
    },
    {
      fieldName: 'channelName',
      label: '通道名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入通道名称',
        allowClear: true,
      },
    },
    {
      fieldName: 'valueKind',
      label: '值类别',
      component: 'Select',
      componentProps: {
        options: VALUE_KIND_OPTIONS,
        placeholder: '请选择值类别',
        allowClear: true,
      },
    },
    {
      fieldName: 'enabled',
      label: '是否启用',
      component: 'Select',
      componentProps: {
        options: ENABLED_OPTIONS,
        placeholder: '请选择是否启用',
        allowClear: true,
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(
  getDeviceName: (id?: number) => string,
  getSensorName: (id?: number) => string,
): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'id',
      title: '编号',
      width: 80,
    },
    {
      field: 'deviceId',
      title: '所属设备',
      minWidth: 160,
      formatter: ({ cellValue }) => getDeviceName(cellValue),
    },
    {
      field: 'sensorId',
      title: '所属传感器',
      minWidth: 170,
      formatter: ({ cellValue }) => getSensorName(cellValue),
    },
    {
      field: 'channelCode',
      title: '通道编码',
      minWidth: 140,
    },
    {
      field: 'channelName',
      title: '通道名称',
      minWidth: 150,
    },
    {
      field: 'unit',
      title: '单位',
      width: 90,
    },
    {
      field: 'valueKind',
      title: '值类别',
      width: 90,
      slots: { default: 'valueKind' },
    },
    {
      field: 'valueMin',
      title: '量程',
      minWidth: 130,
      formatter: ({ row }) =>
        `${row.valueMin ?? '-'} ~ ${row.valueMax ?? '-'}`,
    },
    {
      field: 'enabled',
      title: '启用',
      width: 80,
      formatter: ({ cellValue }) => (cellValue === 1 ? '启用' : '禁用'),
    },
    {
      field: 'alarmEnabled',
      title: '报警',
      width: 80,
      formatter: ({ cellValue }) => (cellValue === 1 ? '启用' : '禁用'),
    },
    {
      field: 'sort',
      title: '排序',
      width: 80,
    },
    {
      title: '操作',
      width: 200,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
