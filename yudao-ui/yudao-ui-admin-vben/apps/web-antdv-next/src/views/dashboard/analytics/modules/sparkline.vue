<script lang="ts" setup>
import type { HistoryPoint } from '../data';

import { computed } from 'vue';

import { sparklineGeometry } from '../data';

defineOptions({ name: 'ScreenSparkline' });

const props = withDefaults(
  defineProps<{
    /** 状态色：正常为青绿、异常为红、陈旧为灰 */
    color?: string;
    height?: number;
    /** 供屏幕阅读器说明曲线来源 */
    label: string;
    points: readonly HistoryPoint[];
  }>(),
  {
    color: '#2dd4bf',
    height: 28,
  },
);

/**
 * 每个 sparkline 只用一段轻量 SVG 路径，不创建 ECharts 实例；
 * 少于两个采样点时画虚线基线，不伪造趋势。
 */
const geometry = computed(() =>
  sparklineGeometry(
    props.points.map((point) => point.v),
    100,
    props.height,
    3,
  ),
);
</script>

<template>
  <svg
    class="sparkline"
    data-testid="channel-sparkline"
    :aria-label="label"
    :height="height"
    preserveAspectRatio="none"
    role="img"
    :viewBox="`0 0 100 ${height}`"
  >
    <template v-if="geometry">
      <path class="spark-area" :d="geometry.area" :style="{ fill: color }" />
      <path class="spark-line" :d="geometry.path" :style="{ stroke: color }" />
    </template>
    <line v-else class="spark-baseline" x1="2" x2="98" y1="14" y2="14" />
  </svg>
</template>

<style scoped>
.sparkline {
  display: block;
  width: 100%;
  overflow: visible;
}

.spark-area {
  opacity: 0.12;
}

.spark-line {
  fill: none;
  stroke-width: 1.4;
  transition: stroke 0.3s ease;
}

.spark-baseline {
  stroke: rgb(148 163 184 / 28%);
  stroke-width: 1;
  stroke-dasharray: 3 4;
}

@media (prefers-reduced-motion: reduce) {
  .spark-line {
    transition: none;
  }
}
</style>
