<script lang="ts" setup>
import type { Dayjs } from 'dayjs';

import type { TrendGranularityValue, TrendPresetValue } from './data';

import type { CollectorApi } from '#/api/collector';

import { computed, onMounted, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  Card,
  DateRangePicker,
  Empty,
  message,
  RadioButton,
  RadioGroup,
  Select,
  Spin,
  Switch,
  Tag,
} from 'antdv-next';
import dayjs from 'dayjs';

import {
  getChannelPage,
  getDeviceSimpleList,
  getTrendSeries,
} from '#/api/collector';

import {
  buildChannelColors,
  describeChartSource,
  distinctUnits,
  GRANULARITY_OPTIONS,
  granularityHint,
  hasAnyPoint,
  isGranularityAvailable,
  MAX_CHANNELS_PER_CHART,
  MAX_CHANNELS_TOTAL,
  MAX_RAW_OVERLAY_RANGE_SECONDS,
  MAX_TOTAL_RANGE_SECONDS,
  sourceLabel,
  TREND_PRESETS,
} from './data';
import TrendChart from './modules/trend-chart.vue';

defineOptions({ name: 'CollectorTrend' });

interface AppliedQuery {
  channelIds: number[];
  compareMode: boolean;
  from: number;
  granularity: string;
  includeRaw: boolean;
  leftChannelIds: number[];
  rangeSeconds: number;
  rightChannelIds: number[];
  singleChannelIds: number[];
  to: number;
}

interface ApiErrorLike {
  message?: string;
  response?: { data?: { msg?: string } };
}

type ChartHandle = {
  applyZoom: (start: number, end: number) => void;
};

const devices = ref<CollectorApi.DeviceSimple[]>([]);
const channels = ref<CollectorApi.Channel[]>([]);
const selectedDeviceId = ref<number>();
const devicesLoading = ref(false);
const channelsLoading = ref(false);
const queryLoading = ref(false);
const loadError = ref<string>();
const queryError = ref<string>();

const preset = ref<TrendPresetValue>('1h');
const customRange = ref<[Dayjs, Dayjs] | null>(null);
const granularity = ref<TrendGranularityValue>('auto');
const includeRaw = ref(false);
const compareMode = ref(true);
const syncZoom = ref(true);

const leftChannelIds = ref<number[]>([]);
const rightChannelIds = ref<number[]>([]);
const singleChannelIds = ref<number[]>([]);

const result = ref<CollectorApi.TrendSeriesResult>();
const applied = ref<AppliedQuery>();

const leftChartRef = ref<ChartHandle>();
const rightChartRef = ref<ChartHandle>();

let devicesGeneration = 0;
let queryGeneration = 0;

const deviceOptions = computed(() =>
  devices.value.map((device) => ({
    label: device.deviceName,
    value: device.id,
  })),
);

const channelOptions = computed(() => {
  const enabled = channels.value.filter(
    (channel) => channel.id !== undefined && channel.enabled !== 0,
  );
  const baseLabels = enabled.map((channel) =>
    channel.unit
      ? `${channel.channelName}（${channel.unit}）`
      : channel.channelName,
  );
  const counts = new Map<string, number>();
  for (const label of baseLabels) {
    counts.set(label, (counts.get(label) ?? 0) + 1);
  }
  return enabled.map((channel, index) => {
    const base = baseLabels[index]!;
    return {
      // 同名通道（如两颗温度传感器）追加通道号，避免下拉与 legend 混淆
      label:
        (counts.get(base) ?? 0) > 1 ? `${base}（通道 ${channel.id}）` : base,
      value: channel.id!,
    };
  });
});

const uiRangeSeconds = computed(() => {
  if (preset.value === 'custom') {
    const range = customRange.value;
    if (!range || range.length !== 2) return 0;
    return Math.max(0, range[1].diff(range[0], 'second'));
  }
  return (
    TREND_PRESETS.find((item) => item.value === preset.value)?.seconds ?? 0
  );
});

const canIncludeRaw = computed(
  () =>
    uiRangeSeconds.value > 0 &&
    uiRangeSeconds.value <= MAX_RAW_OVERLAY_RANGE_SECONDS,
);

