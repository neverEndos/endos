import type { ComputedRef, InjectionKey, Ref } from 'vue';

import type { AnalysisStats, TrendRange } from './chart-utils';

import type { CollectorApi } from '#/api/collector';

import {
  computed,
  inject,
  onBeforeUnmount,
  onMounted,
  ref,
  shallowRef,
  watch,
} from 'vue';

import {
  getChannelPage,
  getDashboardLatest,
  getDeviceSimpleList,
  getTrendSeries,
} from '#/api/collector';

import {
  ACCENT_PALETTE,
  aggregatePoints,
  downsample,
  RANGE_SPECS,
  SLOT_COLORS,
} from './chart-utils';

/** 顶栏系统状态：连接中 / 暂无设备 / 连接中断 / 系统离线 / 系统在线 */
export type SystemStatus = 'connecting' | 'empty' | 'error' | 'offline' | 'online';

/** 遥测卡片通道：dashboard 最新值与通道元数据（decimalPlaces/sort/unit）合并后的视图 */
export interface MonitorChannel {
  id: number;
  name: string;
  unit: string;
  decimalPlaces: number;
  valueKind: string;
  /** 固定色板按排序顺序取色 */
  accent: string;
  /** 采样周期：通道自身配置优先，缺省回退设备轮询周期；用于断档判定 */
  pollIntervalMs: null | number;
  valueNum: null | number;
  valueText: null | string;
  quality: null | number;
  stale: boolean;
  sampleTime?: null | number;
}

/** 一次趋势请求的结果：from/to 为请求窗口，用于波形 X 轴映射 */
export interface SeriesData {
  from: number;
  to: number;
  bucketSeconds: number;
  points: CollectorApi.TrendPoint[];
}

/** 波形曲线：slot 决定颜色与左右轴 */
export interface WaveSeriesItem {
  channel: MonitorChannel;
  color: string;
  data?: SeriesData;
}

export interface MonitorStore {
  cards: ComputedRef<MonitorChannel[]>;
  systemStatus: ComputedRef<SystemStatus>;
  selectedIds: Ref<number[]>;
  selectedChannels: ComputedRef<MonitorChannel[]>;
  range: Ref<TrendRange>;
  page: Ref<number>;
  pageCount: ComputedRef<number>;
  pageCards: ComputedRef<MonitorChannel[]>;
  /** 末页虚线占位卡数量；0 通道时为 0（整区空态） */
  placeholderCount: ComputedRef<number>;
  analysisIdx: Ref<number>;
  analysisChannel: ComputedRef<MonitorChannel | undefined>;
  analysisStats: ComputedRef<AnalysisStats | null>;
  waveSeries: ComputedRef<WaveSeriesItem[]>;
  /** 各通道最近 1 小时序列抽稀到 30 点的 sparkline 数据 */
  sparkValues: ComputedRef<Map<number, number[]>>;
  /** 首次趋势请求是否已结束（区分加载中与暂无数据） */
  trendLoaded: Ref<boolean>;
  toggleChannel: (id: number) => void;
  setRange: (range: TrendRange) => void;
  setPage: (page: number) => void;
  setAnalysisIdx: (idx: number) => void;
  stepAnalysis: (delta: number) => void;
}

const POLL_INTERVAL_MS = 3000;
const PAGE_SIZE = 6;
const SPARK_WINDOW_MS = 3600_000;
const SPARK_POINTS = 30;
/** 趋势接口单请求最多 8 个通道 */
const MAX_TREND_CHANNELS = 8;
const STORAGE_KEY = 'kiosk-monitor:v1';

interface PersistedState {
  range: TrendRange;
  selectedIds: number[];
}

export const monitorStoreKey: InjectionKey<MonitorStore> =
  Symbol('kiosk-monitor');

