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
    /** 数据画像：normal 常规、fast 高频 */
    dataProfile?: string;
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
    dataProfile?: string;
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
    /** 后端序列化为 epoch millis */
    lastPollAt?: null | number;
    /** 采集器进程级数据库写入延迟，不是设备级指标 */
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
    /** 存储质量：0 正常、1 不确定、2 坏质量；无数据为空 */
    quality?: null | number;
    /** 采样超过三倍设备轮询周期时由接口派生 */
    stale: boolean;
    /** 后端序列化为 epoch millis */
    sampleTime?: null | number;
  }

  /** 最新值看板传感器分组 */
  export interface DashboardSensor {
    id: number;
    name: string;
    model?: null | string;
    channels: DashboardChannel[];
  }

  /** 最新值看板聚合结果；每次请求返回完整数据，不使用 304 */
  export interface DashboardLatest {
    device: DashboardDevice;
    sensors: DashboardSensor[];
    /** 后端序列化为 epoch millis */
    serverTime: number;
  }

  /** 采集运行摘要 · 进程心跳；从未收到心跳时为 null */
  export interface HealthProcess {
    instanceId?: null | string;
    /** starting / running / degraded / stopped */
    status?: null | string;
    /** 后端序列化为 epoch millis */
    heartbeatTime?: null | number;
    writeLagMs?: null | number;
    deviceTotal?: null | number;
    deviceOnline?: null | number;
    collectorVersion?: null | string;
  }

  /** 采集运行摘要 · 当前设备 */
  export interface HealthDevice {
    deviceId: number;
    deviceName: string;
    /** null 表示设备从未上报状态行（未知），不代表离线 */
    online?: boolean | null;
    /** 后端序列化为 epoch millis */
    lastPollAt?: null | number;
    /** 后端序列化为 epoch millis */
    lastSuccessAt?: null | number;
    lastLatencyMs?: null | number;
    lastError?: null | string;
    consecutiveFailures?: null | number;
    pollIntervalMs?: null | number;
  }

  /** 采集运行摘要 · 活动报警数量 */
  export interface HealthAlarm {
    activeCount: number;
    /** 0 表示无活动报警，级别仅 0/2/3 */
    highestLevel: number;
  }

  /** 采集运行摘要；全部只读 */
  export interface HealthSummary {
    /** 后端序列化为 epoch millis */
    serverTime: number;
    process?: HealthProcess | null;
    device: HealthDevice;
    alarm: HealthAlarm;
  }

  /** 采集运行详情 · 活动报警明细 */
  export interface HealthAlarmDetail {
    deviceId?: null | number;
    deviceName?: null | string;
    sensorId?: null | number;
    sensorName?: null | string;
    channelId?: null | number;
    channelName?: null | string;
    level?: null | number;
    thresholdType?: null | string;
    /** 后端序列化为 epoch millis */
    since?: null | number;
    lastValue?: null | number;
    lastThreshold?: null | number;
    /** 后端序列化为 epoch millis */
    lastSampleTime?: null | number;
  }

  /** 采集运行详情 · 聚合水位分组汇总 */
  export interface HealthAggregateSummary {
    aggType?: string;
    rowCount?: null | number;
    abnormalCount?: null | number;
    /** 预留字段，当前恒为 0，前端展示为 -- */
    maxRetryCount?: null | number;
    lastSuccessTime?: null | number;
    minWatermarkTime?: null | number;
    maxWatermarkTime?: null | number;
    /** false 表示尚未初始化，不能展示为「全部正常」 */
    initialized?: boolean;
  }

  /** 采集运行详情 · 聚合水位异常明细 */
  export interface HealthAggregateAbnormal {
    aggType?: null | string;
    channelId?: null | number;
    deviceId?: null | number;
    deviceName?: null | string;
    channelName?: null | string;
    status?: null | number;
    retryCount?: null | number;
    lastError?: null | string;
    watermarkTime?: null | number;
  }

  /** 采集运行详情 · 保留策略 */
  export interface HealthRetention {
    id?: number;
    dataClass?: string;
    displayName?: string;
    /** 0 表示永久保留 */
    retentionDays?: null | number;
    enabled?: boolean | null;
    lastRunAt?: null | number;
    lastRunResult?: null | string;
  }

  /** 采集运行详情 · raw 日分区预建情况 */
  export interface HealthRawPartition {
    /** ok / policy_missing / policy_duplicate / policy_invalid */
    status: string;
    /** 策略缺失或重复、非法时为 null，绝不套用默认值 */
    leadDays?: null | number;
    /** yyyy-MM-dd */
    expectedFrom?: null | string;
    /** yyyy-MM-dd；策略异常时为 null */
    expectedTo?: null | string;
    expectedCount?: null | number;
    existingCount?: null | number;
    missingDates?: null | string[];
  }

  /** 采集运行详情；摘要字段 + 报警 / 聚合 / 保留 / 分区明细，全部只读 */
  export interface HealthOverview extends HealthSummary {
    alarms?: HealthAlarmDetail[];
    /** 活动报警全量计数；alarms 仅返回前 20 条 */
    alarmTotal?: null | number;
    aggregate?: HealthAggregateSummary[];
    aggregateAbnormal?: HealthAggregateAbnormal[];
    retention?: HealthRetention[];
    rawPartition?: HealthRawPartition;
  }

  /** 趋势查询数据点 */
  export interface TrendPoint {
    /** 桶起点或抽样点时间（epoch millis） */
    t: number;
    /** 原始采样值；仅 kind=raw 的抽样序列返回 */
    value?: null | number;
    /** 桶内有效样本均值 */
    avg?: null | number;
    min?: null | number;
    max?: null | number;
    /** 桶内有效样本数（quality=0 且 value 非 NULL） */
    count?: null | number;
  }

  /** 趋势序列 */
  export interface TrendSeries {
    channelId: number;
    channelName?: string;
    unit?: null | string;
    dataProfile?: string;
    /** 数据源：raw|second|minute|hour|day */
    source: string;
    /** 实际桶宽（秒） */
    bucketSeconds: number;
    /** aggregate 聚合线、raw 原始点叠加 */
    kind: string;
    points: TrendPoint[];
  }

  /** 趋势查询结果 */
  export interface TrendSeriesResult {
    /** raw|second|minute|hour|day；多通道跨层时为 mixed */
    sourceSummary: string;
    series: TrendSeries[];
    /** 原始点叠加是否因超时降级；true 时 raw 序列为空，仅主聚合序列有效 */
    rawDegraded?: boolean;
    /** 原始点叠加降级时的中文警告信息 */
    rawWarning?: null | string;
  }

  /** 趋势查询参数 */
  export interface TrendSeriesParams {
    deviceId?: number;
    channelIds: number[];
    /** epoch millis；按 Asia/Shanghai 墙钟解释 */
    from: number;
    to: number;
    granularity?: string;
    includeRaw?: boolean;
  }

  /** 趋势选择器设备；仅趋势选择所需字段，由 collector:trend:query 保护 */
  export interface TrendDevice {
    id: number;
    deviceName: string;
  }

  /** 趋势选择器通道；仅启用通道的趋势选择字段，由 collector:trend:query 保护 */
  export interface TrendChannel {
    id: number;
    deviceId: number;
    channelName: string;
    unit?: null | string;
    dataProfile?: string;
    valueKind?: string;
  }

  /** 趋势选择器通道分页参数 */
  export interface TrendChannelPageParams {
    deviceId: number;
    keyword?: string;
    pageNo: number;
    pageSize: number;
  }

  /**
   * 通道历史数据粒度：raw / second / minute / hour / day。
   * 注意：趋势接口的粒度为 auto / raw / 1s / 1m / 1h / 1d，两套枚举不可混用。
   */
  export type HistoryGranularity =
    | 'day'
    | 'hour'
    | 'minute'
    | 'raw'
    | 'second';

  /** 通道历史数据分页参数 */
  export interface HistoryPageParams {
    channelId: number;
    /** epoch 毫秒，包含 */
    from: number;
    /** epoch 毫秒，不包含 */
    to: number;
    granularity: HistoryGranularity;
    /** 默认 1 */
    pageNo?: number;
    /** 默认 20；后端会把超过 100 的值收敛为 100 */
    pageSize?: number;
  }

  /** 通道历史数据导出参数；无分页参数 */
  export interface HistoryExportParams {
    channelId: number;
    /** epoch 毫秒，包含 */
    from: number;
    /** epoch 毫秒，不包含 */
    to: number;
    granularity: HistoryGranularity;
  }

  /**
   * 通道历史数据行：raw 与聚合层共用同一结构，互斥字段为 null。
   * raw 只填 value / rawValue / quality；聚合只填 sampleCount 等统计列。
   */
  export interface HistoryRow {
    /** 桶时间（epoch 毫秒）；raw 为采样时间，day 为当天 00:00 上海时间 */
    bucketTime: number;
    unit: string;
    channelName: string;
    /** raw 专有：工程值 */
    value: null | number;
    /** raw 专有：原始整数 */
    rawValue: null | number;
    /** raw 专有：0 正常 / 1 不确定 / 2 坏 */
    quality: null | number;
    /** 聚合专有：桶内全部采样数 */
    sampleCount: null | number;
    goodCount: null | number;
    badCount: null | number;
    valueAvg: null | number;
    valueMin: null | number;
    valueMax: null | number;
    valueFirst: null | number;
    valueLast: null | number;
  }

  /** 通道历史数据分页结果；JSON 字段为 list 与 total，不是 rows */
  export type HistoryPageResult = PageResult<HistoryRow>;

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

