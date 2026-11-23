<script lang="ts" setup>
import type { CollectorApi } from '#/api/collector';
import type { KioskGlobalStatus } from '../../shell/types';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import dayjs from 'dayjs';

import {
  getAlarmStateList,
  getDashboardLatest,
  getDeviceSimpleList,
} from '#/api/collector';

defineOptions({ name: 'KioskDevices' });

const emit = defineEmits<{
  statusChange: [status: KioskGlobalStatus];
}>();

interface ApiErrorLike {
  message?: string;
  response?: { data?: { msg?: string } };
}

const DEFAULT_POLL_INTERVAL_MS = 10_000;
const BACKOFF_DELAYS_MS = [10_000, 20_000, 60_000] as const;
const ALARM_TYPE_NAMES: Record<string, string> = {
  threshold_high_error: '异常上限',
  threshold_high_warning: '预警上限',
  threshold_low_error: '异常下限',
  threshold_low_warning: '预警下限',
};

const devices = ref<CollectorApi.DeviceSimple[]>([]);
const selectedDeviceId = ref<number>();
const dashboard = ref<CollectorApi.DashboardLatest>();
const alarms = ref<CollectorApi.AlarmState[]>();

const devicesLoading = ref(false);
const devicesLoaded = ref(false);
const devicesError = ref<string>();
const latestLoading = ref(false);
const latestError = ref<string>();
const alarmLoading = ref(false);
const alarmError = ref<string>();
const latestLastSuccessAt = ref<number>();
const alarmLastSuccessAt = ref<number>();
const clockNow = ref(Date.now());

let latestFailures = 0;
let alarmFailures = 0;
let latestInFlight = false;
let alarmInFlight = false;
let latestTimer: ReturnType<typeof setTimeout> | undefined;
let alarmTimer: ReturnType<typeof setTimeout> | undefined;
let clockTimer: ReturnType<typeof setInterval> | undefined;
let generation = 0;
let deviceListGeneration = 0;
let destroyed = false;

const activeAlarmCount = computed(() => alarms.value?.length);
const highestAlarmLevel = computed(() => {
  if (!alarms.value || alarms.value.length === 0) return null;
  return Math.max(...alarms.value.map((alarm) => alarm.level));
});
const latestSampleTime = computed(() => {
  let latest: number | undefined;
  for (const sensor of dashboard.value?.sensors ?? []) {
    for (const channel of sensor.channels) {
      if (!channel.sampleTime) continue;
      const sampleTime = dayjs(channel.sampleTime).valueOf();
      if (
        Number.isFinite(sampleTime) &&
        (latest === undefined || sampleTime > latest)
      ) {
        latest = sampleTime;
      }
    }
  }
  return latest;
});
const hasAnySample = computed(() => latestSampleTime.value !== undefined);
const selectedDevice = computed(() =>
  devices.value.find((device) => device.id === selectedDeviceId.value),
);
const refreshing = computed(
  () => latestLoading.value || alarmLoading.value,
);

const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  day: '2-digit',
  hour: '2-digit',
  hour12: false,
  minute: '2-digit',
  month: '2-digit',
  second: '2-digit',
  timeZone: 'Asia/Shanghai',
});

function resolveErrorMessage(error: unknown, fallback: string) {
  const candidate = error as ApiErrorLike | undefined;
  return candidate?.response?.data?.msg ?? candidate?.message ?? fallback;
}

function formatDateTime(value?: Date | null | number) {
  if (value === null || value === undefined) return '--';
  const timestamp = dayjs(value).valueOf();
  return Number.isFinite(timestamp)
    ? dateTimeFormatter.format(new Date(timestamp))
    : '--';
}

function formatInterval(value?: number) {
  if (!value) return '--';
  if (value < 1000) return `${value} 毫秒`;
  if (value < 60_000) {
    const seconds = value / 1000;
    return `${Number.isInteger(seconds) ? seconds : seconds.toFixed(1)} 秒`;
  }
  const minutes = value / 60_000;
  return `${Number.isInteger(minutes) ? minutes : minutes.toFixed(1)} 分钟`;
}