/** 页面级状态：设备上下文、3 秒轮询、选中规则与 localStorage 持久化 */
export function useMonitorStore(): MonitorStore {
  const deviceId = ref<number>();
  const devicesLoaded = ref(false);
  const devicesFailed = ref(false);
  const dashboard = shallowRef<CollectorApi.DashboardLatest>();
  const dashboardFailed = ref(false);
  const channelMeta = shallowRef<Map<number, CollectorApi.Channel>>(new Map());
  const sparkData = shallowRef<Map<number, SeriesData>>(new Map());
  const waveData = shallowRef<Map<number, SeriesData>>(new Map());

  const selectedIds = ref<number[]>([]);
  const range = ref<TrendRange>('1h');
  const page = ref(0);
  const analysisIdx = ref(0);
  const trendLoaded = ref(false);

  let destroyed = false;
  let generation = 0;
  let pollTimer: ReturnType<typeof setTimeout> | undefined;
  let dashboardInFlight = false;
  let sparkInFlight = false;
  let waveInFlight = false;
  let selectionRestored = false;
  let persistedIds: number[] = [];

  function loadPersisted() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return;
      const parsed = JSON.parse(raw) as null | Partial<PersistedState>;
      if (Array.isArray(parsed?.selectedIds)) {
        persistedIds = parsed.selectedIds.filter(
          (id): id is number => typeof id === 'number' && Number.isFinite(id),
        );
      }
      if (
        parsed?.range === '1h' ||
        parsed?.range === '8h' ||
        parsed?.range === '24h'
      ) {
        range.value = parsed.range;
      }
    } catch {
      // 持久化数据损坏时按默认状态启动
    }
  }

  function persist() {
    try {
      const state: PersistedState = {
        range: range.value,
        selectedIds: selectedIds.value,
      };
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {
      // 本地存储不可用时静默降级
    }
  }

  /** 卡片列表：dashboard 通道扁平化后合并元数据，按 sort 排序，色板按顺序取色 */
  const cards = computed<MonitorChannel[]>(() => {
    const flat = (dashboard.value?.sensors ?? []).flatMap(
      (sensor) => sensor.channels,
    );
    const devicePollMs = dashboard.value?.device.pollIntervalMs ?? null;
    return flat
      .map((channel, index) => {
        const meta = channelMeta.value.get(channel.id);
        return {
          id: channel.id,
          name: meta?.channelName ?? channel.name,
          unit: meta?.unit ?? channel.unit ?? '',
          decimalPlaces: meta?.decimalPlaces ?? 2,
          valueKind: meta?.valueKind ?? channel.valueKind,
          sort: meta?.sort ?? Number.MAX_SAFE_INTEGER,
          order: index,
          pollIntervalMs:
            meta?.pollIntervalMs ?? devicePollMs ?? null,
          valueNum: channel.valueNum ?? null,
          valueText: channel.valueText ?? null,
          quality: channel.quality ?? null,
          stale: channel.stale,
          sampleTime: channel.sampleTime,
        };
      })
      .sort((a, b) => a.sort - b.sort || a.order - b.order)
      .map((channel, index) => ({
        id: channel.id,
        name: channel.name,
        unit: channel.unit,
        decimalPlaces: channel.decimalPlaces,
        valueKind: channel.valueKind,
        accent: ACCENT_PALETTE[index % ACCENT_PALETTE.length]!,
        pollIntervalMs: channel.pollIntervalMs,
        valueNum: channel.valueNum,
        valueText: channel.valueText,
        quality: channel.quality,
        stale: channel.stale,
        sampleTime: channel.sampleTime,
      }));
  });

  const systemStatus = computed<SystemStatus>(() => {
    if (dashboard.value) {
      return dashboard.value.device.online ? 'online' : 'offline';
    }
    if (devicesFailed.value || dashboardFailed.value) return 'error';
    if (devicesLoaded.value && deviceId.value === undefined) return 'empty';
    return 'connecting';
  });

  const selectedChannels = computed(() =>
    selectedIds.value
      .map((id) => cards.value.find((channel) => channel.id === id))
      .filter((channel): channel is MonitorChannel => channel !== undefined),
  );

  const pageCount = computed(() => Math.ceil(cards.value.length / PAGE_SIZE));
  const pageCards = computed(() =>
    cards.value.slice(page.value * PAGE_SIZE, (page.value + 1) * PAGE_SIZE),
  );
  const placeholderCount = computed(() =>
    cards.value.length === 0 ? 0 : PAGE_SIZE - pageCards.value.length,
  );

  const analysisChannel = computed(() => {
    const list = selectedChannels.value;
    if (list.length === 0) return undefined;
    return list[Math.min(analysisIdx.value, list.length - 1)];
  });

  /** 当前范围下选中通道的波形序列；1h 复用 sparkline 的最近 1 小时数据 */
  const waveSeries = computed<WaveSeriesItem[]>(() => {
    const source = range.value === '1h' ? sparkData.value : waveData.value;
    return selectedChannels.value.map((channel, slot) => ({
      channel,
      color: SLOT_COLORS[slot] ?? SLOT_COLORS[0],
      data: source.get(channel.id),
    }));
  });

  const analysisStats = computed(() => {
    const channel = analysisChannel.value;
    if (!channel) return null;
    const source = range.value === '1h' ? sparkData.value : waveData.value;
    return aggregatePoints(source.get(channel.id)?.points);
  });

  const sparkValues = computed(() => {
    const map = new Map<number, number[]>();
    for (const [id, data] of sparkData.value) {
      const values = data.points
        .map((point) => point.avg)
        .filter((avg): avg is number => avg !== null && avg !== undefined);
      if (values.length > 0) map.set(id, downsample(values, SPARK_POINTS));
    }
    return map;
  });

  function numericSelectedIds() {
    return selectedChannels.value
      .filter((channel) => channel.valueKind === 'number')
      .map((channel) => channel.id);
  }

  function clearPollTimer() {
    if (pollTimer !== undefined) clearTimeout(pollTimer);
    pollTimer = undefined;
  }

  function schedulePoll() {
    clearPollTimer();
    if (destroyed || document.hidden || deviceId.value === undefined) return;
    pollTimer = setTimeout(() => void tick(), POLL_INTERVAL_MS);
  }

  /** 3 秒轮询：最新值 + 全部通道 1h 序列（sparkline），非 1h 范围追加波形序列 */
  async function tick() {
    if (destroyed || document.hidden || deviceId.value === undefined) return;
    clearPollTimer();
    await Promise.allSettled([
      refreshDashboard(),
      refreshSpark(),
      refreshWave(),
    ]);
    schedulePoll();
  }

  async function refreshDashboard() {
    const id = deviceId.value;
    if (id === undefined || dashboardInFlight || destroyed) return;
    dashboardInFlight = true;
    const gen = generation;
    try {
      const body = await getDashboardLatest(id);
      if (destroyed || gen !== generation || !body) return;
      dashboard.value = body;
      dashboardFailed.value = false;
    } catch {
      // 失败保留最后一次成功数据，仅在没有数据时反映到顶栏状态
      if (!destroyed && gen === generation) dashboardFailed.value = true;
    } finally {
      if (gen === generation) dashboardInFlight = false;
    }
  }

  async function refreshSpark() {
    const id = deviceId.value;
    if (id === undefined || sparkInFlight || destroyed) return;
    const ids = cards.value
      .filter((channel) => channel.valueKind === 'number')
      .map((channel) => channel.id);
    if (ids.length === 0) {
      trendLoaded.value = true;
      return;
    }
    sparkInFlight = true;
    const gen = generation;
    const to = Date.now();
    const from = to - SPARK_WINDOW_MS;
    try {
      const chunks: number[][] = [];
      for (let i = 0; i < ids.length; i += MAX_TREND_CHANNELS) {
        chunks.push(ids.slice(i, i + MAX_TREND_CHANNELS));
      }
      const results = await Promise.all(
        chunks.map((channelIds) =>
          getTrendSeries({
            channelIds,
            deviceId: id,
            from,
            to,
            granularity: 'auto',
          }),
        ),
      );
      if (destroyed || gen !== generation) return;
      const next = new Map<number, SeriesData>();
      for (const body of results) {
        for (const series of body?.series ?? []) {
          if (series.kind !== 'aggregate') continue;
          next.set(series.channelId, {
            from,
            to,
            bucketSeconds: series.bucketSeconds,
            points: series.points ?? [],
          });
        }
      }
      sparkData.value = next;
    } catch {
      // 失败保留旧序列，卡片 sparkline 不清空
    } finally {
      if (gen === generation) {
        sparkInFlight = false;
        trendLoaded.value = true;
      }
    }
  }

  async function refreshWave() {
    const id = deviceId.value;
    if (id === undefined || waveInFlight || destroyed) return;
    // 1h 范围直接复用 sparkline 数据，无需额外请求
    if (range.value === '1h') return;
    const ids = numericSelectedIds();
    if (ids.length === 0) {
      waveData.value = new Map();
      return;
    }
    waveInFlight = true;
    const gen = generation;
    // 记录请求时的范围与通道签名；响应到达时若已变化则丢弃并立即补一次新请求
    const rangeAtStart = range.value;
    const signature = ids.join(',');
    const to = Date.now();
    const from = to - RANGE_SPECS[rangeAtStart].minutes * 60_000;
    try {
      const body = await getTrendSeries({
        channelIds: ids,
        deviceId: id,
        from,
        to,
        granularity: 'auto',
      });
      if (destroyed || gen !== generation) return;
      if (range.value !== rangeAtStart) return;
      const next = new Map<number, SeriesData>();
      for (const series of body?.series ?? []) {
        if (series.kind !== 'aggregate') continue;
        next.set(series.channelId, {
          from,
          to,
          bucketSeconds: series.bucketSeconds,
          points: series.points ?? [],
        });
      }
      waveData.value = next;
    } catch {
      // 失败保留旧序列
    } finally {
      if (gen === generation) {
        waveInFlight = false;
        // range 是响应式引用，await 期间可能已被切换，这里重新读取并显式放宽类型
        const latestRange = range.value as TrendRange;
        const stale =
          latestRange !== rangeAtStart ||
          numericSelectedIds().join(',') !== signature;
        if (!destroyed && stale && latestRange !== '1h') void refreshWave();
      }
    }
  }

  async function load() {
    const gen = generation;
    let firstDeviceId: number | undefined;
    try {
      const devices = await getDeviceSimpleList();
      if (destroyed || gen !== generation) return;
      firstDeviceId = devices?.[0]?.id;
    } catch {
      if (!destroyed && gen === generation) devicesFailed.value = true;
      return;
    } finally {
      if (gen === generation) devicesLoaded.value = true;
    }
    if (firstDeviceId === undefined) return;
    deviceId.value = firstDeviceId;
    // 通道元数据只取一次；失败时按默认值继续，不阻塞主流程
    try {
      const body = await getChannelPage({
        deviceId: firstDeviceId,
        pageNo: 1,
        pageSize: 200,
      });
      if (destroyed || gen !== generation) return;
      channelMeta.value = new Map(
        (body?.list ?? [])
          .filter((channel) => channel.id !== undefined)
          .map((channel) => [channel.id!, channel]),
      );
    } catch {
      // 元数据缺失时使用 dashboard 字段与默认小数位
    }
    void tick();
  }

  function handleVisibilityChange() {
    if (document.hidden) {
      clearPollTimer();
      return;
    }
    void tick();
  }

  /** 选中规则：最多 2 张，FIFO 挤掉最早选中；至少保留 1 张 */
  function toggleChannel(id: number) {
    const at = selectedIds.value.indexOf(id);
    if (at >= 0) {
      if (selectedIds.value.length === 1) return;
      selectedIds.value.splice(at, 1);
    } else {
      selectedIds.value.push(id);
      if (selectedIds.value.length > 2) selectedIds.value.shift();
    }
    analysisIdx.value = 0;
    persist();
    void refreshWave();
  }

  function setRange(next: TrendRange) {
    if (next === range.value) return;
    range.value = next;
    persist();
    void refreshWave();
  }

  function setPage(next: number) {
    if (next < 0 || next > pageCount.value - 1) return;
    page.value = next;
  }

  function setAnalysisIdx(idx: number) {
    if (idx < 0 || idx >= selectedChannels.value.length) return;
    analysisIdx.value = idx;
  }

  function stepAnalysis(delta: number) {
    const length = selectedChannels.value.length;
    if (length < 2) return;
    analysisIdx.value = (analysisIdx.value + delta + length) % length;
  }

  // 首次拿到通道后恢复持久化的选中；通道被删除时清理失效选中并回退默认
  watch(cards, (list) => {
    if (list.length === 0) return;
    if (!selectionRestored) {
      selectionRestored = true;
      const restored = persistedIds.filter((id) =>
        list.some((channel) => channel.id === id),
      );
      selectedIds.value =
        restored.length > 0 ? restored.slice(0, 2) : [list[0]!.id];
      // 恢复结果与持久化内容可能不同（通道已删除），回写一次保持一致
      persist();
      void refreshWave();
      return;
    }
    const valid = selectedIds.value.filter((id) =>
      list.some((channel) => channel.id === id),
    );
    if (valid.length !== selectedIds.value.length) {
      selectedIds.value = valid.length > 0 ? valid : [list[0]!.id];
      analysisIdx.value = 0;
      persist();
      void refreshWave();
    }
  });

  watch(pageCount, (count) => {
    const maxPage = Math.max(0, count - 1);
    if (page.value > maxPage) page.value = maxPage;
  });

  loadPersisted();

  onMounted(() => {
    document.addEventListener('visibilitychange', handleVisibilityChange);
    void load();
  });

  onBeforeUnmount(() => {
    destroyed = true;
    generation += 1;
    clearPollTimer();
    document.removeEventListener('visibilitychange', handleVisibilityChange);
  });

  return {
    cards,
    systemStatus,
    selectedIds,
    selectedChannels,
    range,
    page,
    pageCount,
    pageCards,
    placeholderCount,
    analysisIdx,
    analysisChannel,
    analysisStats,
    waveSeries,
    sparkValues,
    trendLoaded,
    toggleChannel,
    setRange,
    setPage,
    setAnalysisIdx,
    stepAnalysis,
  };
}

/** 子组件取页面级状态；必须在 monitor/index.vue 的 provide 之后使用 */
export function useMonitor() {
  const store = inject(monitorStoreKey);
  if (!store) throw new Error('kiosk monitor store 未提供');
  return store;
}
