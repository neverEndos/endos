<script lang="ts" setup>
import type { CollectorApi } from '#/api/collector';

import { computed } from 'vue';

import ActiveAlarmPanel from '#/views/collector/components/active-alarm-panel.vue';

import { alarmLevelMeta, computeAlarmLevelStats } from '../data';
import SectionHead from './section-head.vue';

defineOptions({ name: 'ScreenAlarmSide' });

const props = defineProps<{
  alarms: CollectorApi.AlarmState[];
  alarmsError?: string;
  hasAlarmAccess: boolean;
}>();

const emit = defineEmits<{
  refresh: [];
}>();

const stats = computed(() => computeAlarmLevelStats(props.alarms));

const highestLevel = computed(() => {
  let level = 0;
  for (const alarm of props.alarms) {
    level = Math.max(level, alarm.level ?? 0);
  }
  return level;
});

const highestMeta = computed(() => alarmLevelMeta(highestLevel.value));

const statusTone = computed<'error' | 'muted' | 'warn'>(() => {
  if (stats.value.level3 > 0) return 'error';
  if (stats.value.level2 > 0) return 'warn';
  return 'muted';
});

const statusText = computed(() => {
  if (!props.hasAlarmAccess) return '无权限';
  if (stats.value.total === 0) return '当前无活动报警';
  return `活动 ${stats.value.total} 条 · 最高${highestMeta.value.label}`;
});

const levels = computed(() => [
  {
    color: '#f43f5e',
    count: stats.value.level3,
    key: 'level3',
    label: '异常',
    percent: stats.value.total
      ? (stats.value.level3 / stats.value.total) * 100
      : 0,
  },
  {
    color: '#fbbf24',
    count: stats.value.level2,
    key: 'level2',
    label: '预警',
    percent: stats.value.total
      ? (stats.value.level2 / stats.value.total) * 100
      : 0,
  },
]);

const distributionLabel = computed(
  () =>
    `报警等级分布：异常 ${stats.value.level3} 条，预警 ${stats.value.level2} 条，共 ${stats.value.total} 条活动报警`,
);

function formatCount(count: number): string {
  return String(count).padStart(2, '0');
}
</script>

<template>
  <aside class="alarm-side">
    <SectionHead
      code="ALARM-01"
      :pulse="stats.total > 0"
      :status-text="statusText"
      :status-tone="statusTone"
      subtitle="Active Alarms"
      title="当前报警"
    />

    <div v-if="!hasAlarmAccess" class="alarm-note">
      当前账号缺少
      <code>collector:alarm:query</code>
      权限，报警分布与列表已隐藏。
    </div>

    <template v-else>
      <div class="alarm-distribution">
        <div class="distribution-head">
          <span class="distribution-title">等级分布 / LEVELS</span>
          <span class="distribution-unacked">
            未确认 <strong>{{ formatCount(stats.unacknowledged) }}</strong>
          </span>
        </div>
        <div
          class="distribution-rows"
          :aria-label="distributionLabel"
          role="img"
        >
          <div
            v-for="level in levels"
            :key="level.key"
            class="distribution-row"
          >
            <span class="level-label">
              <span
                class="level-dot"
                :style="{ background: level.color }"
                aria-hidden="true"
              ></span>
              {{ level.label }}
            </span>
            <span class="level-track">
              <span
                class="level-fill"
                :style="{ background: level.color, width: `${level.percent}%` }"
              ></span>
            </span>
            <span class="level-count">{{ formatCount(level.count) }}</span>
          </div>
        </div>
        <div v-if="stats.total === 0" class="distribution-calm">
          全部通道处于正常范围，无活动报警
        </div>
        <div v-if="stats.total > 0" class="distribution-legend">
          <span class="legend-badge">异常 = 立即处理</span>
          <span class="legend-badge legend-badge--warn">预警 = 关注趋势</span>
        </div>
      </div>

      <div class="alarm-list-wrap">
        <ActiveAlarmPanel
          :alarms="alarms"
          :error="alarmsError"
          dark
          max-height="100%"
          readonly
          :show-header="false"
          @refresh="emit('refresh')"
        />
      </div>
    </template>
  </aside>
</template>

<style scoped>
.alarm-side {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  padding: 10px 12px 12px;
  overflow: hidden;
  background: var(--scr-surface, rgb(13 22 40 / 86%));
  border: 1px solid var(--scr-border, rgb(72 104 158 / 24%));
  border-radius: 10px;
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 4%);
}

.alarm-note {
  padding: 14px 4px;
  font-size: 12px;
  color: var(--scr-muted, #64748b);
}

.alarm-note code {
  padding: 0 3px;
  font-size: 10px;
  color: var(--scr-ink-soft, #b7c6dd);
  background: rgb(148 163 184 / 10%);
  border-radius: 2px;
}

.alarm-distribution {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 8px 10px;
  background: var(--scr-surface-soft, rgb(17 28 50 / 72%));
  border: 1px solid var(--scr-border, rgb(72 104 158 / 24%));
  border-radius: 6px;
}

.distribution-head {
  display: flex;
  gap: 8px;
  align-items: baseline;
  justify-content: space-between;
}

.distribution-title {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 9px;
  color: var(--scr-muted, #64748b);
  letter-spacing: 1.2px;
}

.distribution-unacked {
  font-size: 10px;
  color: var(--scr-muted, #64748b);
}

.distribution-unacked strong {
  font-variant-numeric: tabular-nums;
  color: var(--scr-warning, #fbbf24);
}

.distribution-rows {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.distribution-row {
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr) 24px;
  gap: 6px;
  align-items: center;
}

.level-label {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  font-size: 10px;
  color: var(--scr-ink-soft, #b7c6dd);
}

.level-dot {
  width: 5px;
  height: 5px;
  border-radius: 999px;
}

.level-track {
  display: block;
  height: 6px;
  overflow: hidden;
  background: rgb(148 163 184 / 10%);
  border-radius: 3px;
}

.level-fill {
  display: block;
  height: 100%;
  border-radius: 3px;
  transition: width 0.4s ease;
}

.level-count {
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  color: var(--scr-ink, #e6eef9);
  text-align: right;
}

.distribution-calm {
  font-size: 10px;
  color: var(--scr-okay, #2dd4bf);
}

.distribution-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 9px;
}

.legend-badge {
  padding: 0 5px;
  color: #fda4af;
  background: rgb(244 63 94 / 10%);
  border: 1px solid rgb(244 63 94 / 25%);
  border-radius: 3px;
}

.legend-badge--warn {
  color: #fcd34d;
  background: rgb(251 191 36 / 10%);
  border-color: rgb(251 191 36 / 28%);
}

.alarm-list-wrap {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  border-top: 1px solid rgb(72 104 158 / 16%);
}

.alarm-list-wrap :deep(.active-alarm-panel) {
  flex: 1;
  min-height: 0;
  padding-top: 6px;
}

.alarm-list-wrap :deep(.ant-empty-description) {
  color: var(--scr-muted, #64748b);
}

@media (prefers-reduced-motion: reduce) {
  .level-fill {
    transition: none;
  }
}
</style>
