<script lang="ts" setup>
import type { Dayjs } from 'dayjs';

import type { CollectorApi } from '#/api/collector';

import { computed, onBeforeUnmount, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import {
  Alert,
  Button,
  DateRangePicker,
  Empty,
  RadioButton,
  RadioGroup,
  Select,
  Spin,
  Switch,
  Tag,
  message,
} from 'antdv-next';
import dayjs from 'dayjs';

import { getTrendSeries } from '#/api/collector';

import {
  buildChannelColors,
  formatTrendValue,
  granularityHint,
  hasAnyPoint,
  isGranularityAvailable,
  MAX_RAW_OVERLAY_RANGE_SECONDS,
  MAX_TOTAL_RANGE_SECONDS,
} from '../../trend/data';
import TrendChart from '../../trend/modules/trend-chart.vue';
import {
  formatAlarmEntityName,
  formatAlarmType,
  getAlarmLevelConfig,
  getAlarmStatusConfig,
  isNumericTrendAlarm,
} from '../data';

defineOptions({ name: 'CollectorAlarmTrendDialog' });

/** 报警时刻趋势只查询数值通道的趋势，分辨率选项与趋势分析页保持同一组 */
type ResolutionValue = '1h' | '1m' | '1s' | 'auto' | 'raw';

interface WindowPreset {
  label: string;
  /** 触发时刻前后各多少秒；自定义窗口为 0 */
  halfSeconds: number;
  value: '15m' | '1h' | '30m' | '6h' | 'custom';
}

interface AppliedRange {
  from: number;
  to: number;
  rangeSeconds: number;
  resolution: ResolutionValue;
  includeRaw: boolean;
  /** 预设窗口的右边界是否被当前时间截断 */
  rightTruncated: boolean;
}

interface ApiErrorLike {
  data?: { code?: number; msg?: string };
  message?: string;
  response?: { data?: { code?: number; msg?: string } };
}

const WINDOW_PRESETS: WindowPreset[] = [
  { label: '前后 15 分钟', value: '15m', halfSeconds: 15 * 60 },
  { label: '前后 30 分钟', value: '30m', halfSeconds: 30 * 60 },
  { label: '前后 1 小时', value: '1h', halfSeconds: 60 * 60 },
  { label: '前后 6 小时', value: '6h', halfSeconds: 6 * 60 * 60 },
  { label: '自定义', value: 'custom', halfSeconds: 0 },
];

const RESOLUTION_OPTIONS: { label: string; value: ResolutionValue }[] = [
  { label: '自动', value: 'auto' },
  { label: '原始', value: 'raw' },
  { label: '1 秒', value: '1s' },
  { label: '1 分钟', value: '1m' },
  { label: '1 小时', value: '1h' },
];

const RESOLUTION_LABELS: Record<ResolutionValue, string> = {
  '1h': '1 小时',
  '1m': '1 分钟',
  '1s': '1 秒',
  auto: '自动',
  raw: '原始',
};

const alarm = ref<CollectorApi.Alarm>();
const preset = ref<WindowPreset['value']>('30m');
const customRange = ref<[Dayjs, Dayjs] | null>(null);
const resolution = ref<ResolutionValue>('auto');
const includeRaw = ref(false);

const loading = ref(false);
const loadError = ref<string>();
const channelMissing = ref(false);
const result = ref<CollectorApi.TrendSeriesResult>();
const applied = ref<AppliedRange>();
/** 时间窗变化导致分辨率自动回退时的显式提示 */
const resolutionFallback = ref<string>();

let querySeq = 0;
let autoTimer: ReturnType<typeof setTimeout> | undefined;
/** 分辨率回退由程序触发时，避免控件 watcher 立即清除提示 */
let applyingResolutionFallback = false;
let autoQueryEnabled = false;

/** 数值趋势的前提：有通道且报警可判断为数值阈值报警 */
const unsupportedReason = computed(() => {
  const current = alarm.value;
  if (!current) return '';
  if (!current.channelId) return '该报警没有关联通道，无法查看数值趋势';
  if (!isNumericTrendAlarm(current)) return '该报警不支持数值趋势';
  return '';
});

/** 状态未结束且没有恢复时间时视为活动报警，只提供手动刷新，不自动轮询 */
const isActiveAlarm = computed(() => {
  const current = alarm.value;
  return !!current && current.status !== 2 && !current.clearedAt;
});

const levelConfig = computed(() => getAlarmLevelConfig(alarm.value?.level));

const statusConfig = computed(() =>
  getAlarmStatusConfig(alarm.value?.status, alarm.value?.eventType),
);

const rangeHint = computed(() => granularityHint(uiRangeSeconds.value));

const rawHint = computed(() => {
  if (uiRangeSeconds.value <= 0) {
    return '选择自定义时间范围后可叠加原始点';
  }
  return canIncludeRaw.value
    ? '仅叠加当前窗口的原始采样点'
    : `时间窗超过 ${MAX_RAW_OVERLAY_RANGE_SECONDS / 3600} 小时，不能叠加原始点`;
});

const chartColors = computed(() => {
  const channelId = alarm.value?.channelId;
  return channelId ? buildChannelColors([channelId]) : {};
});

/** 只保留当前报警通道的主序列；未开启叠加原始点时过滤 raw 序列 */
const chartSeries = computed(() => {
  const query = applied.value;
  const channelId = alarm.value?.channelId;
  if (!query || !result.value || !channelId) return [];
  return result.value.series.filter(
    (series) =>
      series.channelId === channelId &&
      (query.includeRaw || series.kind !== 'raw'),
  );
});

const hasData = computed(() => hasAnyPoint(chartSeries.value));

const rawDegradedWarning = computed(() =>
  result.value?.rawDegraded
    ? (result.value.rawWarning ?? '原始点加载超时，已仅展示聚合趋势')
    : undefined,
);

/** 时刻标线与窗口边界的容差：边界本身（如触发时刻恰好等于左边界）允许落入 */
const MARK_TIME_EPSILON_MS = 1000;

/** 当前图表真实数据的 y 范围（主序列与原始叠加的全部点值）；无有效数据时为 null */
const dataValueRange = computed((): { max: number; min: number } | null => {
  let min = Number.POSITIVE_INFINITY;
  let max = Number.NEGATIVE_INFINITY;
  for (const series of chartSeries.value) {
    for (const point of series.points) {
      for (const candidate of [point.avg, point.value, point.min, point.max]) {
        if (
          candidate === null ||
          candidate === undefined ||
          !Number.isFinite(candidate)
        ) {
          continue;
        }
        min = Math.min(min, candidate);
        max = Math.max(max, candidate);
      }
    }
  }
  return Number.isFinite(min) && Number.isFinite(max) ? { min, max } : null;
});

/** 报警携带的阈值；无阈值或非数值时返回 undefined */
const alarmThreshold = computed(() => {
  const threshold = alarm.value?.thresholdValue;
  if (threshold === null || threshold === undefined) return undefined;
  return Number.isFinite(threshold) ? threshold : undefined;
});

/**
 * 阈值标线只在阈值处于当前窗口真实数据 y 范围内时绘制；
 * 越界不画（图表组件侧也会兜底过滤），避免把 y 轴拉大、曲线压平。
 * 标线取自报警行本身，不请求通道详情，也不依赖通道查询权限。
 */
const markLines = computed(() => {
  const threshold = alarmThreshold.value;
  const range = dataValueRange.value;
  if (threshold === undefined || !range) return [];
  if (threshold < range.min || threshold > range.max) return [];
  return [
    {
      value: threshold,
      label: '事发阈值',
      color: '#fa8c16',
      dashed: true,
    },
  ];
});

/** 阈值落在当前窗口数据范围外时给出明确提示，避免标线静默消失 */
const thresholdOutOfRangeNotice = computed(() => {
  const threshold = alarmThreshold.value;
  const range = dataValueRange.value;
  if (threshold === undefined || !range) return '';
  if (threshold >= range.min && threshold <= range.max) return '';
  return `阈值 ${formatTrendValue(threshold)} 不在当前窗口数据范围（${formatTrendValue(
    range.min,
  )} ~ ${formatTrendValue(range.max)}）内，未绘制阈值标线`;
});

/**
 * 时刻标线只保留落在已应用查询窗口内的触发/恢复时刻；
 * 窗口外的时刻不画，也不夹紧到边界，避免 x 轴被扩到窗口之外。
 */
const markTimes = computed(() => {
  const current = alarm.value;
  const query = applied.value;
  if (!current || !query) return [];
  const inWindow = (t: number) =>
    Number.isFinite(t) &&
    t >= query.from - MARK_TIME_EPSILON_MS &&
    t <= query.to + MARK_TIME_EPSILON_MS;
  const marks: Array<{
    color: string;
    dashed: boolean;
    label: string;
    t: number;
  }> = [];
  const triggered = dayjs(current.triggeredAt).valueOf();
  if (inWindow(triggered)) {
    marks.push({
      t: triggered,
      label: '触发',
      color: '#fa541c',
      dashed: false,
    });
  }
  if (current.clearedAt) {
    const cleared = dayjs(current.clearedAt).valueOf();
    if (inWindow(cleared)) {
      marks.push({
        t: cleared,
        label: '恢复',
        color: '#52c41a',
        dashed: true,
      });
    }
  }
  return marks;
});

const appliedRangeText = computed(() => {
  const query = applied.value;
  if (!query) return '--';
  return `${formatDateTime(query.from)} ~ ${formatDateTime(query.to)}`;
});

/** 当前控件对应的时间范围秒数，用于禁用分辨率/原始点选项 */
const uiRangeSeconds = computed(() => {
  if (preset.value === 'custom') {
    const range = customRange.value;
    if (!range || range.length !== 2) return 0;
    const seconds = Math.round((range[1].valueOf() - range[0].valueOf()) / 1000);
    return Math.max(0, seconds);
  }
  const half = WINDOW_PRESETS.find(
    (item) => item.value === preset.value,
  )?.halfSeconds;
  return half ? half * 2 : 0;
});

const canIncludeRaw = computed(
  () =>
    uiRangeSeconds.value > 0 &&
    uiRangeSeconds.value <= MAX_RAW_OVERLAY_RANGE_SECONDS,
);

const resolutionOptions = computed(() =>
  RESOLUTION_OPTIONS.map((option) => ({
    ...option,
    disabled: !isGranularityAvailable(option.value, uiRangeSeconds.value),
  })),
);

function resolveErrorMessage(error: unknown): string {
  const candidate = error as ApiErrorLike | undefined;
  return (
    candidate?.response?.data?.msg ??
    candidate?.data?.msg ??
    candidate?.message ??
    '请稍后重试'
  );
}

/** 通道被删除时后端返回“通道不存在”，与普通接口错误区分开 */
function isChannelMissingError(error: unknown): boolean {
  const candidate = error as ApiErrorLike | undefined;
  const code = candidate?.response?.data?.code ?? candidate?.data?.code;
  if (code === 1_023_005_000 || code === 1_023_005_004) {
    return true;
  }
  return /通道.*不存在|不存在或不属于设备/u.test(resolveErrorMessage(error));
}

/**
 * 计算本次查询范围：
 * 左边界 = 触发时刻 - 窗口；右边界 = min(触发时刻 + 窗口, 当前时间)。
 * 右边界不大于左边界时退化为 [当前时间 - 5 分钟, 当前时间]。
 * 自定义范围不允许未来时间，且不能超过趋势接口的 365 天上限。
 */
function computeRange(): undefined | Omit<AppliedRange, 'includeRaw' | 'resolution'> {
  const current = alarm.value;
  if (!current) return undefined;
  const now = Date.now();
  const triggered = dayjs(current.triggeredAt).valueOf();

  if (preset.value === 'custom') {
    const range = customRange.value;
    if (!range || range.length !== 2) {
      message.warning('请先选择自定义时间范围');
      return undefined;
    }
    const from = range[0].valueOf();
    const to = range[1].valueOf();
    if (!Number.isFinite(from) || !Number.isFinite(to) || to <= from) {
      message.warning('开始时间必须早于结束时间');
      return undefined;
    }
    if (to > now) {
      message.warning('自定义范围不能选择未来时间');
      return undefined;
    }
    const rangeSeconds = Math.round((to - from) / 1000);
    if (rangeSeconds > MAX_TOTAL_RANGE_SECONDS) {
      message.warning('单次查询时间范围不能超过 365 天');
      return undefined;
    }
    return { from, to, rangeSeconds, rightTruncated: false };
  }

  const halfSeconds =
    WINDOW_PRESETS.find((item) => item.value === preset.value)?.halfSeconds ??
    30 * 60;
  const desiredFrom = triggered - halfSeconds * 1000;
  const desiredTo = triggered + halfSeconds * 1000;
  const from = desiredFrom;
  const to = Math.min(desiredTo, now);
  if (!Number.isFinite(from) || !Number.isFinite(to) || to <= from) {
    // 触发时刻异常（如未来时间）时退化为最近 5 分钟，保证弹窗仍可用
    return {
      from: now - 5 * 60 * 1000,
      to: now,
      rangeSeconds: 5 * 60,
      rightTruncated: false,
    };
  }
  return {
    from,
    to,
    rangeSeconds: Math.round((to - from) / 1000),
    rightTruncated: to < desiredTo,
  };
}

function scheduleAutoQuery() {
  if (autoTimer !== undefined) clearTimeout(autoTimer);
  autoTimer = setTimeout(() => {
    autoTimer = undefined;
    if (autoQueryEnabled) void handleQuery();
  }, 250);
}

async function handleQuery() {
  const current = alarm.value;
  if (!current || !current.channelId || unsupportedReason.value) return;
  const range = computeRange();
  if (!range) return;
  if (!isGranularityAvailable(resolution.value, range.rangeSeconds)) {
    loadError.value = `当前时间窗不支持「${RESOLUTION_LABELS[resolution.value]}」分辨率。${rangeHint.value}，请改用这些分辨率或缩短时间范围`;
    return;
  }

  const useRawOverlay =
    includeRaw.value && range.rangeSeconds <= MAX_RAW_OVERLAY_RANGE_SECONDS;
  const seq = ++querySeq;
  loading.value = true;
  loadError.value = undefined;
  channelMissing.value = false;
  try {
    const body = await getTrendSeries({
      channelIds: [current.channelId],
      from: range.from,
      granularity: resolution.value,
      includeRaw: useRawOverlay,
      to: range.to,
    });
    if (seq !== querySeq) return;
    if (!body) {
      throw new Error('趋势接口返回为空');
    }
    result.value = body;
    applied.value = {
      ...range,
      resolution: resolution.value,
      includeRaw: useRawOverlay,
    };
  } catch (error) {
    if (seq !== querySeq) return;
    if (isChannelMissingError(error)) {
      // 通道已删除时保留报警摘要，仅把图表区域切换为明确的不可用状态
      channelMissing.value = true;
      result.value = undefined;
      applied.value = undefined;
    } else {
      // 普通失败不清空报警摘要，也不丢弃上一次成功图表
      loadError.value = resolveErrorMessage(error);
    }
  } finally {
    if (seq === querySeq) {
      loading.value = false;
    }
  }
}

/** 禁止选择今天之后的日期；今天内的未来时刻由查询校验拦截 */
function disabledFutureDate(current?: Dayjs): boolean {
  return !!current && current.valueOf() > dayjs().endOf('day').valueOf();
}

function resetState() {
  querySeq += 1;
  if (autoTimer !== undefined) {
    clearTimeout(autoTimer);
    autoTimer = undefined;
  }
  resolutionFallback.value = undefined;
  applyingResolutionFallback = false;
  autoQueryEnabled = false;
  preset.value = '30m';
  customRange.value = null;
  resolution.value = 'auto';
  includeRaw.value = false;
  loading.value = false;
  loadError.value = undefined;
  channelMissing.value = false;
  result.value = undefined;
  applied.value = undefined;
}

const [Modal, modalApi] = useVbenModal({
  title: '报警时刻趋势',
  footer: false,
  onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      alarm.value = undefined;
      resetState();
      return;
    }
    alarm.value = modalApi.getData() as CollectorApi.Alarm;
    resetState();
    // 初始查询不受控件 watcher 影响；打开后用户改动窗口/分辨率再自动刷新
    setTimeout(() => {
      autoQueryEnabled = true;
    }, 0);
    void handleQuery();
  },
});

