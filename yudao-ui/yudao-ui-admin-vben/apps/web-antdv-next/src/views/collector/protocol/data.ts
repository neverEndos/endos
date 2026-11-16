import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { ENABLED_OPTIONS } from '../shared';

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
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
      field: 'protocolCode',
      title: '协议码',
      minWidth: 140,
    },
    {
      field: 'protocolName',
      title: '协议名称',
      minWidth: 180,
    },
    {
      field: 'transportType',
      title: '传输类型',
      minWidth: 100,
    },
    {
      field: 'driverKey',
      title: '驱动标识',
      minWidth: 160,
    },
    {
      field: 'protocolVersion',
      title: '版本',
      minWidth: 100,
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
      minWidth: 240,
    },
    {
      title: '操作',
      width: 120,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
