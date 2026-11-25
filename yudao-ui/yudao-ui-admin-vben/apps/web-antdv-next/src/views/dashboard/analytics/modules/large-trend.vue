<script lang="ts" setup>
import type { PixelPoint, ThresholdLine } from '../data';

import type { CollectorApi } from '#/api/collector';

import { computed, ref } from 'vue';

import {
  areaPath,
  chartBounds,
  extractRangePoints,
  extractValueSegments,
  formatAxisTick,
  formatNumeric,
  formatShanghaiMdHm,
  linePath,
  mapPointsToPixels,
  seriesName,
  unitKey,
} from '../data';

defineOptions({ name: 'ScreenLargeTrend' });

const props = withDefaults(
  defineProps<{
    /** 查询时间窗起点（epoch millis），仅在无数据时用作坐标兜底 */
    from?: number;
    rangeSeconds: number;
    series?: CollectorApi.TrendSeries;
    /** 是否绘制 min~max 波动带，仅单通道时开启 */
    showBand?: boolean;
    /** 启用的四档阈值；域外阈值不画线、不扩轴 */
    thresholds?: ThresholdLine[];
    /** 查询时间窗终点（epoch millis） */
    to?: number;
  }>(),
  {
    from: undefined,
    series: undefined,
    showBand: true,
    thresholds: () => [],
    to: undefined,
  },
);

const VIEW_WIDTH = 1000;
const VIEW_HEIGHT = 240;
const PLOT = {
  bottom: 28,
  left: 58,
  right: 18,
  top: 18,
};
const PLOT_WIDTH = VIEW_WIDTH - PLOT.left - PLOT.right;
const PLOT_HEIGHT = VIEW_HEIGHT - PLOT.top - PLOT.bottom;

const segments = computed(() => extractValueSegments(props.series));
const ranges = computed(() => extractRangePoints(props.series));
const bounds = computed(() =>
  chartBounds(
    segments.value.flat(),
    props.showBand ? ranges.value : [],
    props.from,
    props.to,
  ),
);

const plotBox = { height: PLOT_HEIGHT, width: PLOT_WIDTH };

function shiftPoint(point: PixelPoint): PixelPoint {
  return { x: point.x + PLOT.left, y: point.y + PLOT.top };
}

const pixelSegments = computed(() => {
  if (!bounds.value) return [];
  return segments.value.map((segment) =>
    mapPointsToPixels(segment, bounds.value!, plotBox).map((point) =>
      shiftPoint(point),
    ),
  );
});

const bandPolygon = computed(() => {
  if (!bounds.value || !props.showBand || ranges.value.length < 2) return '';
  const upper = mapPointsToPixels(
    ranges.value.map((range) => ({ t: range.t, v: range.max })),
    bounds.value,
    plotBox,
  ).map((point) => shiftPoint(point));
  const lower = mapPointsToPixels(
    ranges.value.map((range) => ({ t: range.t, v: range.min })),
    bounds.value,
    plotBox,
  ).map((point) => shiftPoint(point));
  if (upper.length < 2) return '';
  const upperPath = linePath(upper);
  let lowerPath = '';
  for (let index = lower.length - 1; index >= 0; index -= 1) {
    const point = lower[index];
    if (point) {
      lowerPath += ` L${point.x.toFixed(2)} ${point.y.toFixed(2)}`;
    }
  }
  return `${upperPath} ${lowerPath}`;
});

const lineSegments = computed(() =>
  pixelSegments.value
    .map((segment) => linePath(segment))
    .filter((path) => path.length > 0),
);

const areaSegments = computed(() =>
  pixelSegments.value
    .map((segment) => areaPath(segment, PLOT.top + PLOT_HEIGHT))
    .filter((path) => path.length > 0),
);

/** 全部有效点（含像素坐标），供指针悬停取最近点 */
const flatPoints = computed(() => {
  if (!bounds.value) return [];
  const result: Array<PixelPoint & { t: number; v: number }> = [];
  for (const segment of segments.value) {
    for (const point of segment) {
      const [pixel] = mapPointsToPixels([point], bounds.value, plotBox);
      if (!pixel) continue;
      const shifted = shiftPoint(pixel);
      result.push({ ...shifted, t: point.t, v: point.v });
    }
  }
  return result;
});

const lastPoint = computed(() => {
  const segment = pixelSegments.value[pixelSegments.value.length - 1];
  return segment?.[segment.length - 1];
});

const yTicks = computed(() => {
  if (!bounds.value) return [];
  const { yMax, yMin } = bounds.value;
  const mid = yMin + (yMax - yMin) / 2;
  return [yMax, mid, yMin].map((value, index) => ({
    label: formatNumeric(value),
    y: PLOT.top + (PLOT_HEIGHT * index) / 2,
  }));
});

