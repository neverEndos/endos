<script lang="ts" setup>
import type { HistoryPoint } from '../data';

import type { CollectorApi } from '#/api/collector';

import { computed } from 'vue';

import { cn } from '@vben/utils';

import { Tooltip } from 'antdv-next';

import {
  alarmLevelMeta,
  channelValueText,
  describeThresholds,
  formatNumeric,
  formatShanghaiHms,
  formatShanghaiTime,
  resolveChannelStatus,
} from '../data';
import Sparkline from './sparkline.vue';

defineOptions({ name: 'ScreenChannelCard' });

const props = defineProps<{
  /** 该通道当前的活动报警；无活动报警时为空 */
  alarm?: CollectorApi.AlarmState;
  channel: CollectorApi.DashboardChannel;
  device: CollectorApi.DeviceSimple;
  history: readonly HistoryPoint[];
  /** 通道在传感器分组内的序号，用于 CH-01 样式编号 */
  index: number;
  latest: CollectorApi.DashboardLatest;
  now: number;
  sensor: CollectorApi.DashboardSensor;
  /** 可选阈值矩阵；无 collector:channel:query 权限时为空 */
  threshold?: CollectorApi.ThresholdMatrixChannel;
}>();

const emit = defineEmits<{
  select: [];
}>();

const status = computed(() =>
  resolveChannelStatus(props.channel, props.latest, props.now),
);
const isEnum = computed(() => props.channel.valueKind === 'enum');
const valueText = computed(() => channelValueText(props.channel));
const unitText = computed(() => props.channel.unit?.trim() || '');
const sampleTimeShort = computed(() =>
  formatShanghaiHms(props.channel.sampleTime),
);
const sampleTimeFull = computed(() =>
  formatShanghaiTime(props.channel.sampleTime),
);
const alarmMeta = computed(() => alarmLevelMeta(props.alarm?.level));
const hasAlarm = computed(() => (props.alarm?.level ?? 0) > 0);
const channelCode = computed(
  () => `CH-${String(props.index + 1).padStart(2, '0')}`,
);

/** 悬浮提示：阈值来自阈值矩阵；若当前有活动报警，则额外给出报警阈值 */
const tooltipThreshold = computed(() => describeThresholds(props.threshold));
const alarmThresholdText = computed(() => {
  const value = props.alarm?.lastThreshold;
  return value !== null && value !== undefined && Number.isFinite(value)
    ? `${formatNumeric(value)} ${unitText.value}`.trim()
    : '--';
});

const sparkColor = computed(() => {
  if (status.value.status === 'bad') return '#f43f5e';
  if (status.value.status === 'stale') return '#64748b';
  if (status.value.status === 'uncertain') return '#fbbf24';
  return '#22d3ee';
});

const sparkLabel = computed(
  () => `${props.channel.name} 最近 ${props.history.length} 个采样值的迷你趋势`,
);
</script>

<template>
  <Tooltip placement="topLeft" :mouse-enter-delay="0.35">
    <template #title>
      <div class="channel-tip">
        <div class="tip-head">
          <span class="tip-name">{{ channel.name }}</span>
          <span class="tip-code">{{ channelCode }}</span>
        </div>
        <div class="tip-line">{{ device.deviceName }} / {{ sensor.name }}</div>
        <div class="tip-grid">
          <span>最新值</span>
          <span>{{ valueText }} {{ unitText }}</span>
          <span>状态</span>
          <span>{{ status.label }}</span>
          <span>最近采样</span>
          <span>{{ sampleTimeFull }}</span>
          <span>阈值</span>
          <span>{{ tooltipThreshold }}</span>
          <template v-if="hasAlarm">
            <span>活动报警</span>
            <span>{{ alarmMeta.label }}</span>
            <span>报警阈值</span>
            <span>{{ alarmThresholdText }}</span>
          </template>
        </div>
        <div v-if="isEnum" class="tip-line">枚举通道，无数值趋势曲线</div>
      </div>
    </template>

    <button
      type="button"
      :class="
        cn('channel-card', `channel-card--${status.status}`, {
          'channel-card--alarm': hasAlarm,
        })
      "
      :aria-label="`${channel.name} ${valueText} ${unitText}，打开详情`"
      data-testid="screen-channel-card"
      @click="emit('select')"
    >
      <span class="card-accent" aria-hidden="true"></span>
      <span class="card-top">
        <span class="card-code">{{ channelCode }}</span>
        <span class="card-state">
          <span class="card-dot" aria-hidden="true"></span>
          {{ status.label }}
        </span>
      </span>

      <span class="card-name" :title="channel.name">{{ channel.name }}</span>

      <span class="card-value-row">
        <span
          :key="`${channel.id}-${valueText}`"
          :class="cn('card-value', { 'card-value--text': isEnum })"
        >
          {{ valueText }}
        </span>
        <span v-if="unitText" class="card-unit">{{ unitText }}</span>
      </span>

      <Sparkline
        v-if="!isEnum"
        :color="sparkColor"
        :label="sparkLabel"
        :points="history"
      />
      <span v-else class="card-enum-hint">枚举通道 · 无数值曲线</span>

      <span class="card-foot">
        <span class="card-time">{{ sampleTimeShort }}</span>
        <span v-if="hasAlarm" class="alarm-chip">
          {{ alarmMeta.label }}
        </span>
        <span class="card-unit-label">{{ isEnum ? 'ENUM' : 'NUM' }}</span>
      </span>
    </button>
  </Tooltip>
