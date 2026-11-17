import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/** 采集器模块 API 类型与请求方法，路径统一以 /admin-api 为上下文根。 */
export namespace CollectorApi {
  /** 采集协议 */
  export interface Protocol {
    id?: number;
    protocolCode: string;
    protocolName: string;
    transportType: string;
    driverKey: string;
    connectionSchema?: Record<string, any>;
    channelSchema?: Record<string, any>;
    protocolVersion?: string;
    enabled?: number;
    sort?: number;
    description?: string;
    createTime?: Date;
  }

  /** 采集协议精简信息 */
  export interface ProtocolSimple {
    id: number;
    protocolCode: string;
    protocolName: string;
    transportType: string;
    enabled?: number;
    sort?: number;
  }

  /** 采集设备 */
  export interface Device {
    id?: number;
    deviceCode: string;
    deviceName: string;
    enabled?: number;
    pollIntervalMs?: number;
    timeoutMs?: number;
    retries?: number;
    reconnectIntervalMs?: number;
    description?: string;
    sort?: number;
    createTime?: Date;
  }

  /** 采集设备精简信息 */
  export interface DeviceSimple {
    id: number;
    deviceCode: string;
    deviceName: string;
    enabled?: number;
  }

  /** 传感器类型 */
  export interface SensorType {
    id?: number;
    typeCode: string;
    typeName: string;
    protocolCode: string;
    manufacturer?: string;
    description?: string;
    enabled?: number;
    sort?: number;
    createTime?: Date;
    /** 该类型下的模板数量（后端分页批量统计） */
    templateCount?: number;
    /** 该类型下的传感器数量（后端分页批量统计） */
    sensorCount?: number;
  }

  /** 传感器类型精简信息 */
  export interface SensorTypeSimple {
    id: number;
    typeCode: string;
    typeName: string;
    protocolCode: string;
    enabled?: number;
  }

  /** 传感器参数模板 */
  export interface SensorTemplate {
    id?: number;
    sensorTypeId: number;
    channelCode: string;
    channelName: string;
    valueKind: string;
    unit?: string;
    icon?: string;
    valueMin?: null | number;
    valueMax?: null | number;
    scale?: number;
    valueOffset?: number;
    decimalPlaces?: number;
    thresholdLowErrorRatio?: number;
    thresholdLowWarningRatio?: number;
    thresholdHighWarningRatio?: number;
    thresholdHighErrorRatio?: number;
    alarmEnabled?: number;
    thresholdLowErrorEnabled?: number;
    thresholdLowWarningEnabled?: number;
    thresholdHighWarningEnabled?: number;
    thresholdHighErrorEnabled?: number;
    channelConfig?: Record<string, any>;
    sort?: number;
    enabled?: number;
    createTime?: Date;
  }

  /** 采集传感器 */
  export interface Sensor {
    id?: number;
    deviceId: number;
    sensorTypeId: number;
    sensorCode: string;
    sensorName: string;
    protocolCode?: string;
    connectionConfig?: Record<string, any>;
    enabled?: number;
    description?: string;
    sort?: number;
    createTime?: Date;
    /** 该传感器下的通道数量（后端分页批量统计） */
    channelCount?: number;
  }

  /** 采集传感器精简信息 */
  export interface SensorSimple {
    id: number;
    deviceId: number;
    sensorCode: string;
    sensorName: string;
    protocolCode?: string;
    enabled?: number;
  }

  /** 采集通道 */
  export interface Channel {
    id?: number;
    deviceId: number;
    sensorId: number;
    sensorTemplateId?: number;
    channelCode: string;
    channelName: string;
    enabled?: number;
    valueType?: string;
    valueKind?: string;
    channelConfig?: Record<string, any>;
    scale?: number;
    valueOffset?: number;
    unit?: string;
    icon?: string;
    decimalPlaces?: number;
    deadband?: number;
    valueMin?: number;
    valueMax?: number;
    thresholdLowError?: number;
    thresholdLowWarning?: number;
    thresholdHighWarning?: number;
    thresholdHighError?: number;
    thresholdLowErrorEnabled?: number;
    thresholdLowWarningEnabled?: number;
    thresholdHighWarningEnabled?: number;
    thresholdHighErrorEnabled?: number;
    alarmDeadband?: number;
    alarmEnabled?: number;
    pollIntervalMs?: number;
    description?: string;
    sort?: number;
    createTime?: Date;
  }

