import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { z } from '#/adapter/form';
import { getDeviceSimpleList, getSensorTypeSimpleList } from '#/api/collector';

import { ENABLED_OPTIONS } from '../shared';

/** 按协议生成默认连接参数，避免把 HSI 串口字段套用到其他协议 */
export function getDefaultConnectionConfig(protocolCode?: string): string {
  switch (protocolCode?.toLowerCase()) {
    case 'hsi_rs485': {
      return JSON.stringify(
        {
          port: '/dev/ttyS4',
          baud: 9600,
          bytesize: 8,
          parity: 'none',
          stopbits: 1,
          rs485: true,
          rs485Flags: 0,
        },
        null,
        2,
      );
    }
    case 'modbus_rtu': {
      return JSON.stringify(
        {
          serialPort: '/dev/ttyS1',
          baudRate: 9600,
          dataBits: 8,
          stopBits: 1,
          parity: 'none',
          unitId: 1,
          interFrameDelayMs: 0,
        },
        null,
        2,
      );
    }
    case 'modbus_tcp': {
      return JSON.stringify(
        {
          host: '127.0.0.1',
          port: 502,
          unitId: 1,
        },
        null,
        2,
      );
    }
    default: {
      return '{}';
    }
  }
}

/** 查询启用的设备精简列表 */
function getEnabledDeviceList() {
  return getDeviceSimpleList({ enabled: 1 });
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
      fieldName: 'deviceId',
      label: '所属设备',
      component: 'ApiSelect',
      componentProps: {
        api: getEnabledDeviceList,
        labelField: 'deviceName',
        valueField: 'id',
        placeholder: '请选择所属设备',
      },
      rules: 'selectRequired',
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
      fieldName: 'sensorCode',
      label: '传感器编码',
      component: 'Input',
      componentProps: {
        placeholder: '请输入传感器编码，例如 cs1000-01',
      },
      rules: 'required',
    },
    {
      fieldName: 'sensorName',
      label: '传感器名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入传感器名称',
      },
      rules: 'required',
    },
    {
      fieldName: 'connectionConfig',
      label: '连接参数(JSON)',
      component: 'TextArea',
      componentProps: {
        rows: 6,
        placeholder: '请输入 JSON 对象',
      },
      rules: z.string().default('{}'),
    },
    {
      fieldName: 'autoGenerateChannels',
      label: '自动生成通道',
      component: 'Switch',
      rules: z.boolean().default(true),
      dependencies: {
        triggerFields: ['id'],
        show: (values) => !values.id,
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
      },
      rules: z.number().default(0),
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
      fieldName: 'sensorCode',
      label: '传感器编码',
      component: 'Input',
      componentProps: {
        placeholder: '请输入传感器编码',
        allowClear: true,
      },
    },
    {
      fieldName: 'sensorName',
      label: '传感器名称',
      component: 'Input',
      componentProps: {
        placeholder: '请输入传感器名称',
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
  getTypeName: (id?: number) => string,
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
      minWidth: 180,
      formatter: ({ cellValue }) => getDeviceName(cellValue),
    },
    {
      field: 'sensorTypeId',
      title: '传感器类型',
      minWidth: 200,
      formatter: ({ cellValue }) => getTypeName(cellValue),
    },
    {
      field: 'sensorCode',
      title: '传感器编码',
      minWidth: 140,
    },
    {
      field: 'sensorName',
      title: '传感器名称',
      minWidth: 160,
    },
    {
      field: 'protocolCode',
      title: '协议',
      minWidth: 120,
    },
    {
      field: 'channelCount',
      title: '通道数',
      width: 90,
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
      field: 'createTime',
      title: '创建时间',
      minWidth: 160,
      formatter: 'formatDateTime',
    },
    {
      title: '操作',
      width: 220,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