const granularityOptions = computed(() =>
  GRANULARITY_OPTIONS.map((option) => ({
    label: option.label,
    value: option.value,
    disabled: !isGranularityAvailable(option.value, uiRangeSeconds.value),
  })),
);

const currentChannelIds = computed(() => {
  if (compareMode.value) {
    return [...new Set([...leftChannelIds.value, ...rightChannelIds.value])];
  }
  return [...new Set(singleChannelIds.value)];
});

const channelColors = computed(() =>
  buildChannelColors(currentChannelIds.value),
);

const appliedRangeText = computed(() =>
  applied.value
    ? `${dayjs(applied.value.from).format('YYYY-MM-DD HH:mm')} ~ ${dayjs(
        applied.value.to,
      ).format('YYYY-MM-DD HH:mm')}`
    : '--',
);

function resolveErrorMessage(error: unknown): string {
  const candidate = error as ApiErrorLike | undefined;
  return (
    candidate?.response?.data?.msg ??
    candidate?.message ??
    '趋势查询失败，请稍后重试'
  );
}

function granularityLabel(value?: string): string {
  return (
    GRANULARITY_OPTIONS.find((option) => option.value === value)?.label ??
    '自动'
  );
}

function rangeForQuery(): null | { from: number; to: number; seconds: number } {
  if (preset.value === 'custom') {
    const range = customRange.value;
    if (!range || range.length !== 2) {
      message.warning('请先选择自定义时间范围');
      return null;
    }
    const from = range[0].valueOf();
    const to = range[1].valueOf();
    const seconds = Math.round((to - from) / 1000);
    if (seconds <= 0) {
      message.warning('开始时间必须早于结束时间');
      return null;
    }
    if (seconds > MAX_TOTAL_RANGE_SECONDS) {
      message.warning('单次查询时间范围不能超过 365 天');
      return null;
    }
    return { from, seconds, to };
  }
  const seconds =
    TREND_PRESETS.find((item) => item.value === preset.value)?.seconds ?? 0;
  if (seconds <= 0) {
    message.warning('请选择时间范围');
    return null;
  }
  const to = Date.now();
  return { from: to - seconds * 1000, seconds, to };
}

function handleDeviceChange(value: number) {
  devicesGeneration += 1;
  queryGeneration += 1;
  selectedDeviceId.value = value;
  result.value = undefined;
  applied.value = undefined;
  queryError.value = undefined;
  leftChannelIds.value = [];
  rightChannelIds.value = [];
  singleChannelIds.value = [];
  void loadChannels(value).then(() => {
    applyDefaultChannels();
    if (hasSelection.value) {
      void query();
    }
  });
}

/** 加载设备下全部启用通道；按分页循环，避免通道数超过单页时被截断 */
async function loadChannels(deviceId: number) {
  const generation = devicesGeneration;
  channelsLoading.value = true;
  channels.value = [];
  loadError.value = undefined;
  try {
    const all: CollectorApi.Channel[] = [];
    for (let pageNo = 1; pageNo <= 10; pageNo++) {
      const page = await getChannelPage({ deviceId, pageNo, pageSize: 100 });
      all.push(...page.list);
      if (all.length >= page.total || page.list.length === 0) break;
    }
    if (generation !== devicesGeneration) return;
    channels.value = all.filter((channel) => channel.enabled !== 0);
  } catch (error) {
    if (generation !== devicesGeneration) return;
    loadError.value = resolveErrorMessage(error);
    channels.value = [];
  } finally {
    if (generation === devicesGeneration) {
      channelsLoading.value = false;
    }
  }
}

function applyDefaultChannels() {
  const ids = channels.value
    .map((channel) => channel.id)
    .filter((id): id is number => id !== undefined)
    .slice(0, MAX_CHANNELS_TOTAL);
  if (compareMode.value) {
    const leftCount = Math.ceil(ids.length / 2);
    leftChannelIds.value = ids.slice(0, leftCount);
    rightChannelIds.value = ids.slice(leftCount);
  } else {
    singleChannelIds.value = ids;
  }
}

