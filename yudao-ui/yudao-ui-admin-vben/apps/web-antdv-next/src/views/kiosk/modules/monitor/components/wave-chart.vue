<script lang="ts" setup>
import type { TimeSpan } from '../chart-utils';
import type { MonitorChannel } from '../use-monitor';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import {
  formatAgo,
  formatAxisMoment,
  formatAxisTime,
  formatNumber,
  intersectSpans,
  linePath,
  niceScale,
  RANGE_ORDER,
  RANGE_SPECS,
  scaleDecimals,
  segmentWavePoints,
  WAVE_X_TICK_COUNT,
} from '../chart-utils';
import { useMonitor } from '../use-monitor';

/**
 * 历史趋势：手写 SVG 双 Y 轴平滑曲线 + 十字线 tooltip。
 * slot1 左轴薄荷绿、slot2 右轴琥珀；X 轴为具体时钟刻度，按请求窗口的真实时间映射。
 * 无数据时段断线并以斜纹区标注；绘制点抽稀到约 120 点，聚合仍用全量桶。
 */
defineOptions({ name: 'KioskWaveChart' });

interface ChartPoint {
  avg: number;
  t: number;
  x: number;
  y: number;
}

interface ChartSegment {
  /** 抽稀后的绘制点 */
  points: ChartPoint[];
  /** 该段平滑曲线路径；单点段为空字符串 */
  line: string;
  /** 该段渐变填充路径；仅主序列且成线时非空 */
  area: string;
}

interface ChartSeries {
  channel: MonitorChannel;
  color: string;
  dec: number;
  segments: ChartSegment[];
  /** 断档区（时间），用于判断指针是否落在无数据时段 */
  gaps: TimeSpan[];
  scale: { hi: number; lo: number; step: number };
  /** 选中槽位（1 起），与卡片徽标/分析面板 Y1、Y2 口径一致 */
  slot: number;
}

/** 斜纹断档区（像素坐标）；宽度不足时不出标签 */
interface HatchGap {
  from: number;
  to: number;
  label: boolean;
  x1: number;
  x2: number;
}

/** 断档区是否宽到需要绘制斜纹与「无数据」标签（SVG 视觉像素） */
const HATCH_MIN_WIDTH = 32;

const PAD_L = 50;
const PAD_T = 22;
const PAD_B = 24;
/** 右侧轴区恒定预留宽度：单选/双选切换时绘图区宽度与 X 轴位置完全不变 */
const PAD_R = 44;

const store = useMonitor();

const wrapRef = ref<HTMLElement>();
const size = ref({ h: 278, w: 632 });
const hoverT = ref<null | number>(null);
const hoverX = ref(0);
let resizeObserver: ResizeObserver | undefined;

