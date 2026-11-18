import type { CollectorApi } from '#/api/collector';

/** 时间范围预设；custom 使用日期范围选择器 */
export const TREND_PRESETS = [
  { label: '10分钟', value: '10m', seconds: 10 * 60 },
  { label: '1小时', value: '1h', seconds: 60 * 60 },
  { label: '6小时', value: '6h', seconds: 6 * 60 * 60 },
  { label: '24小时', value: '24h', seconds: 24 * 60 * 60 },
  { label: '7天', value: '7d', seconds: 7 * 24 * 60 * 60 },
  { label: '30天', value: '30d', seconds: 30 * 24 * 60 * 60 },
  { label: '自定义', value: 'custom', seconds: 0 },
] as const;

export type TrendPresetValue = (typeof TREND_PRESETS)[number]['value'];

/** 粒度选项；maxSeconds 必须与后端 CollectorTrendGranularity 的上限一致 */
export const GRANULARITY_OPTIONS = [
  { label: '自动', value: 'auto', maxSeconds: 365 * 24 * 60 * 60 },
  { label: '原始', value: 'raw', maxSeconds: 10 * 60 },
  { label: '1秒', value: '1s', maxSeconds: 2 * 60 * 60 },
  { label: '1分钟', value: '1m', maxSeconds: 48 * 60 * 60 },
  { label: '5分钟', value: '5m', maxSeconds: 90 * 24 * 60 * 60 },
  { label: '1小时', value: '1h', maxSeconds: 90 * 24 * 60 * 60 },
  { label: '1天', value: '1d', maxSeconds: 365 * 24 * 60 * 60 },
] as const;

export type TrendGranularityValue =
  (typeof GRANULARITY_OPTIONS)[number]['value'];

export const MAX_CHANNELS_TOTAL = 8;
export const MAX_CHANNELS_PER_CHART = 6;
export const MAX_TOTAL_RANGE_SECONDS = 365 * 24 * 60 * 60;
export const MAX_RAW_OVERLAY_RANGE_SECONDS = 2 * 60 * 60;

/** 通道配色；按通道在请求列表中的顺序稳定分配，左右两图同一通道颜色一致 */
export const TREND_CHART_COLORS = [
  '#1677ff',
  '#fa8c16',
  '#13c2c2',
  '#52c41a',
  '#eb2f96',
  '#722ed1',
  '#f5222d',
  '#2f54eb',
];

const SOURCE_LABELS: Record<string, string> = {
  raw: '原始采样',
  second: '秒聚合',
  minute: '分钟聚合',
  hour: '小时聚合',
  day: '天聚合',
  mixed: '混合数据源',
};

export function isGranularityAvailable(
  value: TrendGranularityValue,
  rangeSeconds: number,
): boolean {
  const option = GRANULARITY_OPTIONS.find((item) => item.value === value);
  return option ? rangeSeconds <= option.maxSeconds : false;
}

export function granularityHint(rangeSeconds: number): string {
  const available = GRANULARITY_OPTIONS.filter((option) =>
    isGranularityAvailable(option.value, rangeSeconds),
  ).map((option) => option.label);
  return `当前范围可用：${available.join(' / ')}`;
}

/** 秒数格式化为紧凑的中文描述，用于预设和桶宽展示 */
export function formatSeconds(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds <= 0) {
    return '--';
  }
  if (seconds < 60) {
    return `${seconds} 秒`;
  }
  if (seconds < 3600) {
    const minutes = seconds / 60;
    return Number.isInteger(minutes) ? `${minutes} 分钟` : `${seconds} 秒`;
  }
  if (seconds < 86_400) {
    const hours = seconds / 3600;
    return Number.isInteger(hours)
      ? `${hours} 小时`
      : `${hours.toFixed(1)} 小时`;
  }
  const days = seconds / 86_400;
  return Number.isInteger(days) ? `${days} 天` : `${days.toFixed(1)} 天`;
}

export function sourceLabel(source?: null | string): string {
  if (!source) return '未知数据源';
  return SOURCE_LABELS[source] ?? source;
}

/** 顶部“当前数据源/桶宽/点数”摘要；主序列跨层时如实标注 mixed */
export function describeChartSource(
  series: CollectorApi.TrendSeries[],
  channelIds: number[],
): string {
  const mains = series.filter(
    (item) => item.kind !== 'raw' && channelIds.includes(item.channelId),
  );
  if (mains.length === 0) {
    return '暂无可展示的主序列';
  }
  const sources = [...new Set(mains.map((item) => item.source))];
  const buckets = [...new Set(mains.map((item) => item.bucketSeconds))];
  const points = mains.reduce((sum, item) => sum + item.points.length, 0);
  const sourceText =
    sources.length === 1 ? sourceLabel(sources[0]) : '混合数据源';
  const bucketText =
    buckets.length === 1 ? formatSeconds(buckets[0] ?? 0) : '多种桶宽';
  return `${sourceText} · 桶宽 ${bucketText} · 共 ${points} 点`;
}

/** 图表内出现的单位列表；空单位归入“未指定单位” */
export function distinctUnits(
  series: CollectorApi.TrendSeries[],
  channelIds: number[],
): string[] {
  const units = new Set<string>();
  for (const item of series) {
    if (!channelIds.includes(item.channelId)) continue;
    units.add(item.unit?.trim() || '未指定单位');
  }
  return [...units];
}

/** 为所有参与比较的通道建立稳定颜色映射 */
export function buildChannelColors(
  channelIds: number[],
): Record<number, string> {
  const colors: Record<number, string> = {};
  const unique = [...new Set(channelIds)];
  unique.forEach((channelId, index) => {
    colors[channelId] =
      TREND_CHART_COLORS[index % TREND_CHART_COLORS.length] ?? '#1677ff';
  });
  return colors;
}

export function hasAnyPoint(series: CollectorApi.TrendSeries[]): boolean {
  return series.some((item) => item.points.length > 0);
}

/** 数值展示：最多 3 位小数，剔除末尾 0 */
export function formatTrendValue(value?: null | number): string {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return '--';
  }
  return Number.parseFloat(value.toFixed(3)).toString();
}
