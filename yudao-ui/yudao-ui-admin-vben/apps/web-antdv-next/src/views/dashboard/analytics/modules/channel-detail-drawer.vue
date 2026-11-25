<script lang="ts" setup>
import type {
  ChannelSelection,
  ChannelStatusMeta,
  ThresholdLine,
} from '../data';

import type { CollectorApi } from '#/api/collector';

import { computed, ref, watch } from 'vue';

import { Drawer, Spin } from 'antdv-next';

import { getTrendSeries } from '#/api/collector';
import { resolveApiErrorMessage } from '#/views/collector/components/utils';

import {
  alarmLevelMeta,
  CHANNEL_STATUS_META,
  channelValueText,
  enabledThresholdRules,
  formatNumeric,
  formatShanghaiMdHm,
  formatShanghaiTime,
  hasAnyPoint,
  isFiniteNumber,
  thresholdLines,
} from '../data';
import IntervalAnalysis from './interval-analysis.vue';
import LargeTrend from './large-trend.vue';

defineOptions({ name: 'ScreenChannelDetailDrawer' });

const props = defineProps<{
  /** 当前通道的活动报警 */
  alarm?: CollectorApi.AlarmState;
  /**
   * Drawer 浮层容器：全屏时由父级传入 screenRoot，非全屏时为 body。
   * antdv Drawer 默认 teleport 到 body，浏览器全屏下会被全屏元素遮住。
   */
  container?: HTMLElement;
  /** 设备轮询周期，用于展示采样节奏 */
  devicePollIntervalMs?: number;
  hasTrendAccess: boolean;
  target?: ChannelSelection;
  /** 通道阈值矩阵；无 collector:channel:query 权限时为空 */
  threshold?: CollectorApi.ThresholdMatrixChannel;
  now: number;
}>();

const open = defineModel<boolean>('open', { default: false });

const trendSeries = ref<CollectorApi.TrendSeries>();
const trendLoading = ref(false);
const trendError = ref<string>();
const appliedRangeText = ref('--');
let querySeq = 0;

/** 抽屉统一时间窗：大趋势图与区间分析共用（单一数据源），默认 24 小时，独立于主趋势时间窗 */
const ANALYSIS_WINDOWS = [
  { label: '1 小时', seconds: 60 * 60, value: '1h' },
  { label: '24 小时', seconds: 24 * 60 * 60, value: '24h' },
  { label: '7 天', seconds: 7 * 24 * 60 * 60, value: '7d' },
] as const;
type AnalysisWindowValue = (typeof ANALYSIS_WINDOWS)[number]['value'];
const analysisWindow = ref<AnalysisWindowValue>('24h');
const analysisSeries = ref<CollectorApi.TrendSeries>();
/** 枚举通道的抽样原始点序列（kind=raw）；仅 ≤2 小时窗口请求，长窗后端会拒绝 */
const analysisRawSeries = ref<CollectorApi.TrendSeries>();
/** 原始点叠加降级（rawDegraded）时的后端警告 */
const analysisRawWarning = ref<string>();
const analysisLoading = ref(false);
const analysisError = ref<string>();
const analysisFrom = ref(0);
const analysisTo = ref(0);
const appliedAnalysisRangeText = ref('--');
let analysisSeq = 0;

/** 原始点叠加上限：与后端 RAW_OVERLAY_MAX_RANGE_SECONDS（2 小时）一致，超过会被拒绝 */
const RAW_OVERLAY_MAX_WINDOW_SECONDS = 2 * 60 * 60;

