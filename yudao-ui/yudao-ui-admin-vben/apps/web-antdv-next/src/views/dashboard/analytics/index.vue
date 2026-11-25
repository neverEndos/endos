<script lang="ts" setup>
import type {
  ChannelSelection,
  ChannelWithContext,
  DeviceLatestCard,
  HistoryPoint,
  KpiTile,
  TrendWindowValue,
} from './data';

import type { CollectorApi } from '#/api/collector';

import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { useAccess } from '@vben/access';

import { App, Spin } from 'antdv-next';

import {
  getAlarmStateList,
  getChannelListByDevice,
  getDashboardLatest,
  getDeviceSimpleList,
  getHealthSummary,
  getTrendSeries,
} from '#/api/collector';
import {
  formatShanghaiTime,
  resolveApiErrorMessage,
} from '#/views/collector/components/utils';

import {
  alarmLevelMeta,
  appendHistoryPoint,
  computeAlarmLevelStats,
  computeQualityStats,
  distinctTrendUnits,
  formatShanghaiHms,
  hasAnyPoint,
  isFiniteNumber,
  MAX_TREND_CHANNELS,
  SCREEN_COLORS,
  TREND_SERIES_COLORS,
  TREND_WINDOWS,
  trendSourceLabel,
  unitKey,
} from './data';
import AlarmSide from './modules/alarm-side.vue';
import ChannelDetailDrawer from './modules/channel-detail-drawer.vue';
import ChannelMatrix from './modules/channel-matrix.vue';
import KpiStrip from './modules/kpi-strip.vue';
import SectionHead from './modules/section-head.vue';
import TrendChart from './modules/trend-chart.vue';

defineOptions({ name: 'CollectorScreen' });

const POLL_INTERVAL_MS = 10_000;
const TREND_REFRESH_MS = 60_000;
const TREND_DEBOUNCE_MS = 250;
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
const hasTrendAccess = computed(() =>
  hasAccessByCodes(['collector:trend:query']),
);
/** 阈值矩阵为可选能力：无权限时通道卡片与抽屉明确说明“阈值不可用” */
const hasChannelAccess = computed(() =>
  hasAccessByCodes(['collector:channel:query']),
);

const screenRoot = ref<HTMLElement>();
const isFullscreen = ref(false);
const isPolling = ref(false);

const devices = ref<CollectorApi.DeviceSimple[]>([]);
const devicesLoaded = ref(false);
const devicesError = ref<string>();

const health = ref<CollectorApi.HealthSummary>();
const healthError = ref<string>();

const alarms = ref<CollectorApi.AlarmState[]>([]);
const alarmsError = ref<string>();

const latestMap = ref<Record<number, CollectorApi.DashboardLatest>>({});
const deviceErrors = ref<Record<number, string>>({});
const historyMap = ref<Record<number, readonly HistoryPoint[]>>({});
const thresholds = ref<Record<number, CollectorApi.ThresholdMatrixChannel>>({});
const thresholdsError = ref<string>();
const thresholdLoadedDevices = new Set<number>();

const lastSuccessAt = ref<number>();
const connectionState = ref<'error' | 'ok'>('ok');
const failureCount = ref(0);
const nextPollMs = ref(POLL_INTERVAL_MS);
const clockNow = ref(Date.now());
/** 通道陈旧判定只需 5 秒粒度，避免每秒重算全部卡片 */
const statusNow = ref(Date.now());

let pollTimer: ReturnType<typeof setTimeout> | undefined;
let clockTimer: ReturnType<typeof setInterval> | undefined;
let inFlight = false;
let destroyed = false;

const rootStyle: Record<string, string> = {
  '--scr-bg': SCREEN_COLORS.bg,
  '--scr-bg-soft': SCREEN_COLORS.bgSoft,
  '--scr-border': SCREEN_COLORS.border,
  '--scr-border-strong': SCREEN_COLORS.borderStrong,
  '--scr-error': SCREEN_COLORS.error,
  '--scr-ink': SCREEN_COLORS.ink,
  '--scr-ink-soft': SCREEN_COLORS.inkSoft,
  '--scr-muted': SCREEN_COLORS.muted,
  '--scr-okay': SCREEN_COLORS.okay,
  '--scr-primary': SCREEN_COLORS.primary,
  '--scr-surface': SCREEN_COLORS.surface,
  '--scr-surface-soft': SCREEN_COLORS.surfaceSoft,
  '--scr-warning': SCREEN_COLORS.warning,
};