function setLeftChannels(values: number[]) {
  const remaining = MAX_CHANNELS_TOTAL - rightChannelIds.value.length;
  const limit = Math.min(MAX_CHANNELS_PER_CHART, Math.max(0, remaining));
  let next = [...new Set(values)].filter(
    (id) => !rightChannelIds.value.includes(id),
  );
  if (next.length > limit) {
    message.warning(`左侧最多 ${limit} 条通道（左右合计不超过 8 条）`);
    next = next.slice(0, limit);
  }
  leftChannelIds.value = next;
}

function setRightChannels(values: number[]) {
  const remaining = MAX_CHANNELS_TOTAL - leftChannelIds.value.length;
  const limit = Math.min(MAX_CHANNELS_PER_CHART, Math.max(0, remaining));
  let next = [...new Set(values)].filter(
    (id) => !leftChannelIds.value.includes(id),
  );
  if (next.length > limit) {
    message.warning(`右侧最多 ${limit} 条通道（左右合计不超过 8 条）`);
    next = next.slice(0, limit);
  }
  rightChannelIds.value = next;
}

function setSingleChannels(values: number[]) {
  let next = [...new Set(values)];
  if (next.length > MAX_CHANNELS_TOTAL) {
    message.warning('单图最多选择 8 条通道');
    next = next.slice(0, MAX_CHANNELS_TOTAL);
  }
  singleChannelIds.value = next;
}

const leftOptions = computed(() =>
  channelOptions.value.map((option) => ({
    ...option,
    disabled:
      rightChannelIds.value.includes(option.value) ||
      (leftChannelIds.value.length >= MAX_CHANNELS_PER_CHART &&
        !leftChannelIds.value.includes(option.value)) ||
      (leftChannelIds.value.length + rightChannelIds.value.length >=
        MAX_CHANNELS_TOTAL &&
        !leftChannelIds.value.includes(option.value)),
  })),
);

const rightOptions = computed(() =>
  channelOptions.value.map((option) => ({
    ...option,
    disabled:
      leftChannelIds.value.includes(option.value) ||
      (rightChannelIds.value.length >= MAX_CHANNELS_PER_CHART &&
        !rightChannelIds.value.includes(option.value)) ||
      (leftChannelIds.value.length + rightChannelIds.value.length >=
        MAX_CHANNELS_TOTAL &&
        !rightChannelIds.value.includes(option.value)),
  })),
);

function handleCompareModeChange(checked: boolean) {
  if (checked) {
    const ids = [...new Set(singleChannelIds.value)].slice(
      0,
      MAX_CHANNELS_TOTAL,
    );
    leftChannelIds.value = ids.slice(0, MAX_CHANNELS_PER_CHART);
    rightChannelIds.value = ids.slice(MAX_CHANNELS_PER_CHART);
  } else {
    singleChannelIds.value = [
      ...new Set([...leftChannelIds.value, ...rightChannelIds.value]),
    ].slice(0, MAX_CHANNELS_TOTAL);
  }
}

function filterSeries(ids: number[]): CollectorApi.TrendSeries[] {
  if (!result.value) return [];
  const includeRawNow = includeRaw.value;
  return result.value.series.filter(
    (series) =>
      ids.includes(series.channelId) &&
      (includeRawNow || series.kind !== 'raw'),
  );
}

const leftSeries = computed(() => filterSeries(leftChannelIds.value));
const rightSeries = computed(() => filterSeries(rightChannelIds.value));
const singleSeries = computed(() => filterSeries(singleChannelIds.value));

const leftHasData = computed(() => hasAnyPoint(leftSeries.value));
const rightHasData = computed(() => hasAnyPoint(rightSeries.value));
const singleHasData = computed(() => hasAnyPoint(singleSeries.value));

const leftUnits = computed(() =>
  distinctUnits(result.value?.series ?? [], leftChannelIds.value),
);
const rightUnits = computed(() =>
  distinctUnits(result.value?.series ?? [], rightChannelIds.value),
);
const singleUnits = computed(() =>
  distinctUnits(result.value?.series ?? [], singleChannelIds.value),
);

