<script lang="ts" setup>
import type { Dayjs } from 'dayjs';

import type { TableColumnsType } from 'antdv-next';

import type { CollectorApi } from '#/api/collector';

import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { useVbenModal } from '@vben/common-ui';
import { downloadFileFromBlobPart } from '@vben/utils';

import {
  Alert,
  Button,
  DateRangePicker,
  Empty,
  Pagination,
  RadioButton,
  RadioGroup,
  Select,
  Spin,
  TabPane,
  Table,
  Tabs,
  Tag,
  message,
} from 'antdv-next';

import { exportHistory, getHistoryPage, getTrendSeries } from '#/api/collector';

import {
  buildChannelColors,
  formatTrendValue,
  hasAnyPoint,
  sourceLabel,
} from '../../trend/data';
import TrendChart from '../../trend/modules/trend-chart.vue';
import { formatShanghaiDateTime } from '../time';

defineOptions({ name: 'CollectorChannelHistoryDialog' });

/** 看板传入的通道上下文；最新值/质量/采样时间直接取看板已有数据，不额外请求 */
interface ChannelHistoryData {
  deviceId: number;
  deviceName: string;
  channelId: number;
  channelName: string;
  unit?: null | string;
  /** 看板当前的展示值（enum 已按 valueText 处理） */
  latestValueText: string;
  quality?: null | number;
  sampleTime?: null | number;
}

/** UI 分辨率选项；趋势与历史两套接口的粒度枚举不同，必须各自映射 */
type ResolutionValue = '1d' | '1h' | '1m' | '1s' | 'auto' | 'raw';
type PresetValue = '1h' | '24h' | '7d' | 'custom';

/** 一次已生效的查询条件；导出、数据表、趋势都以它为准 */
interface AppliedQuery {
  from: number;
  to: number;
  rangeSeconds: number;
  resolution: ResolutionValue;
}

interface ApiErrorLike {
  message?: string;
  response?: { data?: { msg?: string } };
}

const MAX_RANGE_SECONDS = 365 * 24 * 60 * 60;
const PAGE_SIZE_OPTIONS = ['20', '50', '100'];

const PRESETS = [
  { label: '1小时', seconds: 3600, value: '1h' },
  { label: '24小时', seconds: 24 * 3600, value: '24h' },
  { label: '7天', seconds: 7 * 24 * 3600, value: '7d' },
] as const;

const RESOLUTION_OPTIONS: { label: string; value: ResolutionValue }[] = [
  { label: '自动', value: 'auto' },
  { label: '原始', value: 'raw' },
  { label: '秒', value: '1s' },
  { label: '分', value: '1m' },
  { label: '时', value: '1h' },
  { label: '天', value: '1d' },
];

const RESOLUTION_LABEL: Record<ResolutionValue, string> = {
  '1d': '天',
  '1h': '时',
  '1m': '分',
  '1s': '秒',
  auto: '自动',
  raw: '原始',
};

/**
 * 趋势接口各显式粒度的最大时间范围（秒），与后端 CollectorTrendGranularity 一致；
 * 超限时趋势 Tab 单独提示，不影响数据表 Tab 用同一分辨率查询历史分页。
 */
const TREND_GRANULARITY_MAX_SECONDS: Record<ResolutionValue, number> = {
  '1d': MAX_RANGE_SECONDS,
  // 1 小时聚合最多 90 天
  '1h': 90 * 24 * 3600,
  // 1 分钟聚合最多 48 小时
  '1m': 48 * 3600,
  // 1 秒聚合最多 2 小时
  '1s': 2 * 3600,
  auto: MAX_RANGE_SECONDS,
  // 原始点最多 10 分钟
  raw: 10 * 60,
};

const { hasAccessByCodes } = useAccess();
const { push } = useRouter();

const channel = ref<ChannelHistoryData>();
const activeTab = ref<'chart' | 'table'>('chart');
const preset = ref<PresetValue>('1h');
const customRange = ref<[Dayjs, Dayjs] | null>(null);
const resolution = ref<ResolutionValue>('auto');

/**
 * 已生效条件：数据表、趋势、导出统一使用它。
 * 顶部筛选控件（preset/customRange/resolution）本身即「草稿」，
 * 只有点击「查询」时才由 buildQueryFromControls() 固化为 appliedQuery，
 * 因此改了筛选但没点查询时，导出仍按上一次生效条件执行。
 */
const appliedQuery = ref<AppliedQuery>();