const xTicks = computed(() => {
  if (!bounds.value) return [];
  const { xMax, xMin } = bounds.value;
  return Array.from({ length: 5 }, (_, index) => ({
    label: formatAxisTick(
      xMin + ((xMax - xMin) * index) / 4,
      props.rangeSeconds,
    ),
    x: PLOT.left + (PLOT_WIDTH * index) / 4,
  }));
});

const visibleThresholds = computed(() => {
  if (!bounds.value) return [];
  const { yMax, yMin } = bounds.value;
  return props.thresholds
    .filter((threshold) => threshold.value >= yMin && threshold.value <= yMax)
    .map((threshold) => ({
      ...threshold,
      label: `${threshold.label} ${formatNumeric(threshold.value)}`,
      y:
        PLOT.top +
        PLOT_HEIGHT -
        ((threshold.value - yMin) / (yMax - yMin || 1)) * PLOT_HEIGHT,
    }));
});

const hiddenThresholds = computed(() => {
  if (!bounds.value) return props.thresholds;
  const { yMax, yMin } = bounds.value;
  return props.thresholds.filter(
    (threshold) => threshold.value < yMin || threshold.value > yMax,
  );
});

const hover = ref<PixelPoint & { t: number; v: number }>();

const hoverStyle = computed(() => {
  if (!hover.value) return {};
  const left = (hover.value.x / VIEW_WIDTH) * 100;
  const top = (hover.value.y / VIEW_HEIGHT) * 100;
  return {
    left: `clamp(74px, ${left}%, calc(100% - 74px))`,
    top: `clamp(36px, ${top}%, calc(100% - 56px))`,
  };
});

function handlePointerMove(event: PointerEvent) {
  if (flatPoints.value.length === 0) return;
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
  if (rect.width === 0) return;
  const viewX = ((event.clientX - rect.left) / rect.width) * VIEW_WIDTH;
  let nearest = flatPoints.value[0]!;
  for (const point of flatPoints.value) {
    if (Math.abs(point.x - viewX) < Math.abs(nearest.x - viewX)) {
      nearest = point;
    }
  }
  hover.value = nearest;
}

function handlePointerLeave() {
  hover.value = undefined;
}

const pointCount = computed(() => segments.value.flat().length);
const unitText = computed(() => {
  const unit = props.series?.unit?.trim();
  return unit || '';
});
const chartLabel = computed(() => {
  const name = seriesName(props.series);
  return `${name} 趋势图，${pointCount.value} 个有效采样点，单位 ${unitKey(
    props.series?.unit,
  )}`;
});
</script>

<template>
  <div class="large-trend">
    <svg
      class="trend-svg"
      :aria-label="chartLabel"
      :viewBox="`0 0 ${VIEW_WIDTH} ${VIEW_HEIGHT}`"
      role="img"
      @pointerleave="handlePointerLeave"
      @pointermove="handlePointerMove"
    >
      <defs>
        <linearGradient id="screen-trend-line" x1="0" x2="1" y1="0" y2="0">
          <stop offset="0" stop-color="#22d3ee" stop-opacity="0.4" />
          <stop offset="0.55" stop-color="#22d3ee" />
          <stop offset="1" stop-color="#2dd4bf" />
        </linearGradient>
        <linearGradient id="screen-trend-area" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0" stop-color="#22d3ee" stop-opacity="0.18" />
          <stop offset="1" stop-color="#22d3ee" stop-opacity="0.01" />
        </linearGradient>
        <clipPath id="screen-trend-clip">
          <rect
            :height="PLOT_HEIGHT"
            :width="PLOT_WIDTH"
            :x="PLOT.left"
            :y="PLOT.top"
          />
        </clipPath>
      </defs>

      <!-- 极细网格：先画数据与阈值，再覆盖轴标签，保持读数优先 -->
      <g class="grid">
        <line
          v-for="tick in yTicks"
          :key="`y-${tick.y}`"
          :x1="PLOT.left"
          :x2="PLOT.left + PLOT_WIDTH"
          :y1="tick.y"
          :y2="tick.y"
        />
        <line
          v-for="tick in xTicks"
          :key="`x-${tick.x}`"
          :x1="tick.x"
          :x2="tick.x"
          :y1="PLOT.top"
          :y2="PLOT.top + PLOT_HEIGHT"
        />
      </g>

      <g clip-path="url(#screen-trend-clip)">
        <path v-if="bandPolygon" class="band" :d="bandPolygon" />
        <path
          v-for="(path, index) in areaSegments"
          :key="`area-${index}`"
          class="area"
          :d="path"
        />
        <path
          v-for="(path, index) in lineSegments"
          :key="`line-${index}`"
          class="line"
          :d="path"
        />
        <line
          v-for="threshold in visibleThresholds"
          :key="`threshold-${threshold.label}`"
          class="threshold"
          :stroke="threshold.color"
          :x1="PLOT.left"
          :x2="PLOT.left + PLOT_WIDTH"
          :y1="threshold.y"
          :y2="threshold.y"
        />
        <circle
          v-if="lastPoint"
          class="last-dot"
          :cx="lastPoint.x"
          :cy="lastPoint.y"
          r="4"
        />
        <template v-if="hover">
          <line
            class="crosshair"
            :x1="hover.x"
            :x2="hover.x"
            :y1="PLOT.top"
            :y2="PLOT.top + PLOT_HEIGHT"
          />
          <circle class="hover-dot" :cx="hover.x" :cy="hover.y" r="4.5" />
        </template>
      </g>

      <g class="axis-labels">
        <text
          v-for="tick in yTicks"
          :key="`yl-${tick.y}`"
          :x="PLOT.left - 8"
          :y="tick.y + 3"
          text-anchor="end"
        >
          {{ tick.label }}
        </text>
        <text
          v-for="tick in xTicks"
          :key="`xl-${tick.x}`"
          :x="tick.x"
          :y="PLOT.top + PLOT_HEIGHT + 16"
          text-anchor="middle"
        >
          {{ tick.label }}
        </text>
        <text
          v-for="threshold in visibleThresholds"
          :key="`tl-${threshold.label}`"
          :fill="threshold.color"
          :x="PLOT.left + PLOT_WIDTH - 4"
          :y="threshold.y - 4"
          class="threshold-label"
          text-anchor="end"
        >
          {{ threshold.label }}
        </text>
      </g>
    </svg>

    <div v-if="hover" class="trend-tooltip" :style="hoverStyle">
      <span class="tooltip-time">{{ formatShanghaiMdHm(hover.t) }}</span>
      <span class="tooltip-value">
        {{ formatNumeric(hover.v) }}
        <span v-if="unitText" class="tooltip-unit">{{ unitText }}</span>
      </span>
    </div>

    <div v-if="pointCount === 0" class="trend-empty">
      该通道在当前时间窗内没有可用数据
    </div>
    <div v-else-if="pointCount === 1" class="trend-empty trend-empty--corner">
      仅 1 个采样点，等待后续数据形成趋势
    </div>
    <div
      v-if="hiddenThresholds.length > 0"
      class="trend-empty trend-empty--corner threshold-out"
    >
      {{ hiddenThresholds.length }} 个阈值超出当前数据范围
    </div>
  </div>