const leftSummary = computed(() =>
  describeChartSource(result.value?.series ?? [], leftChannelIds.value),
);
const rightSummary = computed(() =>
  describeChartSource(result.value?.series ?? [], rightChannelIds.value),
);
const singleSummary = computed(() =>
  describeChartSource(result.value?.series ?? [], singleChannelIds.value),
);

const hasSelection = computed(() => currentChannelIds.value.length > 0);

function emptyText(ids: number[]): string {
  if (ids.length === 0) {
    return compareMode.value ? '当前图表未选择通道' : '请选择通道';
  }
  if (!result.value || !applied.value) {
    return queryError.value
      ? '查询失败，请调整条件后重试'
      : '请点击查询加载趋势';
  }
  if (filterSeries(ids).length === 0) {
    return '所选通道不在上次查询结果中，请点击查询';
  }
  return '所选时间范围无有效数据';
}

async function query() {
  if (selectedDeviceId.value === undefined) {
    message.warning('请先选择采集设备');
    return;
  }
  const ids = currentChannelIds.value;
  if (ids.length === 0) {
    message.warning('请至少选择一条通道');
    return;
  }
  const range = rangeForQuery();
  if (!range) return;
  const currentGranularity = granularity.value;
  if (!isGranularityAvailable(currentGranularity, range.seconds)) {
    message.warning('当前粒度不适用于该时间范围，请选择“自动”或更粗粒度');
    return;
  }
  const useRawOverlay =
    includeRaw.value && range.seconds <= MAX_RAW_OVERLAY_RANGE_SECONDS;

  const snapshot: AppliedQuery = {
    channelIds: [...ids],
    compareMode: compareMode.value,
    from: range.from,
    granularity: currentGranularity,
    includeRaw: useRawOverlay,
    leftChannelIds: [...leftChannelIds.value],
    rangeSeconds: range.seconds,
    rightChannelIds: [...rightChannelIds.value],
    singleChannelIds: [...singleChannelIds.value],
    to: range.to,
  };

  const generation = ++queryGeneration;
  queryLoading.value = true;
  queryError.value = undefined;
  try {
    const body = await getTrendSeries({
      channelIds: ids,
      deviceId: selectedDeviceId.value,
      from: range.from,
      granularity: currentGranularity,
      includeRaw: useRawOverlay,
      to: range.to,
    });
    if (generation !== queryGeneration) return;
    if (!body) {
      throw new Error('趋势接口返回为空');
    }
    result.value = body;
    applied.value = snapshot;
  } catch (error) {
    if (generation !== queryGeneration) return;
    queryError.value = resolveErrorMessage(error);
    message.error(queryError.value);
    // 失败时保留上一次成功结果，由页面顶部提示说明数据未更新。
  } finally {
    if (generation === queryGeneration) {
      queryLoading.value = false;
    }
  }
}

function handleLeftZoom(payload: { end: number; start: number }) {
  if (!compareMode.value) return;
  rightChartRef.value?.applyZoom(payload.start, payload.end);
}

function handleRightZoom(payload: { end: number; start: number }) {
  if (!compareMode.value) return;
  leftChartRef.value?.applyZoom(payload.start, payload.end);
}

watch(
  () => uiRangeSeconds.value,
  (seconds) => {
    if (!isGranularityAvailable(granularity.value, seconds)) {
      granularity.value = 'auto';
    }
    if (seconds <= 0 || seconds > MAX_RAW_OVERLAY_RANGE_SECONDS) {
      includeRaw.value = false;
    }
  },
  { immediate: true },
);

async function init() {
  devicesLoading.value = true;
  loadError.value = undefined;
  try {
    devices.value = await getDeviceSimpleList();
    if (devices.value.length === 0) return;
    selectedDeviceId.value = devices.value[0]!.id;
    await loadChannels(selectedDeviceId.value);
    applyDefaultChannels();
    if (hasSelection.value) {
      await query();
    }
  } catch (error) {
    loadError.value = resolveErrorMessage(error);
  } finally {
    devicesLoading.value = false;
  }
}

onMounted(() => {
  void init();
});
</script>

