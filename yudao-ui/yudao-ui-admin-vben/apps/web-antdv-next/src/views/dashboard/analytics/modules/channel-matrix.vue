<script lang="ts" setup>
import type {
  ChannelSelection,
  DeviceLatestCard,
  HistoryPoint,
  QualityStats,
} from '../data';

import type { CollectorApi } from '#/api/collector';

import { computed } from 'vue';

import { cn } from '@vben/utils';

import { formatShanghaiHms } from '../data';
import ChannelCard from './channel-card.vue';

defineOptions({ name: 'ScreenChannelMatrix' });

const props = defineProps<{
  /** 趋势主图当前跟踪的设备，用于在矩阵中标记 */
  activeDeviceId?: number;
  alarmByChannel: Record<number, CollectorApi.AlarmState>;
  devices: DeviceLatestCard[];
  devicesError?: string;
  hasDashboard: boolean;
  hasDevice: boolean;
  history: Record<number, readonly HistoryPoint[]>;
  /** 历史种子失败/过期标记：曲线弱化但不清空 */
  historyStale: Record<number, boolean>;
  now: number;
  qualityStats: QualityStats;
  thresholds: Record<number, CollectorApi.ThresholdMatrixChannel>;
}>();

const emit = defineEmits<{
  select: [payload: ChannelSelection];
}>();

const qualitySegments = computed(() => {
  const stats = props.qualityStats;
  const total = stats.total || 1;
  return [
    {
      color: '#2dd4bf',
      count: stats.normal,
      key: 'normal',
      label: '正常',
      percent: (stats.normal / total) * 100,
    },
    {
      color: '#fbbf24',
      count: stats.uncertain,
      key: 'uncertain',
      label: '不确定',
      percent: (stats.uncertain / total) * 100,
    },
    {
      color: '#f43f5e',
      count: stats.bad,
      key: 'bad',
      label: '坏质量',
      percent: (stats.bad / total) * 100,
    },
    {
      color: '#64748b',
      count: stats.stale + stats.missing,
      key: 'offline',
      label: '陈旧/无数据',
      percent: ((stats.stale + stats.missing) / total) * 100,
    },
  ];
});

const qualitySummary = computed(() => {
  const stats = props.qualityStats;
  return `数据质量分布：正常 ${stats.normal}，不确定 ${stats.uncertain}，坏质量 ${stats.bad}，陈旧或无数据 ${stats.stale + stats.missing}，共 ${stats.total} 个通道`;
});

const hasAnyDevice = computed(() => props.devices.length > 0);

function sensorCode(deviceIndex: number, sensorIndex: number): string {
  return `SENSOR-${String(deviceIndex + 1).padStart(2, '0')}-${String(
    sensorIndex + 1,
  ).padStart(2, '0')}`;
}

function channelCount(device: DeviceLatestCard): number {
  return (
    device.latest?.sensors.reduce(
      (sum, sensor) => sum + sensor.channels.length,
      0,
    ) ?? 0
  );
}

function handleSelect(
  device: CollectorApi.DeviceSimple,
  sensor: CollectorApi.DashboardSensor,
  channel: CollectorApi.DashboardChannel,
) {
  emit('select', { channel, device, sensor });
}
</script>