const trendResult = ref<CollectorApi.TrendSeriesResult>();
const trendQuery = ref<AppliedQuery>();
const trendLoading = ref(false);
const trendError = ref<string>();

const tableRows = ref<CollectorApi.HistoryRow[]>([]);
const tableTotal = ref(0);
const tableQuery = ref<AppliedQuery>();
const tableLoading = ref(false);
const tableError = ref<string>();
const pageNo = ref(1);
const pageSize = ref(20);

const exportLoading = ref(false);

// 两个 Tab 各自独立计数：任一 Tab 失败或被新查询覆盖时，只丢弃自己的迟到响应
let tableSeq = 0;
let trendSeq = 0;

const canExport = computed(() =>
  hasAccessByCodes(['collector:history:export']),
);

function resolveErrorMessage(error: unknown): string {
  const candidate = error as ApiErrorLike | undefined;
  return (
    candidate?.response?.data?.msg ??
    candidate?.message ??
    '查询失败，请稍后重试'
  );
}

/**
 * 趋势接口粒度枚举：auto | raw | 1s | 1m | 1h | 1d。
 * 趋势接口与历史接口的粒度枚举不同，不可混用，只能经本函数转换后传给 getTrendSeries。
 */
function toTrendGranularity(value: ResolutionValue): string {
  switch (value) {
    case '1d': {
      return '1d';
    }
    case '1h': {
      return '1h';
    }
    case '1m': {
      return '1m';
    }
    case '1s': {
      return '1s';
    }
    case 'raw': {
      return 'raw';
    }
    default: {
      return 'auto';
    }
  }
}

/**
 * 历史分页/导出粒度枚举：raw | second | minute | hour | day。
 * 趋势接口与历史接口的粒度枚举不同，不可混用；历史接口没有 auto，
 * 这里把「自动」降级为 minute（分钟聚合），并在提示文案中说明。
 */
function toHistoryGranularity(
  value: ResolutionValue,
): CollectorApi.HistoryGranularity {
  switch (value) {
    case '1d': {
      return 'day';
    }
    case '1h': {
      return 'hour';
    }
    case '1s': {
      return 'second';
    }
    case 'raw': {
      return 'raw';
    }
    default: {
      return 'minute';
    }
  }
}

const isRawGranularity = computed(() => {
  const query = tableQuery.value ?? appliedQuery.value;
  if (!query) return resolution.value === 'raw';
  return toHistoryGranularity(query.resolution) === 'raw';
});

const trendColors = computed(() => {
  const id = channel.value?.channelId;
  return id === undefined ? {} : buildChannelColors([id]);
});

const trendRangeSeconds = computed(
  () =>
    trendQuery.value?.rangeSeconds ?? appliedQuery.value?.rangeSeconds ?? 3600,
);

const hasTrendData = computed(() =>
  trendResult.value ? hasAnyPoint(trendResult.value.series) : false,
);

const trendDegradedWarning = computed(() =>
  trendResult.value?.rawDegraded
    ? (trendResult.value.rawWarning ?? '原始点加载超时，已仅展示聚合趋势')
    : undefined,
);

const appliedRangeText = computed(() => {
  const query = appliedQuery.value;
  if (!query) return '--';
  return `${formatShanghaiDateTime(query.from)} ~ ${formatShanghaiDateTime(query.to)}`;
});

const tableColumns = computed<TableColumnsType>(() => {
  const timeColumn = {
    dataIndex: 'bucketTime',
    key: 'bucketTime',
    title: '时间',
    width: 180,
  };
  if (isRawGranularity.value) {
    return [
      timeColumn,
      { dataIndex: 'value', key: 'value', title: '工程值', width: 160 },
      { dataIndex: 'rawValue', key: 'rawValue', title: '原始值', width: 160 },
      { key: 'quality', title: '质量', width: 120 },
    ];
  }
  return [
    timeColumn,
    { dataIndex: 'sampleCount', key: 'sampleCount', title: '样本数', width: 90 },
    { dataIndex: 'goodCount', key: 'goodCount', title: '正常数', width: 90 },
    { dataIndex: 'badCount', key: 'badCount', title: '坏值数', width: 90 },
    { dataIndex: 'valueAvg', key: 'valueAvg', title: '平均', width: 110 },
    { dataIndex: 'valueMin', key: 'valueMin', title: '最小', width: 110 },
    { dataIndex: 'valueMax', key: 'valueMax', title: '最大', width: 110 },
    { dataIndex: 'valueFirst', key: 'valueFirst', title: '首值', width: 110 },
    { dataIndex: 'valueLast', key: 'valueLast', title: '末值', width: 110 },
  ];
});