const model = computed(() => {
  const items = store.waveSeries.value;
  const firstWithData = items.find(
    (item) => item.data && item.data.points.length > 0,
  );
  if (!firstWithData?.data) return null;
  const { from, to } = firstWithData.data;
  const W = size.value.w;
  const H = size.value.h;
  // 仅保留窗口内有有效样本的序列；双轴判定、右轴单位、图例都以有数据序列为准，
  // 避免无数据通道（enum 通道或窗口内无样本）占住 slot 导致 series[1] 访问崩溃
  const drawable = items.flatMap((item, index) => {
    if (!item.data) return [];
    const segmented = segmentWavePoints(
      item.data.points,
      item.data.bucketSeconds,
      from,
      to,
    );
    return segmented.segments.length > 0
      ? [{ item, segmented, slot: index + 1 }]
      : [];
  });
  if (drawable.length === 0) return null;
  const dual = drawable.length > 1;
  // 右轴区恒定预留：dual 只控制右轴刻度/单位是否绘制，不再影响绘图区宽度
  const padR = PAD_R;
  const iw = W - PAD_L - padR;
  const ih = H - PAD_T - PAD_B;
  const bucketMs = firstWithData.data.bucketSeconds * 1000;
  const xOf = (t: number) => PAD_L + ((t - from) / (to - from)) * iw;

  const series: ChartSeries[] = drawable.map(({ item, segmented, slot }) => {
    // Y 轴刻度取全量有效桶极值（segmentWavePoints 未抽稀前的 min/max）
    const scale = niceScale(segmented.min, segmented.max, 4);
    const yOf = (v: number) =>
      PAD_T + (1 - (v - scale.lo) / (scale.hi - scale.lo)) * ih;
    const segments: ChartSegment[] = segmented.segments.map((segment) => {
      const points: ChartPoint[] = segment.points.map((p) => ({
        avg: p.avg,
        t: p.t,
        x: xOf(p.t),
        y: yOf(p.avg),
      }));
      const line = linePath(points.map((p) => [p.x, p.y]));
      const last = points[points.length - 1]!;
      // 单点段无法成线，仅保留点供单点绘制与十字线/tooltip 使用
      const area = line
        ? `${line} L${last.x.toFixed(1)},${(PAD_T + ih).toFixed(1)} L${points[0]!.x.toFixed(1)},${(PAD_T + ih).toFixed(1)} Z`
        : '';
      return { points, line, area };
    });
    return {
      channel: item.channel,
      color: item.color,
      dec: scaleDecimals(scale.step),
      gaps: segmented.gaps,
      segments,
      scale,
      slot,
    };
  });

  // 斜纹区取各序列断档的交集：只有所有曲线都无数据的时段才标注「无数据」
  const hatchSpans = intersectSpans(series.map((s) => s.gaps));
  const hatchGaps: HatchGap[] = hatchSpans.map((span) => {
    const x1 = xOf(span.from);
    const x2 = xOf(span.to);
    return {
      from: span.from,
      label: x2 - x1 >= HATCH_MIN_WIDTH,
      to: span.to,
      x1,
      x2,
    };
  });

  return {
    from,
    to,
    W,
    H,
    padR,
    iw,
    ih,
    bucketMs,
    series,
    dual,
    hatchGaps,
  };
});

/** 水平网格与左轴刻度（以左轴为准） */
const leftTicks = computed(() => {
  const m = model.value;
  const first = m?.series[0];
  if (!m || !first) return [];
  const rows =
    Math.round((first.scale.hi - first.scale.lo) / first.scale.step) || 4;
  return Array.from({ length: rows + 1 }, (_, i) => {
    const ratio = i / rows;
    return {
      y: PAD_T + m.ih * ratio,
      value: first.scale.hi - (first.scale.hi - first.scale.lo) * ratio,
      dec: first.dec,
      last: i === rows,
    };
  });
});

/** 右轴刻度（仅双选） */
const rightTicks = computed(() => {
  const m = model.value;
  const second = m?.series[1];
  if (!m || !second) return [];
  const rows =
    Math.round((second.scale.hi - second.scale.lo) / second.scale.step) || 4;
  return Array.from({ length: rows + 1 }, (_, i) => {
    const ratio = i / rows;
    return {
      y: PAD_T + m.ih * ratio,
      value: second.scale.hi - (second.scale.hi - second.scale.lo) * ratio,
      dec: second.dec,
    };
  });
});

/** X 轴时钟刻度：按请求窗口均布 5 个具体时间（24h 全部带 MM-DD 避免跨天歧义） */
const xTicks = computed(() => {
  const m = model.value;
  if (!m) return [];
  const formatter =
    store.range.value === '24h' ? formatAxisMoment : formatAxisTime;
  return Array.from({ length: WAVE_X_TICK_COUNT }, (_, i) => {
    const t =
      m.from + ((m.to - m.from) * i) / (WAVE_X_TICK_COUNT - 1);
    return {
      label: formatter(t),
      x: PAD_L + (m.iw * i) / (WAVE_X_TICK_COUNT - 1),
      anchor: (i === 0
        ? 'start'
        : i === WAVE_X_TICK_COUNT - 1
          ? 'end'
          : 'middle') as 'end' | 'middle' | 'start',
    };
  });
});

/** 图例与有数据序列对齐：无数据通道（enum/窗口内无样本）不显示，Y 编号沿用选中槽位 */
const legendItems = computed(() => {
  const m = model.value;
  if (!m) return [];
  return m.series.map((s) => ({
    color: s.color,
    name: s.channel.name,
    unit: s.channel.unit,
    slot: s.slot,
  }));
});