<template>
  <div class="channel-matrix">
    <div class="quality-panel">
      <div class="quality-head">
        <span class="quality-title">数据质量分布 / DATA QUALITY</span>
        <span class="quality-total">{{ qualityStats.total }} 通道</span>
      </div>
      <div class="quality-bar" :aria-label="qualitySummary" role="img">
        <span
          v-for="segment in qualitySegments"
          :key="segment.key"
          class="quality-seg"
          :style="{ background: segment.color, width: `${segment.percent}%` }"
          :title="`${segment.label} ${segment.count}`"
        ></span>
        <span v-if="qualityStats.total === 0" class="quality-empty-bar"></span>
      </div>
      <div class="quality-legend">
        <span
          v-for="segment in qualitySegments"
          :key="segment.key"
          class="legend-item"
        >
          <span
            class="legend-dot"
            :style="{ background: segment.color }"
            aria-hidden="true"
          ></span>
          {{ segment.label }}
          <strong>{{ segment.count }}</strong>
        </span>
      </div>
    </div>

    <div v-if="devicesError" class="matrix-state matrix-state--error">
      {{ devicesError }}
    </div>
    <div v-else-if="!hasDevice || !hasDashboard" class="matrix-state">
      当前账号缺少
      <code>collector:device:query</code>
      或
      <code>collector:dashboard:query</code>
      权限，无法展示通道矩阵。
    </div>
    <div v-else-if="!hasAnyDevice" class="matrix-state">
      暂无可用采集设备，请先在「采集设备」中完成配置。
    </div>

    <div v-else class="matrix-scroll">
      <section
        v-for="(deviceCard, deviceIndex) in devices"
        :key="deviceCard.device.id"
        :class="
          cn('device-group', {
            'device-group--active': activeDeviceId === deviceCard.device.id,
          })
        "
      >
        <div class="device-head">
          <div class="device-title">
            <span class="device-code">{{ deviceCard.device.deviceCode }}</span>
            <span class="device-name">{{ deviceCard.device.deviceName }}</span>
            <span
              :class="
                cn('online-chip', {
                  'online-chip--off': deviceCard.latest
                    ? !deviceCard.latest.device.online
                    : true,
                })
              "
            >
              {{
                deviceCard.latest
                  ? deviceCard.latest.device.online
                    ? '在线'
                    : '离线'
                  : '状态未知'
              }}
            </span>
            <span
              v-if="activeDeviceId === deviceCard.device.id"
              class="tracking-chip"
            >
              趋势跟踪
            </span>
          </div>
          <div class="device-meta">
            <span>{{ deviceCard.latest?.sensors.length ?? 0 }} 组传感器</span>
            <span>{{ channelCount(deviceCard) }} 个通道</span>
            <span
              v-if="deviceCard.latest?.device.lastPollAt"
              class="device-time"
            >
              最近轮询
              {{ formatShanghaiHms(deviceCard.latest.device.lastPollAt) }}
            </span>
            <span v-if="deviceCard.latest" class="device-time">
              周期 {{ deviceCard.latest.device.pollIntervalMs ?? '--' }} ms
            </span>
          </div>
        </div>

        <div v-if="deviceCard.error" class="matrix-state matrix-state--error">
          {{ deviceCard.error }}（以下保留上一次成功数据）
        </div>

        <div
          v-if="!deviceCard.latest && !deviceCard.error"
          class="matrix-state"
        >
          正在读取该设备的通道最新值…
        </div>

        <div
          v-else-if="
            deviceCard.latest && deviceCard.latest.sensors.length === 0
          "
          class="matrix-state"
        >
          该设备暂无传感器通道。
        </div>

        <template v-else-if="deviceCard.latest">
          <div
            v-for="(sensor, sensorIndex) in deviceCard.latest.sensors"
            :key="sensor.id"
            class="sensor-group"
          >
            <div class="sensor-head">
              <span class="sensor-code">{{
                sensorCode(deviceIndex, sensorIndex)
              }}</span>
              <span class="sensor-name">{{
                sensor.name || '未命名传感器'
              }}</span>
              <span v-if="sensor.model" class="sensor-model">{{
                sensor.model
              }}</span>
              <span class="sensor-count">{{
                `${sensor.channels.length} 通道`
              }}</span>
            </div>
            <div class="channel-grid">
              <ChannelCard
                v-for="(channel, channelIndex) in sensor.channels"
                :key="channel.id"
                :alarm="alarmByChannel[channel.id]"
                :channel="channel"
                :device="deviceCard.device"
                :history="history[channel.id] ?? []"
                :history-stale="historyStale[channel.id] ?? false"
                :index="channelIndex"
                :latest="deviceCard.latest"
                :now="now"
                :sensor="sensor"
                :threshold="thresholds[channel.id]"
                @select="handleSelect(deviceCard.device, sensor, channel)"
              />
            </div>
            <div v-if="sensor.channels.length === 0" class="matrix-state">
              该传感器暂无通道配置。
            </div>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<style scoped>
.channel-matrix {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
}

.quality-panel {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 7px 10px;
  background: var(--scr-surface-soft, rgb(17 28 50 / 72%));
  border: 1px solid var(--scr-border, rgb(72 104 158 / 24%));
  border-radius: 6px;
}

