<script lang="ts" setup>
import type { CollectorApi } from '#/api/collector';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { useAccess } from '@vben/access';

import { App, Tag } from 'antdv-next';

import {
  getAlarmStateList,
  getDashboardLatest,
  getDeviceSimpleList,
  getHealthSummary,
} from '#/api/collector';
import ActiveAlarmPanel from '#/views/collector/components/active-alarm-panel.vue';
import {
  formatNumeric,
  formatShanghaiTime,
  resolveApiErrorMessage,
} from '#/views/collector/components/utils';

defineOptions({ name: 'CollectorScreen' });

const POLL_INTERVAL_MS = 10_000;
const BACKOFF_DELAYS = [10_000, 20_000, 60_000] as const;

const route = useRoute();
const { hasAccessByCodes } = useAccess();
const { message } = App.useApp();
/** /screen 为独立无布局大屏；/dashboard/analytics 在 BasicLayout 内复用同一内容 */
const isStandaloneScreen = computed(() => route.path === '/screen');
const hasHealthAccess = computed(() =>
  hasAccessByCodes(['collector:health:query']),
);
const hasAlarmAccess = computed(() =>
  hasAccessByCodes(['collector:alarm:query']),
);
const hasDeviceAccess = computed(() =>
  hasAccessByCodes(['collector:device:query']),
);
const hasDashboardAccess = computed(() =>
  hasAccessByCodes(['collector:dashboard:query']),
);

const screenRoot = ref<HTMLElement>();
const isFullscreen = ref(false);

const devices = ref<CollectorApi.DeviceSimple[]>([]);
const devicesLoaded = ref(false);
const devicesError = ref<string>();

const health = ref<CollectorApi.HealthSummary>();
const healthError = ref<string>();

const alarms = ref<CollectorApi.AlarmState[]>([]);
const alarmsError = ref<string>();

const latestMap = ref<Record<number, CollectorApi.DashboardLatest>>({});
const deviceErrors = ref<Record<number, string>>({});

const lastSuccessAt = ref<number>();
const connectionState = ref<'error' | 'ok'>('ok');
const failureCount = ref(0);
const nextPollMs = ref(POLL_INTERVAL_MS);
const clockNow = ref(Date.now());

let pollTimer: ReturnType<typeof setTimeout> | undefined;
let clockTimer: ReturnType<typeof setInterval> | undefined;
let inFlight = false;
let destroyed = false;

const onlineRatioText = computed(() => {
  const process = health.value?.process;
  if (!process || !process.deviceTotal) return '--';
  return `${process.deviceOnline ?? '--'} / ${process.deviceTotal}`;
});

const activeAlarmText = computed(() =>
  health.value ? String(health.value.alarm.activeCount) : '--',
);

const highestLevel = computed(() => health.value?.alarm.highestLevel ?? 0);
const highestLevelText = computed(() => {
  if (highestLevel.value >= 3) return '异常';
  if (highestLevel.value === 2) return '预警';
  if (highestLevel.value === 0 && health.value) return '无';
  return '--';
});
const highestLevelColor = computed(() => {
  if (highestLevel.value >= 3) return '#f87171';
  if (highestLevel.value === 2) return '#fbbf24';
  return '#4ade80';
});

const heartbeatText = computed(() => {
  const process = health.value?.process;
  if (!process) return healthError.value ? '--' : '尚未收到心跳';
  return formatShanghaiTime(process.heartbeatTime);
});

const writeLagText = computed(() => {
  const lag = health.value?.process?.writeLagMs;
  return lag === null || lag === undefined ? '--' : `${lag} ms`;
});

const lastSuccessText = computed(() =>
  lastSuccessAt.value ? formatShanghaiTime(lastSuccessAt.value) : '尚未成功刷新',
);

const connectionText = computed(() =>
  connectionState.value === 'ok' ? '已连接' : '数据可能已过期',
);

const retryText = computed(() => {
  if (connectionState.value === 'ok') {
    return `自动刷新 ${Math.round(nextPollMs.value / 1000)} 秒`;
  }
  return `将在 ${Math.round(nextPollMs.value / 1000)} 秒后重试`;
});

const deviceCards = computed(() =>
  devices.value.map((device) => {
    const latest = latestMap.value[device.id];
    return {
      device,
      latest,
      error: deviceErrors.value[device.id],
    };
  }),
);

