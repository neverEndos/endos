<script lang="ts" setup>
import type { MonitorChannel } from '../use-monitor';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import {
  formatAgo,
  formatNumber,
  linePath,
  niceScale,
  RANGE_ORDER,
  RANGE_SPECS,
  scaleDecimals,
} from '../chart-utils';
import { useMonitor } from '../use-monitor';

/**
 * 实时波形：手写 SVG 双 Y 轴平滑曲线 + 十字线 tooltip。
 * slot1 左轴薄荷绿、slot2 右轴琥珀；X 轴按请求窗口的真实时间映射。
 */
defineOptions({ name: 'KioskWaveChart' });

interface ChartPoint {
  avg: number;
  t: number;
  x: number;
  y: number;
}

interface ChartSeries {
  channel: MonitorChannel;
  color: string;
  dec: number;
  line: string;
  area: string;
  points: ChartPoint[];
  scale: { hi: number; lo: number; step: number };
}

const PAD_L = 50;
const PAD_T = 22;
const PAD_B = 24;

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
  const dual = items.length > 1;
  const padR = dual ? 44 : 12;
  const iw = W - PAD_L - padR;
  const ih = H - PAD_T - PAD_B;
  const xOf = (t: number) => PAD_L + ((t - from) / (to - from)) * iw;

  const series: ChartSeries[] = [];
  for (const item of items) {
    if (!item.data) continue;
    const valid = item.data.points.filter(
      (p): p is typeof p & { avg: number } =>
        p.avg !== null && p.avg !== undefined && p.t >= from && p.t <= to,
    );
    if (valid.length === 0) continue;
    const values = valid.map((p) => p.avg);
    const scale = niceScale(Math.min(...values), Math.max(...values), 4);
    const yOf = (v: number) =>
      PAD_T + (1 - (v - scale.lo) / (scale.hi - scale.lo)) * ih;
    const points: ChartPoint[] = valid.map((p) => ({
      avg: p.avg,
      t: p.t,
      x: xOf(p.t),
      y: yOf(p.avg),
    }));
    const line = linePath(points.map((p) => [p.x, p.y]));
    const last = points[points.length - 1]!;
    // 单点序列无法成线，仅保留点供十字线/tooltip 使用
    const area = line
      ? `${line} L${last.x.toFixed(1)},${(PAD_T + ih).toFixed(1)} L${PAD_L},${(PAD_T + ih).toFixed(1)} Z`
      : '';
    series.push({
      channel: item.channel,
      color: item.color,
      dec: scaleDecimals(scale.step),
      line,
      area,
      points,
      scale,
    });
  }
  if (series.length === 0) return null;
  return {
    from,
    to,
    W,
    H,
    padR,
    iw,
    ih,
    bucketMs: firstWithData.data.bucketSeconds * 1000,
    series,
    dual,
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

/** X 轴 5 个刻度：按请求窗口均布 */
const xTicks = computed(() => {
  const m = model.value;
  if (!m) return [];
  const ticks = RANGE_SPECS[store.range.value].ticks;
  return ticks.map((label, i) => ({
    label,
    x: PAD_L + (m.iw * i) / (ticks.length - 1),
    anchor: (i === 0 ? 'start' : i === ticks.length - 1 ? 'end' : 'middle') as
      | 'end'
      | 'middle'
      | 'start',
  }));
});

const legendItems = computed(() =>
  store.waveSeries.value.map((item, slot) => ({
    color: item.color,
    name: item.channel.name,
    unit: item.channel.unit,
    slot: slot + 1,
  })),
);

const axisHint = computed(() => {
  const label = RANGE_SPECS[store.range.value].label;
  const items = store.waveSeries.value;
  const first = items[0];
  const second = items[1];
  // 左右轴单位之间用全角空格分隔（与原型一致）
  const gap = String.fromCharCode(0x3000);
  if (first && second) {
    return `X 轴 · 时间区间（${label}）｜左轴 ${first.channel.unit || '--'}${gap}右轴 ${second.channel.unit || '--'}`;
  }
  if (first) return `X 轴 · 时间区间（${label}）｜Y 轴 ${first.channel.unit || '--'}`;
  return `X 轴 · 时间区间（${label}）`;
});

const emptyText = computed(() => {
  if (store.cards.value.length === 0) return '暂无采集通道';
  if (!store.trendLoaded.value) return '正在加载趋势数据…';
  return '暂无趋势数据';
});

/** 十字线与 tooltip：以主序列最近桶为锚，各曲线取该时刻最近的桶 */
const hoverInfo = computed(() => {
  const m = model.value;
  const t = hoverT.value;
  if (!m || t === null) return null;
  const rows = m.series.map((s) => {
    let best = s.points[0]!;
    for (const p of s.points) {
      if (Math.abs(p.t - t) < Math.abs(best.t - t)) best = p;
    }
    return {
      color: s.color,
      value: best.avg,
      unit: s.channel.unit,
      dec: s.channel.decimalPlaces,
      y: best.y,
    };
  });
  const nowMs = Date.now();
  const title =
    m.to - t <= m.bucketMs ? '现在' : formatAgo(t, nowMs);
  const topY = Math.min(...rows.map((row) => row.y)) - 10;
  return { rows, title, topY, x: hoverX.value };
});

const tipStyle = computed(() => {
  const info = hoverInfo.value;
  const m = model.value;
  if (!info || !m) return {};
  const left = Math.min(Math.max(info.x, 52), m.W - 46);
  return { left: `${left}px`, top: `${info.topY}px` };
});

function onPointerMove(event: PointerEvent) {
  const wrap = wrapRef.value;
  const m = model.value;
  if (!wrap || !m) return;
  const rect = wrap.getBoundingClientRect();
  // 画布被等比缩放，按 viewBox 与渲染尺寸的比例换算回布局坐标
  const px = ((event.clientX - rect.left) * m.W) / rect.width;
  const pts = m.series[0]!.points;
  let best = pts[0]!;
  for (const p of pts) {
    if (Math.abs(p.x - px) < Math.abs(best.x - px)) best = p;
  }
  hoverT.value = best.t;
  hoverX.value = best.x;
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
      <h3>实时波形</h3>
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
        </defs>

        <!-- X 轴竖向网格 -->
        <line
          v-for="tick in xTicks"
          :key="`xg-${tick.label}`"
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
          v-for="tick in xTicks"
          :key="`xl-${tick.label}`"
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

        <!-- 主序列渐变填充 -->
        <path :d="model.series[0]!.area" fill="url(#wave-fade)" />
        <!-- 曲线：Y2 在下、Y1 在上 -->
        <path
          v-if="model.series[1]"
          :d="model.series[1]!.line"
          fill="none"
          :stroke="model.series[1]!.color"
          stroke-width="1.7"
          stroke-linecap="round"
          filter="url(#wave-glow)"
        />
        <path
          :d="model.series[0]!.line"
          fill="none"
          :stroke="model.series[0]!.color"
          stroke-width="1.9"
          stroke-linecap="round"
          filter="url(#wave-glow)"
        />

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
  min-height: 0;
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

.wave-foot {
  display: flex;
  height: 22px;
  align-items: center;
  gap: 8px;
  margin-top: 2px;
}

.wave-foot .hint {
  color: #6b7887;
  font-size: 8.5px;
  letter-spacing: 0.15px;
  white-space: nowrap;
}

.ranges {
  display: flex;
  gap: 4px;
  margin-left: auto;
}

.ranges button {
  height: 18px;
  padding: 0 8px;
  color: #8e9aa6;
  font-size: 9px;
  letter-spacing: 0.2px;
  cursor: pointer;
  background: rgb(255 255 255 / 4.5%);
  border: 1px solid rgb(255 255 255 / 9%);
  border-radius: 6px;
  transition: 0.15s;
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
