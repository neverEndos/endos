<script lang="ts" setup>
import type { CollectorApi } from '#/api/collector';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { ReloadOutlined, WarningOutlined } from '@vben/icons';

import { Alert, Button, Card, Empty, Select, Spin, Tag } from 'antdv-next';
import dayjs from 'dayjs';

import { getDashboardLatest, getDeviceSimpleList } from '#/api/collector';

defineOptions({ name: 'CollectorDashboard' });

const POLL_INTERVAL_OPTIONS = [
  { label: '5秒', value: 5000 },
  { label: '10秒', value: 10000 },
  { label: '30秒', value: 30000 },
  { label: '1分钟', value: 60000 },
  { label: '关闭自动刷新', value: 0 },
];
const BACKOFF_DELAYS = [5000, 10000, 30000] as const;
const STORAGE_KEY = 'collector.dashboard.poll-interval-ms';
const DEFAULT_POLL_INTERVAL = 10000;

function readStoredInterval() {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === null) return DEFAULT_POLL_INTERVAL;
    const value = Number(stored);
    return POLL_INTERVAL_OPTIONS.some((option) => option.value === value)
      ? value
      : DEFAULT_POLL_INTERVAL;
  } catch {
    return DEFAULT_POLL_INTERVAL;
  }
}

const devices = ref<CollectorApi.DeviceSimple[]>([]);
const selectedDeviceId = ref<number>();
const pollInterval = ref(readStoredInterval());
const dashboard = ref<CollectorApi.DashboardLatest>();
const devicesLoading = ref(false);
const initialLoading = ref(false);
const manualLoading = ref(false);
const connectionError = ref(false);
const requestFailures = ref(0);
const nextRetryDelayMs = ref<number>();
const lastSuccessAt = ref<number>();
const clockNow = ref(Date.now());

let pollTimer: ReturnType<typeof setTimeout> | undefined;
let clockTimer: ReturnType<typeof setInterval> | undefined;
let inFlight = false;
let destroyed = false;
let generation = 0;
let versionToken = '';
let versionDeviceId: number | undefined;

const deviceOptions = computed(() =>
  devices.value.map((device) => ({
    label: device.deviceName,
    value: device.id,
  })),
);
const hasDashboard = computed(() => dashboard.value !== undefined);
const lastSuccessText = computed(() =>
  lastSuccessAt.value
    ? dayjs(lastSuccessAt.value).format('YYYY-MM-DD HH:mm:ss')
    : '--',
);
const latestSampleTime = computed(() => {
  let latest: number | undefined;
  for (const sensor of dashboard.value?.sensors ?? []) {
    for (const channel of sensor.channels) {
      if (!channel.sampleTime) continue;
      const value = dayjs(channel.sampleTime).valueOf();
      if (Number.isFinite(value) && (latest === undefined || value > latest)) {
        latest = value;
      }
    }
  }
  return latest;
});
const retryText = computed(() => {
  if (pollInterval.value === 0) return '自动刷新已关闭，可点击手动刷新重试';
  return nextRetryDelayMs.value
    ? `将在 ${Math.round(nextRetryDelayMs.value / 1000)} 秒后重试`
    : '正在准备重试';
});

function formatDateTime(value?: null | number | string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '--';
}

function formatInterval(value?: number) {
  if (!value) return '--';
  return value < 60000 ? `${value / 1000} 秒` : `${value / 60000} 分钟`;
}

function formatValue(channel: CollectorApi.DashboardChannel) {
  if (channel.valueKind === 'enum') return channel.valueText ?? '--';
  return channel.valueNum ?? '--';
}

/** 请求失败时也按采样时间继续老化，不能把最后一次成功值长期伪装成实时值。 */
function displayedQuality(channel: CollectorApi.DashboardChannel) {
  if (!channel.sampleTime) return null;
  const interval = dashboard.value?.device.pollIntervalMs ?? 60000;
  const age = clockNow.value - dayjs(channel.sampleTime).valueOf();
  if (Number.isFinite(age) && age > interval * 3) return 2;
  return channel.quality ?? 0;
}

function qualityText(quality?: null | number) {
  if (quality === null || quality === undefined) return '暂无数据';
  return quality === 2 ? '陈旧' : quality === 1 ? '异常' : '正常';
}

function qualityColor(quality?: null | number) {
  if (quality === 2 || quality === 1) return 'warning';
  if (quality === 0) return 'success';
  return 'default';
}

