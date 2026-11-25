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
    /** 历史种子加载失败或过期时弱化曲线，提示数据可能不完整 */
    stale?: boolean;
  }>(),
  {
    color: '#2dd4bf',
    height: 28,
    stale: false,
  },
);

/**
 * 每个 sparkline 只用一段轻量 SVG 路径，不创建 ECharts 实例；
 * 少于两个采样点时只展示占位文字，不画一条伪造的短趋势线。
 */
const geometry = computed(() =>
  sparklineGeometry(
    props.points.map((point) => point.v),
    100,
    props.height,
    3,
  ),
);

const placeholderText = computed(() =>
  props.stale ? '历史加载失败' : '暂无历史',
);
</script>

<template>
  <svg
    class="sparkline"
    :class="{ 'sparkline--stale': stale }"
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
    <text
      v-else
      class="spark-empty"
      text-anchor="middle"
      :x="50"
      :y="height / 2 + 3"
    >
      {{ placeholderText }}
    </text>
  </svg>
</template>

<style scoped>
.sparkline {
  display: block;
  width: 100%;
  overflow: visible;
}

.sparkline--stale {
  opacity: 0.55;
}

.spark-area {
  opacity: 0.12;
}

.spark-line {
  fill: none;
  stroke-width: 1.4;
  transition: stroke 0.3s ease;
}

.spark-empty {
  font-size: 9px;
  fill: rgb(148 163 184 / 65%);
}

@media (prefers-reduced-motion: reduce) {
  .spark-line {
    transition: none;
  }
}
</style>