function qualityText(quality?: null | number) {
  if (quality === null || quality === undefined) return '--';
  if (quality === 2) return '异常(坏质量)';
  return quality === 1 ? '不确定' : '正常';
}

function qualityColor(quality?: null | number) {
  if (quality === 2) return 'error';
  if (quality === 1) return 'warning';
  if (quality === 0) return 'success';
  return 'default';
}

function formatCellValue(record: CollectorApi.HistoryRow, key?: string) {
  if (!key) return '--';
  if (key === 'bucketTime') return formatShanghaiDateTime(record.bucketTime);
  const value = (record as unknown as Record<string, unknown>)[key];
  if (typeof value === 'number') return formatTrendValue(value);
  return '--';
}

const rowKey = (record: CollectorApi.HistoryRow) => record.bucketTime;

/** 从当前控件生成查询条件；自定义范围不完整或超 365 天时返回 undefined */
function buildQueryFromControls(): AppliedQuery | undefined {
  let from: number;
  let to: number;
  let rangeSeconds: number;
  if (preset.value === 'custom') {
    const range = customRange.value;
    if (!range || range.length !== 2) return undefined;
    from = range[0].valueOf();
    to = range[1].valueOf();
    if (!Number.isFinite(from) || !Number.isFinite(to) || to <= from) {
      return undefined;
    }
    rangeSeconds = Math.round((to - from) / 1000);
  } else {
    const seconds =
      PRESETS.find((item) => item.value === preset.value)?.seconds ?? 0;
    if (seconds <= 0) return undefined;
    to = Date.now();
    from = to - seconds * 1000;
    rangeSeconds = seconds;
  }
  if (rangeSeconds <= 0 || rangeSeconds > MAX_RANGE_SECONDS) return undefined;
  return { from, rangeSeconds, resolution: resolution.value, to };
}

/** 应用一次查询：数据表与趋势各自独立刷新，互不影响 */
function applyQuery(query: AppliedQuery) {
  appliedQuery.value = query;
  pageNo.value = 1;
  void loadTable(query);
  void loadTrend(query);
}

function handleQuery() {
  const next = buildQueryFromControls();
  if (!next) {
    message.warning('请选择有效的时间范围（开始早于结束，且不超过 365 天）');
    return;
  }
  applyQuery(next);
}

async function loadTable(query: AppliedQuery) {
  const seq = ++tableSeq;
  tableQuery.value = query;
  tableLoading.value = true;
  tableError.value = undefined;
  try {
    const body = await getHistoryPage({
      channelId: channel.value!.channelId,
      from: query.from,
      granularity: toHistoryGranularity(query.resolution),
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      to: query.to,
    });
    if (seq !== tableSeq) return;
    tableRows.value = body.list ?? [];
    tableTotal.value = body.total ?? 0;
  } catch (error) {
    if (seq !== tableSeq) return;
    // 失败只提示当前 Tab，保留上一次成功结果，不清空整表
    tableError.value = resolveErrorMessage(error);
  } finally {
    if (seq === tableSeq) {
      tableLoading.value = false;
    }
  }
}

/** 把粒度上限秒数格式化为中文，用于趋势 Tab 的越界提示 */
function formatRangeLimit(seconds: number): string {
  if (seconds < 3600) return `${Math.floor(seconds / 60)} 分钟`;
  if (seconds < 24 * 3600) return `${Math.floor(seconds / 3600)} 小时`;
  return `${Math.floor(seconds / (24 * 3600))} 天`;
}

async function loadTrend(query: AppliedQuery) {
  const seq = ++trendSeq;
  const granularity = toTrendGranularity(query.resolution);
  const maxSeconds = TREND_GRANULARITY_MAX_SECONDS[query.resolution];
  if (query.rangeSeconds > maxSeconds) {
    // 与趋势页一致：显式粒度承载不了该范围时不清空图表，但必须明确告知图表是上一次的结果，
    // 否则会出现「筛选是 365 天、图仍是上次 1 小时数据」的误导
    trendLoading.value = false;
    const base = `趋势接口的「${
      RESOLUTION_LABEL[query.resolution]
    }」粒度最多支持 ${formatRangeLimit(maxSeconds)}，请改用自动或缩短时间范围`;
    trendError.value = trendResult.value
      ? `${base}（图表仍显示上一次成功查询的结果）`
      : base;
    return;
  }
  trendLoading.value = true;
  trendError.value = undefined;
  try {
    const body = await getTrendSeries({
      channelIds: [channel.value!.channelId],
      deviceId: channel.value!.deviceId,
      from: query.from,
      granularity,
      to: query.to,
    });
    if (seq !== trendSeq) return;
    if (!body) {
      throw new Error('趋势接口返回为空');
    }
    trendResult.value = body;
    trendQuery.value = query;
  } catch (error) {
    if (seq !== trendSeq) return;
    trendError.value = resolveErrorMessage(error);
  } finally {
    if (seq === trendSeq) {
      trendLoading.value = false;
    }
  }
}