function updateField<T, K extends keyof T>(target: T, key: K, value: T[K]) {
  if (target[key] !== value) target[key] = value;
}

/** 保留未变化通道的响应式对象，页面只更新实际发生变化的值。 */
function mergeChannels(
  current: CollectorApi.DashboardChannel[],
  incoming: CollectorApi.DashboardChannel[],
) {
  const merged = incoming.map((next) => {
    const target = current.find((channel) => channel.id === next.id);
    if (!target) return next;
    updateField(target, 'name', next.name);
    updateField(target, 'unit', next.unit);
    updateField(target, 'valueKind', next.valueKind);
    updateField(target, 'valueNum', next.valueNum);
    updateField(target, 'valueText', next.valueText);
    updateField(target, 'quality', next.quality);
    updateField(target, 'sampleTime', next.sampleTime);
    return target;
  });
  current.splice(0, current.length, ...merged);
}

function mergeSensors(
  current: CollectorApi.DashboardSensor[],
  incoming: CollectorApi.DashboardSensor[],
) {
  const merged = incoming.map((next) => {
    const target = current.find((sensor) => sensor.id === next.id);
    if (!target) return next;
    updateField(target, 'name', next.name);
    updateField(target, 'model', next.model);
    mergeChannels(target.channels, next.channels);
    return target;
  });
  current.splice(0, current.length, ...merged);
}

function applyDashboard(next: CollectorApi.DashboardLatest) {
  if (!dashboard.value) {
    dashboard.value = next;
    return;
  }
  dashboard.value.device = next.device;
  dashboard.value.serverTime = next.serverTime;
  dashboard.value.versionToken = next.versionToken;
  mergeSensors(dashboard.value.sensors, next.sensors);
}

function clearPollTimer() {
  if (pollTimer !== undefined) clearTimeout(pollTimer);
  pollTimer = undefined;
}

function scheduleNext() {
  clearPollTimer();
  if (
    destroyed ||
    document.hidden ||
    selectedDeviceId.value === undefined ||
    pollInterval.value === 0
  ) {
    nextRetryDelayMs.value = undefined;
    return;
  }
  const delay =
    requestFailures.value > 0
      ? BACKOFF_DELAYS[Math.min(requestFailures.value - 1, 2)]
      : pollInterval.value;
  nextRetryDelayMs.value = requestFailures.value > 0 ? delay : undefined;
  pollTimer = setTimeout(() => void refresh(false), delay);
}

/** 串行拉取；旧设备或已卸载页面的迟到响应会被丢弃。 */
async function refresh(manual: boolean) {
  const deviceId = selectedDeviceId.value;
  if (deviceId === undefined || inFlight || destroyed || document.hidden) {
    return;
  }
  clearPollTimer();
  const currentGeneration = generation;
  inFlight = true;
  if (manual) manualLoading.value = true;
  if (!hasDashboard.value) initialLoading.value = true;
  try {
    const response = await getDashboardLatest(
      deviceId,
      versionDeviceId === deviceId ? versionToken : undefined,
    );
    if (destroyed || currentGeneration !== generation) return;
    if (response.status !== 304) {
      const body = response.data;
      if (!body || body.code !== 0 || !body.data) {
        throw new Error(body?.msg || '最新值接口返回格式错误');
      }
      applyDashboard(body.data);
      versionToken = body.data.versionToken;
      versionDeviceId = deviceId;
    }
    requestFailures.value = 0;
    connectionError.value = false;
    nextRetryDelayMs.value = undefined;
    lastSuccessAt.value = Date.now();
    clockNow.value = Date.now();
  } catch {
    if (destroyed || currentGeneration !== generation) return;
    // 保留 dashboard 中原有数据，仅更新连接状态与重试计划。
    requestFailures.value += 1;
    connectionError.value = true;
    clockNow.value = Date.now();
  } finally {
    if (!destroyed && currentGeneration === generation) {
      inFlight = false;
      if (manual) manualLoading.value = false;
      initialLoading.value = false;
      scheduleNext();
    }
  }
}

function handleIntervalChange(value: number) {
  pollInterval.value = value;
  try {
    localStorage.setItem(STORAGE_KEY, String(value));
  } catch {
    // 隐私模式禁用 localStorage 时仍保持本次页面内设置。
  }
  scheduleNext();
}

