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

/** 本地滚动趋势缓冲：轮询最新值即可积累 sparkline，不额外查询历史接口 */
export interface HistoryPoint {
  t: number;
  v: number;
}

export const HISTORY_CAPACITY = 24;

/**
 * 追加一个采样点；只有采样时间前进时才写入，避免同一数据在多次轮询中被重复计入。
 * 返回原数组表示无变化，调用方可跳过响应式替换。
 */
export function appendHistoryPoint(
  history: readonly HistoryPoint[] | undefined,
  point: HistoryPoint,
): readonly HistoryPoint[] | undefined {
  const last = history?.[history.length - 1];
  if (last && last.t === point.t) return history;
  const next = [...(history ?? []), point];
  if (next.length > HISTORY_CAPACITY) {
    next.splice(0, next.length - HISTORY_CAPACITY);
  }
  return next;
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