/** 查询设备最新值；每次返回完整看板，在线/陈旧状态由后端按当前时间重新派生 */
export function getDashboardLatest(deviceId: number) {
  return requestClient.get<CollectorApi.DashboardLatest>(
    '/collector/dashboard/latest',
    { params: { deviceId } },
  );
}

// ==================== 采集运行详情 ====================

/** 查询采集运行摘要；心跳 / 设备状态 / 活动报警数量，全部只读 */
export function getHealthSummary(deviceId: number) {
  return requestClient.get<CollectorApi.HealthSummary>(
    '/collector/health/summary',
    { params: { deviceId } },
  );
}

/** 查询采集运行详情；仅打开详情抽屉与手动刷新时调用，不跟随看板轮询 */
export function getHealthOverview(deviceId: number) {
  return requestClient.get<CollectorApi.HealthOverview>(
    '/collector/health/overview',
    { params: { deviceId } },
  );
}

// ==================== 历史趋势 ====================

/** 查询历史趋势；后端按数据画像与范围自动选源，并按点数上限放大桶宽 */
export function getTrendSeries(params: CollectorApi.TrendSeriesParams) {
  return requestClient.get<CollectorApi.TrendSeriesResult>(
    '/collector/trend/series',
    { params },
  );
}

/** 查询趋势选择器设备列表；仅返回启用设备，供仅有趋势权限的角色加载设备下拉 */
export function getTrendDevices() {
  return requestClient.get<CollectorApi.TrendDevice[]>(
    '/collector/trend/devices',
  );
}

/** 查询趋势选择器通道分页；仅返回指定设备下的启用通道，支持名称关键字模糊匹配 */
export function getTrendChannelPage(
  params: CollectorApi.TrendChannelPageParams,
) {
  return requestClient.get<PageResult<CollectorApi.TrendChannel>>(
    '/collector/trend/channels',
    { params },
  );
}

// ==================== 通道历史数据 ====================

/** 查询通道历史数据分页；固定按时间升序，pageSize 后端上限 100 */
export function getHistoryPage(params: CollectorApi.HistoryPageParams) {
  return requestClient.get<CollectorApi.HistoryPageResult>(
    '/collector/history/page',
    { params },
  );
}

/**
 * 导出通道历史 CSV；后端直接返回文件流（UTF-8 BOM）。
 * 范围超限、行数超限等业务错误以 JSON 抛出，由响应拦截器转成异常。
 */
export function exportHistory(params: CollectorApi.HistoryExportParams) {
  return requestClient.download<Blob>('/collector/history/export', { params });
}