function formatWriteLag(value?: null | number) {
  return value === null || value === undefined ? '--' : `${value} ms`;
}

function channelValue(channel: CollectorApi.DashboardChannel) {
  if (channel.valueKind === 'enum') return channel.valueText ?? '--';
  return channel.valueNum ?? '--';
}

/** 接口的 stale 是请求时刻快照；两次请求之间继续按三倍轮询周期老化。 */
function isChannelStale(channel: CollectorApi.DashboardChannel) {
  if (channel.stale) return true;
  if (!channel.sampleTime) return false;
  const interval = dashboard.value?.device.pollIntervalMs ?? 60_000;
  const age = clockNow.value - dayjs(channel.sampleTime).valueOf();
  return Number.isFinite(age) && age > interval * 3;
}

function qualityText(quality?: null | number) {
  if (quality === 0) return '正常';
  if (quality === 1) return '不确定';
  if (quality === 2) return '坏质量';
  return '质量未知';
}

function alarmLevelText(level: number) {
  if (level === 3) return '异常';
  if (level === 2) return '预警';
  return `级别 ${level}`;
}

function alarmTypeText(type?: string) {
  return (type && ALARM_TYPE_NAMES[type]) || type || '--';
}

function publishStatus() {
  let connection: KioskGlobalStatus['connection'] = 'connecting';
  const hasSuccess =
    devicesLoaded.value ||
    latestLastSuccessAt.value !== undefined ||
    alarmLastSuccessAt.value !== undefined;
  if (latestError.value && alarmError.value) {
    connection = 'offline';
  } else if (devicesError.value || latestError.value || alarmError.value) {
    connection = 'degraded';
  } else if (hasSuccess) {
    connection = 'online';
  }
  emit('statusChange', {
    activeAlarmCount: activeAlarmCount.value ?? null,
    alarmDataStale: Boolean(alarmError.value && alarms.value),
    connection,
    highestAlarmLevel: highestAlarmLevel.value,
  });
}

function clearLatestTimer() {
  if (latestTimer !== undefined) clearTimeout(latestTimer);
  latestTimer = undefined;
}

function clearAlarmTimer() {
  if (alarmTimer !== undefined) clearTimeout(alarmTimer);
  alarmTimer = undefined;
}

function scheduleLatest() {
  clearLatestTimer();
  if (
    destroyed ||
    document.hidden ||
    selectedDeviceId.value === undefined
  ) {
    return;
  }
  const delay =
    latestFailures > 0
      ? BACKOFF_DELAYS_MS[Math.min(latestFailures - 1, 2)]!
      : DEFAULT_POLL_INTERVAL_MS;
  latestTimer = setTimeout(() => void refreshLatest(), delay);
}

function scheduleAlarms() {
  clearAlarmTimer();
  if (
    destroyed ||
    document.hidden ||
    selectedDeviceId.value === undefined
  ) {
    return;
  }
  const delay =
    alarmFailures > 0
      ? BACKOFF_DELAYS_MS[Math.min(alarmFailures - 1, 2)]!
      : DEFAULT_POLL_INTERVAL_MS;
  alarmTimer = setTimeout(() => void refreshAlarms(), delay);
}