</template>

<style scoped>
.channel-card {
  --status-color: #2dd4bf;

  position: relative;
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  padding: 9px 10px 8px;
  overflow: hidden;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: linear-gradient(180deg, rgb(19 31 54 / 82%), rgb(11 18 34 / 72%));
  border: 1px solid var(--scr-border, rgb(72 104 158 / 24%));
  border-radius: 8px;
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 3%);
  transition:
    border-color 0.2s ease,
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.channel-card:hover {
  border-color: var(--status-color);
  box-shadow:
    0 0 0 1px rgb(34 211 238 / 10%),
    0 8px 18px rgb(2 6 14 / 45%);
  transform: translateY(-1px);
}

.channel-card:focus-visible {
  outline: 1px solid var(--scr-primary, #22d3ee);
  outline-offset: 2px;
}

.channel-card--bad {
  --status-color: #f43f5e;
}

.channel-card--missing,
.channel-card--stale {
  --status-color: #64748b;
}

.channel-card--normal {
  --status-color: #2dd4bf;
}

.channel-card--uncertain {
  --status-color: #fbbf24;
}

.channel-card--alarm {
  border-color: rgb(244 63 94 / 40%);
}

/* 顶部 2px 状态高光 + 极淡内发光，不用大面积色块 */
.card-accent {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 2px;
  background: linear-gradient(90deg, var(--status-color), transparent 70%);
  opacity: 0.85;
}

.card-top {
  display: flex;
  gap: 6px;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
}

.card-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 9px;
  color: var(--scr-muted, #64748b);
  letter-spacing: 1.2px;
}

.card-state {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  font-size: 9px;
  color: var(--status-color);
  white-space: nowrap;
}

.card-dot {
  width: 5px;
  height: 5px;
  background: var(--status-color);
  border-radius: 999px;
  box-shadow: 0 0 5px var(--status-color);
}

.card-name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  font-weight: 500;
  color: var(--scr-ink-soft, #b7c6dd);
  white-space: nowrap;
}

.card-value-row {
  display: flex;
  gap: 4px;
  align-items: baseline;
  min-width: 0;
}

.card-value {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 22px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--scr-ink, #e6eef9);
  letter-spacing: 0.2px;
  white-space: nowrap;
  animation: value-in 0.4s ease-out;
}

.channel-card--bad .card-value {
  color: #fda4af;
}

.channel-card--stale .card-value,
.channel-card--missing .card-value {
  color: #94a3b8;
}

.card-value--text {
  font-size: 16px;
  font-weight: 600;
}

.card-unit {
  font-size: 10px;
  color: var(--scr-muted, #64748b);
  white-space: nowrap;
}

.card-enum-hint {
  font-size: 10px;
  color: var(--scr-muted, #64748b);
}

.card-foot {
  display: flex;
  gap: 6px;
  align-items: center;
  min-width: 0;
  font-size: 9px;
  color: var(--scr-muted, #64748b);
}

.card-time {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.card-unit-label {
  margin-left: auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 报警标签保持静态，页面循环动画只保留连接点与报警面板两处 */
.alarm-chip {
  padding: 0 5px;
  font-size: 9px;
  color: #fecdd3;
  background: rgb(244 63 94 / 16%);
  border: 1px solid rgb(244 63 94 / 45%);
  border-radius: 3px;
}

.channel-tip {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 190px;
  padding: 2px 2px 4px;
}

.tip-head {
  display: flex;
  gap: 10px;
  align-items: baseline;
  justify-content: space-between;
}

.tip-name {
  font-size: 13px;
  font-weight: 600;
  color: #f8fafc;
}

.tip-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 9px;
  color: #94a3b8;
}

.tip-line {
  font-size: 11px;
  color: #cbd5e1;
}

.tip-grid {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 3px 10px;
  font-size: 11px;
  color: #e2e8f0;
}

.tip-grid > span:nth-child(odd) {
  color: #94a3b8;
}

@keyframes value-in {
  0% {
    opacity: 0.4;
    transform: translateY(4px);
  }

  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .channel-card {
    transition: none;
  }

  .card-value {
    animation: none;
  }

  .channel-card:hover {
    transform: none;
  }
}
</style>