function handleDeviceChange(value: number) {
  generation += 1;
  inFlight = false;
  selectedDeviceId.value = value;
  dashboard.value = undefined;
  versionToken = '';
  versionDeviceId = undefined;
  requestFailures.value = 0;
  connectionError.value = false;
  lastSuccessAt.value = undefined;
  void refresh(false);
}

function handleVisibilityChange() {
  clockNow.value = Date.now();
  if (document.hidden) {
    clearPollTimer();
    return;
  }
  // 回到前台立即刷新一次，成功后恢复用户选择的周期。
  void refresh(false);
}

async function loadDevices() {
  devicesLoading.value = true;
  try {
    devices.value = await getDeviceSimpleList();
    if (devices.value.length > 0) {
      selectedDeviceId.value = devices.value[0]!.id;
      await refresh(false);
    }
  } catch {
    connectionError.value = true;
  } finally {
    devicesLoading.value = false;
    initialLoading.value = false;
  }
}

onMounted(() => {
  try {
    // 首次进入即持久化默认（或已保存）的轮询间隔，刷新后保持同一节奏。
    localStorage.setItem(STORAGE_KEY, String(pollInterval.value));
  } catch {
    // 隐私模式禁用 localStorage 时仍保持本次页面内设置。
  }
  document.addEventListener('visibilitychange', handleVisibilityChange);
  clockTimer = setInterval(() => {
    if (!document.hidden) clockNow.value = Date.now();
  }, 1000);
  void loadDevices();
});

onBeforeUnmount(() => {
  destroyed = true;
  generation += 1;
  clearPollTimer();
  if (clockTimer !== undefined) clearInterval(clockTimer);
  document.removeEventListener('visibilitychange', handleVisibilityChange);
});
</script>