const channel = computed(() => props.target?.channel);
const device = computed(() => props.target?.device);
const sensor = computed(() => props.target?.sensor);
const isEnum = computed(() => channel.value?.valueKind === 'enum');
const valueText = computed(() => channelValueText(channel.value));
const unitText = computed(() => channel.value?.unit?.trim() || '');
/** 抽屉没有 full DashboardLatest，用设备轮询周期复现与通道卡片一致的陈旧判定 */
const status = computed<ChannelStatusMeta>(() => {
  const value = channel.value;
  if (!value) return CHANNEL_STATUS_META.missing;
  const hasSample = Boolean(value.sampleTime);
  const hasValue =
    value.valueKind === 'enum'
      ? Boolean(value.valueText?.trim())
      : isFiniteNumber(value.valueNum);
  if (!hasSample && !hasValue) return CHANNEL_STATUS_META.missing;
  if (value.quality === 2) return CHANNEL_STATUS_META.bad;
  const interval = props.devicePollIntervalMs ?? 60_000;
  if (
    value.stale ||
    (isFiniteNumber(value.sampleTime) &&
      props.now - value.sampleTime > interval * 3)
  ) {
    return CHANNEL_STATUS_META.stale;
  }
  if (value.quality === 1) return CHANNEL_STATUS_META.uncertain;
  if (value.quality === 0) return CHANNEL_STATUS_META.normal;
  return CHANNEL_STATUS_META.unknown;
});
const alarmMeta = computed(() => alarmLevelMeta(props.alarm?.level));
const hasAlarm = computed(() => (props.alarm?.level ?? 0) > 0);

const channelCode = computed(() => `CHANNEL-${channel.value?.id ?? '--'}`);

/** 趋势加载失败提示；保留旧曲线时额外说明，避免用户误读为最新窗口数据 */
const trendErrorText = computed(() => {
  if (!trendError.value) return '';
  return trendSeries.value
    ? `${trendError.value}；已保留上一次成功加载的曲线`
    : trendError.value;
});

/** 四档阈值全部展示：未配置的显示 --，未启用的明确标注，不静默隐藏 */
const thresholdItems = computed(() => {
  const source = props.threshold;
  return [
    {
      color: '#f43f5e',
      enabled: Boolean(source?.thresholdLowErrorEnabled),
      key: 'lowError',
      label: '低异常',
      value: formatNumeric(source?.thresholdLowError),
    },
    {
      color: '#fbbf24',
      enabled: Boolean(source?.thresholdLowWarningEnabled),
      key: 'lowWarning',
      label: '低预警',
      value: formatNumeric(source?.thresholdLowWarning),
    },
    {
      color: '#fbbf24',
      enabled: Boolean(source?.thresholdHighWarningEnabled),
      key: 'highWarning',
      label: '高预警',
      value: formatNumeric(source?.thresholdHighWarning),
    },
    {
      color: '#f43f5e',
      enabled: Boolean(source?.thresholdHighErrorEnabled),
      key: 'highError',
      label: '高异常',
      value: formatNumeric(source?.thresholdHighError),
    },
  ];
});

const enabledThresholdLines = computed<ThresholdLine[]>(() =>
  thresholdLines(props.threshold),
);

/** 区间分析只使用“已启用且有值”的阈值档位 */
const analysisRules = computed(() => enabledThresholdRules(props.threshold));
const analysisWindowSeconds = computed(
  () =>
    ANALYSIS_WINDOWS.find((item) => item.value === analysisWindow.value)
      ?.seconds ?? 24 * 60 * 60,
);
/** 与主趋势一致：后端为每个通道都返回序列，必须看有效点而不是 series 长度 */
const analysisHasData = computed(() =>
  hasAnyPoint(
    [analysisSeries.value, analysisRawSeries.value].filter(
      (item): item is CollectorApi.TrendSeries => item !== undefined,
    ),
  ),
);

const stats = computed(() => [
  { label: '最新值', value: `${valueText.value} ${unitText.value}`.trim() },
  {
    color: status.value.color,
    label: '通道状态',
    value: status.value.label,
  },
  {
    label: '最近采样',
    value: formatShanghaiTime(channel.value?.sampleTime),
  },
  {
    label: '采样周期',
    value:
      props.devicePollIntervalMs === undefined
        ? '--'
        : `${props.devicePollIntervalMs} ms`,
  },
  {
    label: '设备',
    value: `${device.value?.deviceName ?? '--'}（${
      device.value?.deviceCode ?? '--'
    }）`,
  },
  { label: '传感器', value: sensor.value?.name || '--' },
]);

