import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { z } from '#/adapter/form';
import { getDeviceSimpleList, getSensorSimpleList } from '#/api/collector';

import { ENABLED_OPTIONS, VALUE_KIND_OPTIONS } from '../shared';

/** 查询启用的设备精简列表 */
function getEnabledDeviceList() {
  return getDeviceSimpleList({ enabled: 1 });
}

/** 查询启用的传感器精简列表 */
function getEnabledSensorList() {
  return getSensorSimpleList({ enabled: 1 });
}

/** 可清空的数值字段 */
function nullableNumberRule() {
  return z.number().nullish();
}

const THRESHOLD_FIELDS = [
  'thresholdLowError',
  'thresholdLowWarning',
  'thresholdHighWarning',
  'thresholdHighError',
] as const;

/** 四档阈值允许空档，但所有非空值必须保持先后顺序 */
function thresholdOrderRule(
  values: Record<string, any>,
  currentField: (typeof THRESHOLD_FIELDS)[number],
) {
  return nullableNumberRule().refine((value) => {
    let previous: null | number | undefined;
    for (const field of THRESHOLD_FIELDS) {
      const current = field === currentField ? value : values[field];
      if (current === null || current === undefined) {
        continue;
      }
      if (previous !== null && previous !== undefined && current < previous) {
        return false;
      }
      previous = current;
    }
    return true;
  }, '四档阈值必须按低低限 ≤ 低限 ≤ 高限 ≤ 高高限排列（允许空档）');
}

/** 通道通用编辑表单；值类别、编码与归属均不允许修改 */
export function useChannelFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'channelCode',
      label: '通道编码',
      component: 'Input',
      componentProps: { disabled: true },
      help: '通道编码与所属设备、传感器不可修改',
    },
    {
      fieldName: 'channelName',
      label: '通道名称',
      component: 'Input',
      componentProps: { placeholder: '请输入通道名称', maxlength: 100 },
      rules: z
        .string()
        .trim()
        .min(1, '通道名称不能为空')
        .max(100, '通道名称长度不能超过 100 个字符'),
    },
    {
      fieldName: 'valueKind',
      label: '值类别',
      component: 'RadioGroup',
      componentProps: {
        options: VALUE_KIND_OPTIONS,
        buttonStyle: 'solid',
        optionType: 'button',
        disabled: true,
      },
      help: '值类别由参数模板决定，不能在通道编辑中修改',
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
      fieldName: 'unit',
      label: '单位',
      component: 'Input',
      componentProps: { placeholder: '例如 µm、nS/m', maxlength: 32 },
      rules: z.string().max(32, '单位长度不能超过 32 个字符').nullish(),
    },
    {
      fieldName: 'icon',
      label: '图标',
      component: 'Input',
      componentProps: { placeholder: '例如 i-ep:filter', maxlength: 64 },
      rules: z.string().max(64, '图标长度不能超过 64 个字符').nullish(),
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
      rules: z.number().finite('缩放系数必须是有限数').default(1),
    },
    {
      fieldName: 'valueOffset',
      label: '偏移量',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入偏移量',
      },
      rules: z.number().finite('偏移量必须是有限数').default(0),
    },
    {
      fieldName: 'decimalPlaces',
      label: '小数位',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0, max: 6 },
      rules: z
        .number()
        .int('小数位必须为整数')
        .min(0, '小数位不能小于 0')
        .max(6, '小数位不能大于 6')
        .default(0),
    },
    {
      fieldName: 'pollIntervalMs',
      label: '轮询间隔(ms)',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 100,
        max: 86_400_000,
        placeholder: '留空继承设备，范围 100~86400000',
      },
      rules: z
        .number()
        .min(100, '轮询间隔不能小于 100 毫秒')
        .max(86_400_000, '轮询间隔不能超过 86400000 毫秒')
        .nullish(),
    },
    {
      fieldName: 'channelConfig',
      label: '通道参数(JSON)',
      component: 'TextArea',
      componentProps: {
        rows: 8,
        placeholder: '请输入符合当前协议 Schema 的 JSON 对象',
      },
      rules: z.string().default('{}'),
    },
    {
      fieldName: 'description',
      label: '描述',
      component: 'TextArea',
      componentProps: { placeholder: '请输入描述', maxlength: 255 },
      rules: z.string().max(255, '描述长度不能超过 255 个字符').nullish(),
    },
    {
      fieldName: 'sort',
      label: '排序',
      component: 'InputNumber',
      componentProps: { class: '!w-full', min: 0 },
      rules: z
        .number()
        .int('排序必须为整数')
        .min(0, '排序不能小于 0')
        .default(0),
    },
  ];
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
      fieldName: 'thresholdLowError',
      label: '低低限阈值',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        placeholder: '请输入低低限阈值',
      },
      rules: nullableNumberRule(),
      dependencies: {
        triggerFields: ['valueKind', ...THRESHOLD_FIELDS],
        disabled: (values) => values.valueKind === 'enum',
        rules: (values) => thresholdOrderRule(values, 'thresholdLowError'),
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
      rules: nullableNumberRule(),
      dependencies: {
        triggerFields: ['valueKind', ...THRESHOLD_FIELDS],
        disabled: (values) => values.valueKind === 'enum',
        rules: (values) => thresholdOrderRule(values, 'thresholdLowWarning'),
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
      rules: nullableNumberRule(),
      dependencies: {
        triggerFields: ['valueKind', ...THRESHOLD_FIELDS],
        disabled: (values) => values.valueKind === 'enum',
        rules: (values) => thresholdOrderRule(values, 'thresholdHighWarning'),
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
      rules: nullableNumberRule(),
      dependencies: {
        triggerFields: ['valueKind', ...THRESHOLD_FIELDS],
        disabled: (values) => values.valueKind === 'enum',
        rules: (values) => thresholdOrderRule(values, 'thresholdHighError'),
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
      fieldName: 'deadband',
      label: '变化死区',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 0,
        placeholder: '请输入变化死区（非负数）',
      },
      rules: z.number().min(0, '变化死区不能小于 0').default(0),
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
        placeholder: '请输入报警死区（非负数）',
      },
      rules: z.number().min(0, '报警死区不能小于 0').default(0),
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
      formatter: ({ row }) => `${row.valueMin ?? '-'} ~ ${row.valueMax ?? '-'}`,
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