/** 最新值始终单请求串行；设备代次或组件卸载后到达的响应直接丢弃。 */
async function refreshLatest() {
  const deviceId = selectedDeviceId.value;
  if (
    deviceId === undefined ||
    latestInFlight ||
    destroyed ||
    document.hidden
  ) {
    return;
  }
  clearLatestTimer();
  const requestGeneration = generation;
  latestInFlight = true;
  latestLoading.value = true;
  try {
    const body = await getDashboardLatest(deviceId);
    if (destroyed || requestGeneration !== generation) return;
    if (!body) throw new Error('最新值接口返回为空');
    dashboard.value = body;
    latestFailures = 0;
    latestError.value = undefined;
    latestLastSuccessAt.value = Date.now();
    clockNow.value = Date.now();
  } catch (error) {
    if (destroyed || requestGeneration !== generation) return;
    latestFailures += 1;
    latestError.value = resolveErrorMessage(
      error,
      '最新值获取失败，请稍后重试',
    );
    clockNow.value = Date.now();
  } finally {
    if (!destroyed && requestGeneration === generation) {
      latestInFlight = false;
      latestLoading.value = false;
      publishStatus();
      scheduleLatest();
    }
  }
}

/** 报警独立轮询和记录失败，最新值接口失败不会清空或伪造报警状态。 */
async function refreshAlarms() {
  const deviceId = selectedDeviceId.value;
  if (
    deviceId === undefined ||
    alarmInFlight ||
    destroyed ||
    document.hidden
  ) {
    return;
  }
  clearAlarmTimer();
  const requestGeneration = generation;
  alarmInFlight = true;
  alarmLoading.value = true;
  try {
    const body = await getAlarmStateList({ deviceId });
    if (destroyed || requestGeneration !== generation) return;
    alarms.value = (body ?? []).filter((alarm) => alarm.level > 0);
    alarmFailures = 0;
    alarmError.value = undefined;
    alarmLastSuccessAt.value = Date.now();
  } catch (error) {
    if (destroyed || requestGeneration !== generation) return;
    alarmFailures += 1;
    alarmError.value = resolveErrorMessage(
      error,
      '当前报警获取失败，请稍后重试',
    );
  } finally {
    if (!destroyed && requestGeneration === generation) {
      alarmInFlight = false;
      alarmLoading.value = false;
      publishStatus();
      scheduleAlarms();
    }
  }
}

function refreshAll() {
  void refreshLatest();
  void refreshAlarms();
}

function resetDeviceData() {
  generation += 1;
  clearLatestTimer();
  clearAlarmTimer();
  latestInFlight = false;
  alarmInFlight = false;
  latestLoading.value = false;
  alarmLoading.value = false;
  dashboard.value = undefined;
  alarms.value = undefined;
  latestError.value = undefined;
  alarmError.value = undefined;
  latestLastSuccessAt.value = undefined;
  alarmLastSuccessAt.value = undefined;
  latestFailures = 0;
  alarmFailures = 0;
  publishStatus();
}

function selectDevice(deviceId: number) {
  if (!devices.value.some((device) => device.id === deviceId)) return;
  selectedDeviceId.value = deviceId;
  resetDeviceData();
  refreshAll();
}

function handleDeviceChange(event: Event) {
  const deviceId = Number((event.target as HTMLSelectElement).value);
  if (Number.isFinite(deviceId)) selectDevice(deviceId);
}

async function loadDevices() {
  if (devicesLoading.value || destroyed) return;
  const requestGeneration = ++deviceListGeneration;
  devicesLoading.value = true;
  devicesError.value = undefined;
  publishStatus();
  try {
    const body = await getDeviceSimpleList();
    if (destroyed || requestGeneration !== deviceListGeneration) return;
    devices.value = body ?? [];
    devicesLoaded.value = true;
    if (devices.value.length === 0) {
      selectedDeviceId.value = undefined;
      resetDeviceData();
      return;
    }
    const retained = devices.value.find(
      (device) => device.id === selectedDeviceId.value,
    );
    selectDevice(retained?.id ?? devices.value[0]!.id);
  } catch (error) {
    if (destroyed || requestGeneration !== deviceListGeneration) return;
    devicesError.value = resolveErrorMessage(
      error,
      '设备列表获取失败，请稍后重试',
    );
  } finally {
    if (!destroyed && requestGeneration === deviceListGeneration) {
      devicesLoading.value = false;
      publishStatus();
    }
  }
}