watch(
  [open, () => props.target?.channel.id, analysisWindow],
  ([, channelId], [, previousChannelId]) => {
    // 切换通道时清空旧曲线：加载失败不能残留其他通道的数据；
    // 仅切换时间窗时保留旧曲线，失败时仍可回看（见 loadTrend）
    if (channelId !== previousChannelId) {
      trendSeries.value = undefined;
    }
    void loadTrend();
  },
  { immediate: true },
);

watch(
  [open, () => props.target?.channel.id, analysisWindow],
  () => {
    void loadAnalysis();
  },
  { immediate: true },
);

async function loadTrend() {
  querySeq += 1;
  const seq = querySeq;
  trendError.value = undefined;
  if (!open.value || !props.target || !props.hasTrendAccess || isEnum.value) {
    trendLoading.value = false;
    return;
  }
  const to = Date.now();
  const from = to - analysisWindowSeconds.value * 1000;
  appliedRangeText.value = `${formatShanghaiMdHm(from)} ~ ${formatShanghaiMdHm(to)}`;
  trendLoading.value = true;
  try {
    const body = await getTrendSeries({
      channelIds: [props.target.channel.id],
      deviceId: props.target.device.id,
      from,
      granularity: 'auto',
      includeRaw: false,
      to,
    });
    if (seq !== querySeq) return;
    trendSeries.value =
      body?.series.find((item) => item.kind !== 'raw') ?? body?.series[0];
    if (!trendSeries.value) {
      trendError.value = '趋势接口未返回该通道的数据';
    }
  } catch (error) {
    if (seq !== querySeq) return;
    // 失败保留旧曲线，由模板在错误提示旁继续渲染 trendSeries
    trendError.value = resolveApiErrorMessage(error, '趋势加载失败');
  } finally {
    if (seq === querySeq) {
      trendLoading.value = false;
    }
  }
}

/**
 * 区间分析独立查询：只调用一次 getTrendSeries（单通道）。
 * 枚举通道在 ≤2 小时窗口叠加 includeRaw 取抽样原始点（真实枚举码）；
 * 24h/7d 超过后端原始点叠加范围上限，只能拿聚合桶，由 IntervalAnalysis
 * 按“整桶同码 + 状态变化标记”降级展示，不做均值近似。
 */
async function loadAnalysis() {
  analysisSeq += 1;
  const seq = analysisSeq;
  analysisSeries.value = undefined;
  analysisRawSeries.value = undefined;
  analysisRawWarning.value = undefined;
  analysisError.value = undefined;
  if (!open.value || !props.target || !props.hasTrendAccess) {
    analysisLoading.value = false;
    return;
  }
  const to = Date.now();
  const from = to - analysisWindowSeconds.value * 1000;
  analysisFrom.value = from;
  analysisTo.value = to;
  appliedAnalysisRangeText.value = `${formatShanghaiMdHm(
    from,
  )} ~ ${formatShanghaiMdHm(to)}`;
  const includeRaw =
    isEnum.value && analysisWindowSeconds.value <= RAW_OVERLAY_MAX_WINDOW_SECONDS;
  analysisLoading.value = true;
  try {
    const body = await getTrendSeries({
      channelIds: [props.target.channel.id],
      deviceId: props.target.device.id,
      from,
      granularity: 'auto',
      includeRaw,
      to,
    });
    if (seq !== analysisSeq) return;
    analysisSeries.value =
      body?.series.find((item) => item.kind !== 'raw') ?? body?.series[0];
    if (includeRaw) {
      analysisRawSeries.value = body?.series.find(
        (item) => item.kind === 'raw',
      );
      analysisRawWarning.value = body?.rawDegraded
        ? (body?.rawWarning ?? '原始点加载失败')
        : undefined;
    }
    if (!analysisSeries.value && !analysisRawSeries.value) {
      analysisError.value = '区间分析接口未返回该通道的数据';
    }
  } catch (error) {
    if (seq !== analysisSeq) return;
    analysisError.value = resolveApiErrorMessage(error, '区间分析加载失败');
  } finally {
    if (seq === analysisSeq) {
      analysisLoading.value = false;
    }
  }
}

