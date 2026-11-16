import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { z } from '#/adapter/form';
import { getProtocolSimpleList } from '#/api/collector';

import { ENABLED_OPTIONS } from '../shared';

/** 查询启用的协议精简列表 */
function getEnabledProtocolList() {
  return getProtocolSimpleList({ enabled: 1 });
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
      component: 'Input',
      fieldName: 'typeCode',
      label: '类型编码',
      componentProps: {
        placeholder: '请输入类型编码，例如 hlb1400',
      },
      rules: 'required',
    },
    {
      component: 'Input',
      fieldName: 'typeName',
      label: '类型名称',
      componentProps: {
        placeholder: '请输入类型名称',
      },
      rules: 'required',
    },
    {
      fieldName: 'protocolCode',
      label: '关联协议',
      component: 'ApiSelect',
      componentProps: {
        api: getEnabledProtocolList,
        labelField: 'protocolName',
        valueField: 'protocolCode',
        placeholder: '请选择关联协议',
      },
      rules: 'selectRequired',
    },
    {
      fieldName: 'manufacturer',
      label: '制造商',
      component: 'Input',
      componentProps: {
        placeholder: '请输入制造商',
      },
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
      fieldName: 'enabled',
      label: '是否启用',
      component: 'RadioGroup',
      componentProps: {
        options: ENABLED_OPTIONS,
        buttonStyle: 'solid',
        optionType: 'button',
      },
      rules: z.number().default(1),
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
      fieldName: 'typeCode',
      label: '类型编码',
      component: 'Input',
      componentProps: {
        placeholder: '请输入类型编码',
        allowClear: true,
      },
    },
    {
      fieldName: 'typeName',
      label: '类型名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入类型名称',
        allowClear: true,
      },
    },
    {
      fieldName: 'protocolCode',
      label: '关联协议',
      component: 'ApiSelect',
      componentProps: {
        api: getEnabledProtocolList,
        labelField: 'protocolName',
        valueField: 'protocolCode',
        placeholder: '请选择关联协议',
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
      field: 'typeCode',
      title: '类型编码',
      minWidth: 140,
    },
    {
      field: 'typeName',
      title: '类型名称',
      minWidth: 160,
    },
    {
      field: 'protocolCode',
      title: '关联协议',
      minWidth: 140,
    },
    {
      field: 'manufacturer',
      title: '制造商',
      minWidth: 140,
    },
    {
      field: 'templateCount',
      title: '模板数',
      width: 90,
    },
    {
      field: 'sensorCount',
      title: '传感器数',
      width: 100,
    },
    {
      field: 'enabled',
      title: '启用',
      width: 80,
      formatter: ({ cellValue }) => (cellValue === 1 ? '启用' : '禁用'),
    },
    {
      field: 'sort',
      title: '排序',
      width: 80,
    },
    {
      field: 'description',
      title: '描述',
      minWidth: 200,
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