  /** 通道通用信息更新参数；不包含归属、值类别和阈值字段 */
  export interface ChannelUpdate {
    id: number;
    channelName?: string;
    enabled?: number;
    channelConfig?: Record<string, any>;
    scale?: number;
    valueOffset?: number;
    unit?: string;
    icon?: string;
    decimalPlaces?: number;
    pollIntervalMs?: number;
    description?: string;
    sort?: number;
  }

  /** 通道阈值更新参数 */
  export interface ChannelThresholdUpdate {
    id: number;
    valueMin?: null | number;
    valueMax?: null | number;
    thresholdLowError?: null | number;
    thresholdLowWarning?: null | number;
    thresholdHighWarning?: null | number;
    thresholdHighError?: null | number;
    thresholdLowErrorEnabled?: number;
    thresholdLowWarningEnabled?: number;
    thresholdHighWarningEnabled?: number;
    thresholdHighErrorEnabled?: number;
    alarmEnabled?: number;
    deadband?: number;
    alarmDeadband?: number;
  }

  /** 设备阈值矩阵通道 */
  export interface ThresholdMatrixChannel {
    id: number;
    channelCode: string;
    channelName: string;
    unit?: null | string;
    valueKind?: string;
    decimalPlaces?: number;
    valueMin?: null | number;
    valueMax?: null | number;
    thresholdLowError?: null | number;
    thresholdLowWarning?: null | number;
    thresholdHighWarning?: null | number;
    thresholdHighError?: null | number;
    thresholdLowErrorEnabled?: number;
    thresholdLowWarningEnabled?: number;
    thresholdHighWarningEnabled?: number;
    thresholdHighErrorEnabled?: number;
    alarmEnabled?: number;
    deadband?: null | number;
  }

  /** 设备阈值矩阵传感器分组 */
  export interface ThresholdMatrixSensor {
    id: number;
    name: string;
    model?: null | string;
    channels: ThresholdMatrixChannel[];
  }

  /** 设备阈值矩阵 */
  export interface DeviceThresholdMatrix {
    sensors: ThresholdMatrixSensor[];
  }

  /** 最新值看板设备状态 */
  export interface DashboardDevice {
    id: number;
    name: string;
    online: boolean;
    lastPollAt?: null | string;
    lastWriteLagMs?: null | number;
    consecutiveFailures: number;
    pollIntervalMs: number;
    staleOffline: boolean;
  }

  /** 最新值看板通道 */
  export interface DashboardChannel {
    id: number;
    name: string;
    unit?: null | string;
    valueKind: string;
    valueNum?: null | number;
    valueText?: null | string;
    quality?: null | number;
    sampleTime?: null | string;
  }

  /** 最新值看板传感器分组 */
  export interface DashboardSensor {
    id: number;
    name: string;
    model?: null | string;
    channels: DashboardChannel[];
  }

  /** 最新值看板聚合结果 */
  export interface DashboardLatest {
    device: DashboardDevice;
    sensors: DashboardSensor[];
    serverTime: string;
    versionToken: string;
  }

  /** 保留 HTTP 状态以处理真实 304 条件响应 */
  export interface DashboardLatestHttpResponse {
    status: number;
    data: {
      code: number;
      data: DashboardLatest;
      msg: string;
    };
  }

  /** 通道阈值批量更新项 */
  export interface ChannelThresholdBatchItem {
    channelId: number;
    thresholdLowError?: null | number;
    thresholdLowWarning?: null | number;
    thresholdHighWarning?: null | number;
    thresholdHighError?: null | number;
    thresholdLowErrorEnabled?: number;
    thresholdLowWarningEnabled?: number;
    thresholdHighWarningEnabled?: number;
    thresholdHighErrorEnabled?: number;
    alarmEnabled?: number;
    deadband?: number;
  }