function closeDrawer() {
  open.value = false;
}
</script>

<template>
  <Drawer
    v-model:open="open"
    :destroy-on-hidden="true"
    :get-container="container"
    placement="right"
    root-class="screen-detail-drawer"
    :styles="{
      body: {
        background: '#070C18',
        overflowY: 'auto',
        padding: '12px 18px 16px',
      },
      header: {
        background: '#0B1222',
        borderBottom: '1px solid rgba(72, 104, 158, 0.32)',
      },
    }"
    width="min(720px, 92vw)"
  >
    <template #title>
      <div class="drawer-title">
        <span class="drawer-code">{{ channelCode }}</span>
        <span class="drawer-name">
          {{ channel?.name || '通道详情' }}
        </span>
        <span class="drawer-meta">
          {{ device?.deviceName || '--' }} /
          {{ sensor?.name || '--' }}
        </span>
      </div>
    </template>

    <div v-if="target" class="drawer-body">
      <div class="badge-row">
        <span class="badge" :style="{ color: status.color }">
          <span
            class="badge-dot"
            :style="{ background: status.color }"
            aria-hidden="true"
          ></span>
          {{ status.label }}
        </span>
        <span class="badge">
          {{ isEnum ? '枚举通道' : '数值通道' }}
        </span>
        <span v-if="hasAlarm" class="badge badge--alarm">
          {{ alarmMeta.label }} · 阈值 {{ formatNumeric(alarm?.lastThreshold) }}
        </span>
        <span v-if="channel?.stale" class="badge badge--muted">
          后端标记陈旧
        </span>
      </div>

      <div class="hero-value">
        <span class="hero-number">{{ valueText }}</span>
        <span v-if="unitText" class="hero-unit">{{ unitText }}</span>
        <span class="hero-time">
          最近采样 {{ formatShanghaiTime(channel?.sampleTime) }}
        </span>
      </div>

      <section class="drawer-section">
        <div class="drawer-section-head">
          <span class="drawer-section-title">趋势 / TREND</span>
          <span class="drawer-section-sub">{{ appliedRangeText }}</span>
        </div>
        <div v-if="!hasTrendAccess" class="drawer-note">
          当前账号缺少
          <code>collector:trend:query</code>
          权限，已隐藏趋势图；下方仍展示最新值与阈值信息。
        </div>
        <div v-else-if="isEnum" class="drawer-note">
          枚举通道没有数值趋势，最新枚举值见上方。
        </div>
        <div v-else class="trend-wrap">
          <div v-if="trendLoading" class="trend-loading">
            <Spin size="small" />
            <span>正在加载趋势…</span>
          </div>
          <template v-else>
            <div v-if="trendError" class="drawer-note drawer-note--error">
              {{ trendErrorText }}
            </div>
            <LargeTrend
              v-if="trendSeries"
              :range-seconds="analysisWindowSeconds"
              :series="trendSeries"
              :thresholds="enabledThresholdLines"
            />
            <div v-else-if="!trendError" class="drawer-note">
              该通道没有可用趋势数据。
            </div>
          </template>
        </div>
      </section>

      <section class="drawer-section">
        <div class="drawer-section-head">
          <span class="drawer-section-title">区间分析 / INTERVAL</span>
          <span class="drawer-section-sub">{{ appliedAnalysisRangeText }}</span>
        </div>

        <div class="window-tabs" aria-label="选择区间分析时间窗">
          <button
            v-for="item in ANALYSIS_WINDOWS"
            :key="item.value"
            type="button"
            class="window-btn"
            :class="{ 'window-btn--active': analysisWindow === item.value }"
            :aria-pressed="analysisWindow === item.value"
            @click="analysisWindow = item.value"
          >
            {{ item.label }}
          </button>
        </div>

        <div v-if="!hasTrendAccess" class="drawer-note">
          当前账号缺少
          <code>collector:trend:query</code>
          权限，区间分析不可用。
        </div>
        <div v-else-if="analysisLoading" class="analysis-loading">
          <Spin size="small" />
          <span>正在加载区间数据…</span>
        </div>
        <div v-else-if="analysisError" class="drawer-note drawer-note--error">
          {{ analysisError }}
        </div>
        <div v-else-if="!target" class="drawer-note">未选择通道。</div>
        <IntervalAnalysis
          v-else-if="analysisHasData && target"
          :channel="target.channel"
          :from="analysisFrom"
          :raw-series="analysisRawSeries"
          :raw-warning="analysisRawWarning"
          :rules="analysisRules"
          :series="analysisSeries"
          :threshold="threshold"
          :to="analysisTo"
          :unit="unitText"
        />
        <div v-else class="drawer-note">
          {{
            isEnum
              ? '该枚举通道在分析区间内没有可用数据。'
              : '该通道在分析区间内没有可用数据，无法计算区间指标。'
          }}
        </div>
      </section>

      <section class="drawer-section">
        <div class="drawer-section-head">
          <span class="drawer-section-title">读数 / READINGS</span>
        </div>
        <div class="stat-grid">
          <div v-for="item in stats" :key="item.label" class="stat-cell">
            <span class="stat-label">{{ item.label }}</span>
            <span
              class="stat-value"
              :style="item.color ? { color: item.color } : {}"
            >
              {{ item.value }}
            </span>
          </div>
        </div>
      </section>

      <section class="drawer-section">
        <div class="drawer-section-head">
          <span class="drawer-section-title">阈值 / THRESHOLDS</span>
          <span class="drawer-section-sub">
            {{
              threshold
                ? '来源：通道阈值矩阵'
                : '阈值矩阵不可用（需 collector:channel:query）'
            }}
          </span>
        </div>
        <div class="threshold-grid">
          <div
            v-for="item in thresholdItems"
            :key="item.key"
            class="threshold-cell"
            :class="{ 'threshold-cell--off': !item.enabled }"
          >
            <span class="threshold-label" :style="{ color: item.color }">
              {{ item.label }}
            </span>
            <span class="threshold-value">
              {{ item.value }} {{ unitText }}
            </span>
            <span class="threshold-flag">
              {{ item.enabled ? '已启用' : '未启用' }}
            </span>
          </div>
        </div>
        <div v-if="!threshold" class="drawer-note">
          未获取到阈值矩阵；若当前存在活动报警，可在下方报警阈值处查看触发阈值。
        </div>
        <div
          v-if="
            alarm?.lastThreshold !== null && alarm?.lastThreshold !== undefined
          "
          class="drawer-note"
        >
          活动报警触发阈值：{{ formatNumeric(alarm.lastThreshold) }}
          {{ unitText }}
        </div>
      </section>

      <div class="drawer-actions">
        <button type="button" class="drawer-close" @click="closeDrawer">
          关闭（Esc）
        </button>
      </div>
    </div>
    <div v-else class="drawer-note">未选择通道。</div>
  </Drawer>