function handleVisibilityChange() {
  clockNow.value = Date.now();
  if (document.hidden) {
    clearLatestTimer();
    clearAlarmTimer();
    return;
  }
  // 恢复可见后立即刷新；仍在进行的请求不会被重复发起。
  refreshAll();
}

onMounted(() => {
  document.addEventListener('visibilitychange', handleVisibilityChange);
  clockTimer = setInterval(() => {
    if (!document.hidden) clockNow.value = Date.now();
  }, 1000);
  publishStatus();
  void loadDevices();
});

onBeforeUnmount(() => {
  destroyed = true;
  generation += 1;
  deviceListGeneration += 1;
  clearLatestTimer();
  clearAlarmTimer();
  if (clockTimer !== undefined) clearInterval(clockTimer);
  document.removeEventListener('visibilitychange', handleVisibilityChange);
});
</script>

<template>
  <div class="device-monitor">
    <section class="device-toolbar" aria-label="设备选择与刷新">
      <label class="device-picker">
        <span>采集设备</span>
        <select
          :value="selectedDeviceId"
          :disabled="devicesLoading || devices.length === 0"
          aria-label="选择采集设备"
          @change="handleDeviceChange"
        >
          <option v-if="devices.length === 0" value="">暂无设备</option>
          <option
            v-for="device in devices"
            :key="device.id"
            :value="device.id"
          >
            {{ device.deviceName }}（{{ device.deviceCode }}）
          </option>
        </select>
      </label>
      <button
        class="refresh-button"
        type="button"
        :disabled="selectedDeviceId === undefined || refreshing"
        @click="refreshAll"
      >
        {{ refreshing ? '刷新中…' : '立即刷新' }}
      </button>
      <span class="polling-note">每 10 秒自动刷新 · 页面隐藏时暂停</span>
    </section>

    <div v-if="devicesError" class="notice error" role="alert">
      <div>
        <strong>设备列表获取失败</strong>
        <p>{{ devicesError }}</p>
      </div>
      <button type="button" @click="void loadDevices()">重试设备列表</button>
    </div>

    <section
      v-if="devicesLoading && devices.length === 0"
      class="empty-state"
      aria-live="polite"
    >
      正在读取设备列表…
    </section>
    <section
      v-else-if="devicesError && devices.length === 0"
      class="empty-state"
    >
      设备列表暂不可用，请使用上方“重试设备列表”。
    </section>
    <section
      v-else-if="devicesLoaded && devices.length === 0"
      class="empty-state"
    >
      <strong>暂无设备</strong>
      <span>当前账号可查询的设备列表为空，请先在管理端完成设备配置。</span>
    </section>

    <template v-else-if="selectedDevice">
      <div v-if="latestError" class="notice error" role="alert">
        <div>
          <strong>最新值获取失败</strong>
          <p>
            {{ latestError }}；{{
              dashboard
                ? `页面保留最后成功数据（${formatDateTime(latestLastSuccessAt)}），不会视为当前采样。`
                : '尚无成功数据，恢复后将自动重试。'
            }}
          </p>
        </div>
      </div>

      <section v-if="dashboard" class="device-summary">
        <div class="summary-heading">
          <div>
            <h2>{{ dashboard.device.name }}</h2>
            <span class="device-code">{{ selectedDevice.deviceCode }}</span>
          </div>
          <div class="status-tags">
            <span
              class="tag"
              :class="dashboard.device.online ? 'ok' : 'danger'"
            >
              {{ dashboard.device.online ? '在线' : '离线' }}
            </span>
            <span v-if="dashboard.device.staleOffline" class="tag warning">
              采样超时
            </span>
          </div>
        </div>
        <dl class="device-metrics">
          <div>
            <dt>最后轮询</dt>
            <dd>{{ formatDateTime(dashboard.device.lastPollAt) }}</dd>
          </div>
          <div>
            <dt>最后数据更新</dt>
            <dd>{{ formatDateTime(latestSampleTime) }}</dd>
          </div>
          <div>
            <dt>采集器写入延迟（进程级）</dt>
            <dd>{{ formatWriteLag(dashboard.device.lastWriteLagMs) }}</dd>
          </div>
          <div>
            <dt>连续失败</dt>
            <dd>{{ dashboard.device.consecutiveFailures }} 次</dd>
          </div>
          <div>
            <dt>当前采集间隔</dt>
            <dd>{{ formatInterval(dashboard.device.pollIntervalMs) }}</dd>
          </div>
          <div>
            <dt>最新值最后成功请求</dt>
            <dd>{{ formatDateTime(latestLastSuccessAt) }}</dd>
          </div>
        </dl>
      </section>
      <section v-else-if="latestLoading" class="empty-state">
        正在读取设备最新值…
      </section>
      <section v-else class="empty-state">
        <strong>暂无最新值</strong>
        <span>设备已选择，但尚未取得最新值数据。</span>
      </section>

      <section class="panel alarm-panel">
        <div class="panel-heading">
          <div>
            <h2>当前报警</h2>
            <span class="panel-caption">只读展示，不提供确认、清除或处置</span>
          </div>
          <span v-if="alarms" class="alarm-total" :class="{ active: alarms.length > 0 }">
            {{ alarms.length }} 项
            <template v-if="highestAlarmLevel">
              · 最高{{ alarmLevelText(highestAlarmLevel) }}
            </template>
          </span>
        </div>
        <div v-if="alarmError" class="inline-error" role="alert">
          报警获取失败：{{ alarmError }}；
          {{
            alarms
              ? `以下保留最后成功数据（${formatDateTime(alarmLastSuccessAt)}）。`
              : '尚无成功报警数据。'
          }}
        </div>
        <div v-if="!alarms && alarmLoading" class="compact-empty">
          正在读取当前报警…
        </div>
        <div v-else-if="!alarms" class="compact-empty">
          当前报警状态暂不可用。
        </div>
        <div v-else-if="alarms.length === 0" class="compact-empty no-alarm">
          当前无活动报警。
        </div>
        <div v-else class="alarm-list">
          <article v-for="alarm in alarms" :key="alarm.channelId" class="alarm-row">
            <div class="alarm-channel">
              <strong>{{ alarm.channelName || `通道 ${alarm.channelId}` }}</strong>
              <span>
                {{ alarm.sensorName || `传感器 ${alarm.sensorId}` }} ·
                {{ alarmTypeText(alarm.thresholdType) }}
              </span>
            </div>
            <span class="tag" :class="alarm.level === 3 ? 'danger' : 'warning'">
              {{ alarmLevelText(alarm.level) }}
            </span>
            <div class="alarm-value">
              <span>最近值 {{ alarm.lastValue ?? '--' }}</span>
              <span>阈值 {{ alarm.lastThreshold ?? '--' }}</span>
            </div>
            <div class="alarm-time">
              <span>最近采样 {{ formatDateTime(alarm.lastSampleTime) }}</span>
              <span>开始于 {{ formatDateTime(alarm.since) }}</span>
            </div>
          </article>
        </div>
      </section>

      <section v-if="dashboard" class="channel-section">
        <div class="section-heading">
          <h2>传感器通道</h2>
          <span>数值均来自最近一次成功最新值请求</span>
        </div>
        <div v-if="dashboard.sensors.length === 0" class="empty-state">
          <strong>当前设备没有传感器</strong>
          <span>这与接口失败不同；请在管理端检查传感器配置。</span>
        </div>
        <template v-else>
          <div v-if="!hasAnySample" class="notice warning">
            当前设备已有传感器配置，但所有通道均无采样数据。
          </div>
          <article
            v-for="sensor in dashboard.sensors"
            :key="sensor.id"
            class="panel sensor-panel"
          >
            <div class="panel-heading">
              <div>
                <h3>{{ sensor.name }}</h3>
                <span v-if="sensor.model" class="panel-caption">
                  {{ sensor.model }}
                </span>
              </div>
              <span class="channel-count">{{ sensor.channels.length }} 个通道</span>
            </div>
            <div v-if="sensor.channels.length === 0" class="compact-empty">
              当前传感器没有通道。
            </div>
            <div v-else class="channel-table-wrap">
              <div class="channel-table" role="table" :aria-label="`${sensor.name}通道最新值`">
                <div class="channel-row channel-header" role="row">
                  <span role="columnheader">通道</span>
                  <span role="columnheader">当前值</span>
                  <span role="columnheader">单位</span>
                  <span role="columnheader">采样时间</span>
                  <span role="columnheader">质量 / 数据状态</span>
                </div>
                <div
                  v-for="channel in sensor.channels"
                  :key="channel.id"
                  class="channel-row"
                  role="row"
                >
                  <strong role="cell">{{ channel.name }}</strong>
                  <span class="channel-value" role="cell">
                    {{ channelValue(channel) }}
                  </span>
                  <span role="cell">{{ channel.unit || '--' }}</span>
                  <span class="sample-time" role="cell">
                    {{ formatDateTime(channel.sampleTime) }}
                  </span>
                  <span class="channel-state" role="cell">
                    <template v-if="channel.sampleTime">
                      <span
                        class="tag"
                        :class="channel.quality === 0 ? 'ok' : 'warning'"
                      >
                        {{ qualityText(channel.quality) }}
                      </span>
                      <span v-if="isChannelStale(channel)" class="tag warning">
                        陈旧
                      </span>
                      <small v-if="latestError">最后成功值</small>
                    </template>
                    <span v-else class="no-data">无数据</span>
                  </span>
                </div>
              </div>
            </div>
          </article>
        </template>
      </section>
    </template>
  </div>
