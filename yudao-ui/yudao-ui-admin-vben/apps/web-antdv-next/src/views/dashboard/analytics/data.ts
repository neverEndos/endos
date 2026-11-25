import type { CollectorApi } from '#/api/collector';

import {
  formatNumeric,
  formatShanghaiTime,
} from '#/views/collector/components/utils';

export { formatNumeric, formatShanghaiTime };

/** 大屏设计令牌：深空蓝黑底 + 青蓝主色，语义色固定，不在组件内散落十六进制 */
export const SCREEN_COLORS = {
  bg: '#070C18',
  bgSoft: '#0B1222',
  border: 'rgba(72, 104, 158, 0.24)',
  borderStrong: 'rgba(72, 104, 158, 0.44)',
  error: '#F43F5E',
  ink: '#E6EEF9',
  inkSoft: '#B7C6DD',
  muted: '#64748B',
  offline: '#64748B',
  okay: '#2DD4BF',
  primary: '#22D3EE',
  surface: 'rgba(13, 22, 40, 0.86)',
  surfaceSoft: 'rgba(17, 28, 50, 0.72)',
  warning: '#FBBF24',
} as const;

/**
 * 趋势序列配色：取自经过校验的深色分类色板前三槽。
 * 在 #0B1222 表面下以全配对方式通过色盲与正常视力分离度检查，
 * 因此主趋势图最多同时叠加 3 条通道；第 4 条请改用通道卡片/抽屉查看。
 */
export const TREND_SERIES_COLORS = ['#3987e5', '#d95926', '#199e70'] as const;

export const MAX_TREND_CHANNELS = TREND_SERIES_COLORS.length;

/** 时间窗预设：仅提供常用三档，避免板端频繁发起大范围趋势查询 */
export const TREND_WINDOWS = [
  { label: '15 分钟', seconds: 15 * 60, value: '15m' },
  { label: '1 小时', seconds: 60 * 60, value: '1h' },
  { label: '24 小时', seconds: 24 * 60 * 60, value: '24h' },
] as const;

export type TrendWindowValue = (typeof TREND_WINDOWS)[number]['value'];

/** 趋势接口数据源的中文描述 */
const TREND_SOURCE_LABELS: Record<string, string> = {
  day: '天聚合',
  hour: '小时聚合',
  minute: '分钟聚合',
  mixed: '混合数据源',
  raw: '原始采样',
  second: '秒聚合',
};

export function trendSourceLabel(source?: null | string): string {
  if (!source) return '未知数据源';
  return TREND_SOURCE_LABELS[source] ?? source;
}

/** 通道展示状态：坏质量优先于陈旧，陈旧优先于不确定 */
export type ChannelDisplayStatus =
  | 'bad'
  | 'missing'
  | 'normal'
  | 'stale'
  | 'uncertain'
  | 'unknown';

export interface ChannelStatusMeta {
  color: string;
  label: string;
  status: ChannelDisplayStatus;
}

export const CHANNEL_STATUS_META: Record<
  ChannelDisplayStatus,
  ChannelStatusMeta
> = {
  bad: { color: SCREEN_COLORS.error, label: '坏质量', status: 'bad' },
  missing: { color: SCREEN_COLORS.muted, label: '暂无数据', status: 'missing' },
  normal: { color: SCREEN_COLORS.okay, label: '正常', status: 'normal' },
  stale: { color: SCREEN_COLORS.muted, label: '陈旧', status: 'stale' },
  uncertain: {
    color: SCREEN_COLORS.warning,
    label: '不确定',
    status: 'uncertain',
  },
  unknown: { color: SCREEN_COLORS.muted, label: '质量未知', status: 'unknown' },
};

export interface AlarmLevelMeta {
  color: string;
  label: string;
}

export function alarmLevelMeta(level?: null | number): AlarmLevelMeta {
  if ((level ?? 0) >= 3) {
    return { color: SCREEN_COLORS.error, label: '异常' };
  }
  if ((level ?? 0) === 2) {
    return { color: SCREEN_COLORS.warning, label: '预警' };
  }
  return { color: SCREEN_COLORS.muted, label: '无报警' };
}

export function isFiniteNumber(value?: null | number): value is number {
  return value !== null && value !== undefined && Number.isFinite(value);
}

/** 数值通道展示文本；枚举通道使用 valueText，不允许出现 undefined / NaN */
export function channelValueText(
  channel?: CollectorApi.DashboardChannel,
): string {
  if (!channel) return '--';
  if (channel.valueKind === 'enum') {
    return channel.valueText?.trim() || '--';
  }
  return formatNumeric(channel.valueNum);
}

/** 通道是否陈旧：后端 stale 优先，另按设备轮询周期派生“超过三倍周期”判断 */
export function isChannelStale(
  channel: CollectorApi.DashboardChannel | undefined,
  latest: CollectorApi.DashboardLatest | undefined,
  now: number,
): boolean {
  if (!channel) return false;
  if (channel.stale) return true;
  if (!channel.sampleTime) return false;
  const interval = latest?.device.pollIntervalMs ?? 60_000;
  return now - channel.sampleTime > interval * 3;
}