  /** 通道阈值批量更新参数 */
  export interface ChannelThresholdBatchUpdate {
    deviceId: number;
    channels: ChannelThresholdBatchItem[];
  }

  /** 阈值比例预览参数 */
  export interface ThresholdPreviewParams {
    valueMin?: null | number;
    valueMax?: null | number;
    thresholdLowErrorRatio?: number;
    thresholdLowWarningRatio?: number;
    thresholdHighWarningRatio?: number;
    thresholdHighErrorRatio?: number;
  }

  /** 阈值比例预览结果 */
  export interface ThresholdPreview {
    valueMin?: number;
    valueMax?: number;
    thresholdLowError?: number;
    thresholdLowWarning?: number;
    thresholdHighWarning?: number;
    thresholdHighError?: number;
  }

  /** 生成通道预览项 */
  export interface GenerateChannelPreview {
    sensorTemplateId: number;
    channelCode: string;
    channelName: string;
    valueKind: string;
    unit?: string;
    icon?: string;
    valueMin?: null | number;
    valueMax?: null | number;
    scale?: number;
    valueOffset?: number;
    decimalPlaces?: number;
    thresholdLowError?: number;
    thresholdLowWarning?: number;
    thresholdHighWarning?: number;
    thresholdHighError?: number;
    thresholdLowErrorEnabled?: number;
    thresholdLowWarningEnabled?: number;
    thresholdHighWarningEnabled?: number;
    thresholdHighErrorEnabled?: number;
    alarmEnabled?: number;
    channelConfig?: Record<string, any>;
    skipped?: boolean;
  }

  /** 生成通道结果 */
  export interface GenerateChannelsResult {
    createdCount: number;
    skippedCount: number;
    channels: GenerateChannelPreview[];
  }

  /** 报警记录查询参数 */
  export interface AlarmPageParams {
    deviceId?: number;
    sensorId?: number;
    channelId?: number;
    level?: number;
    eventType?: string;
    triggeredAt?: string[];
  }

  /** 报警事件记录 */
  export interface Alarm {
    id: number;
    deviceId: number;
    deviceName?: string;
    sensorId: number;
    sensorName?: string;
    channelId: number;
    channelName?: string;
    alarmType: string;
    eventType: string;
    level: number;
    status: number;
    message?: string;
    triggerValue?: number;
    thresholdValue?: number;
    triggeredAt: Date;
    lastSeenAt?: Date;
    occurrenceCount?: number;
    clearedAt?: Date;
    clearReason?: string;
  }

  /** 通道当前报警状态 */
  export interface AlarmState {
    deviceId: number;
    deviceName?: string;
    sensorId: number;
    sensorName?: string;
    channelId: number;
    channelName?: string;
    level: number;
    thresholdType?: string;
    since?: Date;
    lastValue?: number;
    lastThreshold?: number;
    lastSampleTime?: Date;
    updatedTime?: Date;
  }

  /** 传感器保存参数 */
  export interface SensorSave {
    id?: number;
    deviceId: number;
    sensorTypeId: number;
    sensorCode: string;
    sensorName: string;
    connectionConfig?: Record<string, any>;
    enabled?: number;
    description?: string;
    sort?: number;
    autoGenerateChannels?: boolean;
  }
}

// ==================== 采集协议 ====================

/** 查询协议列表 */
export function getProtocolList(params?: { enabled?: number }) {
  return requestClient.get<CollectorApi.Protocol[]>(
    '/collector/protocol/list',
    { params },
  );
}

/** 查询协议详情 */
export function getProtocol(id: number) {
  return requestClient.get<CollectorApi.Protocol>(
    `/collector/protocol/get?id=${id}`,
  );
}

/** 查询协议精简列表 */
export function getProtocolSimpleList(params?: { enabled?: number }) {
  return requestClient.get<CollectorApi.ProtocolSimple[]>(
    '/collector/protocol/simple-list',
    { params },
  );
}

// ==================== 采集设备 ====================

