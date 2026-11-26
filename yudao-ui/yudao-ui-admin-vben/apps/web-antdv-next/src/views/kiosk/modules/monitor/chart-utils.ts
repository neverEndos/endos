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
}

/** X 轴刻度数量（首尾含） */
export const WAVE_X_TICK_COUNT = 5;

export const RANGE_SPECS: Record<TrendRange, RangeSpec> = {
  '1h': {
    key: '1h',
    label: '1 小时',
    minutes: 60,
  },
  '8h': {
    key: '8h',
    label: '8 小时',
    minutes: 480,
  },
  '24h': {
    key: '24h',
    label: '24 小时',
    minutes: 1440,
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

/** 波形有效点（时间 + 平均值），用于分段与抽稀后的绘制 */
export interface WavePoint {
  t: number;
  avg: number;
}

/** 断档时间跨度（含窗口首尾的空档） */
export interface TimeSpan {
  from: number;
  to: number;
}

export interface WaveSegmentedData {
  /** 已抽稀的分段序列，各段独立绘制（不跨段连线/填充） */
  segments: { points: WavePoint[] }[];
  /** 断档区间：相邻数据段之间，以及窗口首尾到首/末点 */
  gaps: TimeSpan[];
  /** 全量有效桶的极值（不受抽稀影响），供 Y 轴刻度使用 */
  max: number;
  min: number;
}

/** 波形显示目标点数（段内抽稀后的大致总数） */
export const WAVE_TARGET_POINTS = 120;

/** 相邻有效点时间差超过 1.5 个采样步长（max(桶宽, 采样周期)）即判定为断档 */
export const WAVE_GAP_BUCKET_RATIO = 1.5;

/** 分段数量上限：超限时合并最短段，避免慢采样通道产生海量细段 */
export const WAVE_MAX_SEGMENTS = 24;

/** 斜纹断档块数量上限（取最宽的若干块，其余只断线不标注） */
export const WAVE_MAX_HATCH_GAPS = 8;

/** 绘制斜纹与「无数据」标签的最小像素宽度 */
export const WAVE_HATCH_MIN_WIDTH = 32;

export interface HatchSpan {
  span: TimeSpan;
  width: number;
}

/**
 * 选出需要绘制斜纹的断档：宽度达到阈值才绘制（窄断档只断线），
 * 且最多保留最宽的 maxCount 块，保证 SVG 元素数有界。
 */
export function selectHatchSpans(
  spans: TimeSpan[],
  widthOf: (span: TimeSpan) => number,
  minWidth = WAVE_HATCH_MIN_WIDTH,
  maxCount = WAVE_MAX_HATCH_GAPS,
): HatchSpan[] {
  return spans
    .map((span) => ({ span, width: widthOf(span) }))
    .filter((hatch) => hatch.width >= minWidth)
    .sort((a, b) => b.width - a.width)
    .slice(0, maxCount);
}

/** 把段数压到上限：反复将时间跨度最短的段合并到相邻段（跨过的断档随之消失） */
function limitSegments(segments: WavePoint[][], max: number) {
  let list = segments;
  while (list.length > max) {
    let shortest = 0;
    let shortestSpan = Number.POSITIVE_INFINITY;
    for (let i = 0; i < list.length; i++) {
      const segment = list[i]!;
      const span = segment[segment.length - 1]!.t - segment[0]!.t;
      if (span < shortestSpan) {
        shortestSpan = span;
        shortest = i;
      }
    }
    // 优先并入前一段，首段则并入后一段，保持时间顺序
    const neighbor = shortest > 0 ? shortest - 1 : shortest + 1;
    const merged =
      shortest < neighbor
        ? [...list[shortest]!, ...list[neighbor]!]
        : [...list[neighbor]!, ...list[shortest]!];
    const next = list.filter((_, i) => i !== shortest && i !== neighbor);
    next.splice(Math.min(shortest, neighbor), 0, merged);
    list = next;
  }
  return list;
}

/** 段内抽稀：组内取时间中心与平均值；不足 2 点时按单点返回 */
function downsampleWaveSegment(points: WavePoint[], target: number) {
  if (target <= 1) return [points[0]!];
  if (points.length <= target) return points;
  const out: WavePoint[] = [];
  const chunk = points.length / target;
  for (let i = 0; i < target; i++) {
    const start = Math.floor(i * chunk);
    const end = Math.max(start + 1, Math.floor((i + 1) * chunk));
    const group = points.slice(start, end);
    out.push({
      avg: group.reduce((sum, p) => sum + p.avg, 0) / group.length,
      t: (group[0]!.t + group[group.length - 1]!.t) / 2,
    });
  }
  return out;
}

/**
 * 趋势桶序列分段 + 抽稀。规则：
 * 1. 相邻有效点时间差 > 1.5×max(桶宽, 采样周期) → 断档，各段独立；
 *    采样周期取自通道 pollIntervalMs（缺省回退设备轮询周期，再回退桶宽）；
 * 2. 窗口首尾无数据的部分同样计入断档；
 * 3. 段内按时间跨度按比例分配约 target 个点（组内平均），不跨段合并；
 * 4. 段数超过上限时合并最短段（优先并入前一段），保证元素数有界；
 * 5. min/max 取全量有效桶（抽稀前），保证 Y 轴与极值刻度不因抽稀变化。
 */
export function segmentWavePoints(
  points: CollectorApi.TrendPoint[] | undefined,
  bucketSeconds: number,
  from: number,
  to: number,
  target = WAVE_TARGET_POINTS,
  sampleIntervalMs?: null | number,
): WaveSegmentedData {
  const bucketMs =
    Number.isFinite(bucketSeconds) && bucketSeconds > 0
      ? bucketSeconds * 1000
      : 60_000;
  // 慢采样通道相邻点间隔天然大于桶宽，阈值需以采样周期为下限
  const sampleMs =
    sampleIntervalMs !== null &&
    sampleIntervalMs !== undefined &&
    Number.isFinite(sampleIntervalMs) &&
    sampleIntervalMs > 0
      ? sampleIntervalMs
      : 0;
  const threshold = Math.max(bucketMs, sampleMs) * WAVE_GAP_BUCKET_RATIO;
  const valid: WavePoint[] = (points ?? [])
    .filter(
      (p): p is CollectorApi.TrendPoint & { avg: number } =>
        p.avg !== null &&
        p.avg !== undefined &&
        Number.isFinite(p.avg) &&
        p.t >= from &&
        p.t <= to,
    )
    .map((p) => ({ avg: p.avg, t: p.t }))
    .sort((a, b) => a.t - b.t);
  if (valid.length === 0) {
    return {
      gaps: [{ from, to }],
      max: Number.NaN,
      min: Number.NaN,
      segments: [],
    };
  }

  // 按时间间隔切段
  let raw: WavePoint[][] = [];
  for (const point of valid) {
    const current = raw[raw.length - 1];
    const prev = current?.[current.length - 1];
    if (!current || (prev && point.t - prev.t > threshold)) {
      raw.push([point]);
    } else {
      current.push(point);
    }
  }
  // 段数超限时合并最短段，避免慢采样/抖动数据产生海量细段与斜纹块
  raw = limitSegments(raw, WAVE_MAX_SEGMENTS);

  // 断档：窗口首尾 + 相邻段之间；起点取上一个桶的结束时刻（桶本身视为有数据）
  const firstT = valid[0]!.t;
  const lastT = valid[valid.length - 1]!.t;
  const gaps: TimeSpan[] = [];
  if (firstT - from > threshold) gaps.push({ from, to: firstT });
  for (let i = 1; i < raw.length; i++) {
    const prevEnd = raw[i - 1]![raw[i - 1]!.length - 1]!.t + bucketMs;
    const nextStart = raw[i]![0]!.t;
    if (nextStart > prevEnd) gaps.push({ from: prevEnd, to: nextStart });
  }
  const trailingFrom = lastT + bucketMs;
  if (to > trailingFrom && to - lastT > threshold) {
    gaps.push({ from: trailingFrom, to });
  }

  // 按各段时间跨度分配总点数；单点段至少分到 1 个点
  const spans = raw.map((segment) =>
    Math.max(segment[segment.length - 1]!.t - segment[0]!.t, bucketMs),
  );
  const totalSpan = spans.reduce((sum, span) => sum + span, 0);
  const allocs = spans.map((span) => Math.max(1, Math.round((target * span) / totalSpan)));
  // 大量短段时每段至少 1 点会超出目标总数，从最长段逐步回收，保证总数接近 target
  let totalAlloc = allocs.reduce((sum, n) => sum + n, 0);
  while (totalAlloc > target) {
    let largest = -1;
    for (let i = 0; i < allocs.length; i++) {
      if (allocs[i]! > 1 && (largest < 0 || allocs[i]! > allocs[largest]!)) {
        largest = i;
      }
    }
    if (largest < 0) break;
    allocs[largest] = allocs[largest]! - 1;
    totalAlloc--;
  }
  const segments = raw.map((segment, index) => ({
    points: downsampleWaveSegment(segment, allocs[index]!),
  }));

  let min = Number.POSITIVE_INFINITY;
  let max = Number.NEGATIVE_INFINITY;
  for (const point of valid) {
    if (point.avg < min) min = point.avg;
    if (point.avg > max) max = point.avg;
  }
  return { gaps, max, min, segments };
}

/** 多序列断档取交集：只有所有序列都无数据的时段才是整体「无数据」区 */
export function intersectSpans(spans: TimeSpan[][]) {
  const [first, ...rest] = spans;
  if (!first) return [];
  let result = first;
  for (const other of rest) {
    const next: TimeSpan[] = [];
    for (const a of result) {
      for (const b of other) {
        const from = Math.max(a.from, b.from);
        const to = Math.min(a.to, b.to);
        if (to > from) next.push({ from, to });
      }
    }
    result = next;
  }
  return result;
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

const hourMinuteFormatter = new Intl.DateTimeFormat('en-GB', {
  hour: '2-digit',
  hourCycle: 'h23',
  minute: '2-digit',
  timeZone: TIME_ZONE,
});

/** X 轴刻度：HH:MM（1h/8h 窗口） */
export function formatAxisTime(ms: number) {
  return hourMinuteFormatter.format(new Date(ms));
}

/** X 轴刻度：MM-DD HH:MM（24h 跨天窗口，全部带日期避免歧义） */
export function formatAxisMoment(ms: number) {
  return `${dayFormatter.format(new Date(ms))} ${hourMinuteFormatter.format(new Date(ms))}`;
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