export function resolveChannelStatus(
  channel: CollectorApi.DashboardChannel | undefined,
  latest: CollectorApi.DashboardLatest | undefined,
  now: number,
): ChannelStatusMeta {
  if (!channel) return CHANNEL_STATUS_META.missing;
  const hasSample = Boolean(channel.sampleTime);
  const hasValue =
    channel.valueKind === 'enum'
      ? Boolean(channel.valueText?.trim())
      : isFiniteNumber(channel.valueNum);
  if (!hasSample && !hasValue) return CHANNEL_STATUS_META.missing;
  if (channel.quality === 2) return CHANNEL_STATUS_META.bad;
  if (isChannelStale(channel, latest, now)) return CHANNEL_STATUS_META.stale;
  if (channel.quality === 1) return CHANNEL_STATUS_META.uncertain;
  if (channel.quality === 0) return CHANNEL_STATUS_META.normal;
  return CHANNEL_STATUS_META.unknown;
}

/** 设备下的全部通道上下文；用于通道矩阵、质量统计与趋势通道选择 */
export interface ChannelWithContext {
  channel: CollectorApi.DashboardChannel;
  device: CollectorApi.DeviceSimple;
  latest: CollectorApi.DashboardLatest;
  sensor: CollectorApi.DashboardSensor;
}

export interface DeviceLatestCard {
  device: CollectorApi.DeviceSimple;
  error?: string;
  latest?: CollectorApi.DashboardLatest;
}

/** 通道卡片点击后的详情上下文；阈值由父级按通道编号补齐 */
export interface ChannelSelection {
  channel: CollectorApi.DashboardChannel;
  device: CollectorApi.DeviceSimple;
  sensor: CollectorApi.DashboardSensor;
}

/** KPI 卡数据；available=false 表示权限不足，只能展示占位 */
export interface KpiTile {
  available: boolean;
  hint?: string;
  key: string;
  label: string;
  subtitle: string;
  tone?: 'error' | 'muted' | 'ok' | 'primary' | 'warn';
  unit?: string;
  value: string;
}

export interface QualityStats {
  bad: number;
  missing: number;
  normal: number;
  stale: number;
  total: number;
  uncertain: number;
}

export function computeQualityStats(
  devices: DeviceLatestCard[],
  now: number,
): QualityStats {
  const stats: QualityStats = {
    bad: 0,
    missing: 0,
    normal: 0,
    stale: 0,
    total: 0,
    uncertain: 0,
  };
  for (const item of devices) {
    if (!item.latest) continue;
    for (const sensor of item.latest.sensors) {
      for (const channel of sensor.channels) {
        stats.total += 1;
        const meta = resolveChannelStatus(channel, item.latest, now);
        switch (meta.status) {
          case 'bad': {
            stats.bad += 1;
            break;
          }
          case 'stale': {
            stats.stale += 1;
            break;
          }
          case 'uncertain': {
            stats.uncertain += 1;
            break;
          }
          case 'missing': {
            stats.missing += 1;
            break;
          }
          default: {
            stats.normal += 1;
          }
        }
      }
    }
  }
  return stats;
}

export interface AlarmLevelStats {
  level2: number;
  level3: number;
  total: number;
  unacknowledged: number;
}

export function computeAlarmLevelStats(
  alarms: CollectorApi.AlarmState[],
): AlarmLevelStats {
  const stats: AlarmLevelStats = {
    level2: 0,
    level3: 0,
    total: 0,
    unacknowledged: 0,
  };
  for (const alarm of alarms) {
    const level = alarm.level ?? 0;
    if (level <= 0) continue;
    stats.total += 1;
    if (level >= 3) {
      stats.level3 += 1;
    } else {
      stats.level2 += 1;
    }
    if (!alarm.acknowledged) {
      stats.unacknowledged += 1;
    }
  }
  return stats;
}

/** 本地滚动趋势缓冲：首屏用真实历史做种子，轮询只在尾部追加并滑动窗口 */
export interface HistoryPoint {
  t: number;
  v: number;
}

/** sparkline 默认容量（未按设备通道数自适应时的上限） */
export const HISTORY_CAPACITY = 60;

/** 种子历史固定取最近 30 分钟，与轮询缓冲拼接成滚动窗口 */
export const HISTORY_SEED_WINDOW_SECONDS = 30 * 60;

/**
 * 按设备数值通道数自适应 sparkline 容量：通道越多单卡越窄，
 * 减少点数以控制种子请求体积与 SVG 路径长度。上限 60、下限 40。
 */
export function historyCapacityFor(channelCount: number): number {
  const count = Math.max(1, Math.floor(channelCount));
  if (count <= 8) return 60;
  if (count <= 16) return 50;
  return 40;
}