function mainChannel(
  latest?: CollectorApi.DashboardLatest,
): CollectorApi.DashboardChannel | undefined {
  const channels = latest?.sensors.flatMap((sensor) => sensor.channels) ?? [];
  return channels.find((channel) => channel.sampleTime) ?? channels[0];
}

function channelValue(channel?: CollectorApi.DashboardChannel): string {
  if (!channel) return '--';
  if (channel.valueKind === 'enum') return channel.valueText ?? '--';
  return formatNumeric(channel.valueNum);
}

function isChannelStale(
  channel: CollectorApi.DashboardChannel | undefined,
  latest?: CollectorApi.DashboardLatest,
): boolean {
  if (!channel) return false;
  if (channel.stale) return true;
  if (!channel.sampleTime) return false;
  const interval = latest?.device.pollIntervalMs ?? 60_000;
  return clockNow.value - channel.sampleTime > interval * 3;
}

function qualityInfo(quality?: null | number) {
  if (quality === null || quality === undefined) {
    return { color: '#64748b', text: '暂无数据' };
  }
  if (quality === 2) return { color: '#f87171', text: '坏质量' };
  if (quality === 1) return { color: '#fbbf24', text: '不确定' };
  return { color: '#4ade80', text: '正常' };
}

function clearPollTimer() {
  if (pollTimer !== undefined) clearTimeout(pollTimer);
  pollTimer = undefined;
}

function scheduleNext() {
  clearPollTimer();
  if (destroyed || document.hidden) return;
  const delay =
    failureCount.value > 0
      ? (BACKOFF_DELAYS[Math.min(failureCount.value - 1, 2)] ?? 60_000)
      : POLL_INTERVAL_MS;
  nextPollMs.value = delay;
  pollTimer = setTimeout(() => void refreshAll(), delay);
}

async function refreshAll() {
  if (inFlight || destroyed || document.hidden) return;
  inFlight = true;
  clearPollTimer();
  const failures: unknown[] = [];
  try {
    // 设备列表仅在首次或列表为空时加载；设备变更可通过刷新页面重新拉取
    if (hasDeviceAccess.value && !devicesLoaded.value) {
      try {
        devices.value = await getDeviceSimpleList();
        devicesLoaded.value = true;
        devicesError.value = undefined;
      } catch (error) {
        devices.value = [];
        devicesError.value = resolveApiErrorMessage(error, '设备加载失败');
        failures.push(error);
      }
    }

    const tasks: Promise<unknown>[] = [];

    if (hasHealthAccess.value) {
      const deviceId = devices.value[0]?.id;
      if (deviceId === undefined) {
        healthError.value = devicesError.value
          ? '设备加载失败，无法读取运行摘要'
          : '暂无采集设备，无法读取运行摘要';
      } else {
        tasks.push(
          (async () => {
            try {
              const body = await getHealthSummary(deviceId);
              if (!body) throw new Error('运行摘要接口返回为空');
              health.value = body;
              healthError.value = undefined;
            } catch (error) {
              // 保留上一次运行摘要，不伪造健康状态
              healthError.value = resolveApiErrorMessage(
                error,
                '运行摘要加载失败',
              );
              failures.push(error);
            }
          })(),
        );
      }
    }

    if (hasAlarmAccess.value) {
      tasks.push(
        (async () => {
          try {
            alarms.value = (await getAlarmStateList()) ?? [];
            alarmsError.value = undefined;
          } catch (error) {
            // 保留上一次报警列表，并标记数据可能过期
            alarmsError.value = resolveApiErrorMessage(
              error,
              '当前报警加载失败',
            );
            failures.push(error);
          }
        })(),
      );
    }

    if (hasDashboardAccess.value && devices.value.length > 0) {
      tasks.push(
        (async () => {
          const results = await Promise.allSettled(
            devices.value.map((device) => getDashboardLatest(device.id)),
          );
          const nextLatest = { ...latestMap.value };
          const nextErrors = { ...deviceErrors.value };
          devices.value.forEach((device, index) => {
            const result = results[index];
            if (result?.status === 'fulfilled' && result.value) {
              nextLatest[device.id] = result.value;
              delete nextErrors[device.id];
            } else {
              nextErrors[device.id] = resolveApiErrorMessage(
                result?.status === 'rejected' ? result.reason : undefined,
                '最新值加载失败',
              );
              failures.push(
                result?.status === 'rejected' ? result.reason : undefined,
              );
            }
          });
          latestMap.value = nextLatest;
          deviceErrors.value =
            Object.keys(nextErrors).length > 0 ? nextErrors : {};
        })(),
      );
    }

    await Promise.all(tasks);

    if (failures.length === 0) {
      failureCount.value = 0;
      connectionState.value = 'ok';
      lastSuccessAt.value = Date.now();
    } else {
      failureCount.value += 1;
      connectionState.value = 'error';
    }
  } finally {
    inFlight = false;
    if (!destroyed) {
      scheduleNext();
    }
  }
}