<template>
  <Page auto-content-height>
    <div class="flex h-full flex-col gap-4 overflow-auto">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 class="m-0 text-lg font-semibold">最新值看板</h2>
          <div class="mt-1 text-xs opacity-60">
            只展示各通道最近一次采样；陈旧与离线由采样时间判定
          </div>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <Select
            data-testid="dashboard-device"
            class="w-56"
            :loading="devicesLoading"
            :options="deviceOptions"
            :value="selectedDeviceId"
            placeholder="请选择采集设备"
            @change="handleDeviceChange"
          />
          <Select
            data-testid="dashboard-interval"
            class="w-40"
            :options="POLL_INTERVAL_OPTIONS"
            :value="pollInterval"
            aria-label="自动轮询间隔"
            @change="handleIntervalChange"
          />
          <Button
            data-testid="dashboard-refresh"
            type="primary"
            :loading="manualLoading"
            :disabled="selectedDeviceId === undefined"
            @click="void refresh(true)"
          >
            <ReloadOutlined v-if="!manualLoading" />
            手动刷新
          </Button>
        </div>
      </div>

      <Alert
        v-if="connectionError"
        data-testid="dashboard-connection-alert"
        type="warning"
        show-icon
      >
        <template #message>
          连接中断，保留最后一次成功数据；最后更新于 {{ lastSuccessText }}。{{
            retryText
          }}。
        </template>
      </Alert>
      <Alert
        v-else-if="dashboard?.device.staleOffline"
        data-testid="dashboard-stale-offline-alert"
        type="error"
        show-icon
        message="最新采样已超过十倍轮询周期，设备按离线处理；表格中的旧值仅供核对。"
      />

      <Spin :spinning="initialLoading && !hasDashboard">
        <Card v-if="dashboard" data-testid="dashboard-device-card">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
              <div class="flex flex-wrap items-center gap-2">
                <span class="text-lg font-semibold">
                  {{ dashboard.device.name }}
                </span>
                <Tag :color="dashboard.device.online ? 'success' : 'error'">
                  <span aria-hidden="true">●</span>
                  {{ dashboard.device.online ? '在线' : '离线' }}
                </Tag>
                <Tag v-if="dashboard.device.staleOffline" color="warning">
                  <WarningOutlined /> 采样超时
                </Tag>
              </div>
              <div class="mt-1 text-xs opacity-60">
                最后成功更新：{{ lastSuccessText }}
              </div>
            </div>
            <div class="text-xs opacity-60">
              {{ pollInterval === 0 ? '自动刷新已关闭' : '页面隐藏时暂停轮询' }}
            </div>
          </div>
          <div
            class="mt-5 grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-5"
          >
            <div>
              <div class="text-xs opacity-60">最后采样时间</div>
              <div class="mt-1 font-medium" data-testid="last-sample-time">
                {{ formatDateTime(latestSampleTime) }}
              </div>
            </div>
            <div>
              <div class="text-xs opacity-60">最后轮询时间</div>
              <div class="mt-1 font-medium">
                {{ formatDateTime(dashboard.device.lastPollAt) }}
              </div>
            </div>
            <div>
              <div class="text-xs opacity-60">写入延迟</div>
              <div class="mt-1 font-medium">
                {{ dashboard.device.lastWriteLagMs ?? '--' }} ms
              </div>
            </div>
            <div>
              <div class="text-xs opacity-60">连续失败次数</div>
              <div class="mt-1 font-medium">
                {{ dashboard.device.consecutiveFailures }}
              </div>
            </div>
            <div>
              <div class="text-xs opacity-60">当前采集间隔</div>
              <div class="mt-1 font-medium">
                {{ formatInterval(dashboard.device.pollIntervalMs) }}
              </div>
            </div>
          </div>
        </Card>

        <Empty
          v-if="!initialLoading && !dashboard"
          class="py-20"
          description="暂无可用设备或看板数据，请检查设备配置与连接"
        />

        <div v-if="dashboard" class="mt-4 flex flex-col gap-4">
          <Empty
            v-if="dashboard.sensors.length === 0"
            class="py-16"
            description="当前设备还没有传感器"
          />
          <Card
            v-for="sensor in dashboard.sensors"
            :key="sensor.id"
            :data-sensor-id="sensor.id"
            size="small"
          >
            <template #title>
              <div class="flex flex-wrap items-center gap-2">
                <span>{{ sensor.name }}</span>
                <span
                  v-if="sensor.model"
                  class="text-xs font-normal opacity-60"
                >
                  {{ sensor.model }}
                </span>
              </div>
            </template>
            <template #extra>
              <span class="text-xs opacity-60">
                {{ sensor.channels.length }} 个通道
              </span>
            </template>
            <div class="overflow-x-auto">
              <table class="latest-table">
                <thead>
                  <tr>
                    <th>通道名</th>
                    <th>单位</th>
                    <th>当前值</th>
                    <th>采样时间</th>
                    <th>状态</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="channel in sensor.channels"
                    :key="channel.id"
                    :data-channel-id="channel.id"
                  >
                    <td>
                      <div class="flex items-center gap-2">
                        <span class="font-medium">{{ channel.name }}</span>
                        <Tag v-if="channel.valueKind === 'enum'" color="blue">
                          枚举
                        </Tag>
                      </div>
                    </td>
                    <td>{{ channel.unit || '--' }}</td>
                    <td class="latest-value" :data-value="formatValue(channel)">
                      {{ formatValue(channel) }}
                    </td>
                    <td class="sample-time">
                      {{ formatDateTime(channel.sampleTime) }}
                    </td>
                    <td :data-quality="displayedQuality(channel) ?? 'none'">
                      <Tag :color="qualityColor(displayedQuality(channel))">
                        <template #icon>
                          <WarningOutlined
                            v-if="displayedQuality(channel) !== 0"
                          />
                          <span v-else aria-hidden="true">●</span>
                        </template>
                        {{ qualityText(displayedQuality(channel)) }}
                      </Tag>
                      <div
                        v-if="connectionError"
                        class="mt-1 text-xs opacity-60"
                      >
                        最后成功值
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </Card>
        </div>
      </Spin>
    </div>
  </Page>
</template>

<style scoped>
.latest-table {
  width: 100%;
  min-width: 760px;
  border-spacing: 0;
  border-collapse: separate;
  font-size: 13px;
}

.latest-table th,
.latest-table td {
  padding: 12px 14px;
  text-align: left;
  vertical-align: middle;
  border-bottom: 1px solid hsl(var(--border));
}

.latest-table thead th {
  font-weight: 600;
  background: hsl(var(--muted));
}

.latest-table tbody tr:last-child td {
  border-bottom: 0;
}

.latest-table tbody tr:hover td {
  background: hsl(var(--accent) / 35%);
}

.latest-value {
  min-width: 140px;
  font-size: 20px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.sample-time {
  min-width: 190px;
  font-variant-numeric: tabular-nums;
}
</style>