/**
 * 追加一个采样点；只有采样时间前进时才写入，避免同一数据在多次轮询中被重复计入。
 * 超出容量时从头部移出，形成滑动窗口。返回原数组表示无变化。
 */
export function appendHistoryPoint(
  history: readonly HistoryPoint[] | undefined,
  point: HistoryPoint,
  capacity = HISTORY_CAPACITY,
): readonly HistoryPoint[] | undefined {
  const last = history?.[history.length - 1];
  if (last && point.t <= last.t) return history;
  const next = [...(history ?? []), point];
  if (next.length > capacity) {
    next.splice(0, next.length - capacity);
  }
  return next;
}

/**
 * 等距抽稀真实历史点：保留首尾并均匀取点，不做插值，
 * 保证 sparkline 仍只展示接口返回的真实采样值。
 */
export function downsampleHistoryPoints(
  points: readonly HistoryPoint[],
  max: number,
): HistoryPoint[] {
  if (points.length === 0 || max <= 0) return [];
  if (points.length <= max) return [...points];
  if (max === 1) return [points[points.length - 1]!];
  const result: HistoryPoint[] = [];
  const step = (points.length - 1) / (max - 1);
  for (let index = 0; index < max; index += 1) {
    const point = points[Math.round(index * step)];
    if (point) result.push(point);
  }
  // 抽稀后末点可能与倒数第二个重合，强制以最新真实点收尾
  const last = points[points.length - 1]!;
  if (result[result.length - 1]?.t !== last.t) {
    result[result.length - 1] = last;
  }
  return result;
}

/** 趋势序列按单位分组时的单位键；空单位归入“未指定单位” */
export function unitKey(unit?: null | string): string {
  return unit?.trim() || '未指定单位';
}

export function distinctTrendUnits(
  entries: ChannelWithContext[],
  channelIds: readonly number[],
): string[] {
  const units = new Set<string>();
  for (const entry of entries) {
    if (!channelIds.includes(entry.channel.id)) continue;
    units.add(unitKey(entry.channel.unit));
  }
  return [...units];
}

export interface ThresholdLine {
  color: string;
  label: string;
  value: number;
}

/**
 * 从通道阈值矩阵中提取启用的四档阈值；未启用或未配置的档位不画线。
 * 阈值线只用于展示，不参与坐标轴范围计算。
 */
export function thresholdLines(
  channel?: CollectorApi.ThresholdMatrixChannel | null,
): ThresholdLine[] {
  if (!channel) return [];
  const result: ThresholdLine[] = [];
  const push = (
    value: null | number | undefined,
    enabled: number | undefined,
    label: string,
    color: string,
  ) => {
    if (!enabled || !isFiniteNumber(value)) return;
    result.push({ color, label, value });
  };
  push(
    channel.thresholdLowError,
    channel.thresholdLowErrorEnabled,
    '低异常',
    SCREEN_COLORS.error,
  );
  push(
    channel.thresholdLowWarning,
    channel.thresholdLowWarningEnabled,
    '低预警',
    SCREEN_COLORS.warning,
  );
  push(
    channel.thresholdHighWarning,
    channel.thresholdHighWarningEnabled,
    '高预警',
    SCREEN_COLORS.warning,
  );
  push(
    channel.thresholdHighError,
    channel.thresholdHighErrorEnabled,
    '高异常',
    SCREEN_COLORS.error,
  );
  return result;
}

/** 通道阈值摘要文本：用于悬浮提示与抽屉，数据缺失时明确说明而不是留空 */
export function describeThresholds(
  channel?: CollectorApi.ThresholdMatrixChannel | null,
): string {
  const lines = thresholdLines(channel);
  if (!channel) return '阈值不可用（当前账号无采集通道权限）';
  if (lines.length === 0) return '未启用报警阈值';
  return lines
    .map((line) => `${line.label} ${formatNumeric(line.value)}`)
    .join(' / ');
}

/** 参与区间分析的单档阈值；direction 决定越限判定方向 */
export interface ThresholdRule {
  color: string;
  direction: 'high' | 'low';
  key: string;
  label: string;
  value: number;
}

/** 只提取“已启用且有值”的阈值档位；未启用的档位不参与越限统计 */
export function enabledThresholdRules(
  channel?: CollectorApi.ThresholdMatrixChannel | null,
): ThresholdRule[] {
  if (!channel) return [];
  const rules: ThresholdRule[] = [];
  const push = (
    value: null | number | undefined,
    enabled: number | undefined,
    key: string,
    label: string,
    direction: 'high' | 'low',
    color: string,
  ) => {
    if (!enabled || !isFiniteNumber(value)) return;
    rules.push({ color, direction, key, label, value });
  };
  push(
    channel.thresholdLowError,
    channel.thresholdLowErrorEnabled,
    'lowError',
    '低异常',
    'low',
    SCREEN_COLORS.error,
  );
  push(
    channel.thresholdLowWarning,
    channel.thresholdLowWarningEnabled,
    'lowWarning',
    '低预警',
    'low',
    SCREEN_COLORS.warning,
  );
  push(
    channel.thresholdHighWarning,
    channel.thresholdHighWarningEnabled,
    'highWarning',
    '高预警',
    'high',
    SCREEN_COLORS.warning,
  );
  push(
    channel.thresholdHighError,
    channel.thresholdHighErrorEnabled,
    'highError',
    '高异常',
    'high',
    SCREEN_COLORS.error,
  );
  return rules;
}