</template>

<style scoped>
.drawer-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.drawer-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 10px;
  color: #22d3ee;
  letter-spacing: 1.2px;
}

.drawer-name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 16px;
  font-weight: 600;
  color: #e6eef9;
  white-space: nowrap;
}

.drawer-meta {
  font-size: 11px;
  color: #94a3b8;
}

.drawer-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.badge-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.badge {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  padding: 2px 7px;
  font-size: 11px;
  color: #b7c6dd;
  background: rgb(148 163 184 / 8%);
  border: 1px solid rgb(72 104 158 / 30%);
  border-radius: 4px;
}

.badge-dot {
  width: 5px;
  height: 5px;
  border-radius: 999px;
}

.badge--alarm {
  color: #fda4af;
  background: rgb(244 63 94 / 10%);
  border-color: rgb(244 63 94 / 36%);
}

.badge--muted {
  color: #94a3b8;
}

.hero-value {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: baseline;
  padding: 8px 12px;
  background: linear-gradient(
    120deg,
    rgb(34 211 238 / 8%),
    rgb(13 22 40 / 40%)
  );
  border: 1px solid rgb(34 211 238 / 22%);
  border-radius: 8px;
}

.hero-number {
  font-size: 34px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: #f1f5f9;
  letter-spacing: 0.5px;
}