const lastSuccessText = computed(() =>
  lastSuccessAt.value
    ? formatShanghaiTime(lastSuccessAt.value)
    : '尚未成功刷新',
);
const lastSuccessShort = computed(() =>
  lastSuccessAt.value ? formatShanghaiHms(lastSuccessAt.value) : '--',
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

const deviceCards = computed<DeviceLatestCard[]>(() =>
  devices.value.map((device) => ({
    device,
    error: deviceErrors.value[device.id],
    latest: latestMap.value[device.id],
  })),
);

/** 全量通道上下文；通道矩阵、质量统计与趋势选择都从这一份数据派生 */
const allChannels = computed<ChannelWithContext[]>(() => {
  const result: ChannelWithContext[] = [];
  for (const card of deviceCards.value) {
    if (!card.latest) continue;
    for (const sensor of card.latest.sensors) {
      for (const channel of sensor.channels) {
        result.push({
          channel,
          device: card.device,
          latest: card.latest,
          sensor,
        });
      }
    }
  }
  return result;
});

const qualityStats = computed(() =>
  computeQualityStats(deviceCards.value, statusNow.value),
);

const numericChannelCount = computed(
  () =>
    allChannels.value.filter((entry) => entry.channel.valueKind !== 'enum')
      .length,
);
const enumChannelCount = computed(
  () => allChannels.value.length - numericChannelCount.value,
);

const onlineSummary = computed(() => {
  const process = hasHealthAccess.value ? health.value?.process : undefined;
  if (process?.deviceTotal) {
    return {
      observed: process.deviceTotal,
      online: process.deviceOnline ?? 0,
      onlineKnown:
        process.deviceOnline !== null && process.deviceOnline !== undefined,
      source: 'health' as const,
      total: process.deviceTotal,
    };
  }
  if (!hasDashboardAccess.value || devices.value.length === 0) return undefined;
  let observed = 0;
  let online = 0;
  let total = 0;
  for (const device of devices.value) {
    total += 1;
    const latest = latestMap.value[device.id];
    if (!latest) continue;
    observed += 1;
    if (latest.device.online) online += 1;
  }
  return {
    observed,
    online,
    onlineKnown: observed > 0,
    source: 'dashboard' as const,
    total,
  };
});

const onlineValue = computed(() => {
  const summary = onlineSummary.value;
  if (!summary) return '--';
  if (!summary.onlineKnown) return `-- / ${summary.total}`;
  return `${summary.online} / ${summary.total}`;
});

const onlineHint = computed(() => {
  const summary = onlineSummary.value;
  if (!summary) return '需要 collector:health:query 或看板权限';
  if (!summary.onlineKnown) return '等待设备在线状态';
  return summary.source === 'health' ? '来源：运行摘要' : '来源：看板设备状态';
});

const onlineTone = computed<'muted' | 'ok' | 'warn'>(() => {
  const summary = onlineSummary.value;
  if (!summary || !summary.onlineKnown) return 'muted';
  return summary.online === summary.total ? 'ok' : 'warn';
});

const alarmStats = computed(() => computeAlarmLevelStats(alarms.value));
const activeAlarmCount = computed<number | undefined>(() => {
  if (hasAlarmAccess.value) return alarmStats.value.total;
  if (hasHealthAccess.value && health.value) {
    return health.value.alarm.activeCount;
  }
  return undefined;
});
const highestAlarmLevel = computed<number | undefined>(() => {
  if (hasAlarmAccess.value) {
    if (alarmStats.value.level3 > 0) return 3;
    if (alarmStats.value.level2 > 0) return 2;
    return 0;
  }
  if (hasHealthAccess.value && health.value) {
    return health.value.alarm.highestLevel;
  }
  return undefined;
});
const alarmTone = computed<'error' | 'muted' | 'ok' | 'warn'>(() => {
  const count = activeAlarmCount.value;
  if (count === undefined) return 'muted';
  const level = highestAlarmLevel.value ?? 0;
  if (level >= 3) return 'error';
  if (level >= 2) return 'warn';
  return count > 0 ? 'warn' : 'ok';
});
const alarmHint = computed(() => {
  const count = activeAlarmCount.value;
  if (count === undefined) {
    return '需要 collector:alarm:query 或 collector:health:query';
  }
  if (count === 0) return '当前无活动报警';
  const meta = alarmLevelMeta(highestAlarmLevel.value);
  if (hasAlarmAccess.value) {
    return `最高${meta.label} · 未确认 ${alarmStats.value.unacknowledged}`;
  }
  return `最高${meta.label} · 来源：运行摘要`;
});

const heartbeatText = computed(() => {
  if (!hasHealthAccess.value) return '--';
  const process = health.value?.process;
  if (!process) return healthError.value ? '加载失败' : '尚未收到';
  return formatShanghaiHms(process.heartbeatTime);
});
const heartbeatHint = computed(() => {
  if (!hasHealthAccess.value) return '需要 collector:health:query';
  if (healthError.value) return healthError.value;
  return '来源：运行摘要 · Asia/Shanghai';
});
const writeLagText = computed(() => {
  const lag = health.value?.process?.writeLagMs;
  return lag === null || lag === undefined ? '--' : `${lag} ms`;
});

const anomalyCount = computed(
  () => qualityStats.value.bad + qualityStats.value.stale,
);
const anomalyTone = computed<'error' | 'ok' | 'warn'>(() => {
  if (qualityStats.value.bad > 0) return 'error';
  if (qualityStats.value.stale > 0) return 'warn';
  return 'ok';
});

const kpiTiles = computed<KpiTile[]>(() => [
  {
    available: onlineSummary.value !== undefined,
    hint: onlineHint.value,
    key: 'online',
    label: '在线设备',
    subtitle: 'Devices',
    tone: onlineTone.value,
    value: onlineValue.value,
  },
  {
    available: hasDashboardAccess.value && hasDeviceAccess.value,
    hint:
      hasDashboardAccess.value && hasDeviceAccess.value
        ? `数值 ${numericChannelCount.value} · 枚举 ${enumChannelCount.value}`
        : '需要 collector:device:query 与 collector:dashboard:query',
    key: 'channels',
    label: '通道总数',
    subtitle: 'Channels',
    tone: 'primary',
    value: String(qualityStats.value.total),
  },
  {
    available: hasDashboardAccess.value && hasDeviceAccess.value,
    hint:
      hasDashboardAccess.value && hasDeviceAccess.value
        ? `坏质量 ${qualityStats.value.bad} · 陈旧 ${qualityStats.value.stale} · 无数据 ${qualityStats.value.missing}`
        : '需要设备与最新值看板权限',
    key: 'anomaly',
    label: '异常通道',
    subtitle: 'Anomaly',
    tone: anomalyTone.value,
    value: String(anomalyCount.value),
  },
  {
    available: activeAlarmCount.value !== undefined,
    hint: alarmHint.value,
    key: 'alarms',
    label: '活动报警',
    subtitle: 'Alarms',
    tone: alarmTone.value,
    value:
      activeAlarmCount.value === undefined
        ? '--'
        : String(activeAlarmCount.value),
  },
  {
    available: hasHealthAccess.value,
    hint: heartbeatHint.value,
    key: 'heartbeat',
    label: '心跳时间',
    subtitle: 'Heartbeat',
    tone: 'primary',
    value: heartbeatText.value,
  },
  {
    available: hasHealthAccess.value,
    hint: healthError.value ?? '来源：运行摘要 · 采集器进程级，非设备级',
    key: 'write-lag',
    label: '写入延迟',
    subtitle: 'Write Lag',
    tone: 'primary',
    value: writeLagText.value,
  },
]);

const matrixStatusTone = computed<'error' | 'muted' | 'ok' | 'warn'>(() => {
  if (!hasDeviceAccess.value || !hasDashboardAccess.value) return 'muted';
  if (devicesError.value) return 'error';
  if (qualityStats.value.bad > 0) return 'error';
  if (qualityStats.value.stale > 0 || qualityStats.value.missing > 0) {
    return 'warn';
  }
  return 'ok';
});
const matrixStatusText = computed(() => {
  if (!hasDeviceAccess.value || !hasDashboardAccess.value) return '无权限';
  if (devicesError.value) return '设备加载失败';
  if (!devicesLoaded.value) return '加载中';
  return `${devices.value.length} 台 · ${qualityStats.value.total} 通道`;
});

const alarmByChannel = computed<Record<number, CollectorApi.AlarmState>>(() => {
  const map: Record<number, CollectorApi.AlarmState> = {};
  for (const alarm of alarms.value) {
    const id = alarm.channelId;
    if (!id) continue;
    const current = map[id];
    if (!current || (alarm.level ?? 0) > (current.level ?? 0)) {
      map[id] = alarm;
    }
  }
  return map;
});

const hasPartialFailure = computed(
  () =>
    Boolean(alarmsError.value || healthError.value || devicesError.value) ||
    Object.keys(deviceErrors.value).length > 0,
);

// ---------------- 趋势 ----------------

const trendWindow = ref<TrendWindowValue>('1h');
const trendWindowSeconds = computed(
  () =>
    TREND_WINDOWS.find((item) => item.value === trendWindow.value)?.seconds ??
    60 * 60,
);

const trendDeviceId = ref<number>();
const trendDevice = computed(() => {
  if (devices.value.length === 0) return undefined;
  return (
    devices.value.find((device) => device.id === trendDeviceId.value) ??
    devices.value[0]
  );
});
const trendDeviceCard = computed(() =>
  deviceCards.value.find((card) => card.device.id === trendDevice.value?.id),
);
const trendDevicePending = computed(
  () =>
    Boolean(trendDevice.value) &&
    !trendDeviceCard.value?.latest &&
    !trendDeviceCard.value?.error,
);
const trendDeviceLoadError = computed(() =>
  trendDeviceCard.value?.latest ? undefined : trendDeviceCard.value?.error,
);

const trendSelectedIds = ref<number[]>([]);
const trendColorMap = ref<Record<number, string>>({});
const trendSeries = ref<CollectorApi.TrendSeries[]>([]);
const trendSourceSummary = ref('');
const trendLoading = ref(false);
const trendError = ref<string>();
const trendLastSuccessAt = ref<number>();
/** 每次进入某设备只自动选中首个数值通道一次，尊重用户清空选择的操作 */
const trendInitialized = new Set<number>();
let trendDebounceTimer: ReturnType<typeof setTimeout> | undefined;
let trendRefreshTimer: ReturnType<typeof setInterval> | undefined;
let trendQuerySeq = 0;

const trendDeviceChannels = computed(() =>
  allChannels.value.filter(
    (entry) =>
      entry.device.id === trendDevice.value?.id &&
      entry.channel.valueKind !== 'enum',
  ),
);

/** 后端为每个请求通道都返回序列，必须看有效点而不是 series 数组长度 */
const trendHasData = computed(() => hasAnyPoint(trendSeries.value));

const trendStatusTone = computed<'error' | 'muted' | 'ok' | 'warn'>(() => {
  if (
    !hasTrendAccess.value ||
    !hasDashboardAccess.value ||
    !hasDeviceAccess.value
  ) {
    return 'muted';
  }
  if (trendError.value) return 'error';
  if (trendLoading.value) return 'warn';
  if (trendHasData.value) return 'ok';
  return 'muted';
});
const trendStatusText = computed(() => {
  if (!hasTrendAccess.value) return '无趋势权限';
  if (!hasDashboardAccess.value) return '缺少看板权限';
  if (!hasDeviceAccess.value) return '缺少设备权限';
  if (trendError.value) return '查询失败';
  if (trendLoading.value) return '查询中';
  if (trendSelectedIds.value.length === 0) return '待选择通道';
  if (trendHasData.value) {
    return `${trendSeries.value.length} 通道 · ${trendSourceLabel(
      trendSourceSummary.value,
    )}`;
  }
  return `${trendSelectedIds.value.length} 通道 · 无有效数据`;
});

// ---------------- 通道详情抽屉 ----------------

const detailOpen = ref(false);
const detailFallback = ref<ChannelSelection>();
const detailSelection = computed<ChannelSelection | undefined>(() => {
  const fallback = detailFallback.value;
  if (!fallback) return undefined;
  const live = allChannels.value.find(
    (entry) =>
      entry.channel.id === fallback.channel.id &&
      entry.device.id === fallback.device.id,
  );
  if (!live) return fallback;
  return { channel: live.channel, device: live.device, sensor: live.sensor };
});
const detailAlarm = computed(() => {
  const selection = detailSelection.value;
  return selection ? alarmByChannel.value[selection.channel.id] : undefined;
});
const detailThreshold = computed(() => {
  const selection = detailSelection.value;
  return selection ? thresholds.value[selection.channel.id] : undefined;
});
const detailPollInterval = computed(() => {
  const deviceId = detailSelection.value?.device.id;
  return deviceId === undefined
    ? undefined
    : latestMap.value[deviceId]?.device.pollIntervalMs;
});

function openChannelDetail(selection: ChannelSelection) {
  detailFallback.value = selection;
  detailOpen.value = true;
}

// ---------------- 趋势选择与查询 ----------------

function syncTrendColors() {
  const ids = trendSelectedIds.value;
  const used = new Set<string>();
  const next: Record<number, string> = {};
  // 先保留仍在选中的通道颜色，筛选减少通道时不重排幸存系列
  for (const id of ids) {
    const existing = trendColorMap.value[id];
    if (existing && !used.has(existing)) {
      next[id] = existing;
      used.add(existing);
    }
  }
  for (const id of ids) {
    if (next[id]) continue;
    const free = TREND_SERIES_COLORS.find((color) => !used.has(color));
    if (!free) break;
    next[id] = free;
    used.add(free);
  }
  trendColorMap.value = next;
}

function isTrendSelected(id: number): boolean {
  return trendSelectedIds.value.includes(id);
}

function trendColorOf(id: number): string {
  return trendColorMap.value[id] ?? SCREEN_COLORS.muted;
}

function channelChipTitle(entry: ChannelWithContext): string {
  const unit = entry.channel.unit?.trim();
  return `${entry.channel.name}${unit ? `（${unit}）` : ''} · ${
    entry.sensor.name
  }`;
}

function selectTrendDevice(id: number) {
  if (id === trendDevice.value?.id) return;
  trendDeviceId.value = id;
}

function toggleTrendChannel(id: number) {
  if (!hasTrendAccess.value) return;
  const entry = trendDeviceChannels.value.find(
    (item) => item.channel.id === id,
  );
  if (!entry) return;
  const current = trendSelectedIds.value;
  if (current.includes(id)) {
    trendSelectedIds.value = current.filter((item) => item !== id);
    syncTrendColors();
    return;
  }
  if (current.length >= MAX_TREND_CHANNELS) {
    message.warning(
      `最多同时叠加 ${MAX_TREND_CHANNELS} 个通道，请先取消一个通道`,
    );
    return;
  }
  const units = distinctTrendUnits(trendDeviceChannels.value, current);
  const nextUnit = unitKey(entry.channel.unit);
  if (units.some((unit) => unit !== nextUnit)) {
    message.warning(
      `只能叠加同一单位的通道（当前为 ${
        units[0] ?? '未指定单位'
      }），请先取消其他通道`,
    );
    return;
  }
  trendSelectedIds.value = [...current, id];
  syncTrendColors();
}

function scheduleTrendQuery(clear: boolean) {
  if (trendDebounceTimer !== undefined) {
    clearTimeout(trendDebounceTimer);
  }
  trendDebounceTimer = setTimeout(() => {
    trendDebounceTimer = undefined;
    void queryTrend(clear);
  }, TREND_DEBOUNCE_MS);
}

async function queryTrend(clear: boolean) {
  if (destroyed || document.hidden) return;
  trendQuerySeq += 1;
  const seq = trendQuerySeq;
  if (clear) {
    trendSeries.value = [];
    trendSourceSummary.value = '';
    trendError.value = undefined;
  }
  const selected = trendSelectedIds.value;
  const device = trendDevice.value;
  if (
    !hasTrendAccess.value ||
    !hasDashboardAccess.value ||
    !hasDeviceAccess.value ||
    !device ||
    selected.length === 0
  ) {
    trendLoading.value = false;
    if (clear) trendSeries.value = [];
    return;
  }
  const to = Date.now();
  const from = to - trendWindowSeconds.value * 1000;
  trendLoading.value = true;
  trendError.value = undefined;
  try {
    const body = await getTrendSeries({
      channelIds: selected,
      deviceId: device.id,
      from,
      granularity: 'auto',
      includeRaw: false,
      to,
    });
    if (seq !== trendQuerySeq) return;
    const series = (body?.series ?? []).filter((item) => item.kind !== 'raw');
    trendSeries.value = series;
    trendSourceSummary.value = body?.sourceSummary ?? '';
    trendLastSuccessAt.value = Date.now();
  } catch (error) {
    if (seq !== trendQuerySeq) return;
    trendError.value = resolveApiErrorMessage(error, '趋势查询失败');
    if (clear) trendSeries.value = [];
  } finally {
    if (seq === trendQuerySeq) {
      trendLoading.value = false;
    }
  }
}

function retryTrend() {
  void queryTrend(true);
}

watch(
  devices,
  (list) => {
    if (list.length === 0) {
      trendDeviceId.value = undefined;
      return;
    }
    if (list.some((device) => device.id === trendDeviceId.value)) return;
    const first = list[0];
    if (first) trendDeviceId.value = first.id;
  },
  { immediate: true },
);

watch(
  () => trendDevice.value?.id,
  (id, previous) => {
    if (id === previous) return;
    trendSelectedIds.value = [];
    trendColorMap.value = {};
    trendSeries.value = [];
    trendSourceSummary.value = '';
    trendError.value = undefined;
    if (id !== undefined) trendInitialized.delete(id);
  },
);

watch(
  trendDeviceChannels,
  (channels) => {
    if (!hasTrendAccess.value) return;
    const deviceId = trendDevice.value?.id;
    if (deviceId === undefined) return;
    const validIds = new Set(channels.map((entry) => entry.channel.id));
    const nextSelected = trendSelectedIds.value.filter((id) =>
      validIds.has(id),
    );
    if (nextSelected.length !== trendSelectedIds.value.length) {
      trendSelectedIds.value = nextSelected;
      syncTrendColors();
    }
    if (trendInitialized.has(deviceId)) return;
    trendInitialized.add(deviceId);
    const first = channels[0];
    if (first) {
      trendSelectedIds.value = [first.channel.id];
      syncTrendColors();
    }
  },
  { immediate: true },
);

watch(
  [() => trendDevice.value?.id, trendSelectedIds, trendWindow],
  () => {
    scheduleTrendQuery(true);
  },
  { deep: true },
);

// ---------------- 轮询 ----------------

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

async function loadThresholdsForDevices(
  deviceList: CollectorApi.DeviceSimple[],
) {
  if (!hasChannelAccess.value) return;
  for (const device of deviceList) {
    if (thresholdLoadedDevices.has(device.id)) continue;
    thresholdLoadedDevices.add(device.id);
    try {
      const matrix = await getChannelListByDevice(device.id);
      const next = { ...thresholds.value };
      for (const sensor of matrix?.sensors ?? []) {
        for (const channel of sensor.channels) {
          next[channel.id] = channel;
        }
      }
      thresholds.value = next;
      thresholdsError.value = undefined;
    } catch (error) {
      // 阈值属于可选信息，加载失败不影响连接状态与轮询退避
      thresholdsError.value = resolveApiErrorMessage(error, '阈值矩阵加载失败');
    }
  }
}

async function refreshAll() {
  if (inFlight || destroyed || document.hidden) return;
  inFlight = true;
  isPolling.value = true;
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

    if (hasChannelAccess.value && devices.value.length > 0) {
      void loadThresholdsForDevices(devices.value);
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
          // 每轮都重新判定全部设备，失败集合不需要沿用上一轮
          const nextErrors: Record<number, string> = {};
          const nextHistory: Record<number, readonly HistoryPoint[]> = {
            ...historyMap.value,
          };
          let historyChanged = false;
          devices.value.forEach((device, index) => {
            const result = results[index];
            if (result?.status === 'fulfilled' && result.value) {
              nextLatest[device.id] = result.value;
              // 用最新值累积 sparkline，不额外查询历史接口
              for (const sensor of result.value.sensors) {
                for (const channel of sensor.channels) {
                  if (channel.valueKind === 'enum') continue;
                  const sampleTime = channel.sampleTime;
                  const valueNum = channel.valueNum;
                  if (
                    !isFiniteNumber(sampleTime) ||
                    !isFiniteNumber(valueNum)
                  ) {
                    continue;
                  }
                  const current = nextHistory[channel.id];
                  const last = current?.[current.length - 1];
                  if (last && sampleTime <= last.t) continue;
                  const appended = appendHistoryPoint(current, {
                    t: sampleTime,
                    v: valueNum,
                  });
                  if (!appended) continue;
                  nextHistory[channel.id] = appended;
                  historyChanged = true;
                }
              }
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
          if (historyChanged) historyMap.value = nextHistory;
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
    isPolling.value = false;
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
  statusNow.value = clockNow.value;
  void refreshAll();
  if (hasTrendAccess.value && trendSelectedIds.value.length > 0) {
    void queryTrend(false);
  }
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
    if (document.hidden) return;
    clockNow.value = Date.now();
    if (clockNow.value - statusNow.value >= 5000) {
      statusNow.value = clockNow.value;
    }
  }, 1000);
  trendRefreshTimer = setInterval(() => {
    if (
      document.hidden ||
      destroyed ||
      !hasTrendAccess.value ||
      trendSelectedIds.value.length === 0
    ) {
      return;
    }
    void queryTrend(false);
  }, TREND_REFRESH_MS);
  void refreshAll();
});

onBeforeUnmount(() => {
  destroyed = true;
  clearPollTimer();
  if (clockTimer !== undefined) clearInterval(clockTimer);
  if (trendRefreshTimer !== undefined) clearInterval(trendRefreshTimer);
  if (trendDebounceTimer !== undefined) clearTimeout(trendDebounceTimer);
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
    :style="rootStyle"
  >
    <header class="screen-header">
      <div class="brand">
        <div class="brand-title-row">
          <h1>采集器运行大屏</h1>
          <span class="brand-code">COLLECTOR OPS</span>
        </div>
        <span class="brand-sub">只读 · 10 秒轮询 · Asia/Shanghai</span>
      </div>
      <div class="header-status">
        <span
          class="connection"
          :class="{ 'connection--error': connectionState === 'error' }"
        >
          <span
            class="conn-dot"
            :class="{ 'conn-dot--polling': isPolling }"
            aria-hidden="true"
          ></span>
          {{ connectionText }}
        </span>
        <span
          v-if="hasHealthAccess"
          class="header-metric"
          title="采集器进程级写入延迟，非设备级"
        >
          写延迟 <strong>{{ writeLagText }}</strong>
        </span>
        <span class="header-metric">
          最后成功 <strong>{{ lastSuccessShort }}</strong>
        </span>
        <span class="clock">{{ formatShanghaiTime(clockNow) }}</span>
        <button
          v-if="!isStandaloneScreen"
          type="button"
          class="screen-btn"
          @click="openStandaloneScreen"
        >
          全屏大屏
        </button>
        <button type="button" class="screen-btn" @click="toggleFullscreen">
          {{ isFullscreen ? '退出全屏' : '全屏' }}
        </button>
      </div>
    </header>

    <KpiStrip :tiles="kpiTiles" />

    <div v-if="connectionState === 'error'" class="stale-banner">
      连接异常，数据可能已过期；页面保留最后一次成功数据，{{ retryText }}。
    </div>

    <main
      class="screen-body"
      :class="{ 'screen-body--no-alarm': !hasAlarmAccess }"
    >
      <div class="screen-main">
        <section class="panel trend-panel">
          <SectionHead
            code="TREND-01"
            :status-text="trendStatusText"
            :status-tone="trendStatusTone"
            subtitle="Main Trend"
            title="主趋势"
          />

          <div v-if="!hasTrendAccess" class="panel-note">
            当前账号缺少
            <code>collector:trend:query</code>
            权限，趋势区已隐藏；通道矩阵中的最新值仍可查看。
          </div>
          <div v-else-if="!hasDashboardAccess" class="panel-note">
            趋势通道列表依赖
            <code>collector:dashboard:query</code>
            权限，当前无法选择通道。
          </div>
          <div v-else-if="!hasDeviceAccess" class="panel-note">
            趋势通道列表依赖
            <code>collector:device:query</code>
            权限，当前无法加载设备。
          </div>
          <div v-else-if="devicesError" class="panel-note panel-note--error">
            {{ devicesError }}
          </div>
          <div v-else-if="!devicesLoaded" class="panel-note">
            正在加载采集设备…
          </div>
          <div v-else-if="devices.length === 0" class="panel-note">
            暂无可用采集设备，请先在「采集设备」中完成配置。
          </div>

          <template v-else>
            <div class="trend-controls">
              <div class="device-tabs" aria-label="选择趋势设备">
                <button
                  v-for="device in devices"
                  :key="device.id"
                  type="button"
                  class="tab-btn"
                  :class="{
                    'tab-btn--active': trendDevice?.id === device.id,
                  }"
                  :aria-pressed="trendDevice?.id === device.id"
                  @click="selectTrendDevice(device.id)"
                >
                  {{ device.deviceName }}
                </button>
              </div>
              <div class="window-tabs" aria-label="选择趋势时间窗">
                <button
                  v-for="item in TREND_WINDOWS"
                  :key="item.value"
                  type="button"
                  class="tab-btn"
                  :class="{ 'tab-btn--active': trendWindow === item.value }"
                  :aria-pressed="trendWindow === item.value"
                  @click="trendWindow = item.value"
                >
                  {{ item.label }}
                </button>
              </div>
              <span
                v-if="trendLastSuccessAt"
                class="trend-last"
                title="最近一次趋势查询时间（Asia/Shanghai）"
              >
                T {{ formatShanghaiHms(trendLastSuccessAt) }}
              </span>
            </div>

            <div v-if="trendDeviceChannels.length" class="trend-chips">
              <button
                v-for="entry in trendDeviceChannels"
                :key="entry.channel.id"
                type="button"
                class="chip-btn"
                :class="{
                  'chip-btn--active': isTrendSelected(entry.channel.id),
                }"
                :style="
                  isTrendSelected(entry.channel.id)
                    ? { borderColor: trendColorOf(entry.channel.id) }
                    : undefined
                "
                :aria-pressed="isTrendSelected(entry.channel.id)"
                :title="channelChipTitle(entry)"
                @click="toggleTrendChannel(entry.channel.id)"
              >
                <span
                  class="chip-dot"
                  :style="{
                    background: trendColorOf(entry.channel.id),
                    opacity: isTrendSelected(entry.channel.id) ? 1 : 0.45,
                  }"
                  aria-hidden="true"
                ></span>
                <span class="chip-name">
                  {{ entry.channel.name || '未命名通道' }}
                </span>
                <span v-if="entry.channel.unit" class="chip-unit">
                  {{ entry.channel.unit }}
                </span>
              </button>
            </div>

            <div v-if="trendError && trendHasData" class="trend-alert">
              <span>{{ trendError }}（保留上次曲线）</span>
              <button type="button" class="inline-btn" @click="retryTrend">
                重试
              </button>
            </div>

            <div class="trend-chart-wrap">
              <div
                v-if="trendDeviceLoadError"
                class="panel-note panel-note--center panel-note--error"
              >
                {{ trendDeviceLoadError }}（保留上一次成功数据）
              </div>
              <div
                v-else-if="trendDevicePending"
                class="panel-note panel-note--center"
              >
                <Spin size="small" />
                正在读取该设备通道最新值…
              </div>
              <div
                v-else-if="trendDeviceChannels.length === 0"
                class="panel-note panel-note--center"
              >
                该设备暂无数值通道，枚举通道无法绘制趋势。
              </div>
              <div
                v-else-if="trendSelectedIds.length === 0"
                class="panel-note panel-note--center"
              >
                请选择 1–{{ MAX_TREND_CHANNELS }} 个同单位通道叠加显示。
              </div>
              <div
                v-else-if="trendError && !trendHasData"
                class="panel-note panel-note--center panel-note--error"
              >
                <span>{{ trendError }}</span>
                <button type="button" class="inline-btn" @click="retryTrend">
                  重试
                </button>
              </div>
              <div
                v-else-if="trendLoading && !trendHasData"
                class="panel-note panel-note--center"
              >
                <Spin size="small" />
                正在查询趋势…
              </div>
              <template v-else-if="trendHasData">
                <TrendChart
                  :colors="trendColorMap"
                  :range-seconds="trendWindowSeconds"
                  :series="trendSeries"
                />
                <span v-if="trendLoading" class="trend-refreshing">
                  刷新中
                </span>
              </template>
              <div v-else class="panel-note panel-note--center">
                所选通道在当前时间窗内没有有效数据。
              </div>
            </div>
          </template>
        </section>

        <section class="panel matrix-panel">
          <SectionHead
            code="MATRIX-01"
            :status-text="matrixStatusText"
            :status-tone="matrixStatusTone"
            subtitle="Channel Matrix"
            title="通道矩阵"
          />
          <div v-if="thresholdsError" class="panel-note panel-note--tight">
            阈值矩阵加载失败：{{ thresholdsError }}（通道卡片不显示阈值）
          </div>
          <div class="matrix-body">
            <ChannelMatrix
              :active-device-id="trendDevice?.id"
              :alarm-by-channel="alarmByChannel"
              :devices="deviceCards"
              :devices-error="devicesError"
              :has-dashboard="hasDashboardAccess"
              :has-device="hasDeviceAccess"
              :history="historyMap"
              :now="statusNow"
              :quality-stats="qualityStats"
              :thresholds="thresholds"
              @select="openChannelDetail"
            />
          </div>
        </section>
      </div>

      <AlarmSide
        v-if="hasAlarmAccess"
        class="alarm-column"
        :alarms="alarms"
        :alarms-error="alarmsError"
        :has-alarm-access="hasAlarmAccess"
        @refresh="refreshAll"
      />
    </main>

    <footer class="screen-footer">
      <span>最后成功更新：{{ lastSuccessText }}</span>
      <span v-if="hasPartialFailure" class="footer-error">
        部分数据刷新失败，保留旧数据
      </span>
      <span class="footer-right">
        {{ retryText }}
        <span class="footer-mono">10s · 退避 10s/20s/60s</span>
      </span>
    </footer>

    <ChannelDetailDrawer
      v-model:open="detailOpen"
      :alarm="detailAlarm"
      :device-poll-interval-ms="detailPollInterval"
      :has-trend-access="hasTrendAccess"
      :now="statusNow"
      :range-seconds="trendWindowSeconds"
      :target="detailSelection"
      :threshold="detailThreshold"
    />
  </div>
</template>

<style scoped>
.screen-root {
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: 100%;
  min-height: 0;
  padding: 14px 16px;
  overflow: auto;
  color: var(--scr-ink, #e6eef9);
  background-color: var(--scr-bg, #070c18);

  /* 静态细网格 + 两处径向辉光，不使用大范围 backdrop-filter */
  background-image:
    radial-gradient(circle at 14% -8%, rgb(34 211 238 / 10%), transparent 42%),
    radial-gradient(circle at 88% 2%, rgb(37 99 235 / 10%), transparent 38%),
    linear-gradient(rgb(72 104 158 / 7%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(72 104 158 / 7%) 1px, transparent 1px);
  background-size:
    auto,
    auto,
    48px 48px,
    48px 48px;
}

/* 独立 /screen 路由无布局，按视口铺满且不出现整页滚动 */
.screen-root--standalone {
  height: 100vh;
  overflow: hidden;
}

.screen-header {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  align-items: center;
  justify-content: space-between;
}

.brand {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.brand-title-row {
  display: flex;
  gap: 10px;
  align-items: baseline;
}

.brand h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 2px;
}

.brand-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 9px;
  color: var(--scr-primary, #22d3ee);
  letter-spacing: 2.4px;
  opacity: 0.8;
}

.brand-sub {
  font-size: 10px;
  color: var(--scr-muted, #64748b);
  letter-spacing: 0.4px;
}

.header-status {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  align-items: center;
}

.connection {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 2px 9px;
  font-size: 11px;
  color: var(--scr-okay, #2dd4bf);
  background: rgb(45 212 191 / 10%);
  border: 1px solid rgb(45 212 191 / 35%);
  border-radius: 999px;
}

.connection--error {
  color: #fda4af;
  background: rgb(244 63 94 / 10%);
  border-color: rgb(244 63 94 / 40%);
}

.conn-dot {
  width: 6px;
  height: 6px;
  background: currentcolor;
  border-radius: 999px;
  box-shadow: 0 0 6px currentcolor;
}

/* 页面唯一的非报警循环动画：仅在轮询请求进行中闪烁 */
.conn-dot--polling {
  animation: conn-pulse 1.6s ease-in-out infinite;
}

.header-metric {
  font-size: 10px;
  font-variant-numeric: tabular-nums;
  color: var(--scr-muted, #64748b);
}

.header-metric strong {
  font-weight: 500;
  color: var(--scr-ink-soft, #b7c6dd);
}

.clock {
  font-size: 16px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--scr-ink, #e6eef9);
  letter-spacing: 0.5px;
}

.screen-btn {
  padding: 3px 10px;
  font: inherit;
  font-size: 11px;
  color: var(--scr-ink-soft, #b7c6dd);
  cursor: pointer;
  background: rgb(17 28 50 / 80%);
  border: 1px solid var(--scr-border-strong, rgb(72 104 158 / 44%));
  border-radius: 5px;
}

.screen-btn:hover {
  color: var(--scr-ink, #e6eef9);
  background: rgb(22 35 61 / 90%);
}

.stale-banner {
  padding: 6px 10px;
  font-size: 11px;
  color: #fda4af;
  background: rgb(244 63 94 / 9%);
  border: 1px solid rgb(244 63 94 / 32%);
  border-radius: 6px;
}

.screen-body {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(0, 2.35fr) minmax(300px, 0.95fr);
  gap: 10px;
  min-height: 0;
}

.screen-body--no-alarm {
  grid-template-columns: minmax(0, 1fr);
}

.screen-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
}

.panel {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 8px 10px;
  overflow: hidden;
  background: var(--scr-surface, rgb(13 22 40 / 86%));
  border: 1px solid var(--scr-border, rgb(72 104 158 / 24%));
  border-radius: 10px;
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 4%);
}

/* 面板顶部 2px 渐变高光，替代大面积彩色标题条 */
.panel::before {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 2px;
  content: '';
  background: linear-gradient(
    90deg,
    var(--scr-primary, #22d3ee),
    transparent 62%
  );
  opacity: 0.7;
}

.trend-panel {
  flex-shrink: 0;
  height: clamp(190px, 30vh, 320px);
}

.matrix-panel {
  flex: 1;
  min-height: 0;
}

.matrix-body {
  display: flex;
  flex: 1;
  min-height: 0;
  margin-top: 5px;
}

.panel-note {
  padding: 8px 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--scr-muted, #64748b);
}

.panel-note--center {
  display: flex;
  flex: 1;
  gap: 8px;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.panel-note--error {
  color: #fda4af;
}

.panel-note--tight {
  padding: 2px 4px;
  font-size: 10px;
  color: var(--scr-warning, #fbbf24);
}

.panel-note code {
  padding: 0 3px;
  font-size: 10px;
  color: var(--scr-ink-soft, #b7c6dd);
  background: rgb(148 163 184 / 10%);
  border-radius: 2px;
}

.trend-controls {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 10px;
  align-items: center;
  justify-content: space-between;
  margin-top: 5px;
}

.device-tabs,
.window-tabs {
  display: flex;
  gap: 4px;
  overflow-x: auto;
  scrollbar-width: none;
}

.device-tabs::-webkit-scrollbar,
.window-tabs::-webkit-scrollbar {
  display: none;
}

.tab-btn {
  flex: 0 0 auto;
  padding: 2px 8px;
  font: inherit;
  font-size: 10px;
  color: var(--scr-muted, #64748b);
  white-space: nowrap;
  cursor: pointer;
  background: transparent;
  border: 1px solid var(--scr-border, rgb(72 104 158 / 24%));
  border-radius: 4px;
}

.tab-btn:hover {
  color: var(--scr-ink-soft, #b7c6dd);
  border-color: var(--scr-border-strong, rgb(72 104 158 / 44%));
}

.tab-btn--active {
  color: var(--scr-primary, #22d3ee);
  background: rgb(34 211 238 / 8%);
  border-color: rgb(34 211 238 / 55%);
}

.trend-last {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 9px;
  font-variant-numeric: tabular-nums;
  color: var(--scr-muted, #64748b);
}

.trend-chips {
  display: flex;
  gap: 4px;
  padding-bottom: 1px;
  margin-top: 5px;
  overflow-x: auto;
  scrollbar-color: rgb(72 104 158 / 45%) transparent;
  scrollbar-width: thin;
}

.chip-btn {
  display: inline-flex;
  flex: 0 0 auto;
  gap: 4px;
  align-items: center;
  padding: 2px 7px;
  font: inherit;
  font-size: 10px;
  color: var(--scr-muted, #64748b);
  white-space: nowrap;
  cursor: pointer;
  background: rgb(17 28 50 / 70%);
  border: 1px solid var(--scr-border, rgb(72 104 158 / 24%));
  border-radius: 4px;
}

.chip-btn:hover {
  color: var(--scr-ink-soft, #b7c6dd);
}

.chip-btn--active {
  color: var(--scr-ink, #e6eef9);
  background: rgb(17 28 50 / 95%);
}

.chip-dot {
  width: 5px;
  height: 5px;
  border-radius: 999px;
}

.chip-name {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chip-unit {
  font-size: 8px;
  color: var(--scr-muted, #64748b);
}

.trend-alert {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 3px 8px;
  margin-top: 4px;
  font-size: 10px;
  color: #fda4af;
  background: rgb(244 63 94 / 8%);
  border: 1px solid rgb(244 63 94 / 28%);
  border-radius: 4px;
}

.inline-btn {
  padding: 1px 7px;
  font: inherit;
  font-size: 10px;
  color: var(--scr-ink, #e6eef9);
  cursor: pointer;
  background: rgb(17 28 50 / 90%);
  border: 1px solid var(--scr-border-strong, rgb(72 104 158 / 44%));
  border-radius: 3px;
}

.inline-btn:hover {
  background: rgb(22 35 61 / 95%);
}

.trend-chart-wrap {
  position: relative;
  display: flex;
  flex: 1;
  min-height: 0;
  margin-top: 5px;
}

.trend-chart-wrap > :deep(.echarts),
.trend-chart-wrap > :deep(div) {
  min-height: 0;
}

.trend-refreshing {
  position: absolute;
  top: 3px;
  right: 6px;
  padding: 1px 6px;
  font-size: 9px;
  color: var(--scr-warning, #fbbf24);
  pointer-events: none;
  background: rgb(7 12 24 / 82%);
  border: 1px solid rgb(251 191 36 / 35%);
  border-radius: 3px;
}

.alarm-column {
  min-height: 0;
}

.screen-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
  align-items: center;
  font-size: 10px;
  color: var(--scr-muted, #64748b);
}

.footer-error {
  color: #fda4af;
}

.footer-right {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
}

.footer-mono {
  margin-left: 6px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 9px;
  color: rgb(100 116 139 / 80%);
}

.screen-btn:focus-visible,
.tab-btn:focus-visible,
.chip-btn:focus-visible,
.inline-btn:focus-visible {
  outline: 1px solid var(--scr-primary, #22d3ee);
  outline-offset: 2px;
}

@keyframes conn-pulse {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.25;
  }
}

/* 1366×768 等高度紧张的场景：收紧留白与趋势区高度，优先保证通道矩阵可见 */
@media (max-height: 800px) {
  .screen-root {
    gap: 6px;
    padding: 10px 14px;
  }

  .brand h1 {
    font-size: 20px;
    letter-spacing: 1px;
  }

  .clock {
    font-size: 14px;
  }

  .trend-panel {
    height: clamp(180px, 28vh, 260px);
  }

  .screen-main {
    gap: 6px;
  }
}

/* 1024×600 等更小尺寸：进一步压缩，趋势区与矩阵均可内部滚动 */
@media (max-width: 1280px), (max-height: 720px) {
  .screen-root {
    gap: 6px;
    padding: 8px 12px;
  }

  .brand h1 {
    font-size: 18px;
    letter-spacing: 1px;
  }

  .brand-sub {
    display: none;
  }

  .trend-panel {
    height: clamp(170px, 27vh, 250px);
  }

  .panel {
    padding: 7px 9px;
  }

  .screen-body {
    grid-template-columns: minmax(0, 2.2fr) minmax(260px, 0.9fr);
  }

  .screen-body--no-alarm {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 1024px) {
  .screen-body {
    grid-template-columns: minmax(0, 2fr) minmax(240px, 0.9fr);
  }
}

@media (prefers-reduced-motion: reduce) {
  .conn-dot--polling {
    animation: none;
  }

  .screen-root,
  .screen-root * {
    transition: none !important;
    animation: none !important;
  }
}
</style>
