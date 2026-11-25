<script lang="ts" setup>
import type { KpiTile } from '../data';

import { cn } from '@vben/utils';

defineOptions({ name: 'ScreenKpiStrip' });

defineProps<{
  tiles: KpiTile[];
}>();
</script>

<template>
  <div class="kpi-strip">
    <div
      v-for="(tile, index) in tiles"
      :key="tile.key"
      :class="
        cn('kpi-tile', {
          'kpi-tile--muted': !tile.available,
          [`kpi-tile--${tile.tone ?? 'primary'}`]: tile.available,
        })
      "
    >
      <div class="kpi-top">
        <span class="kpi-code">{{
          `KPI-${String(index + 1).padStart(2, '0')}`
        }}</span>
        <span class="kpi-dot" aria-hidden="true"></span>
      </div>
      <div class="kpi-label-row">
        <span class="kpi-label">{{ tile.label }}</span>
        <span class="kpi-subtitle">{{ tile.subtitle }}</span>
      </div>
      <div class="kpi-value-row">
        <!-- key 随数值变化重放一次 0.4 秒数码淡入，不做常驻循环动画 -->
        <span
          :key="`${tile.key}-${tile.value}`"
          class="kpi-value"
          data-testid="screen-kpi-value"
        >
          {{ tile.value }}
        </span>
        <span v-if="tile.unit" class="kpi-unit">{{ tile.unit }}</span>
      </div>
      <div class="kpi-hint">{{ tile.hint || '&nbsp;' }}</div>
    </div>
  </div>
</template>

<style scoped>
.kpi-strip {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
}

.kpi-tile {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding: 10px 12px 9px;
  overflow: hidden;
  background: var(--scr-surface, rgb(13 22 40 / 86%));
  border: 1px solid var(--scr-border, rgb(72 104 158 / 24%));
  border-radius: 8px;
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 4%);
}

/* 顶部 2px 渐变高光：用状态色弱化呈现，不做大面积纯色块 */
.kpi-tile::before {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 2px;
  content: '';
  background: linear-gradient(90deg, var(--tile-color), transparent 78%);
}

.kpi-tile--primary {
  --tile-color: #22d3ee;
}

.kpi-tile--ok {
  --tile-color: #2dd4bf;
}

.kpi-tile--warn {
  --tile-color: #fbbf24;
}

.kpi-tile--error {
  --tile-color: #f43f5e;
}

.kpi-tile--muted {
  --tile-color: #64748b;
}

.kpi-top {
  display: flex;
  gap: 6px;
  align-items: center;
  justify-content: space-between;
}

.kpi-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 9px;
  color: var(--scr-muted, #64748b);
  letter-spacing: 1.4px;
}

.kpi-dot {
  width: 5px;
  height: 5px;
  background: var(--tile-color);
  border-radius: 999px;
  opacity: 0.85;
}

.kpi-label-row {
  display: flex;
  gap: 6px;
  align-items: baseline;
  min-width: 0;
}

.kpi-label {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--scr-ink-soft, #b7c6dd);
  white-space: nowrap;
}

.kpi-subtitle {
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 8px;
  color: var(--scr-muted, #64748b);
  text-transform: uppercase;
  letter-spacing: 1.2px;
  white-space: nowrap;
}

.kpi-value-row {
  display: flex;
  gap: 4px;
  align-items: baseline;
  min-width: 0;
  min-height: 28px;
}

.kpi-value {
  font-size: 24px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--tile-color);
  letter-spacing: 0.4px;
  animation: kpi-pop 0.4s ease-out;
}

.kpi-tile--muted .kpi-value {
  color: var(--scr-muted, #64748b);
}

.kpi-unit {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  color: var(--scr-muted, #64748b);
  white-space: nowrap;
}

.kpi-hint {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 10px;
  line-height: 1.3;
  color: var(--scr-muted, #64748b);
  white-space: nowrap;
}

@keyframes kpi-pop {
  0% {
    opacity: 0.35;
    transform: translateY(5px);
  }

  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 1366×768 下保持单行 6 格，避免 KPI 占掉两行挤压通道矩阵 */
@media (max-width: 1180px) {
  .kpi-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 820px) {
  .kpi-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-height: 800px) {
  .kpi-tile {
    padding: 8px 10px 7px;
  }

  .kpi-value {
    font-size: 21px;
  }

  .kpi-value-row {
    min-height: 24px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .kpi-value {
    animation: none;
  }
}
</style>
