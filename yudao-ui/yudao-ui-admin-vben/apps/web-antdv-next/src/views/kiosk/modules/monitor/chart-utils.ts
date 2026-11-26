/**
 * Kiosk 主界面图表与格式化工具。
 * 算法与视觉参数以 docs/kiosk-design/04-半透明科技风.html 原型为准。
 */

import type { CollectorApi } from '#/api/collector';

/** 数据分析六项聚合结果；时刻为极值所在桶的起点（epoch millis） */
export interface AnalysisStats {
  avg: number;
  max: number;
  maxT: number;
  min: number;
  minT: number;
  pp: number;
}

/** 从趋势桶序列聚合：最大/最小取桶极值，平均按桶内样本数加权 */
export function aggregatePoints(
  points: CollectorApi.TrendPoint[] | undefined,
): AnalysisStats | null {
  if (!points || points.length === 0) return null;
  let max = Number.NEGATIVE_INFINITY;
  let min = Number.POSITIVE_INFINITY;
  let maxT = 0;
  let minT = 0;
  let sum = 0;
  let count = 0;
  for (const point of points) {
    if (point.max !== null && point.max !== undefined && point.max > max) {
      max = point.max;
      maxT = point.t;
    }
    if (point.min !== null && point.min !== undefined && point.min < min) {
      min = point.min;
      minT = point.t;
    }
    if (
      point.avg !== null &&
      point.avg !== undefined &&
      point.count !== null &&
      point.count !== undefined &&
      point.count > 0
    ) {
      sum += point.avg * point.count;
      count += point.count;
    }
  }
  if (!Number.isFinite(max) || !Number.isFinite(min) || count === 0) {
    return null;
  }
  return { avg: sum / count, max, maxT, min, minT, pp: max - min };
}

/** 卡片固定色板：按排序后的通道顺序依次取色（取自原型 PARAMS 的 accent 序列） */
export const ACCENT_PALETTE = [
  '#34D399',
  '#FF7A1A',
  '#5AD2C8',
  '#6FF0BE',
  '#9A8CFF',
  '#FFA659',
  '#FF8FB1',
] as const;

/** 选中槽位曲线色：slot1 左轴薄荷绿、slot2 右轴琥珀（同单位也照常双轴） */
export const SLOT_COLORS = ['#6FF0BE', '#FFA659'] as const;

/** 波形时间范围 */
export type TrendRange = '1h' | '8h' | '24h';

export interface RangeSpec {
  key: TrendRange;
  /** 窗口时长（分钟） */
  minutes: number;
  /** 范围按钮与副标题文案 */
  label: string;
  /** X 轴 5 个刻度文案 */
  ticks: string[];
}

export const RANGE_SPECS: Record<TrendRange, RangeSpec> = {
  '1h': {
    key: '1h',
    label: '1 小时',
    minutes: 60,
    ticks: ['-60min', '-45min', '-30min', '-15min', '现在'],
  },
  '8h': {
    key: '8h',
    label: '8 小时',
    minutes: 480,
    ticks: ['-8h', '-6h', '-4h', '-2h', '现在'],
  },
  '24h': {
    key: '24h',
    label: '24 小时',
    minutes: 1440,
    ticks: ['-24h', '-18h', '-12h', '-6h', '现在'],
  },
};

export const RANGE_ORDER: TrendRange[] = ['1h', '8h', '24h'];

