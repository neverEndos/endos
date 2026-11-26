<script lang="ts" setup>
import { computed } from 'vue';

import { linePath } from '../chart-utils';

/**
 * 卡片迷你趋势图：30 点平滑折线 + 向下渐隐填充，末端荧光圆点。
 * 无数据时退化为灰色虚线基准（与原型占位卡一致）。
 */
const props = defineProps<{
  /** 曲线色（未选中为卡片 accent，选中为 slot 色） */
  color: string;
  /** 渐变 id，需页面内唯一 */
  gid: string;
  /** 抽稀后的序列值 */
  points: number[];
}>();

const shape = computed(() => {
  const pts = props.points;
  if (pts.length < 2) return null;
  const min = Math.min(...pts);
  const max = Math.max(...pts);
  const span = max - min || 1;
  const coords: [number, number][] = pts.map((v, i) => [
    (i / (pts.length - 1)) * 100,
    34 - (0.12 + ((v - min) / span) * 0.8) * 26,
  ]);
  const d = linePath(coords);
  return { area: `${d} L100,36 L0,36 Z`, d, last: coords[coords.length - 1]! };
});
</script>

<template>
  <svg class="spark-svg" viewBox="0 0 100 36" preserveAspectRatio="none">
    <template v-if="shape">
      <defs>
        <linearGradient :id="gid" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" :stop-color="color" stop-opacity="0.26" />
          <stop offset="1" :stop-color="color" stop-opacity="0" />
        </linearGradient>
      </defs>
      <path :d="shape.area" :fill="`url(#${gid})`" />
      <path
        :d="shape.d"
        fill="none"
        :stroke="color"
        stroke-width="1.5"
        stroke-linecap="round"
        vector-effect="non-scaling-stroke"
        :style="{ filter: `drop-shadow(0 0 3px ${color}88)` }"
      />
      <circle
        :cx="shape.last[0]"
        :cy="shape.last[1]"
        r="2"
        :fill="color"
        vector-effect="non-scaling-stroke"
      />
    </template>
    <template v-else>
      <path
        d="M0,25 L100,25"
        stroke="rgba(255,255,255,.07)"
        stroke-width="1"
        stroke-dasharray="3 4"
        vector-effect="non-scaling-stroke"
      />
      <path
        d="M0,31 L14,29 L28,31 L42,27 L56,30 L70,28 L84,31 L100,29"
        fill="none"
        stroke="rgba(255,255,255,.10)"
        stroke-width="1.2"
        stroke-dasharray="4 4"
        vector-effect="non-scaling-stroke"
      />
    </template>
  </svg>
</template>

<style scoped>
.spark-svg {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