/** 查询设备分页 */
export function getDevicePage(params: PageParam) {
  return requestClient.get<PageResult<CollectorApi.Device>>(
    '/collector/device/page',
    { params },
  );
}

/** 查询设备详情 */
export function getDevice(id: number) {
  return requestClient.get<CollectorApi.Device>(
    `/collector/device/get?id=${id}`,
  );
}

/** 查询设备精简列表 */
export function getDeviceSimpleList(params?: { enabled?: number }) {
  return requestClient.get<CollectorApi.DeviceSimple[]>(
    '/collector/device/simple-list',
    { params },
  );
}

/** 新增设备 */
export function createDevice(data: CollectorApi.Device) {
  return requestClient.post<number>('/collector/device/create', data);
}

/** 修改设备 */
export function updateDevice(data: CollectorApi.Device) {
  return requestClient.put('/collector/device/update', data);
}

/** 删除设备 */
export function deleteDevice(id: number) {
  return requestClient.delete(`/collector/device/delete?id=${id}`);
}

// ==================== 传感器类型 ====================

/** 查询传感器类型分页 */
export function getSensorTypePage(params: PageParam) {
  return requestClient.get<PageResult<CollectorApi.SensorType>>(
    '/collector/sensor-type/page',
    { params },
  );
}

/** 查询传感器类型详情 */
export function getSensorType(id: number) {
  return requestClient.get<CollectorApi.SensorType>(
    `/collector/sensor-type/get?id=${id}`,
  );
}

/** 查询传感器类型精简列表 */
export function getSensorTypeSimpleList(params?: { enabled?: number }) {
  return requestClient.get<CollectorApi.SensorTypeSimple[]>(
    '/collector/sensor-type/simple-list',
    { params },
  );
}

/** 新增传感器类型 */
export function createSensorType(data: CollectorApi.SensorType) {
  return requestClient.post<number>('/collector/sensor-type/create', data);
}

/** 修改传感器类型 */
export function updateSensorType(data: CollectorApi.SensorType) {
  return requestClient.put('/collector/sensor-type/update', data);
}

/** 删除传感器类型 */
export function deleteSensorType(id: number) {
  return requestClient.delete(`/collector/sensor-type/delete?id=${id}`);
}

// ==================== 传感器参数模板 ====================

/** 查询参数模板分页 */
export function getSensorTemplatePage(params: PageParam) {
  return requestClient.get<PageResult<CollectorApi.SensorTemplate>>(
    '/collector/sensor-template/page',
    { params },
  );
}

/** 查询参数模板详情 */
export function getSensorTemplate(id: number) {
  return requestClient.get<CollectorApi.SensorTemplate>(
    `/collector/sensor-template/get?id=${id}`,
  );
}

/** 查询指定传感器类型下的模板列表 */
export function getSensorTemplateListByType(params: {
  enabled?: number;
  sensorTypeId: number;
}) {
  return requestClient.get<CollectorApi.SensorTemplate[]>(
    '/collector/sensor-template/list-by-type',
    { params },
  );
}

/** 新增参数模板 */
export function createSensorTemplate(data: CollectorApi.SensorTemplate) {
  return requestClient.post<number>('/collector/sensor-template/create', data);
}

/** 修改参数模板 */
export function updateSensorTemplate(data: CollectorApi.SensorTemplate) {
  return requestClient.put('/collector/sensor-template/update', data);
}

/** 删除参数模板 */
export function deleteSensorTemplate(id: number) {
  return requestClient.delete(`/collector/sensor-template/delete?id=${id}`);
}

// ==================== 采集传感器 ====================

/** 查询传感器分页 */
export function getSensorPage(params: PageParam) {
  return requestClient.get<PageResult<CollectorApi.Sensor>>(
    '/collector/sensor/page',
    { params },
  );
}

/** 查询传感器详情 */
export function getSensor(id: number) {
  return requestClient.get<CollectorApi.Sensor>(
    `/collector/sensor/get?id=${id}`,
  );
}