/** 底部提示：显示当前窗口的实际起止时钟时间（与 X 轴刻度口径一致，不用倒计时措辞） */
const axisHint = computed(() => {
  const m = model.value;
  const first = m?.series[0];
  const second = m?.series[1];
  // 左右轴单位之间用全角空格分隔（与原型一致）
  const gap = String.fromCharCode(0x3000);
  let windowText = `X 轴 · ${RANGE_SPECS[store.range.value].label}窗口`;
  if (m) {
    const formatter =
      store.range.value === '24h' ? formatAxisMoment : formatAxisTime;
    windowText = `X 轴 · ${formatter(m.from)} → ${formatter(m.to)}`;
  }
  if (first && second) {
    return `${windowText}｜左轴 ${first.channel.unit || '--'}${gap}右轴 ${second.channel.unit || '--'}`;
  }
  if (first) return `${windowText}｜Y 轴 ${first.channel.unit || '--'}`;
  return windowText;
});

const emptyText = computed(() => {
  if (store.cards.value.length === 0) return '暂无采集通道';
  if (!store.trendLoaded.value) return '正在加载趋势数据…';
  return '暂无趋势数据';
});

/** 时间是否落在该序列的断档区 */
function inGap(gaps: TimeSpan[], t: number) {
  return gaps.some((gap) => t > gap.from && t < gap.to);
}

/** 该序列全部分段中距离 t 最近的有效绘制点 */
function nearestPoint(series: ChartSeries, t: number) {
  let best: ChartPoint | undefined;
  for (const segment of series.segments) {
    for (const point of segment.points) {
      if (!best || Math.abs(point.t - t) < Math.abs(best.t - t)) best = point;
    }
  }
  return best;
}

/**
 * 十字线与 tooltip：横坐标在绘图区内连续取值；
 * 主序列落在断档区时只显示时间标题 +「无数据」，不再显示数值行。
 */
const hoverInfo = computed(() => {
  const m = model.value;
  const t = hoverT.value;
  if (!m || t === null) return null;
  const primary = m.series[0]!;
  const primaryGap = inGap(primary.gaps, t);
  const rows = primaryGap
    ? []
    : m.series.flatMap((s) => {
        // 该曲线此刻处于断档区 → 不显示数值行
        if (inGap(s.gaps, t)) return [];
        const best = nearestPoint(s, t);
        if (!best) return [];
        return [
          {
            color: s.color,
            dec: s.channel.decimalPlaces,
            unit: s.channel.unit,
            value: best.avg,
            y: best.y,
          },
        ];
      });
  const nowMs = Date.now();
  const title = m.to - t <= m.bucketMs ? '现在' : formatAgo(t, nowMs);
  const topY = rows.length > 0 ? Math.min(...rows.map((row) => row.y)) - 10 : PAD_T;
  return { rows, title, topY, x: hoverX.value };
});

const tipStyle = computed(() => {
  const info = hoverInfo.value;
  const m = model.value;
  if (!info || !m) return {};
  // 夹取基于恒定绘图区：左右各留约半个 tooltip 宽度，避免压住右轴刻度区
  const tipHalf = 40;
  const leftBound = PAD_L + tipHalf;
  const rightBound = Math.max(leftBound, PAD_L + m.iw - tipHalf);
  const left = Math.min(Math.max(info.x, leftBound), rightBound);
  return { left: `${left}px`, top: `${Math.max(14, info.topY)}px` };
});

function onPointerMove(event: PointerEvent) {
  const wrap = wrapRef.value;
  const m = model.value;
  if (!wrap || !m) return;
  const rect = wrap.getBoundingClientRect();
  // 画布被等比缩放，按 viewBox 与渲染尺寸的比例换算回布局坐标
  const px = ((event.clientX - rect.left) * m.W) / rect.width;
  // 限制在绘图区内，再换算为连续时间（断档区也需要可悬停）
  const x = Math.min(Math.max(px, PAD_L), PAD_L + m.iw);
  hoverX.value = x;
  hoverT.value = m.from + ((x - PAD_L) / m.iw) * (m.to - m.from);
}