</template>

<style scoped>
.device-monitor {
  display: flex;
  min-height: 100%;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  color: #dbe7f5;
}

.device-toolbar {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.device-picker {
  display: flex;
  min-width: 280px;
  flex: 1;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 600;
}

.device-picker select {
  width: min(360px, 100%);
  min-height: 48px;
  flex: 1;
  padding: 0 38px 0 12px;
  color: #f1f5f9;
  font-size: 15px;
  background: #142237;
  border: 1px solid #3a4d68;
  border-radius: 8px;
}

button {
  min-width: 44px;
  min-height: 44px;
  padding: 8px 16px;
  color: #e0f2fe;
  font: inherit;
  font-weight: 600;
  touch-action: manipulation;
  background: #155e75;
  border: 1px solid #0e7490;
  border-radius: 8px;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.polling-note {
  color: #8ea1b9;
  font-size: 12px;
}

.notice,
.inline-error {
  padding: 10px 12px;
  color: #fde68a;
  font-size: 13px;
  background: #422006;
  border: 1px solid #a16207;
  border-radius: 8px;
}

.notice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.notice p {
  margin: 4px 0 0;
  line-height: 1.45;
}

.notice.error,
.inline-error {
  color: #fecdd3;
  background: #4c0519;
  border-color: #be123c;
}

.notice.warning {
  color: #fde68a;
}

.device-summary,
.panel {
  background: #101e30;
  border: 1px solid #263a53;
  border-radius: 10px;
}

.device-summary {
  padding: 14px 16px;
}

.summary-heading,
.panel-heading,
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.summary-heading h2,
.panel-heading h2,
.panel-heading h3,
.section-heading h2 {
  display: inline;
  margin: 0;
  color: #f1f5f9;
  font-size: 17px;
}

.summary-heading h2 {
  margin-right: 10px;
  font-size: 20px;
}

.device-code,
.panel-caption,
.section-heading > span,
.channel-count {
  color: #8ea1b9;
  font-size: 12px;
}

.status-tags,
.channel-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.tag {
  display: inline-flex;
  min-height: 26px;
  align-items: center;
  padding: 2px 8px;
  color: #cbd5e1;
  font-size: 12px;
  font-weight: 650;
  white-space: nowrap;
  background: #334155;
  border-radius: 5px;
}

.tag.ok {
  color: #a7f3d0;
  background: #064e3b;
}

.tag.warning {
  color: #fde68a;
  background: #713f12;
}

.tag.danger {
  color: #fecdd3;
  background: #9f1239;
}

.device-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px 18px;
  margin: 14px 0 0;
}

.device-metrics div {
  min-width: 0;
}

.device-metrics dt {
  color: #8ea1b9;
  font-size: 12px;
}

.device-metrics dd {
  margin: 4px 0 0;
  overflow: hidden;
  color: #e2e8f0;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel {
  padding: 12px;
}

.panel-heading {
  margin-bottom: 10px;
}

.alarm-panel {
  margin-top: 12px;
}

.alarm-total {
  color: #a7f3d0;
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
}

.alarm-total.active {
  color: #fda4af;
}

.inline-error {
  margin-bottom: 10px;
  line-height: 1.45;
}

.alarm-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.alarm-row {
  display: grid;
  grid-template-columns: minmax(150px, 1.2fr) auto minmax(110px, 0.7fr) minmax(160px, 1fr);
  align-items: center;
  gap: 10px;
  min-height: 52px;
  padding: 8px 10px;
  background: #2b1624;
  border-left: 3px solid #fb7185;
  border-radius: 6px;
}

.alarm-channel,
.alarm-value,
.alarm-time {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.alarm-channel span,
.alarm-value,
.alarm-time {
  color: #b9c8da;
  font-size: 11px;
}

.alarm-value,
.alarm-time {
  font-variant-numeric: tabular-nums;
}

.channel-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
}

.sensor-panel {
  padding: 10px 12px;
}

.channel-table-wrap {
  overflow-x: auto;
}

.channel-table {
  min-width: 610px;
}

.channel-row {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) minmax(80px, 0.7fr) 64px 148px minmax(145px, 1fr);
  min-height: 46px;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-top: 1px solid #263a53;
}