function handlePageChange(page: number, size: number) {
  const query = appliedQuery.value;
  if (!query) return;
  const sizeChanged = size !== pageSize.value;
  pageSize.value = size;
  pageNo.value = sizeChanged ? 1 : page;
  void loadTable(query);
}

/** 导出固定使用 appliedQuery：改了筛选但未点查询时，导出仍是上次生效条件 */
async function handleExport() {
  const query = appliedQuery.value;
  const current = channel.value;
  if (!query || !current) {
    message.warning('请先点击查询生成生效条件');
    return;
  }
  exportLoading.value = true;
  try {
    const blob = await exportHistory({
      channelId: current.channelId,
      from: query.from,
      granularity: toHistoryGranularity(query.resolution),
      to: query.to,
    });
    const compact = (value: number) =>
      formatShanghaiDateTime(value).replace(/[-: ]/g, '');
    // 与后端 sanitizeFileName 保持一致：去掉多数文件系统非法字符，
    // 否则通道名含 : / 等字符时浏览器会自行截断或下载失败
    const safeName = current.channelName.replace(/[\\/:*?"<>|\r\n]/g, '_');
    downloadFileFromBlobPart({
      fileName: `${safeName}_${compact(query.from)}_${compact(query.to)}.csv`,
      source: blob,
    });
  } catch (error) {
    message.error(`导出失败：${resolveErrorMessage(error)}`);
  } finally {
    exportLoading.value = false;
  }
}

/** 趋势页当前不读取 query 参数，这里只带参数跳转，不做预填（趋势页未改动） */
function openInTrend() {
  const current = channel.value;
  if (!current) return;
  void push({
    path: '/collector/trend',
    query: {
      channelId: String(current.channelId),
      deviceId: String(current.deviceId),
    },
  });
}

function resetState() {
  tableSeq += 1;
  trendSeq += 1;
  activeTab.value = 'chart';
  preset.value = '1h';
  customRange.value = null;
  resolution.value = 'auto';
  appliedQuery.value = undefined;
  trendResult.value = undefined;
  trendQuery.value = undefined;
  trendLoading.value = false;
  trendError.value = undefined;
  tableRows.value = [];
  tableTotal.value = 0;
  tableQuery.value = undefined;
  tableLoading.value = false;
  tableError.value = undefined;
  pageNo.value = 1;
  pageSize.value = 20;
  exportLoading.value = false;
}

const [Modal, modalApi] = useVbenModal({
  title: '通道历史',
  footer: false,
  onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      channel.value = undefined;
      return;
    }
    channel.value = modalApi.getData() as ChannelHistoryData;
    resetState();
    // 打开即按默认 1 小时 / 自动触发一次查询，两个 Tab 各自加载
    const initial = buildQueryFromControls();
    if (initial) applyQuery(initial);
  },
});
</script>