function onPointerLeave() {
  hoverT.value = null;
}

onMounted(() => {
  const wrap = wrapRef.value;
  if (!wrap) return;
  const measure = () => {
    size.value = { w: wrap.clientWidth || 632, h: wrap.clientHeight || 278 };
  };
  measure();
  resizeObserver = new ResizeObserver(measure);
  resizeObserver.observe(wrap);
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
});
</script>

<template>
  <section class="kiosk-glass wave">
    <div class="wave-head">
      <h3>历史趋势</h3>
      <div class="legend">
        <span
          v-for="item in legendItems"
          :key="item.slot"
          class="it"
          :style="{ color: item.color }"
        >
          <i></i>Y{{ item.slot }} · {{ item.name }}<em>{{ item.unit }}</em>
        </span>
      </div>
    </div>

    <div
      ref="wrapRef"
      class="chart-wrap"
      @pointerleave="onPointerLeave"
      @pointermove="onPointerMove"
    >
      <svg
        v-if="model"
        class="wave-svg"
        :viewBox="`0 0 ${model.W} ${model.H}`"
      >
        <defs>
          <linearGradient id="wave-fade" x1="0" y1="0" x2="0" y2="1">
            <stop
              offset="0"
              :stop-color="model.series[0]!.color"
              stop-opacity="0.22"
            />
            <stop
              offset="1"
              :stop-color="model.series[0]!.color"
              stop-opacity="0"
            />
          </linearGradient>
          <filter id="wave-glow" x="-20%" y="-40%" width="140%" height="180%">
            <feGaussianBlur stdDeviation="2.6" result="b" />
            <feMerge>
              <feMergeNode in="b" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
          <!-- 无数据区斜纹：低亮度 45° 条纹，不抢曲线视觉 -->
          <pattern
            id="wave-hatch"
            width="7"
            height="7"
            patternTransform="rotate(45)"
            patternUnits="userSpaceOnUse"
          >
            <rect
              width="7"
              height="7"
              fill="rgba(255,255,255,0.02)"
            />
            <rect width="2.4" height="7" fill="rgba(255,255,255,0.05)" />
          </pattern>
        </defs>

        <!-- X 轴竖向网格 -->
        <line
          v-for="(tick, i) in xTicks"
          :key="`xg-${i}`"
          :x1="tick.x"
          :y1="PAD_T"
          :x2="tick.x"
          :y2="PAD_T + model.ih"
          stroke="rgba(255,255,255,0.03)"
          stroke-width="1"
        />
        <!-- 水平网格（以左轴为准） -->
        <line
          v-for="(tick, i) in leftTicks"
          :key="`yg-${i}`"
          :x1="PAD_L"
          :y1="tick.y"
          :x2="model.W - model.padR"
          :y2="tick.y"
          :stroke="`rgba(255,255,255,${tick.last ? 0.12 : 0.05})`"
          stroke-width="1"
        />
        <!-- 无数据时段：斜纹块 + 「无数据」标注（宽度不足的断档仅断线） -->
        <g v-for="(gap, i) in model.hatchGaps" :key="`gap-${i}`">
          <rect
            :x="gap.x1"
            :y="PAD_T"
            :width="Math.max(0, gap.x2 - gap.x1)"
            :height="model.ih"
            fill="url(#wave-hatch)"
          />
          <text
            v-if="gap.label"
            :x="(gap.x1 + gap.x2) / 2"
            :y="PAD_T + model.ih / 2 + 3"
            text-anchor="middle"
            font-size="8.5"
            fill="#5E6B78"
            letter-spacing="0.6"
          >
            无数据
          </text>
        </g>
        <!-- 左轴刻度 -->
        <text
          v-for="(tick, i) in leftTicks"
          :key="`yl-${i}`"
          :x="PAD_L - 8"
          :y="tick.y + 3"
          text-anchor="end"
          font-size="8.5"
          fill="#66737F"
        >
          {{ formatNumber(tick.value, tick.dec) }}
        </text>
        <!-- 右轴刻度（仅双选） -->
        <template v-if="model.dual">
          <template v-for="(tick, i) in rightTicks" :key="`yr-${i}`">
            <line
              :x1="model.W - model.padR"
              :y1="tick.y"
              :x2="model.W - model.padR + 4"
              :y2="tick.y"
              stroke="rgba(255,166,89,.45)"
              stroke-width="1"
            />
            <text
              :x="model.W - 6"
              :y="tick.y + 3"
              text-anchor="end"
              font-size="8.5"
              fill="#B08A5C"
            >
              {{ formatNumber(tick.value, tick.dec) }}
            </text>
          </template>
        </template>
        <!-- X 轴刻度 -->
        <text
          v-for="(tick, i) in xTicks"
          :key="`xl-${i}`"
          :x="tick.x"
          :y="model.H - 7"
          :text-anchor="tick.anchor"
          font-size="8.5"
          fill="#5E6B78"
        >
          {{ tick.label }}
        </text>
        <!-- 轴单位 -->
        <text
          :x="PAD_L"
          :y="PAD_T - 9"
          text-anchor="end"
          font-size="8.5"
          fill="#8593A1"
          letter-spacing="0.3"
        >
          {{ model.series[0]!.channel.unit }}
        </text>
        <text
          v-if="model.dual"
          :x="model.W - 6"
          :y="PAD_T - 9"
          text-anchor="end"
          font-size="8.5"
          fill="#B08A5C"
          letter-spacing="0.3"
        >
          {{ model.series[1]!.channel.unit }}
        </text>

        <!-- 曲线：Y2 在下、Y1 在上；各数据段独立绘制，断档不连线、不填充 -->
        <template v-if="model.series[1]">
          <template
            v-for="(segment, gi) in model.series[1]!.segments"
            :key="`s2-${gi}`"
          >
            <path
              v-if="segment.line"
              :d="segment.line"
              fill="none"
              :stroke="model.series[1]!.color"
              stroke-width="1.7"
              stroke-linecap="round"
              filter="url(#wave-glow)"
            />
            <circle
              v-else
              :cx="segment.points[0]!.x"
              :cy="segment.points[0]!.y"
              r="2.4"
              :fill="model.series[1]!.color"
              stroke="#0A0F14"
              stroke-width="1.2"
            />
          </template>
        </template>
        <template
          v-for="(segment, gi) in model.series[0]!.segments"
          :key="`s1-${gi}`"
        >
          <path v-if="segment.area" :d="segment.area" fill="url(#wave-fade)" />
          <path
            v-if="segment.line"
            :d="segment.line"
            fill="none"
            :stroke="model.series[0]!.color"
            stroke-width="1.9"
            stroke-linecap="round"
            filter="url(#wave-glow)"
          />
          <circle
            v-else
            :cx="segment.points[0]!.x"
            :cy="segment.points[0]!.y"
            r="2.6"
            :fill="model.series[0]!.color"
            stroke="#0A0F14"
            stroke-width="1.2"
          />
        </template>

        <!-- 十字线与高亮点 -->
        <template v-if="hoverInfo">
          <line
            :x1="hoverInfo.x"
            :y1="PAD_T"
            :x2="hoverInfo.x"
            :y2="PAD_T + model.ih"
            stroke="rgba(255,255,255,.22)"
            stroke-width="1"
            stroke-dasharray="3 3"
          />
          <circle
            v-for="(row, i) in hoverInfo.rows"
            :key="`hd-${i}`"
            :cx="hoverInfo.x"
            :cy="row.y"
            r="3.2"
            :fill="row.color"
            stroke="#0A0F14"
            stroke-width="1.4"
          />
        </template>
      </svg>
      <div v-else class="chart-empty">{{ emptyText }}</div>

      <div v-if="hoverInfo" class="tip" :style="tipStyle">
        <div class="tt">{{ hoverInfo.title }}</div>
        <div v-for="(row, i) in hoverInfo.rows" :key="`tv-${i}`" class="vv">
          <i :style="{ background: row.color }"></i>
          {{ formatNumber(row.value, row.dec) }}
          <span>{{ row.unit }}</span>
        </div>
        <div v-if="hoverInfo.rows.length === 0" class="vv nodata">
          无数据
        </div>
      </div>
    </div>

    <div class="wave-foot">
      <span class="hint">{{ axisHint }}</span>
      <div class="ranges">
        <button
          v-for="r in RANGE_ORDER"
          :key="r"
          type="button"
          :class="{ on: store.range.value === r }"
          @click="store.setRange(r)"
        >
          {{ RANGE_SPECS[r].label }}
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.wave {
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: 12px 14px 10px;
}