/** 区间分析使用的有效点：优先桶均值，同时保留桶内 min/max 用于峰谷时刻 */
interface AnalysisPoint {
  count?: number;
  max?: number;
  min?: number;
  t: number;
  v: number;
}

function validAnalysisPoints(
  series?: CollectorApi.TrendSeries,
): AnalysisPoint[] {
  if (!series) return [];
  const result: AnalysisPoint[] = [];
  for (const point of series.points) {
    const value = isFiniteNumber(point.avg) ? point.avg : point.value;
    if (!isFiniteNumber(value) || !isFiniteNumber(point.t)) continue;
    result.push({
      count: isFiniteNumber(point.count) ? point.count : undefined,
      max: isFiniteNumber(point.max) ? point.max : undefined,
      min: isFiniteNumber(point.min) ? point.min : undefined,
      t: point.t,
      v: value,
    });
  }
  return result.sort((left, right) => left.t - right.t);
}

/** 桶宽缺失时按相邻点时间间隔中位数估算，并在 UI 注明“估算” */
function estimateBucketSeconds(points: readonly AnalysisPoint[]): number {
  if (points.length < 2) return 1;
  const deltas: number[] = [];
  for (let index = 1; index < points.length; index += 1) {
    const delta = points[index]!.t - points[index - 1]!.t;
    if (delta > 0) deltas.push(delta);
  }
  if (deltas.length === 0) return 1;
  deltas.sort((left, right) => left - right);
  const median = deltas[Math.floor(deltas.length / 2)]!;
  return Math.max(1, Math.round(median / 1000));
}

export interface IntervalThresholdStat {
  buckets: number;
  color: string;
  direction: 'high' | 'low';
  key: string;
  label: string;
  /** 连续越限区段数；相邻越限桶只算一次进入 */
  occurrences: number;
  /** 越限时长占时间窗的比例（0~100） */
  percent: number;
  seconds: number;
  value: number;
}

export type IntervalTrend = '上升' | '下降' | '平稳' | '样本不足';

export interface IntervalAnalysis {
  amplitude: number;
  /** 桶宽（秒），优先取接口返回值 */
  bucketSeconds: number;
  /** true 表示桶宽由相邻点间隔估算，不是接口返回 */
  bucketEstimated: boolean;
  changeRate?: number;
  expectedBuckets: number;
  first: number;
  last: number;
  latestVsFirst: number;
  latestVsFirstPercent?: number;
  latestVsMean: number;
  latestVsMeanPercent?: number;
  maxTime: number;
  maxValue: number;
  mean: number;
  minTime: number;
  minValue: number;
  missingRate: number;
  thresholds: IntervalThresholdStat[];
  trend: IntervalTrend;
  validBuckets: number;
  validSamples?: number;
  windowSeconds: number;
}

/**
 * 区间聚合分析（纯函数）：
 * - 均值按桶内有效样本数（count）加权，count 缺失时退化为等权；
 * - 缺测率 = 1 - 有效桶数 / 期望桶数（期望桶数 = ⌈时间窗 / 桶宽⌉）；
 * - 越限按桶判定，时长 = 越限桶数 × 桶宽；连续越限区段计一次“越限次数”；
 * - 趋势用首三分之一与末三分之一均值比较，相对差异 ≥5% 才判定上升/下降。
 */
