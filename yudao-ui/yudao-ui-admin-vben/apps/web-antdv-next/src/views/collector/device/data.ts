import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { z } from '#/adapter/form';

import { ENABLED_OPTIONS } from '../shared';

/** 新增/修改的表单 */
export function useFormSchema(): VbenFormSchema[] {
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
      fieldName: 'deviceCode',
      label: '设备编码',
      componentProps: {
        placeholder: '请输入设备编码，例如 cs1000',
      },
      rules: 'required',
    },
    {
      component: 'Input',
      fieldName: 'deviceName',
      label: '设备名称',
      componentProps: {
        placeholder: '请输入设备名称',
      },
      rules: 'required',
    },
    {
      fieldName: 'enabled',
      label: '是否启用',
      component: 'RadioGroup',
      componentProps: {
        options: ENABLED_OPTIONS,
        buttonStyle: 'solid',
        optionType: 'button',
      },
      rules: z
        .number()
        .min(0, '启用开关只能是 0 或 1')
        .max(1, '启用开关只能是 0 或 1')
        .default(1),
    },
    {
      fieldName: 'pollIntervalMs',
      label: '轮询间隔(ms)',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入轮询间隔（100~86400000）',
      },
      rules: z
        .number()
        .min(100, '轮询间隔不能小于 100 毫秒')
        .max(86_400_000, '轮询间隔不能超过 86400000 毫秒')
        .default(5000),
    },
    {
      fieldName: 'timeoutMs',
      label: '超时时间(ms)',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入超时时间（10~60000）',
      },
      rules: z
        .number()
        .min(10, '超时时间不能小于 10 毫秒')
        .max(60_000, '超时时间不能超过 60000 毫秒')
        .default(1000),
    },
    {
      fieldName: 'retries',
      label: '重试次数',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入重试次数（0~10）',
      },
      rules: z
        .number()
        .int('重试次数必须为整数')
        .min(0, '重试次数不能小于 0')
        .max(10, '重试次数不能大于 10')
        .default(2),
    },
    {
      fieldName: 'reconnectIntervalMs',
      label: '重连间隔(ms)',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入重连间隔（100~86400000）',
      },
      rules: z
        .number()
        .min(100, '重连间隔不能小于 100 毫秒')
        .max(86_400_000, '重连间隔不能超过 86400000 毫秒')
        .default(5000),
    },
    {
      fieldName: 'description',
      label: '描述',
      component: 'TextArea',
      componentProps: {
        placeholder: '请输入描述',
      },
    },
    {
      fieldName: 'sort',
      label: '排序',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 0,
        placeholder: '请输入排序',
      },
      rules: z.number().default(0),
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'deviceCode',
      label: '设备编码',
      component: 'Input',
      componentProps: {
        placeholder: '请输入设备编码',
        allowClear: true,
      },
    },
    {
      fieldName: 'deviceName',
      label: '设备名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入设备名称',
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
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'id',
      title: '编号',
      width: 80,
    },
    {
      field: 'deviceCode',
      title: '设备编码',
      minWidth: 140,
    },
    {
      field: 'deviceName',
      title: '设备名称',
      minWidth: 160,
    },
    {
      field: 'enabled',
      title: '启用',
      width: 80,
      formatter: ({ cellValue }) => (cellValue === 1 ? '启用' : '禁用'),
    },
    {
      field: 'pollIntervalMs',
      title: '轮询间隔(ms)',
      width: 120,
    },
    {
      field: 'timeoutMs',
      title: '超时(ms)',
      width: 100,
    },
    {
      field: 'retries',
      title: '重试',
      width: 80,
    },
    {
      field: 'reconnectIntervalMs',
      title: '重连间隔(ms)',
      width: 120,
    },
    {
      field: 'sort',
      title: '排序',
      width: 80,
    },
    {
      field: 'description',
      title: '描述',
      minWidth: 180,
    },
    {
      field: 'createTime',
      title: '创建时间',
      minWidth: 160,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 160,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