.wave-head {
  display: flex;
  height: 20px;
  align-items: center;
  gap: 8px;
}

.wave-head h3 {
  margin: 0;
  font-size: 13.5px;
  font-weight: 600;
  letter-spacing: 0.6px;
}

.legend {
  display: flex;
  align-items: center;
  gap: 13px;
  margin-left: auto;
}

.legend .it {
  display: flex;
  align-items: center;
  color: #c3cfdd;
  font-size: 9.5px;
  letter-spacing: 0.2px;
  gap: 5px;
}

.legend .it i {
  width: 6px;
  height: 6px;
  flex: none;
  background: currentcolor;
  border-radius: 50%;
  box-shadow: 0 0 8px currentcolor;
}

.legend .it em {
  margin-left: 1px;
  color: #65727f;
  font-size: 8.5px;
  font-style: normal;
}

.chart-wrap {
  position: relative;
  /* 底部范围按钮放大后绘图区仍至少保留 210px 高度 */
  min-height: 210px;
  flex: 1;
  margin-top: 6px;
  touch-action: none;
}

.wave-svg {
  display: block;
  width: 100%;
  height: 100%;
  overflow: visible;
}

.chart-empty {
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  color: #65727f;
  font-size: 11px;
  letter-spacing: 0.5px;
  border: 1px dashed rgb(255 255 255 / 10%);
  border-radius: 10px;
}