export function analyzeInterval(
  series: CollectorApi.TrendSeries | undefined,
  from: number,
  to: number,
  rules: readonly ThresholdRule[],
): IntervalAnalysis | undefined {
  const points = validAnalysisPoints(series);
  if (points.length === 0) return undefined;
  const firstPoint = points[0]!;
  const lastPoint = points[points.length - 1]!;
  const reportedBucket = isFiniteNumber(series?.bucketSeconds)
    ? series.bucketSeconds
    : 0;
  const bucketEstimated = reportedBucket <= 0;
  const bucketSeconds = bucketEstimated
    ? estimateBucketSeconds(points)
    : reportedBucket;
  const rawWindowSeconds =
    isFiniteNumber(from) && isFiniteNumber(to) && to > from
      ? (to - from) / 1000
      : (lastPoint.t - firstPoint.t) / 1000 + bucketSeconds;
  const windowSeconds = Math.max(bucketSeconds, rawWindowSeconds);
  const expectedBuckets = Math.max(
    points.length,
    Math.ceil(windowSeconds / bucketSeconds),
  );
  const missingRate = Math.max(
    0,
    Math.min(1, 1 - points.length / expectedBuckets),
  );

  let maxValue = Number.NEGATIVE_INFINITY;
  let maxTime = firstPoint.t;
  let minValue = Number.POSITIVE_INFINITY;
  let minTime = firstPoint.t;
  let weightedSum = 0;
  let weightTotal = 0;
  let validSamples = 0;
  let hasCount = false;
  for (const point of points) {
    const peak = point.max ?? point.v;
    if (peak > maxValue) {
      maxValue = peak;
      maxTime = point.t;
    }
    const trough = point.min ?? point.v;
    if (trough < minValue) {
      minValue = trough;
      minTime = point.t;
    }
    const weight = isFiniteNumber(point.count) && point.count > 0 ? point.count : 1;
    if (isFiniteNumber(point.count) && point.count > 0) {
      validSamples += point.count;
      hasCount = true;
    }
    weightedSum += point.v * weight;
    weightTotal += weight;
  }
  const mean = weightTotal > 0 ? weightedSum / weightTotal : firstPoint.v;
  const first = firstPoint.v;
  const last = lastPoint.v;
  const latestVsMean = last - mean;
  const latestVsMeanPercent =
    mean === 0 ? undefined : (latestVsMean / Math.abs(mean)) * 100;
  const latestVsFirst = last - first;
  const latestVsFirstPercent =
    first === 0 ? undefined : (latestVsFirst / Math.abs(first)) * 100;

  // 首/末三分之一均值比较；样本不足 6 个桶时不做趋势判定
  let trend: IntervalTrend = '样本不足';
  if (points.length >= 6) {
    const third = Math.floor(points.length / 3);
    const head = points.slice(0, third);
    const tail = points.slice(-third);
    const headMean =
      head.reduce((sum, point) => sum + point.v, 0) / head.length;
    const tailMean =
      tail.reduce((sum, point) => sum + point.v, 0) / tail.length;
    const scale = Math.max(Math.abs(headMean), Math.abs(tailMean));
    const relative = scale > 0 ? (tailMean - headMean) / scale : 0;
    if (relative >= 0.05) trend = '上升';
    else if (relative <= -0.05) trend = '下降';
    else trend = '平稳';
  }

  const thresholds: IntervalThresholdStat[] = rules.map((rule) => {
    let buckets = 0;
    let occurrences = 0;
    let inRun = false;
    for (const point of points) {
      const violated =
        rule.direction === 'high'
          ? point.v > rule.value
          : point.v < rule.value;
      if (violated) {
        buckets += 1;
        if (!inRun) occurrences += 1;
        inRun = true;
      } else {
        inRun = false;
      }
    }
    const seconds = buckets * bucketSeconds;
    return {
      buckets,
      color: rule.color,
      direction: rule.direction,
      key: rule.key,
      label: rule.label,
      occurrences,
      percent: windowSeconds > 0 ? (seconds / windowSeconds) * 100 : 0,
      seconds,
      value: rule.value,
    };
  });

  return {
    amplitude: maxValue - minValue,
    bucketEstimated,
    bucketSeconds,
    changeRate: latestVsFirstPercent,
    expectedBuckets,
    first,
    last,
    latestVsFirst,
    latestVsFirstPercent,
    latestVsMean,
    latestVsMeanPercent,
    maxTime,
    maxValue,
    mean,
    minTime,
    minValue,
    missingRate,
    thresholds,
    trend,
    validBuckets: points.length,
    validSamples: hasCount ? validSamples : undefined,
    windowSeconds,
  };
}

export interface EnumCodeShare {
  code: number;
  /**
   * 样本数：raw 口径为抽样原始点数；aggregate 口径为“整桶同码”桶的有效样本数
   * （count 加权，count 缺失时按 1 计）。
   */
  count: number;
  /** 占全部可确定样本的比例（0~100） */
  percent: number;
}

export interface EnumIntervalAnalysis {
  /** 统计口径：raw=抽样原始点（真实枚举码）；aggregate=聚合桶（只给可确定信息） */
  basis: 'aggregate' | 'raw';
  /** raw 口径为抽样步长（秒），aggregate 口径为聚合桶宽（秒） */
  bucketSeconds: number;
  /** 桶内发生过状态变化的桶数（仅 aggregate 口径有意义，raw 口径恒为 0） */
  changeBuckets: number;
  /** 区间内是否观察到状态变化（raw：相邻抽样点码变化；aggregate：存在 min≠max 的桶） */
  changeObserved: boolean;
  entries: EnumCodeShare[];
  /** 最近一次状态变化时刻（仅 raw 口径可近似给出，受抽样步长限制） */
  lastChangeTime?: number;
  /** 参与统计的样本总数（raw：全部抽样点；aggregate：整桶同码桶的有效样本数） */
  total: number;
}

