<script lang="ts" setup>
import type { EChartsOption, EchartsUIType } from '@vben/plugins/echarts';

import type { CollectorApi } from '#/api/collector';

import { onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import dayjs from 'dayjs';

import { formatTrendValue } from '../data';

type ChartOption = EChartsOption;
/** 只声明本组件实际构造的字段，最终整体交给 ECharts 渲染 */
type SeriesItem = Record<string, unknown> & { name?: string };

interface ChartEventInstance {
  dispatchAction: (action: Record<string, unknown>) => void;
  getOption: () => {
    dataZoom?: Array<{ end?: number; start?: number }>;
  };
  off: (event: string) => void;
  on: (event: string, handler: () => void) => void;
}

interface TooltipParam {
  marker?: string;
  seriesName?: string;
  value?: [number, null | number];
}

defineOptions({ name: 'CollectorTrendChart' });

const props = withDefaults(
  defineProps<{
    colors: Record<number, string>;
    /** 查询时间范围秒数，用于选择时间轴标签格式 */
    rangeSeconds: number;
    series: CollectorApi.TrendSeries[];
    /** 是否绘制 min~max 波动区间阴影；关闭时直接不生成阴影系列，避免无谓绘制开销 */
    showBand?: boolean;
    syncEnabled?: boolean;
  }>(),
  { showBand: true, syncEnabled: true },
);

const emit = defineEmits<{
  zoom: [payload: { end: number; start: number }];
}>();

const chartRef = ref<EchartsUIType>();
const { renderEcharts, getChartInstance } = useEcharts(chartRef);
let listenerInstance: ChartEventInstance | null = null;
let applyingExternal = false;

function finite(value?: null | number): value is number {
  return value !== null && value !== undefined && Number.isFinite(value);
}

function unitKey(series: CollectorApi.TrendSeries): string {
  return series.unit?.trim() || '未指定单位';
}

function channelName(series: CollectorApi.TrendSeries): string {
  return series.channelName?.trim() || `通道 ${series.channelId}`;
}

/** 不同通道可能同名（如两颗温度传感器）；重名时追加通道号，避免 legend 合并 */
function duplicateNameCounts(): Map<string, number> {
  const counts = new Map<string, number>();
  const seen = new Set<number>();
  for (const series of props.series) {
    if (seen.has(series.channelId)) continue;
    seen.add(series.channelId);
    const base = channelName(series);
    counts.set(base, (counts.get(base) ?? 0) + 1);
  }
  return counts;
}

function displayName(
  series: CollectorApi.TrendSeries,
  counts: Map<string, number>,
): string {
  const base = channelName(series);
  return (counts.get(base) ?? 0) > 1
    ? `${base}（通道 ${series.channelId}）`
    : base;
}

/** 聚合线 + min/max 阴影带；includeRaw 时叠加同通道的原始抽样线 */
function buildSeries(): SeriesItem[] {
  const unitIndex = new Map<string, number>();
  for (const series of props.series) {
    if (!unitIndex.has(unitKey(series))) {
      unitIndex.set(unitKey(series), unitIndex.size);
    }
  }

  const nameCounts = duplicateNameCounts();
  const result: SeriesItem[] = [];
  for (const series of props.series) {
    const color = props.colors[series.channelId] ?? '#1677ff';
    const axisIndex = unitIndex.get(unitKey(series)) ?? 0;
    const name = displayName(series, nameCounts);
    if (series.kind === 'raw') {
      result.push({
        name: `${name}（原始）`,
        type: 'line',
        yAxisIndex: axisIndex,
        showSymbol: true,
        symbol: 'circle',
        symbolSize: 2,
        connectNulls: false,
        lineStyle: { color, opacity: 0.55, width: 1 },
        itemStyle: { color, opacity: 0.65 },
        data: series.points.map((point) => [
          point.t,
          finite(point.value) ? point.value : (point.avg ?? null),
        ]),
        z: 2,
      } as SeriesItem);
      continue;
    }

    result.push({
      name,
      type: 'line',
      yAxisIndex: axisIndex,
      showSymbol: false,
      smooth: false,
      connectNulls: false,
      lineStyle: { color, width: 2 },
      itemStyle: { color },
      emphasis: { focus: 'series' },
      data: series.points.map((point) => [
        point.t,
        finite(point.avg) ? point.avg : null,
      ]),
      z: 3,
    } as SeriesItem);

    const hasRange = series.points.some(
      (point) => finite(point.min) && finite(point.max),
    );
    // 波动区间关闭时不生成阴影系列（而不是生成后透明），减少系列数量与绘制开销
    if (hasRange && props.showBand) {
      // 两个同名系列用同一 stack 形成 min~max 阴影带；图例只列出主序列名。
      result.push(
        {
          name: `${name}·范围`,
          type: 'line',
          yAxisIndex: axisIndex,
          stack: `band-${series.channelId}`,
          showSymbol: false,
          connectNulls: false,
          silent: true,
          legendHoverLink: false,
          lineStyle: { opacity: 0, width: 0 },
          areaStyle: { opacity: 0 },
          tooltip: { show: false },
          data: series.points.map((point) => [
            point.t,
            finite(point.min) ? point.min : null,
          ]),
          z: 1,
        } as SeriesItem,
        {
          name: `${name}·范围`,
          type: 'line',
          yAxisIndex: axisIndex,
          stack: `band-${series.channelId}`,
          showSymbol: false,
          connectNulls: false,
          silent: true,
          legendHoverLink: false,
          lineStyle: { opacity: 0, width: 0 },
          areaStyle: { color, opacity: 0.18 },
          tooltip: { show: false },
          data: series.points.map((point) => [
            point.t,
            finite(point.min) && finite(point.max)
              ? point.max - point.min
              : null,
          ]),
          z: 1,
        } as SeriesItem,
      );
    }
  }
  return result;
}

function buildYAxis(): ChartOption['yAxis'] {
  const units = new Map<string, number>();
  for (const series of props.series) {
    if (!units.has(unitKey(series))) {
      units.set(unitKey(series), units.size);
    }
  }
  if (units.size === 0) {
    return { type: 'value' };
  }
  return [...units.keys()].map((unit, index) => ({
    type: 'value',
    name: unit,
    position: index % 2 === 0 ? 'left' : 'right',
    offset: Math.floor(index / 2) * 54,
    scale: true,
    axisLine: { show: true },
    splitLine: {
      show: index === 0,
      lineStyle: { opacity: 0.3, type: 'dashed' },
    },
    nameTextStyle: { fontSize: 12 },
  })) as ChartOption['yAxis'];
}

function buildSeriesLookup() {
  const nameCounts = duplicateNameCounts();
  const units = new Map<string, string>();
  const counts = new Map<string, Map<number, number>>();
  for (const series of props.series) {
    const name =
      series.kind === 'raw'
        ? `${displayName(series, nameCounts)}（原始）`
        : displayName(series, nameCounts);
    units.set(name, series.unit?.trim() || '');
    const countMap = new Map<number, number>();
    for (const point of series.points) {
      countMap.set(point.t, point.count ?? 0);
    }
    counts.set(name, countMap);
  }
  return { counts, units };
}

function buildTooltip(): ChartOption['tooltip'] {
  const lookup = buildSeriesLookup();
  return {
    trigger: 'axis',
    axisPointer: { type: 'cross' },
    confine: true,
    formatter: (rawParams: unknown) => {
      const params = (
        Array.isArray(rawParams) ? rawParams : [rawParams]
      ).filter(
        (item): item is TooltipParam =>
          !!item &&
          typeof item === 'object' &&
          !(item as TooltipParam).seriesName?.endsWith('·范围') &&
          Array.isArray((item as TooltipParam).value) &&
          (item as TooltipParam).value?.[1] !== null &&
          (item as TooltipParam).value?.[1] !== undefined,
      );
      if (params.length === 0) {
        return '无有效数据';
      }
      const time = params[0]?.value?.[0];
      const header = dayjs(time).format('YYYY-MM-DD HH:mm:ss');
      const rows = params
        .map((param) => {
          const name = param.seriesName ?? '';
          const value = formatTrendValue(param.value?.[1]);
          const unit = lookup.units.get(name);
          const isRaw = name.endsWith('（原始）');
          const count = isRaw
            ? 1
            : (lookup.counts.get(name)?.get(param.value?.[0] ?? 0) ?? 0);
          const suffix = isRaw
            ? '（原始）'
            : `${formatTrendValue(count)} 个样本`;
          return `${param.marker ?? ''}${name}: ${value}${
            unit ? ` ${unit}` : ''
          }（${suffix}）`;
        })
        .join('<br/>');
      return `${header}<br/>${rows}`;
    },
  } as ChartOption['tooltip'];
}

function axisLabelFormat(): string {
  if (props.rangeSeconds <= 24 * 60 * 60) {
    return 'HH:mm';
  }
  if (props.rangeSeconds <= 7 * 24 * 60 * 60) {
    return 'MM-DD HH:mm';
  }
  return 'MM-DD';
}

function buildOption(): ChartOption {
  const series = buildSeries();
  const legendData = series
    .map((item) => item.name)
    .filter(
      (name): name is string =>
        typeof name === 'string' && !name.endsWith('·范围'),
    );
  return {
    animation: false,
    color: Object.values(props.colors),
    grid: { bottom: 76, containLabel: true, left: 12, right: 12, top: 42 },
    legend: {
      data: legendData,
      type: 'scroll',
      selectedMode: 'multiple',
      top: 4,
    },
    tooltip: buildTooltip(),
    xAxis: {
      type: 'time',
      axisLabel: {
        formatter: (value: number | string) =>
          dayjs(value).format(axisLabelFormat()),
        hideOverlap: true,
      },
      axisPointer: { label: { show: true } },
    },
    yAxis: buildYAxis(),
    dataZoom: [
      { id: 'trend-inside', type: 'inside', filterMode: 'none' },
      {
        id: 'trend-slider',
        type: 'slider',
        bottom: 22,
        height: 22,
        filterMode: 'none',
        showDetail: false,
      },
    ],
    series: series as unknown as ChartOption['series'],
  } as ChartOption;
}

async function renderChart() {
  const instance = (await renderEcharts(buildOption())) as
    | ChartEventInstance
    | null
    | undefined;
  if (!instance) {
    return;
  }
  if (listenerInstance !== instance) {
    listenerInstance?.off('datazoom');
    instance.on('datazoom', handleZoom);
    listenerInstance = instance;
  }
}

function handleZoom() {
  if (applyingExternal || !props.syncEnabled || !listenerInstance) {
    return;
  }
  const zoom = listenerInstance.getOption().dataZoom?.[0];
  if (!zoom) return;
  emit('zoom', {
    end: zoom.end ?? 100,
    start: zoom.start ?? 0,
  });
}

/** 联动缩放：由父级把另一张图的百分比范围同步过来 */
function applyZoom(start: number, end: number) {
  const instance = getChartInstance() as ChartEventInstance | null;
  if (!instance) return;
  applyingExternal = true;
  instance.dispatchAction({ type: 'dataZoom', start, end });
  setTimeout(() => {
    applyingExternal = false;
  }, 150);
}

watch(
  [
    () => props.series,
    () => props.colors,
    () => props.rangeSeconds,
    () => props.showBand,
  ],
  () => {
    void renderChart();
  },
  { deep: true },
);

onMounted(() => {
  void renderChart();
});

onBeforeUnmount(() => {
  listenerInstance?.off('datazoom');
  listenerInstance = null;
});

defineExpose({ applyZoom });
</script>

<template>
  <EchartsUI ref="chartRef" height="100%" />
</template>