.tip {
  position: absolute;
  z-index: 6;
  padding: 5px 8px;
  font-size: 9.5px;
  line-height: 1.6;
  white-space: nowrap;
  pointer-events: none;
  background: rgb(10 15 20 / 94%);
  border: 1px solid rgb(255 255 255 / 13%);
  border-radius: 8px;
  box-shadow: 0 12px 24px -10px #000;
  opacity: 1;
  transform: translate(-50%, -100%);
  transition: opacity 0.12s;
}

.tip .tt {
  margin-bottom: 1px;
  color: var(--txt3);
  font-size: 8.5px;
  letter-spacing: 0.3px;
}

.tip .vv {
  display: flex;
  align-items: center;
  color: #eaf0f7;
  font-variant-numeric: tabular-nums;
  gap: 5px;
}

.tip .vv i {
  display: inline-block;
  width: 5px;
  height: 5px;
  margin-right: 4px;
  border-radius: 50%;
}

.tip .vv span {
  color: #7c8a97;
  font-size: 8.5px;
}

.tip .vv.nodata {
  color: #7e8a96;
  letter-spacing: 0.3px;
}

.wave-foot {
  display: flex;
  height: 40px;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
}

.wave-foot .hint {
  overflow: hidden;
  color: #6b7887;
  font-size: 8.5px;
  letter-spacing: 0.15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ranges {
  display: flex;
  flex: none;
  gap: 8px;
  margin-left: auto;
}

/* 范围按钮热区 40px 高、单按钮 ≥64px 宽，三按钮间距 8px */
.ranges button {
  min-width: 64px;
  height: 40px;
  padding: 0 10px;
  color: #8e9aa6;
  font-size: 10.5px;
  letter-spacing: 0.2px;
  cursor: pointer;
  background: rgb(255 255 255 / 4.5%);
  border: 1px solid rgb(255 255 255 / 9%);
  border-radius: 6px;
  transition: 0.15s;
  -webkit-tap-highlight-color: transparent;
}

.ranges button:hover {
  color: #d3deea;
  border-color: rgb(255 255 255 / 20%);
}

.ranges button.on {
  color: #b9ffe2;
  background: rgb(52 211 153 / 16%);
  border-color: rgb(52 211 153 / 55%);
}
</style>