/**
 * 从序列中提取整数枚举码样本点：枚举码在 raw 层以数值 value 存储，
 * 非整数说明不是合法枚举码（异常数据），直接跳过而不是四舍五入伪造一个码。
 */
function extractEnumSamplePoints(
  series?: CollectorApi.TrendSeries,
): Array<{ code: number; t: number }> {
  if (!series) return [];
  const points: Array<{ code: number; t: number }> = [];
  for (const point of series.points) {
    const value = isFiniteNumber(point.value) ? point.value : point.avg;
    if (!isFiniteNumber(value) || !Number.isInteger(value)) continue;
    if (!isFiniteNumber(point.t)) continue;
    points.push({ code: value, t: point.t });
  }
  return points.sort((left, right) => left.t - right.t);
}

/**
 * 枚举区间分布（raw 口径）：基于趋势接口的抽样原始点序列（kind=raw，
 * 每个步长桶取最早一个有效样本）统计真实枚举码，绝不使用桶均值近似。
 * 占比与最近切换时刻都是抽样口径：实际切换发生在所示时刻之前的一个步长内。
 */
export function analyzeEnumIntervalFromRaw(
  series?: CollectorApi.TrendSeries,
): EnumIntervalAnalysis | undefined {
  const points = extractEnumSamplePoints(series);
  if (points.length === 0) return undefined;
  const weights = new Map<number, number>();
  let lastChangeTime: number | undefined;
  let previousCode: number | undefined;
  for (const point of points) {
    weights.set(point.code, (weights.get(point.code) ?? 0) + 1);
    if (previousCode !== undefined && point.code !== previousCode) {
      lastChangeTime = point.t;
    }
    previousCode = point.code;
  }
  const total = points.length;
  const entries: EnumCodeShare[] = [...weights.entries()]
    .map(([code, count]) => ({
      code,
      count,
      percent: (count / total) * 100,
    }))
    .sort((left, right) => right.count - left.count || left.code - right.code);
  return {
    basis: 'raw',
    bucketSeconds: isFiniteNumber(series?.bucketSeconds)
      ? series.bucketSeconds
      : 1,
    changeBuckets: 0,
    changeObserved: lastChangeTime !== undefined,
    entries,
    lastChangeTime,
    total,
  };
}

/**
 * 枚举区间分布（aggregate 口径）：聚合桶只保留均值与 min/max，
 * 均值可能对应从未出现过的码，因此这里只对 min === max 的桶下结论
 * （整桶有效样本都是同一个码，可确定归入），min ≠ max 的桶只标记
 * “发生过状态变化”，不伪造码值与分布；最近切换时刻无法从聚合桶定位。
 */
export function analyzeEnumIntervalFromAggregate(
  series?: CollectorApi.TrendSeries,
): EnumIntervalAnalysis | undefined {
  const points = validAnalysisPoints(series);
  if (points.length === 0) return undefined;
  const weights = new Map<number, number>();
  let changeBuckets = 0;
  for (const point of points) {
    // min/max 是桶内全部有效样本的精确极值：min === max 才能确定整桶同码
    if (!isFiniteNumber(point.min) || !isFiniteNumber(point.max)) continue;
    if (point.min !== point.max) {
      changeBuckets += 1;
      continue;
    }
    if (!Number.isInteger(point.min)) continue;
    const weight = isFiniteNumber(point.count) && point.count > 0 ? point.count : 1;
    weights.set(point.min, (weights.get(point.min) ?? 0) + weight);
  }
  const total = [...weights.values()].reduce((sum, value) => sum + value, 0);
  if (total <= 0 && changeBuckets === 0) return undefined;
  const entries: EnumCodeShare[] =
    total > 0
      ? [...weights.entries()]
          .map(([code, count]) => ({
            code,
            count,
            percent: (count / total) * 100,
          }))
          .sort(
            (left, right) => right.count - left.count || left.code - right.code,
          )
      : [];
  return {
    basis: 'aggregate',
    bucketSeconds: isFiniteNumber(series?.bucketSeconds)
      ? series.bucketSeconds
      : estimateBucketSeconds(points),
    changeBuckets,
    changeObserved: changeBuckets > 0,
    entries,
    total,
  };
}