function handleVisibilityChange() {
  if (document.hidden) {
    clearPollTimer();
    return;
  }
  clockNow.value = Date.now();
  void refreshAll();
}

async function toggleFullscreen() {
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen();
    } else if (screenRoot.value) {
      await screenRoot.value.requestFullscreen();
    }
  } catch {
    message.warning('全屏切换失败，请按 F11 手动切换全屏');
  }
}

function handleFullscreenChange() {
  isFullscreen.value = Boolean(document.fullscreenElement);
}

/** BasicLayout 内提供进入独立大屏的入口，避免把内容页当作整页大屏裁切 */
function openStandaloneScreen() {
  window.open('/screen', '_blank', 'noopener,noreferrer');
}

onMounted(() => {
  document.addEventListener('visibilitychange', handleVisibilityChange);
  document.addEventListener('fullscreenchange', handleFullscreenChange);
  clockTimer = setInterval(() => {
    if (!document.hidden) {
      clockNow.value = Date.now();
    }
  }, 1000);
  void refreshAll();
});

onBeforeUnmount(() => {
  destroyed = true;
  clearPollTimer();
  if (clockTimer !== undefined) clearInterval(clockTimer);
  document.removeEventListener('visibilitychange', handleVisibilityChange);
  document.removeEventListener('fullscreenchange', handleFullscreenChange);
  if (document.fullscreenElement) {
    void document.exitFullscreen().catch(() => {});
  }
});
</script>