watch(
  [preset, customRange, resolution, includeRaw],
  () => {
    // 用户再次调整控件时清除上一次回退提示；程序触发的自动回退不在此清除
    if (!applyingResolutionFallback) {
      resolutionFallback.value = undefined;
    }
    if (autoQueryEnabled) scheduleAutoQuery();
  },
  { deep: true },
);

/** 时间窗变化后，当前分辨率或原始点叠加可能超出上限，显式回退并提示 */
watch(uiRangeSeconds, (seconds) => {
  if (!isGranularityAvailable(resolution.value, seconds)) {
    const previous = RESOLUTION_LABELS[resolution.value];
    applyingResolutionFallback = true;
    resolution.value = 'auto';
    // 用弹窗内提示代替全局 message，避免 antd Message 在渲染周期内调用产生告警
    resolutionFallback.value = `当前时间窗不支持「${previous}」分辨率，已自动切换为自动`;
    setTimeout(() => {
      applyingResolutionFallback = false;
    }, 0);
  }
  if (seconds <= 0 || seconds > MAX_RAW_OVERLAY_RANGE_SECONDS) {
    includeRaw.value = false;
  }
});

onBeforeUnmount(() => {
  if (autoTimer !== undefined) clearTimeout(autoTimer);
});
</script>

<template>
  <Modal class="w-[1040px] max-w-[94vw]">
    <div v-if="alarm" class="flex min-w-0 flex-col gap-3">
      <!-- 顶部：直接使用报警行数据，不请求通道详情 -->
      <div class="flex flex-wrap items-center gap-2">
        <Tag :color="levelConfig.color">{{ levelConfig.text }}</Tag>
        <Tag :color="statusConfig.color">{{ statusConfig.text }}</Tag>
        <span class="min-w-0 flex-1 truncate text-sm font-semibold">
          {{ alarm.message || '报警内容缺失' }}
        </span>
      </div>

      <div
        class="border-border bg-muted/30 grid grid-cols-2 gap-x-4 gap-y-1.5 rounded border p-3 text-xs md:grid-cols-4"
      >
        <div class="min-w-0">
          <span class="opacity-60">设备：</span>
          <span class="font-medium">
            {{ formatAlarmEntityName(alarm.deviceName, '设备', alarm.deviceId) }}
          </span>
        </div>
        <div class="min-w-0">
          <span class="opacity-60">传感器：</span>
          <span class="font-medium">
            {{
              formatAlarmEntityName(
                alarm.sensorName,
                '传感器',
                alarm.sensorId,
              )
            }}
          </span>
        </div>
        <div class="min-w-0">
          <span class="opacity-60">通道：</span>
          <span class="font-medium">
            {{
              formatAlarmEntityName(
                alarm.channelName,
                '通道',
                alarm.channelId,
              )
            }}
          </span>
        </div>
        <div class="min-w-0">
          <span class="opacity-60">触发时间：</span>
          <span class="font-medium">{{ formatDateTime(alarm.triggeredAt) }}</span>
        </div>
        <div>
          <span class="opacity-60">触发值：</span>
          <span class="font-medium">{{ formatTrendValue(alarm.triggerValue) }}</span>
        </div>
        <div>
          <span class="opacity-60">事发阈值：</span>
          <span class="font-medium">
            {{ formatTrendValue(alarm.thresholdValue) }}
          </span>
        </div>
        <div>
          <span class="opacity-60">阈值类型：</span>
          <span class="font-medium">{{ formatAlarmType(alarm.alarmType) }}</span>
        </div>
        <div class="min-w-0">
          <span class="opacity-60">报警内容：</span>
          <span class="font-medium">{{ alarm.message || '-' }}</span>
        </div>
      </div>

      <!-- 无通道 / 非数值报警：不做无意义的趋势请求 -->
      <Empty
        v-if="unsupportedReason"
        :description="unsupportedReason"
        class="py-8"
      />

      <template v-else>
        <div class="flex flex-wrap items-center gap-2">
          <RadioGroup v-model:value="preset" size="small">
            <RadioButton
              v-for="item in WINDOW_PRESETS"
              :key="item.value"
              :value="item.value"
            >
              {{ item.label }}
            </RadioButton>
          </RadioGroup>
          <DateRangePicker
            v-if="preset === 'custom'"
            v-model:value="customRange"
            class="w-72"
            format="YYYY-MM-DD HH:mm"
            :disabled-date="disabledFutureDate"
            :placeholder="['开始时间', '结束时间']"
            show-time
            size="small"
          />
          <Select
            v-model:value="resolution"
            class="w-28"
            :options="resolutionOptions"
            aria-label="分辨率"
            size="small"
          />
          <span class="flex items-center gap-1 text-xs">
            <Switch v-model:checked="includeRaw" :disabled="!canIncludeRaw" />
            <span :class="canIncludeRaw ? '' : 'opacity-50'">叠加原始点</span>
          </span>
          <Button
            v-if="isActiveAlarm"
            size="small"
            :loading="loading"
            @click="handleQuery"
          >
            刷新
          </Button>
        </div>
        <div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
          <span class="opacity-60">{{ rangeHint }}</span>
          <span class="opacity-60">{{ rawHint }}</span>
          <Tag v-if="applied" color="blue">范围：{{ appliedRangeText }}</Tag>
          <Tag v-if="applied" color="default">
            分辨率：{{ RESOLUTION_LABELS[applied.resolution] }}
          </Tag>
          <Tag v-if="applied?.includeRaw" color="orange">含原始点</Tag>
        </div>

        <Alert
          v-if="thresholdOutOfRangeNotice"
          type="warning"
          show-icon
          :message="thresholdOutOfRangeNotice"
        />
        <Alert
          v-if="resolutionFallback"
          type="warning"
          show-icon
          :message="resolutionFallback"
        />
        <Alert
          v-if="channelMissing"
          type="warning"
          show-icon
          message="通道已删除或不存在，无法查看趋势"
        />
        <Alert
          v-if="loadError"
          type="error"
          show-icon
          :message="`趋势查询失败：${loadError}`"
        >
          <template #action>
            <Button size="small" @click="handleQuery">重试</Button>
          </template>
        </Alert>
        <Alert
          v-if="rawDegradedWarning"
          type="warning"
          show-icon
          :message="rawDegradedWarning"
        />

        <Spin :spinning="loading">
          <div class="relative h-[340px] w-full">
            <TrendChart
              v-if="hasData && !channelMissing"
              class="h-full"
              :colors="chartColors"
              :mark-lines="markLines"
              :mark-times="markTimes"
              :range-seconds="applied?.rangeSeconds ?? 0"
              :series="chartSeries"
            />
            <div
              v-else-if="!channelMissing && !loadError"
              class="absolute inset-0 flex flex-col items-center justify-center gap-2"
            >
              <Empty description="该时间窗无采样数据" />
              <span class="text-xs opacity-60">
                可扩大时间范围或切换分辨率
              </span>
            </div>
            <Tag
              v-if="applied?.rightTruncated"
              class="absolute right-2 top-2 z-10"
              color="orange"
            >
              截止当前时间
            </Tag>
          </div>
        </Spin>
      </template>

      <div class="flex flex-wrap items-center justify-between gap-2 border-t pt-3">
        <span class="text-xs opacity-60">
          {{
            isActiveAlarm
              ? '报警尚未结束，数据不会自动轮询，可手动刷新'
              : '报警已结束，可调整时间窗查看恢复前后的趋势'
          }}
        </span>
        <Button type="primary" @click="modalApi.close()">关闭</Button>
      </div>
    </div>
  </Modal>
</template>