/** 越限时长等绝对秒数的中文展示；7 天区间可能出现天级时长 */
export function formatIntervalDuration(totalSeconds: number): string {
  if (!Number.isFinite(totalSeconds) || totalSeconds <= 0) return '0 秒';
  const seconds = Math.round(totalSeconds);
  const days = Math.floor(seconds / 86_400);
  const hours = Math.floor((seconds % 86_400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const secs = seconds % 60;
  if (days > 0) return `${days} 天 ${hours} 小时`;
  if (hours > 0) return `${hours} 小时 ${minutes} 分`;
  if (minutes > 0) return `${minutes} 分 ${secs} 秒`;
  return `${secs} 秒`;
}

/** 百分比展示：保留一位小数并去掉末尾 0；非法值展示 -- */
export function formatPercent(value?: null | number, digits = 1): string {
  if (!isFiniteNumber(value)) return '--';
  return `${Number.parseFloat(value.toFixed(digits))}%`;
}

/** 统一按 Asia/Shanghai 格式化短时间，禁止依赖浏览器本地时区 */
const SHANGHAI_HM = new Intl.DateTimeFormat('zh-CN', {
  hour: '2-digit',
  hourCycle: 'h23',
  minute: '2-digit',
  timeZone: 'Asia/Shanghai',
});

const SHANGHAI_HMS = new Intl.DateTimeFormat('zh-CN', {
  hour: '2-digit',
  hourCycle: 'h23',
  minute: '2-digit',
  second: '2-digit',
  timeZone: 'Asia/Shanghai',
});

const SHANGHAI_MDHM = new Intl.DateTimeFormat('zh-CN', {
  day: '2-digit',
  hour: '2-digit',
  hourCycle: 'h23',
  minute: '2-digit',
  month: '2-digit',
  timeZone: 'Asia/Shanghai',
});

const SHANGHAI_MD = new Intl.DateTimeFormat('zh-CN', {
  day: '2-digit',
  month: '2-digit',
  timeZone: 'Asia/Shanghai',
});

function partsOf(
  value: null | number | undefined,
  formatter: Intl.DateTimeFormat,
): Record<string, string> | undefined {
  if (!isFiniteNumber(value)) return undefined;
  const parts = formatter.formatToParts(new Date(value));
  const result: Record<string, string> = {};
  for (const part of parts) {
    if (part.type !== 'literal') result[part.type] = part.value;
  }
  return result;
}

export function formatShanghaiHm(value?: null | number): string {
  const parts = partsOf(value, SHANGHAI_HM);
  return parts ? `${parts.hour}:${parts.minute}` : '--';
}

export function formatShanghaiHms(value?: null | number): string {
  const parts = partsOf(value, SHANGHAI_HMS);
  return parts ? `${parts.hour}:${parts.minute}:${parts.second}` : '--';
}

export function formatShanghaiMdHm(value?: null | number): string {
  const parts = partsOf(value, SHANGHAI_MDHM);
  return parts
    ? `${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`
    : '--';
}

export function formatShanghaiMd(value?: null | number): string {
  const parts = partsOf(value, SHANGHAI_MD);
  return parts ? `${parts.month}-${parts.day}` : '--';
}

/** 按时间窗选择坐标轴标签格式，全部走上海时区 */
export function formatAxisTick(
  value: null | number | undefined,
  rangeSeconds: number,
): string {
  if (rangeSeconds <= 24 * 60 * 60) return formatShanghaiHm(value);
  if (rangeSeconds <= 7 * 24 * 60 * 60) return formatShanghaiMdHm(value);
  return formatShanghaiMd(value);
}

/** 十六进制颜色转 rgba，用于渐变描边与面积淡色，不引入额外颜色库 */
export function hexToRgba(hex: string, alpha: number): string {
  const normalized = hex.replace('#', '');
  if (normalized.length !== 6) return `rgba(34, 211, 238, ${alpha})`;
  const value = Number.parseInt(normalized, 16);
  const red = (value >> 16) & 255;
  const green = (value >> 8) & 255;
  const blue = value & 255;
  return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
}

/** ECharts 提示框会按 HTML 渲染系列名，必须转义后端配置文本 */
export function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

/** 数值点提取：聚合线优先 avg，原始/抽样线使用 value */
export interface ValuePoint {
  t: number;
  v: number;
}

export function extractValuePoints(
  series?: CollectorApi.TrendSeries,
): ValuePoint[] {
  if (!series) return [];
  const points: ValuePoint[] = [];
  for (const point of series.points) {
    const value = isFiniteNumber(point.avg) ? point.avg : point.value;
    if (!isFiniteNumber(value)) continue;
    points.push({ t: point.t, v: value });
  }
  return points;
}

/** 按连续有效点切分线段；缺失桶处断开，避免大图跨空洞直连伪造趋势 */
export function extractValueSegments(
  series?: CollectorApi.TrendSeries,
): ValuePoint[][] {
  if (!series) return [];
  const segments: ValuePoint[][] = [];
  let current: ValuePoint[] = [];
  for (const point of series.points) {
    const value = isFiniteNumber(point.avg) ? point.avg : point.value;
    if (!isFiniteNumber(value) || !isFiniteNumber(point.t)) {
      if (current.length > 0) segments.push(current);
      current = [];
      continue;
    }
    current.push({ t: point.t, v: value });
  }
  if (current.length > 0) segments.push(current);
  return segments;
}

/** 提取 min~max 波动带；用于单通道大图，缺失的桶不参与填充 */
export interface RangePoint {
  max: number;
  min: number;
  t: number;
}

export function extractRangePoints(
  series?: CollectorApi.TrendSeries,
): RangePoint[] {
  if (!series) return [];
  const points: RangePoint[] = [];
  for (const point of series.points) {
    if (!isFiniteNumber(point.min) || !isFiniteNumber(point.max)) continue;
    points.push({ max: point.max, min: point.min, t: point.t });
  }
  return points;
}

/**
 * 序列是否含有可绘制的有效点：后端对每个请求通道都会返回序列，
 * 时间窗内无数据时 points 为空；不能只看数组长度，否则会误判为成功。
 */
export function hasAnyPoint(
  series: readonly CollectorApi.TrendSeries[],
): boolean {
  return series.some((item) =>
    item.points.some((point) => {
      const value = isFiniteNumber(point.avg) ? point.avg : point.value;
      return isFiniteNumber(value);
    }),
  );
}

export function seriesName(series?: CollectorApi.TrendSeries): string {
  if (!series) return '--';
  return series.channelName?.trim() || `通道 ${series.channelId}`;
}

export interface ChartBounds {
  xMax: number;
  xMin: number;
  yMax: number;
  yMin: number;
}

/** 统一计算图表坐标域；阈值线不得参与，避免把坐标轴拉大 */
export function chartBounds(
  points: readonly ValuePoint[],
  ranges: readonly RangePoint[] = [],
  fallbackFrom?: number,
  fallbackTo?: number,
): ChartBounds | undefined {
  let xMin = Number.POSITIVE_INFINITY;
  let xMax = Number.NEGATIVE_INFINITY;
  let yMin = Number.POSITIVE_INFINITY;
  let yMax = Number.NEGATIVE_INFINITY;
  for (const point of points) {
    xMin = Math.min(xMin, point.t);
    xMax = Math.max(xMax, point.t);
    yMin = Math.min(yMin, point.v);
    yMax = Math.max(yMax, point.v);
  }
  for (const range of ranges) {
    yMin = Math.min(yMin, range.min);
    yMax = Math.max(yMax, range.max);
  }
  if (!isFiniteNumber(xMin) || !isFiniteNumber(xMax)) {
    if (!isFiniteNumber(fallbackFrom) || !isFiniteNumber(fallbackTo))
      return undefined;
    xMin = fallbackFrom;
    xMax = fallbackTo;
  }
  if (!isFiniteNumber(yMin) || !isFiniteNumber(yMax)) return undefined;
  if (xMax === xMin) {
    xMax = xMin + 1;
  }
  if (yMax === yMin) {
    // 单值序列也要有可见的纵向空间，否则折线贴边不可读
    const padding = Math.abs(yMin) * 0.05 || 1;
    yMin -= padding;
    yMax += padding;
  }
  return { xMax, xMin, yMax, yMin };
}

export interface PixelPoint {
  x: number;
  y: number;
}

export function mapPointsToPixels(
  points: readonly ValuePoint[],
  bounds: ChartBounds,
  box: { height: number; width: number },
): PixelPoint[] {
  const xSpan = bounds.xMax - bounds.xMin || 1;
  const ySpan = bounds.yMax - bounds.yMin || 1;
  return points.map((point) => ({
    x: ((point.t - bounds.xMin) / xSpan) * box.width,
    y: box.height - ((point.v - bounds.yMin) / ySpan) * box.height,
  }));
}

export function linePath(points: readonly PixelPoint[]): string {
  if (points.length === 0) return '';
  return points
    .map(
      (point, index) =>
        `${index === 0 ? 'M' : 'L'}${point.x.toFixed(2)} ${point.y.toFixed(2)}`,
    )
    .join(' ');
}

export function areaPath(
  points: readonly PixelPoint[],
  baselineY: number,
): string {
  if (points.length === 0) return '';
  const first = points[0]!;
  const last = points[points.length - 1]!;
  return `${linePath(points)} L${last.x.toFixed(2)} ${baselineY.toFixed(2)} L${first.x.toFixed(2)} ${baselineY.toFixed(2)} Z`;
}

export interface SparklineGeometry {
  area: string;
  last?: PixelPoint;
  max: number;
  min: number;
  path: string;
}

/** sparkline 几何数据：仅做缩放，不插值、不补点，保证曲线不伪造数据 */
export function sparklineGeometry(
  values: readonly number[],
  width = 100,
  height = 28,
  padding = 2,
): SparklineGeometry | undefined {
  if (values.length < 2) return undefined;
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  const innerWidth = Math.max(0, width - padding * 2);
  const innerHeight = Math.max(0, height - padding * 2);
  const points = values.map((value, index) => ({
    x: padding + (innerWidth * index) / (values.length - 1),
    y: padding + innerHeight - ((value - min) / span) * innerHeight,
  }));
  const path = linePath(points);
  return {
    area: areaPath(points, height - padding),
    last: points[points.length - 1],
    max,
    min,
    path,
  };
}

export function sparklinePath(
  values: readonly number[],
  width = 100,
  height = 28,
  padding = 2,
): string {
  return sparklineGeometry(values, width, height, padding)?.path ?? '';
}