.hero-unit {
  font-size: 13px;
  color: #94a3b8;
}

.hero-time {
  margin-left: auto;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  color: #94a3b8;
}

.drawer-section {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.drawer-section-head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: baseline;
  justify-content: space-between;
}

.drawer-section-title {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  color: #94a3b8;
  letter-spacing: 1.6px;
}

.drawer-section-sub {
  font-size: 10px;
  color: #94a3b8;
}

.drawer-note {
  padding: 8px 0;
  font-size: 12px;
  color: #94a3b8;
}

.drawer-note--error {
  color: #fda4af;
}

.drawer-note code {
  padding: 0 3px;
  font-size: 10px;
  color: #cbd5e1;
  background: rgb(148 163 184 / 10%);
  border-radius: 2px;
}

.trend-wrap {
  position: relative;
  min-height: 120px;
}

.trend-loading {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
  height: 180px;
  font-size: 12px;
  color: #94a3b8;
}

.window-tabs {
  display: flex;
  gap: 4px;
}

.window-btn {
  padding: 2px 10px;
  font: inherit;
  font-size: 11px;
  color: #94a3b8;
  cursor: pointer;
  background: transparent;
  border: 1px solid rgb(72 104 158 / 28%);
  border-radius: 4px;
}

.window-btn:hover {
  color: #b7c6dd;
  border-color: rgb(72 104 158 / 48%);
}

.window-btn--active {
  color: #22d3ee;
  background: rgb(34 211 238 / 8%);
  border-color: rgb(34 211 238 / 55%);
}

.window-btn:focus-visible {
  outline: 1px solid #22d3ee;
  outline-offset: 2px;
}

.analysis-loading {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
  min-height: 88px;
  font-size: 12px;
  color: #94a3b8;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}

.stat-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  padding: 6px 9px;
  background: rgb(17 28 50 / 55%);
  border: 1px solid rgb(72 104 158 / 20%);
  border-radius: 5px;
}

.stat-label {
  font-size: 12px;
  color: #94a3b8;
}

.stat-value {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  color: #e2e8f0;
  white-space: nowrap;
}

.threshold-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
}

.threshold-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 8px;
  background: rgb(17 28 50 / 55%);
  border: 1px solid rgb(72 104 158 / 20%);
  border-radius: 5px;
}

.threshold-cell--off {
  opacity: 0.45;
}

.threshold-label {
  font-size: 11px;
}

.threshold-value {
  font-size: 15px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: #e2e8f0;
}

.threshold-flag {
  font-size: 10px;
  color: #94a3b8;
}

/* 窄视口（抽屉按 92vw 收缩）时读数与阈值降为 2 列，避免横向挤压 */
@media (max-width: 640px) {
  .stat-grid,
  .threshold-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
}

.drawer-close {
  padding: 5px 14px;
  font: inherit;
  font-size: 12px;
  color: #cbd5e1;
  cursor: pointer;
  background: #111c32;
  border: 1px solid rgb(72 104 158 / 40%);
  border-radius: 5px;
}

.drawer-close:hover {
  background: #16233d;
}
</style>

<!-- Drawer 会 teleport 到 body，外壳样式需挂在 root-class 上 -->
<style>
.screen-detail-drawer .ant-drawer-content,
.screen-detail-drawer .ant-drawer-content-wrapper {
  background: #070c18;
}

.screen-detail-drawer .ant-drawer-mask {
  background: rgb(2 6 14 / 68%);
}

.screen-detail-drawer .ant-drawer-header {
  background: #0b1222;
  border-bottom: 1px solid rgb(72 104 158 / 32%);
}

.screen-detail-drawer .ant-drawer-title {
  color: #e6eef9;
}

.screen-detail-drawer .ant-drawer-close {
  color: #94a3b8;
}

.screen-detail-drawer .ant-drawer-body {
  background: #070c18;
}
</style>