.channel-header {
  min-height: 34px;
  padding-block: 4px;
  color: #8ea1b9;
  font-size: 12px;
  border-top: 0;
}

.channel-value {
  color: #f0f9ff;
  font-size: 20px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.sample-time {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.channel-state small {
  width: 100%;
  color: #fda4af;
  font-size: 10px;
}

.no-data {
  color: #94a3b8;
  font-size: 12px;
}

.empty-state,
.compact-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  text-align: center;
  background: #101e30;
  border: 1px dashed #334a66;
  border-radius: 9px;
}

.empty-state {
  min-height: 104px;
  flex-direction: column;
  gap: 8px;
  padding: 20px;
}

.empty-state strong {
  color: #dbe7f5;
  font-size: 17px;
}

.compact-empty {
  min-height: 48px;
  padding: 10px;
  font-size: 13px;
}

.compact-empty.no-alarm {
  color: #a7f3d0;
  border-color: #065f46;
}

@media (max-width: 900px) {
  .device-monitor {
    gap: 10px;
    padding: 10px;
  }

  .polling-note {
    width: 100%;
  }

  .device-summary {
    padding: 12px;
  }

  .device-metrics {
    gap: 10px 12px;
  }

  .alarm-row {
    grid-template-columns: minmax(130px, 1fr) auto minmax(110px, 0.8fr);
  }

  .alarm-time {
    grid-column: 1 / -1;
    flex-direction: row;
    gap: 16px;
  }
}

@media (max-width: 640px) {
  .device-picker {
    min-width: 100%;
  }

  .device-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .summary-heading,
  .section-heading {
    align-items: flex-start;
  }

  .alarm-row {
    grid-template-columns: 1fr auto;
  }

  .alarm-value,
  .alarm-time {
    grid-column: 1 / -1;
  }
}
</style>