/** Y 轴取整刻度（1/2/5×10ⁿ 步长） */
export function niceScale(mn: number, mx: number, ticks = 4) {
  const span = mx - mn || Math.abs(mx) || 1;
  const raw = span / ticks;
  const mag = 10 ** Math.floor(Math.log10(raw));
  const n = raw / mag;
  const step = (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * mag;
  const lo = Math.floor(mn / step) * step;
  let hi = Math.ceil(mx / step) * step;
  // 全部值相同且恰好在刻度上时 hi 可能等于 lo，撑开一个步长避免除零
  if (hi === lo) hi = lo + step;
  return { hi, lo, step };
}

/** 刻度小数位：step>=1 取 0，否则按步长精度最多 3 位 */
export function scaleDecimals(step: number) {
  return step >= 1 ? 0 : Math.min(3, Math.ceil(-Math.log10(step)));
}

/** 数值格式化：0 位小数时千分位取整，否则按小数位定长 */
export function formatNumber(value: null | number | undefined, dec: number) {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return '--';
  }
  return dec === 0
    ? Math.round(value).toLocaleString('en-US')
    : value.toFixed(dec);
}

/** Catmull-Rom 转三次贝塞尔的平滑折线路径 */
export function linePath(pts: [number, number][]) {
  if (pts.length < 2) return '';
  const first = pts[0]!;
  let d = `M${first[0].toFixed(1)},${first[1].toFixed(1)}`;
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i - 1] ?? pts[i]!;
    const p1 = pts[i]!;
    const p2 = pts[i + 1]!;
    const p3 = pts[i + 2] ?? p2;
    const c1x = p1[0] + (p2[0] - p0[0]) / 6;
    const c1y = p1[1] + (p2[1] - p0[1]) / 6;
    const c2x = p2[0] - (p3[0] - p1[0]) / 6;
    const c2y = p2[1] - (p3[1] - p1[1]) / 6;
    d += ` C${c1x.toFixed(1)},${c1y.toFixed(1)} ${c2x.toFixed(1)},${c2y.toFixed(1)} ${p2[0].toFixed(1)},${p2[1].toFixed(1)}`;
  }
  return d;
}

/** 序列抽稀到 target 点：分段取平均，避免点过密导致渲染卡顿 */
export function downsample(values: number[], target = 30) {
  if (values.length <= target) return values;
  const out: number[] = [];
  const chunk = values.length / target;
  for (let i = 0; i < target; i++) {
    const from = Math.floor(i * chunk);
    const to = Math.max(from + 1, Math.floor((i + 1) * chunk));
    const seg = values.slice(from, to);
    out.push(seg.reduce((sum, v) => sum + v, 0) / seg.length);
  }
  return out;
}

const TIME_ZONE = 'Asia/Shanghai';

const timeFormatter = new Intl.DateTimeFormat('en-GB', {
  hour: '2-digit',
  hourCycle: 'h23',
  minute: '2-digit',
  second: '2-digit',
  timeZone: TIME_ZONE,
});
const dateFormatter = new Intl.DateTimeFormat('en-CA', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  timeZone: TIME_ZONE,
});
const dayFormatter = new Intl.DateTimeFormat('en-CA', {
  day: '2-digit',
  month: '2-digit',
  timeZone: TIME_ZONE,
});

/** 顶栏时钟：HH:MM:SS 与 YYYY-MM-DD */
export function formatClock(ms: number) {
  const d = new Date(ms);
  return { date: dateFormatter.format(d), time: timeFormatter.format(d) };
}

/** 极值时刻：当天 HH:MM:SS，跨天补 MM-DD 前缀 */
export function formatMoment(ms: number, nowMs: number) {
  const d = new Date(ms);
  const hm = timeFormatter.format(d);
  return dayFormatter.format(d) === dayFormatter.format(new Date(nowMs))
    ? hm
    : `${dayFormatter.format(d)} ${hm}`;
}

/** tooltip 相对时间：≤60 分钟显示 -45 min，更长显示 -8h / -8h 30m */
export function formatAgo(t: number, nowMs: number) {
  const mins = Math.round((nowMs - t) / 60_000);
  if (mins <= 0) return '现在';
  if (mins < 60) return `-${mins} min`;
  const hours = Math.floor(mins / 60);
  const rest = mins % 60;
  return rest === 0 ? `-${hours}h` : `-${hours}h ${rest}m`;
}