</template>

<style scoped>
.large-trend {
  position: relative;
  width: 100%;
}

.trend-svg {
  display: block;
  width: 100%;
  height: auto;
  overflow: hidden;
  cursor: crosshair;
  background: rgb(7 12 24 / 45%);
  border: 1px solid var(--scr-border, rgb(72 104 158 / 24%));
  border-radius: 8px;
}

.grid line {
  stroke: rgb(148 163 184 / 9%);
  stroke-width: 1;
}

.band {
  fill: rgb(34 211 238 / 7%);
}

.area {
  fill: url('#screen-trend-area');
}

.line {
  fill: none;
  stroke: url('#screen-trend-line');
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.threshold {
  opacity: 0.6;
  stroke-width: 1;
  stroke-dasharray: 6 5;
}

.last-dot {
  fill: var(--scr-primary, #22d3ee);
  stroke: #070c18;
  stroke-width: 2;
}

.crosshair {
  opacity: 0.55;
  stroke: var(--scr-primary, #22d3ee);
  stroke-width: 1;
  stroke-dasharray: 2 3;
}

.hover-dot {
  fill: #f8fafc;
  stroke: var(--scr-primary, #22d3ee);
  stroke-width: 2;
}

.axis-labels text {
  font-size: 10px;
  font-variant-numeric: tabular-nums;
  fill: var(--scr-muted, #64748b);
}

.threshold-label {
  font-size: 9px;
  stroke: #070c18;
  stroke-width: 3px;
  paint-order: stroke;
}

.trend-tooltip {
  position: absolute;
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 84px;
  padding: 4px 7px;
  color: var(--scr-ink, #e6eef9);
  pointer-events: none;
  background: rgb(7 12 24 / 92%);
  border: 1px solid var(--scr-border-strong, rgb(72 104 158 / 44%));
  border-radius: 5px;
  box-shadow: 0 4px 14px rgb(0 0 0 / 35%);
  transform: translate(-50%, 10px);
}

.tooltip-time {
  font-size: 9px;
  color: var(--scr-muted, #64748b);
}

.tooltip-value {
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.tooltip-unit {
  margin-left: 3px;
  font-size: 9px;
  font-weight: 400;
  color: var(--scr-muted, #64748b);
}

.trend-empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  font-size: 12px;
  color: var(--scr-muted, #64748b);
  pointer-events: none;
}

.trend-empty--corner {
  inset: auto 10px 8px auto;
  display: block;
  font-size: 10px;
}

.threshold-out {
  color: var(--scr-warning, #fbbf24);
}
</style>