<template>
  <div
    ref="screenRoot"
    class="screen-root"
    :class="{ 'screen-root--standalone': isStandaloneScreen }"
  >
    <header class="screen-header">
      <div class="brand">
        <h1>采集器运行大屏</h1>
        <span class="brand-sub">只读 · 每 10 秒自动刷新</span>
      </div>
      <div class="header-right">
        <span class="clock">{{ formatShanghaiTime(clockNow) }}</span>
        <span class="connection" :class="{ 'connection--error': connectionState === 'error' }">
          <span class="dot" aria-hidden="true"></span>
          {{ connectionText }}
        </span>
        <button
          v-if="!isStandaloneScreen"
          type="button"
          class="screen-fullscreen"
          @click="openStandaloneScreen"
        >
          全屏大屏
        </button>
        <button
          type="button"
          class="screen-fullscreen"
          @click="toggleFullscreen"
        >
          {{ isFullscreen ? '退出全屏' : '全屏' }}
        </button>
      </div>
    </header>

    <main class="screen-body">
      <div class="screen-left">
        <section class="metric-grid">
          <div v-if="!hasHealthAccess" class="metric metric--note">
            <span class="metric-label">运行指标</span>
            <span class="metric-note">
              无 collector:health:query 权限，隐藏在线设备、心率与延迟
            </span>
          </div>
          <template v-else>
            <div class="metric">
              <span class="metric-label">在线设备 / 总数</span>
              <span class="metric-value">{{ onlineRatioText }}</span>
            </div>
            <div class="metric">
              <span class="metric-label">活动报警数</span>
              <span class="metric-value">{{ activeAlarmText }}</span>
            </div>
            <div class="metric">
              <span class="metric-label">最高级别</span>
              <span class="metric-value" :style="{ color: highestLevelColor }">
                {{ highestLevelText }}
              </span>
            </div>
            <div class="metric">
              <span class="metric-label">心跳时间</span>
              <span class="metric-value metric-value--small">{{ heartbeatText }}</span>
            </div>
            <div class="metric">
              <span class="metric-label">写入延迟</span>
              <span class="metric-value metric-value--small">{{ writeLagText }}</span>
            </div>
          </template>
        </section>

        <div v-if="connectionState === 'error'" class="stale-banner">
          连接异常，数据可能已过期；页面保留最后一次成功数据，{{ retryText }}。
        </div>

        <section class="device-area">
          <div class="section-title">
            <span>设备概览</span>
            <span class="section-sub">
              {{
                hasDeviceAccess && hasDashboardAccess
                  ? `共 ${devices.length} 台设备`
                  : '设备卡片需要 collector:device:query 与 collector:dashboard:query'
              }}
            </span>
          </div>
          <div v-if="devicesError" class="state-text state-text--error">
            {{ devicesError }}
          </div>
          <div
            v-else-if="!hasDeviceAccess || !hasDashboardAccess"
            class="state-text"
          >
            当前账号缺少设备或最新值看板权限，无法展示设备卡片。
          </div>
          <div v-else-if="devices.length === 0" class="state-text">
            暂无可用采集设备，请先在「采集设备」中完成配置。
          </div>
          <div v-else class="device-grid">
            <div
              v-for="card in deviceCards"
              :key="card.device.id"
              class="device-card"
            >
              <div class="device-head">
                <span class="device-name">{{ card.device.deviceName }}</span>
                <Tag
                  v-if="card.latest"
                  :color="card.latest.device.online ? 'success' : 'error'"
                >
                  {{ card.latest.device.online ? '在线' : '离线' }}
                </Tag>
                <span v-else class="device-unknown">状态未知</span>
              </div>

              <div v-if="card.error" class="device-error">
                {{ card.error }}
              </div>

              <template v-if="mainChannel(card.latest)">
                <div class="device-main">
                  <span class="device-channel">
                    {{ mainChannel(card.latest)?.name }}
                  </span>
                  <span class="device-value">
                    {{ channelValue(mainChannel(card.latest)) }}
                    <span class="device-unit">
                      {{ mainChannel(card.latest)?.unit || '' }}
                    </span>
                  </span>
                </div>
                <div class="device-meta">
                  <span
                    class="quality"
                    :style="{
                      color: qualityInfo(mainChannel(card.latest)?.quality).color,
                    }"
                  >
                    {{ qualityInfo(mainChannel(card.latest)?.quality).text }}
                  </span>
                  <span
                    v-if="isChannelStale(mainChannel(card.latest), card.latest)"
                    class="stale-tag"
                  >
                    陈旧
                  </span>
                  <span class="device-time">
                    {{ formatShanghaiTime(mainChannel(card.latest)?.sampleTime) }}
                  </span>
                </div>
              </template>
              <div v-else-if="!card.error" class="device-empty">
                暂无通道最新值
              </div>
            </div>
          </div>
        </section>
      </div>

      <aside class="alarms">
        <ActiveAlarmPanel
          :alarms="alarms"
          :error="alarmsError"
          dark
          max-height="100%"
          readonly
          title="当前报警"
          @refresh="refreshAll"
        />
      </aside>
    </main>

    <footer class="screen-footer">
      <span>最后成功更新：{{ lastSuccessText }}</span>
      <span v-if="alarmsError || healthError" class="footer-error">
        部分数据刷新失败，保留旧数据
      </span>
      <span class="footer-right">{{ retryText }}</span>
    </footer>
  </div>
</template>

<style scoped>
.screen-root {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-height: 0;
  padding: 18px 22px;
  overflow: auto;
  color: #e5edf8;
  background:
    radial-gradient(circle at 20% 0%, rgb(37 99 235 / 18%), transparent 45%),
    radial-gradient(circle at 90% 10%, rgb(14 165 233 / 12%), transparent 40%),
    #0b1220;
}

/* 独立 /screen 路由无布局，按视口铺满且不出现整页滚动 */
.screen-root--standalone {
  height: 100vh;
  overflow: hidden;
}

.screen-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.brand {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.brand h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 2px;
}

.brand-sub {
  font-size: 12px;
  color: #8fa3ba;
}

.header-right {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
}

.clock {
  font-size: 18px;
  font-variant-numeric: tabular-nums;
}