/** 查询传感器精简列表 */
export function getSensorSimpleList(params?: {
  deviceId?: number;
  enabled?: number;
}) {
  return requestClient.get<CollectorApi.SensorSimple[]>(
    '/collector/sensor/simple-list',
    { params },
  );
}

/** 新增传感器 */
export function createSensor(data: CollectorApi.SensorSave) {
  return requestClient.post<number>('/collector/sensor/create', data);
}

/** 修改传感器 */
export function updateSensor(data: CollectorApi.SensorSave) {
  return requestClient.put('/collector/sensor/update', data);
}

/** 删除传感器 */
export function deleteSensor(id: number) {
  return requestClient.delete(`/collector/sensor/delete?id=${id}`);
}

/** 预览按传感器类型模板生成的通道 */
export function generateChannelPreview(sensorTypeId: number) {
  return requestClient.get<CollectorApi.GenerateChannelPreview[]>(
    `/collector/sensor/generate-channel-preview?sensorTypeId=${sensorTypeId}`,
  );
}

/** 为传感器生成通道 */
export function generateChannels(sensorId: number) {
  return requestClient.post<CollectorApi.GenerateChannelsResult>(
    `/collector/sensor/generate-channels?sensorId=${sensorId}`,
  );
}

// ==================== 采集通道 ====================

/** 查询通道分页 */
export function getChannelPage(params: PageParam) {
  return requestClient.get<PageResult<CollectorApi.Channel>>(
    '/collector/channel/page',
    { params },
  );
}

/** 查询通道详情 */
export function getChannel(id: number) {
  return requestClient.get<CollectorApi.Channel>(
    `/collector/channel/get?id=${id}`,
  );
}

/** 修改通道通用信息 */
export function updateChannel(data: CollectorApi.ChannelUpdate) {
  return requestClient.put('/collector/channel/update', data);
}

/** 查询设备下按传感器分组的通道阈值矩阵 */
export function getChannelListByDevice(deviceId: number) {
  return requestClient.get<CollectorApi.DeviceThresholdMatrix>(
    '/collector/channel/list-by-device',
    { params: { deviceId } },
  );
}

/** 修改通道阈值与报警配置 */
export function updateChannelThreshold(
  data: CollectorApi.ChannelThresholdUpdate,
) {
  return requestClient.put('/collector/channel/update-threshold', data);
}

/** 批量修改设备下通道的阈值与报警配置 */
export function updateChannelThresholdBatch(
  data: CollectorApi.ChannelThresholdBatchUpdate,
) {
  return requestClient.put<number>(
    '/collector/channel/update-threshold-batch',
    data,
  );
}

/** 按量程与比例预览四档阈值 */
export function previewThreshold(params: CollectorApi.ThresholdPreviewParams) {
  return requestClient.get<CollectorApi.ThresholdPreview>(
    '/collector/channel/preview-threshold',
    { params },
  );
}

/** 删除通道 */
export function deleteChannel(id: number) {
  return requestClient.delete(`/collector/channel/delete?id=${id}`);
}

// ==================== 采集报警 ====================

/** 查询报警记录分页 */
export function getAlarmPage(params: CollectorApi.AlarmPageParams & PageParam) {
  return requestClient.get<PageResult<CollectorApi.Alarm>>(
    '/collector/alarm/page',
    { params },
  );
}

/** 查询通道当前报警状态 */
export function getAlarmStateList(params?: { deviceId?: number }) {
  return requestClient.get<CollectorApi.AlarmState[]>(
    '/collector/alarm/state/list',
    { params },
  );
}

// ==================== 最新值看板 ====================

/** 查询设备最新值；携带版本令牌时由后端决定返回 200 全量或 304 空响应 */
export function getDashboardLatest(deviceId: number, versionToken?: string) {
  return requestClient.get<CollectorApi.DashboardLatestHttpResponse>(
    '/collector/dashboard/latest',
    {
      params: { deviceId },
      headers: versionToken
        ? { 'If-None-Match': `"${versionToken}"` }
        : undefined,
      responseReturn: 'raw',
      validateStatus: (status: number) =>
        (status >= 200 && status < 300) || status === 304,
    },
  );
}
