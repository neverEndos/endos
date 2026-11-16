import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { z } from '#/adapter/form';
import { getSensorTypeSimpleList } from '#/api/collector';

import { ENABLED_OPTIONS, VALUE_KIND_OPTIONS } from '../shared';

/** 阈值比例只能在 0~1 之间 */
function ratioRule(defaultValue: number) {
  return z
    .number()
    .min(0, '阈值比例不能小于 0')
    .max(1, '阈值比例不能大于 1')
    .default(defaultValue);
}

/** 可清空的数值字段 */
function nullableNumberRule() {
  return z.number().nullish();
}

/** 查询启用的传感器类型精简列表 */
function getEnabledSensorTypeList() {
  return getSensorTypeSimpleList({ enabled: 1 });
}

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
      fieldName: 'sensorTypeId',
      label: '传感器类型',
      component: 'ApiSelect',
      componentProps: {
        api: getEnabledSensorTypeList,
        labelField: 'typeName',
        valueField: 'id',
        placeholder: '请选择传感器类型',
      },
      rules: 'selectRequired',
    },
    {
      fieldName: 'channelCode',
      label: '通道编码',
      component: 'Input',
      componentProps: {
        placeholder: '请输入通道编码，例如 iso_4',
      },
      rules: 'required',
    },
    {
      fieldName: 'channelName',
      label: '通道名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入通道名称',
      },
      rules: 'required',
    },
    {
      fieldName: 'valueKind',
      label: '值类别',
      component: 'RadioGroup',
      componentProps: {
        options: VALUE_KIND_OPTIONS,
        buttonStyle: 'solid',
        optionType: 'button',
      },
      rules: z.string().default('number'),
    },
    {
      fieldName: 'unit',
      label: '单位',
      component: 'Input',
      componentProps: {
        placeholder: '例如 µm、nS/m',
      },
    },
    {
      fieldName: 'icon',
      label: '图标',
      component: 'Input',
      componentProps: {
        placeholder: '例如 i-ep:filter',
      },
    },
    {
      fieldName: 'valueMin',
      label: '量程下限',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        placeholder: '可清空，表示未配置',
      },
      rules: nullableNumberRule(),
      dependencies: {
        triggerFields: ['valueMax'],
        rules: (values) =>
          nullableNumberRule().refine(
            (value) =>
              value === null ||
              value === undefined ||
              values.valueMax === null ||
              values.valueMax === undefined ||
              value <= values.valueMax,
            '量程下限不能大于量程上限',
          ),
      },
    },
    {
      fieldName: 'valueMax',
      label: '量程上限',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        placeholder: '可清空，表示未配置',
      },
      rules: nullableNumberRule(),
      dependencies: {
        triggerFields: ['valueMin'],
        rules: (values) =>
          nullableNumberRule().refine(
            (value) =>
              value === null ||
              value === undefined ||
              values.valueMin === null ||
              values.valueMin === undefined ||
              values.valueMin <= value,
            '量程上限不能小于量程下限',
          ),
      },
    },
    {
      fieldName: 'scale',
      label: '缩放系数',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        step: 0.001,
        placeholder: '原始值 × 系数',
      },
      rules: z.number().default(1),
    },
    {
      fieldName: 'valueOffset',
      label: '偏移量',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入偏移量',
      },
      rules: z.number().default(0),
    },
    {
      fieldName: 'decimalPlaces',
      label: '小数位',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 0,
        max: 6,
      },
      rules: z
        .number()
        .int('小数位必须为整数')
        .min(0, '小数位不能小于 0')
        .max(6, '小数位不能大于 6')
        .default(0),
    },
    {
      fieldName: 'thresholdLowErrorRatio',
      label: '低低限比例',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 0,
        max: 1,
        step: 0.01,
      },
      rules: ratioRule(0.1),
      dependencies: {
        triggerFields: ['valueKind'],
        disabled: (values) => values.valueKind === 'enum',
      },
    },
    {
      fieldName: 'thresholdLowWarningRatio',
      label: '低限比例',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 0,
        max: 1,
        step: 0.01,
      },
      rules: ratioRule(0.3),
      dependencies: {
        triggerFields: ['valueKind', 'thresholdLowErrorRatio'],
        disabled: (values) => values.valueKind === 'enum',
        rules: (values) =>
          ratioRule(0.3).refine(
            (value) =>
              values.thresholdLowErrorRatio === null ||
              values.thresholdLowErrorRatio === undefined ||
              value >= values.thresholdLowErrorRatio,
            '低限比例不能小于低低限比例',
          ),
      },
    },
    {
      fieldName: 'thresholdHighWarningRatio',
      label: '高限比例',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 0,
        max: 1,
        step: 0.01,
      },
      rules: ratioRule(0.7),
      dependencies: {
        triggerFields: ['valueKind', 'thresholdLowWarningRatio'],
        disabled: (values) => values.valueKind === 'enum',
        rules: (values) =>
          ratioRule(0.7).refine(
            (value) =>
              values.thresholdLowWarningRatio === null ||
              values.thresholdLowWarningRatio === undefined ||
              value >= values.thresholdLowWarningRatio,
            '高限比例不能小于低限比例',
          ),
      },
    },
    {
      fieldName: 'thresholdHighErrorRatio',
      label: '高高限比例',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 0,
        max: 1,
        step: 0.01,
      },
      rules: ratioRule(0.9),
      dependencies: {
        triggerFields: ['valueKind', 'thresholdHighWarningRatio'],
        disabled: (values) => values.valueKind === 'enum',
        rules: (values) =>
          ratioRule(0.9).refine(
            (value) =>
              values.thresholdHighWarningRatio === null ||
              values.thresholdHighWarningRatio === undefined ||
              value >= values.thresholdHighWarningRatio,
            '高高限比例不能小于高限比例',
          ),
      },
    },
    {
      fieldName: 'thresholdLowErrorEnabled',
      label: '低低限报警',
      component: 'Switch',
      rules: z.boolean().default(true),
      dependencies: {
        triggerFields: ['valueKind'],
        disabled: (values) => values.valueKind === 'enum',
      },
    },
    {
      fieldName: 'thresholdLowWarningEnabled',
      label: '低限报警',
      component: 'Switch',
      rules: z.boolean().default(true),
      dependencies: {
        triggerFields: ['valueKind'],
        disabled: (values) => values.valueKind === 'enum',
      },
    },
    {
      fieldName: 'thresholdHighWarningEnabled',
      label: '高限报警',
      component: 'Switch',
      rules: z.boolean().default(true),
      dependencies: {
        triggerFields: ['valueKind'],
        disabled: (values) => values.valueKind === 'enum',
      },
    },
    {
      fieldName: 'thresholdHighErrorEnabled',
      label: '高高限报警',
      component: 'Switch',
      rules: z.boolean().default(true),
      dependencies: {
        triggerFields: ['valueKind'],
        disabled: (values) => values.valueKind === 'enum',
      },
    },
    {
      fieldName: 'alarmEnabled',
      label: '启用报警',
      component: 'Switch',
      rules: z.boolean().default(true),
      dependencies: {
        triggerFields: ['valueKind'],
        disabled: (values) => values.valueKind === 'enum',
      },
    },
    {
      fieldName: 'channelConfig',
      label: '通道参数(JSON)',
      component: 'TextArea',
      componentProps: {
        rows: 8,
        placeholder: '请输入 JSON 对象，例如 {"address":97,"message":3}',
      },
      rules: z.string().default('{}'),
    },
    {
      fieldName: 'sort',
      label: '排序',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 0,
      },
      rules: z.number().default(0),
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
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'sensorTypeId',
      label: '传感器类型',
      component: 'ApiSelect',
      componentProps: {
        api: getEnabledSensorTypeList,
        labelField: 'typeName',
        valueField: 'id',
        placeholder: '请选择传感器类型',
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
  getSensorTypeName: (id?: number) => string,
): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'id',
      title: '编号',
      width: 80,
    },
    {
      field: 'sensorTypeId',
      title: '传感器类型',
      minWidth: 180,
      formatter: ({ cellValue }) => getSensorTypeName(cellValue),
    },
    {
      field: 'channelCode',
      title: '通道编码',
      minWidth: 140,
    },
    {
      field: 'channelName',
      title: '通道名称',
      minWidth: 160,
    },
    {
      field: 'valueKind',
      title: '值类别',
      width: 90,
      slots: { default: 'valueKind' },
    },
    {
      field: 'unit',
      title: '单位',
      width: 90,
    },
    {
      field: 'valueMin',
      title: '量程下限',
      width: 110,
      formatter: ({ cellValue }) => cellValue ?? '未配置',
    },
    {
      field: 'valueMax',
      title: '量程上限',
      width: 110,
      formatter: ({ cellValue }) => cellValue ?? '未配置',
    },
    {
      field: 'scale',
      title: '缩放',
      width: 80,
    },
    {
      field: 'valueOffset',
      title: '偏移',
      width: 80,
    },
    {
      field: 'decimalPlaces',
      title: '小数位',
      width: 80,
    },
    {
      field: 'thresholdLowErrorRatio',
      title: '阈值比例',
      minWidth: 160,
      formatter: ({ row }) =>
        `${row.thresholdLowErrorRatio ?? '-'} / ${row.thresholdLowWarningRatio ?? '-'} / ${row.thresholdHighWarningRatio ?? '-'} / ${row.thresholdHighErrorRatio ?? '-'}`,
    },
    {
      field: 'alarmEnabled',
      title: '报警',
      width: 80,
      formatter: ({ cellValue }) => (cellValue === 1 ? '启用' : '禁用'),
    },
    {
      field: 'enabled',
      title: '启用',
      width: 80,
      formatter: ({ cellValue }) => (cellValue === 1 ? '启用' : '禁用'),
    },
    {
      title: '操作',
      width: 200,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