.connection {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  font-size: 12px;
  color: #4ade80;
  background: rgb(74 222 128 / 12%);
  border: 1px solid rgb(74 222 128 / 35%);
  border-radius: 999px;
}

.connection--error {
  color: #fda4af;
  background: rgb(248 113 113 / 12%);
  border-color: rgb(248 113 113 / 40%);
}

.dot {
  width: 8px;
  height: 8px;
  background: currentColor;
  border-radius: 999px;
}

.screen-fullscreen {
  padding: 4px 12px;
  font: inherit;
  font-size: 13px;
  color: #dbe7f5;
  cursor: pointer;
  background: #1b2a45;
  border: 1px solid #3a5a86;
  border-radius: 6px;
}

.screen-fullscreen:hover {
  background: #24395c;
}

.screen-body {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(0, 2.4fr) minmax(280px, 1fr);
  gap: 14px;
  min-height: 0;
}

.screen-left {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.metric {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  background: rgb(20 33 54 / 82%);
  border: 1px solid #223047;
  border-radius: 10px;
}

.metric-label {
  font-size: 12px;
  color: #8fa3ba;
}

.metric-value {
  font-size: 24px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.metric-value--small {
  font-size: 16px;
  font-weight: 600;
}

.metric--note {
  grid-column: 1 / -1;
  justify-content: center;
}

.metric-note {
  font-size: 13px;
  color: #8fa3ba;
}

.stale-banner {
  padding: 8px 12px;
  font-size: 13px;
  color: #fca5a5;
  background: rgb(248 113 113 / 12%);
  border: 1px solid rgb(248 113 113 / 35%);
  border-radius: 8px;
}

.device-area {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.section-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 15px;
  font-weight: 600;
}

.section-sub {
  font-size: 12px;
  font-weight: 400;
  color: #8fa3ba;
}

.state-text {
  padding: 20px 4px;
  font-size: 14px;
  color: #8fa3ba;
}

.state-text--error {
  color: #fda4af;
}

.device-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
  min-height: 0;
  padding-bottom: 4px;
  overflow-y: auto;
}

.device-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  background: rgb(20 33 54 / 82%);
  border: 1px solid #223047;
  border-radius: 10px;
}

.device-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
}

.device-name {
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-unknown {
  font-size: 12px;
  color: #8fa3ba;
}

.device-error {
  font-size: 12px;
  color: #fda4af;
}

.device-main {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.device-channel {
  overflow: hidden;
  font-size: 12px;
  color: #8fa3ba;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-value {
  font-size: 26px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.device-unit {
  font-size: 13px;
  font-weight: 400;
  color: #8fa3ba;
}

.device-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.quality {
  font-weight: 600;
}

.stale-tag {
  padding: 1px 6px;
  color: #fbbf24;
  border: 1px solid rgb(251 191 36 / 40%);
  border-radius: 6px;
}

.device-time {
  margin-left: auto;
  color: #8fa3ba;
}

.device-empty {
  font-size: 12px;
  color: #8fa3ba;
}

.alarms {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 14px;
  overflow: hidden;
  background: rgb(15 25 43 / 88%);
  border: 1px solid #223047;
  border-radius: 10px;
}

.alarms :deep(.active-alarm-panel) {
  flex: 1;
  min-height: 0;
}

.alarms :deep(.ant-empty-description) {
  color: #8fa3ba;
}

.alarms :deep(.ant-empty-img-simple-ellipse),
.alarms :deep(.ant-empty-img-simple-path),
.alarms :deep(.ant-empty-img-simple-g) {
  opacity: 0.25;
}

.screen-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  font-size: 12px;
  color: #8fa3ba;
}

.footer-error {
  color: #fda4af;
}

.footer-right {
  margin-left: auto;
}

/* 1024×600 等小尺寸：缩小字号与留白，指标改为 3 列 */
@media (max-width: 1280px), (max-height: 720px) {
  .screen-root {
    gap: 8px;
    padding: 12px 14px;
  }

  .brand h1 {
    font-size: 20px;
    letter-spacing: 1px;
  }

  .clock {
    font-size: 14px;
  }

  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .metric-value {
    font-size: 20px;
  }

  .device-grid {
    grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
    gap: 8px;
  }

  .device-value {
    font-size: 22px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .screen-root,
  .screen-root * {
    transition: none !important;
    animation: none !important;
  }
}
</style>