.quality-head {
  display: flex;
  gap: 8px;
  align-items: baseline;
  justify-content: space-between;
}

.quality-title {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 9px;
  color: var(--scr-muted, #64748b);
  letter-spacing: 1.2px;
}

.quality-total {
  font-size: 10px;
  color: var(--scr-ink-soft, #b7c6dd);
}

.quality-bar {
  display: flex;
  gap: 2px;
  height: 7px;
  overflow: hidden;
  background: #0a1322;
  border-radius: 3px;
}

.quality-seg {
  min-width: 0;
  transition: width 0.4s ease;
}

.quality-empty-bar {
  flex: 1;
  background: repeating-linear-gradient(
    45deg,
    rgb(100 116 139 / 12%),
    rgb(100 116 139 / 12%) 4px,
    transparent 4px,
    transparent 8px
  );
}

.quality-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  font-size: 10px;
  color: var(--scr-muted, #64748b);
}

.legend-item {
  display: inline-flex;
  gap: 4px;
  align-items: center;
}

.legend-dot {
  width: 5px;
  height: 5px;
  border-radius: 999px;
}

.legend-item strong {
  font-variant-numeric: tabular-nums;
  color: var(--scr-ink-soft, #b7c6dd);
}

.matrix-state {
  padding: 12px 4px;
  font-size: 12px;
  color: var(--scr-muted, #64748b);
}

.matrix-state--error {
  color: #fda4af;
}

.matrix-state code {
  padding: 0 3px;
  font-size: 10px;
  color: var(--scr-ink-soft, #b7c6dd);
  background: rgb(148 163 184 / 10%);
  border-radius: 2px;
}

.matrix-scroll {
  flex: 1;
  min-height: 0;
  padding-right: 4px;
  overflow-y: auto;
  scrollbar-color: rgb(72 104 158 / 45%) transparent;
  scrollbar-width: thin;
}

.device-group {
  padding: 8px 0 10px;
  border-bottom: 1px solid rgb(72 104 158 / 16%);
}

.device-group:last-child {
  border-bottom: 0;
}

.device-group--active .device-head {
  padding-left: 8px;
  border-left: 2px solid var(--scr-primary, #22d3ee);
}

.device-head {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.device-title {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  min-width: 0;
}

.device-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 9px;
  color: var(--scr-muted, #64748b);
  letter-spacing: 1px;
}

.device-name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 600;
  color: var(--scr-ink, #e6eef9);
  white-space: nowrap;
}

.online-chip {
  padding: 0 6px;
  font-size: 9px;
  color: #2dd4bf;
  background: rgb(45 212 191 / 10%);
  border: 1px solid rgb(45 212 191 / 35%);
  border-radius: 3px;
}

.online-chip--off {
  color: #94a3b8;
  background: rgb(100 116 139 / 10%);
  border-color: rgb(100 116 139 / 32%);
}

.tracking-chip {
  padding: 0 6px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 8px;
  color: var(--scr-primary, #22d3ee);
  letter-spacing: 0.6px;
  border: 1px solid rgb(34 211 238 / 35%);
  border-radius: 3px;
}

.device-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 10px;
  color: var(--scr-muted, #64748b);
}

.device-time {
  font-variant-numeric: tabular-nums;
}

.sensor-group {
  padding-left: 6px;
  margin-top: 6px;
}

.sensor-head {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: baseline;
  margin-bottom: 5px;
}

.sensor-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 8px;
  color: var(--scr-primary, #22d3ee);
  letter-spacing: 1px;
  opacity: 0.8;
}

.sensor-name {
  font-size: 11px;
  font-weight: 500;
  color: var(--scr-ink-soft, #b7c6dd);
}

.sensor-model {
  font-size: 9px;
  color: var(--scr-muted, #64748b);
}

.sensor-count {
  margin-left: auto;
  font-size: 9px;
  color: var(--scr-muted, #64748b);
}

.channel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(168px, 1fr));
  gap: 6px;
}

@media (max-width: 1400px) {
  .channel-grid {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  }
}

@media (max-height: 800px) {
  .channel-grid {
    grid-template-columns: repeat(auto-fill, minmax(144px, 1fr));
  }

  .device-group {
    padding: 6px 0 8px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .quality-seg {
    transition: none;
  }
}
</style>