<template>
  <Modal class="w-[1080px] max-w-[94vw]">
    <div v-if="channel" class="flex min-w-0 flex-col gap-3">
      <!-- 顶部：通道信息 + 看板已有最新值 -->
      <div class="flex flex-wrap items-center gap-2">
        <span class="text-base font-semibold">{{ channel.channelName }}</span>
        <Tag v-if="channel.unit">{{ channel.unit }}</Tag>
        <span class="text-xs opacity-60">{{ channel.deviceName }}</span>
      </div>
      <div class="grid grid-cols-2 gap-3 md:grid-cols-4">
        <div>
          <div class="text-xs opacity-60">最新值</div>
          <div class="mt-1 font-medium">{{ channel.latestValueText }}</div>
        </div>
        <div>
          <div class="text-xs opacity-60">质量</div>
          <div class="mt-1">
            <Tag :color="qualityColor(channel.quality)">
              {{ qualityText(channel.quality) }}
            </Tag>
          </div>
        </div>
        <div>
          <div class="text-xs opacity-60">采样时间</div>
          <div class="mt-1 font-medium">
            {{ formatShanghaiDateTime(channel.sampleTime) }}
          </div>
        </div>
        <div>
          <div class="text-xs opacity-60">已生效范围</div>
          <div class="mt-1 text-xs font-medium">{{ appliedRangeText }}</div>
        </div>
      </div>

      <!-- 筛选条件：草稿态，点击查询才生效 -->
      <div class="flex flex-wrap items-center gap-2">
        <RadioGroup v-model:value="preset">
          <RadioButton
            v-for="item in PRESETS"
            :key="item.value"
            :value="item.value"
          >
            {{ item.label }}
          </RadioButton>
          <RadioButton value="custom">自定义</RadioButton>
        </RadioGroup>
        <DateRangePicker
          v-if="preset === 'custom'"
          v-model:value="customRange"
          class="w-72"
          format="YYYY-MM-DD HH:mm"
          :placeholder="['开始时间', '结束时间']"
          show-time
        />
        <Select
          v-model:value="resolution"
          class="w-32"
          :options="RESOLUTION_OPTIONS"
          aria-label="分辨率"
        />
        <Button type="primary" @click="handleQuery">查询</Button>
      </div>
      <div class="text-xs opacity-60">
        时间按 Asia/Shanghai 展示；历史分页粒度为 raw/second/minute/hour/day，「自动」在数据表中按分钟聚合；
        单次范围不超过 365 天。
      </div>

      <Tabs v-model:active-key="activeTab">
        <TabPane key="chart" tab="趋势">
          <Alert
            v-if="trendError"
            type="warning"
            show-icon
            :message="`趋势查询失败：${trendError}`"
          />
          <Alert
            v-if="trendDegradedWarning"
            type="warning"
            show-icon
            :message="trendDegradedWarning"
          />
          <div v-if="trendResult" class="mt-2 flex flex-wrap gap-2">
            <Tag color="blue">
              数据源：{{ sourceLabel(trendResult.sourceSummary) }}
            </Tag>
            <Tag v-if="trendQuery">
              范围：{{ formatShanghaiDateTime(trendQuery.from) }} ~
              {{ formatShanghaiDateTime(trendQuery.to) }}
            </Tag>
          </div>
          <Spin :spinning="trendLoading">
            <div class="relative mt-2 h-[300px]">
              <TrendChart
                v-if="hasTrendData"
                class="h-full"
                :colors="trendColors"
                :range-seconds="trendRangeSeconds"
                :series="trendResult?.series ?? []"
              />
              <div
                v-else
                class="absolute inset-0 flex items-center justify-center"
              >
                <Empty description="所选范围无数据" />
              </div>
            </div>
          </Spin>
        </TabPane>

        <TabPane key="table" tab="数据表">
          <Alert
            v-if="tableError"
            type="warning"
            show-icon
            :message="`数据表查询失败：${tableError}`"
          />
          <Table
            class="mt-2"
            :columns="tableColumns"
            :data-source="tableRows"
            :loading="tableLoading"
            :pagination="false"
            :scroll="{ x: isRawGranularity ? 620 : 980 }"
            :row-key="rowKey"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'quality'">
                <Tag :color="qualityColor(record.quality)">
                  {{ qualityText(record.quality) }}
                </Tag>
              </template>
              <template v-else>
                {{ formatCellValue(record, String(column.key ?? '')) }}
              </template>
            </template>
            <template #emptyText>
              <Empty description="所选范围无数据" />
            </template>
          </Table>
          <div class="mt-3 flex flex-wrap items-center justify-between gap-2">
            <span class="text-xs opacity-60">共 {{ tableTotal }} 条</span>
            <Pagination
              :current="pageNo"
              :page-size="pageSize"
              :page-size-options="PAGE_SIZE_OPTIONS"
              :show-size-changer="true"
              :total="tableTotal"
              size="small"
              @change="handlePageChange"
            />
          </div>
        </TabPane>
      </Tabs>

      <!-- 底部操作 -->
      <div
        class="flex flex-wrap items-center justify-between gap-2 border-t pt-3"
      >
        <Button @click="openInTrend">在趋势分析中打开</Button>
        <div class="flex items-center gap-2">
          <Button
            v-if="canExport"
            :disabled="!appliedQuery"
            :loading="exportLoading"
            @click="handleExport"
          >
            导出 CSV
          </Button>
          <Button type="primary" @click="modalApi.close()">关闭</Button>
        </div>
      </div>
    </div>
  </Modal>
</template>