<template>
  <Page auto-content-height>
    <div class="flex h-full flex-col gap-3 overflow-auto">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 class="m-0 text-lg font-semibold">趋势分析</h2>
          <div class="mt-1 text-xs opacity-60">
            自动按通道画像和时间范围选择数据源；单序列最多返回 1000 个点
          </div>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <Select
            data-testid="trend-device"
            class="w-52"
            :loading="devicesLoading || channelsLoading"
            :options="deviceOptions"
            :show-search="{ optionFilterProp: 'label' }"
            :value="selectedDeviceId"
            placeholder="请选择采集设备"
            @change="handleDeviceChange"
          />
          <RadioGroup v-model:value="preset">
            <RadioButton
              v-for="option in TREND_PRESETS"
              :key="option.value"
              :data-testid="`trend-preset-${option.value}`"
              :value="option.value"
            >
              {{ option.label }}
            </RadioButton>
          </RadioGroup>
          <DateRangePicker
            v-if="preset === 'custom'"
            v-model:value="customRange"
            data-testid="trend-custom-range"
            class="w-80"
            format="YYYY-MM-DD HH:mm"
            :placeholder="['开始时间', '结束时间']"
            show-time
          />
          <Select
            v-model:value="granularity"
            data-testid="trend-granularity"
            class="w-32"
            :options="granularityOptions"
            :show-search="{ optionFilterProp: 'label' }"
            aria-label="聚合粒度"
          />
          <label class="flex items-center gap-1 text-sm">
            对比模式
            <Switch
              v-model:checked="compareMode"
              data-testid="trend-compare"
              @change="handleCompareModeChange"
            />
          </label>
          <label v-if="compareMode" class="flex items-center gap-1 text-sm">
            缩放联动
            <Switch v-model:checked="syncZoom" data-testid="trend-sync-zoom" />
          </label>
          <label class="flex items-center gap-1 text-sm">
            叠加原始点
            <Switch
              v-model:checked="includeRaw"
              data-testid="trend-include-raw"
              :disabled="!canIncludeRaw"
            />
          </label>
          <Button
            data-testid="trend-query"
            type="primary"
            :disabled="!hasSelection"
            :loading="queryLoading"
            @click="query"
          >
            查询
          </Button>
        </div>
      </div>

      <div class="text-xs opacity-60">
        {{ granularityHint(uiRangeSeconds) }}；叠加原始点仅支持 2 小时以内范围
      </div>

      <Alert
        v-if="loadError"
        data-testid="trend-load-error"
        type="error"
        show-icon
        :message="`加载设备或通道失败：${loadError}`"
      />
      <Alert
        v-else-if="queryError"
        data-testid="trend-query-error"
        type="warning"
        show-icon
      >
        <template #message>
          查询失败：{{ queryError }}。
          {{ result ? '图表保留上一次成功查询的结果。' : '' }}
        </template>
      </Alert>

      <Spin
        :spinning="devicesLoading || (channelsLoading && channels.length === 0)"
      >
        <Empty
          v-if="!devicesLoading && devices.length === 0"
          class="py-20"
          description="暂无可用采集设备，请先在“采集设备”中完成配置"
        />
        <template v-else>
          <div v-if="compareMode" class="grid gap-3 xl:grid-cols-2">
            <div>
              <div class="mb-1 text-xs font-medium">左侧通道</div>
              <Select
                data-testid="trend-left-channels"
                mode="multiple"
                class="w-full"
                :max-tag-count="3"
                :options="leftOptions"
                :show-search="{ optionFilterProp: 'label' }"
                :value="leftChannelIds"
                placeholder="选择左侧图表通道（最多 6 条）"
                @change="setLeftChannels"
              />
            </div>
            <div>
              <div class="mb-1 text-xs font-medium">右侧通道</div>
              <Select
                data-testid="trend-right-channels"
                mode="multiple"
                class="w-full"
                :max-tag-count="3"
                :options="rightOptions"
                :show-search="{ optionFilterProp: 'label' }"
                :value="rightChannelIds"
                placeholder="选择右侧图表通道（最多 6 条）"
                @change="setRightChannels"
              />
            </div>
          </div>
          <div v-else>
            <div class="mb-1 text-xs font-medium">图表通道</div>
            <Select
              data-testid="trend-single-channels"
              mode="multiple"
              class="w-full"
              :max-tag-count="4"
              :options="channelOptions"
              :show-search="{ optionFilterProp: 'label' }"
              :value="singleChannelIds"
              placeholder="选择单图通道（最多 8 条）"
              @change="setSingleChannels"
            />
          </div>

          <div
            v-if="result"
            data-testid="trend-summary"
            class="mt-3 flex flex-wrap items-center gap-2"
          >
            <Tag color="blue">
              数据源：{{ sourceLabel(result.sourceSummary) }}
            </Tag>
            <Tag>时间范围：{{ appliedRangeText }}</Tag>
            <Tag v-if="applied">
              粒度：{{ granularityLabel(applied.granularity) }}
            </Tag>
            <Tag v-if="applied?.includeRaw" color="orange">含原始点叠加</Tag>
            <Tag v-if="currentChannelIds.length">
              {{
                compareMode
                  ? `左侧 ${leftChannelIds.length} 条 / 右侧 ${rightChannelIds.length} 条`
                  : `${singleChannelIds.length} 条通道`
              }}
            </Tag>
          </div>

          <Spin :spinning="queryLoading">
            <div v-if="compareMode" class="mt-3 grid gap-3 xl:grid-cols-2">
              <Card size="small" class="min-w-0">
                <template #title>左侧趋势</template>
                <div class="mb-2 flex flex-wrap items-center gap-1 text-xs">
                  <span class="opacity-60">{{ leftSummary }}</span>
                  <Tag v-if="leftUnits.length > 1" color="warning">
                    {{ leftUnits.join(' / ') }} 已按单位分轴
                  </Tag>
                </div>
                <div class="relative h-[430px]" data-testid="trend-left-chart">
                  <TrendChart
                    v-if="leftHasData"
                    ref="leftChartRef"
                    class="h-full"
                    :colors="channelColors"
                    :range-seconds="applied?.rangeSeconds ?? 3600"
                    :series="leftSeries"
                    :sync-enabled="syncZoom"
                    @zoom="handleLeftZoom"
                  />
                  <div
                    v-else
                    class="absolute inset-0 flex items-center justify-center"
                  >
                    <Empty :description="emptyText(leftChannelIds)" />
                  </div>
                </div>
              </Card>
              <Card size="small" class="min-w-0">
                <template #title>右侧趋势</template>
                <div class="mb-2 flex flex-wrap items-center gap-1 text-xs">
                  <span class="opacity-60">{{ rightSummary }}</span>
                  <Tag v-if="rightUnits.length > 1" color="warning">
                    {{ rightUnits.join(' / ') }} 已按单位分轴
                  </Tag>
                </div>
                <div class="relative h-[430px]" data-testid="trend-right-chart">
                  <TrendChart
                    v-if="rightHasData"
                    ref="rightChartRef"
                    class="h-full"
                    :colors="channelColors"
                    :range-seconds="applied?.rangeSeconds ?? 3600"
                    :series="rightSeries"
                    :sync-enabled="syncZoom"
                    @zoom="handleRightZoom"
                  />
                  <div
                    v-else
                    class="absolute inset-0 flex items-center justify-center"
                  >
                    <Empty :description="emptyText(rightChannelIds)" />
                  </div>
                </div>
              </Card>
            </div>
            <Card v-else size="small" class="mt-3">
              <template #title>趋势图</template>
              <div class="mb-2 flex flex-wrap items-center gap-1 text-xs">
                <span class="opacity-60">{{ singleSummary }}</span>
                <Tag v-if="singleUnits.length > 1" color="warning">
                  {{ singleUnits.join(' / ') }} 已按单位分轴
                </Tag>
              </div>
              <div class="relative h-[540px]" data-testid="trend-single-chart">
                <TrendChart
                  v-if="singleHasData"
                  class="h-full"
                  :colors="channelColors"
                  :range-seconds="applied?.rangeSeconds ?? 3600"
                  :series="singleSeries"
                />
                <div
                  v-else
                  class="absolute inset-0 flex items-center justify-center"
                >
                  <Empty :description="emptyText(singleChannelIds)" />
                </div>
              </div>
            </Card>
          </Spin>
        </template>
      </Spin>
    </div>
  </Page>
</template>
