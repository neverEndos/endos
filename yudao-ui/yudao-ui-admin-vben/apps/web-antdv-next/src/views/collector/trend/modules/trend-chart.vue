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

/** 水平标线：按 y 轴数值绘制，用于事发阈值等 */
export interface TrendMarkLine {
  value: number;
  label: string;
  color?: string;
  dashed?: boolean;
}

/** 垂直标线：按时间轴时刻绘制，用于触发/恢复时刻等 */
export interface TrendMarkTime {
  t: number;
  label: string;
  color?: string;
  dashed?: boolean;
}

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
    /** 水平标线：默认空数组，趋势分析页不传时行为与旧版一致 */
    markLines?: TrendMarkLine[];
    /** 垂直标线：默认空数组，用于触发/恢复时刻 */
    markTimes?: TrendMarkTime[];
    /** 查询时间范围秒数，用于选择时间轴标签格式 */
    rangeSeconds: number;
    series: CollectorApi.TrendSeries[];
    syncEnabled?: boolean;
  }>(),
  {
    markLines: () => [],
    markTimes: () => [],
    syncEnabled: true,
  },
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

/**
 * 标线系列名统一前缀：图例与 tooltip 都据此过滤，保证标线不进入图例/提示。
 * 共享 ECharts 未注册 MarkLineComponent（packages 不在本功能改动范围），
 * 因此用两点普通折线模拟标线：水平线固定 y、垂直线固定 x，样式与 markLine 等价。
 */
const MARKER_SERIES_PREFIX = '__trend-marker__';

interface MarkerBounds {
  xMax: number;
  xMin: number;
  yMax: number;
  yMin: number;
}

/** 汇总绘制标线所需的坐标范围；阈值和触发时刻也计入，避免标线落在轴范围外 */
function collectMarkerBounds(): MarkerBounds | undefined {
  let xMax = Number.NEGATIVE_INFINITY;
  let xMin = Number.POSITIVE_INFINITY;
  let yMax = Number.NEGATIVE_INFINITY;
  let yMin = Number.POSITIVE_INFINITY;
  const useX = (value?: null | number) => {
    if (!finite(value)) return;
    xMin = Math.min(xMin, value);
    xMax = Math.max(xMax, value);
  };
  const useY = (value?: null | number) => {
    if (!finite(value)) return;
    yMin = Math.min(yMin, value);
    yMax = Math.max(yMax, value);
  };

  for (const series of props.series) {
    for (const point of series.points) {
      useX(point.t);
      useY(point.avg);
      useY(point.value);
      useY(point.min);
      useY(point.max);
    }
  }
  for (const item of props.markLines) useY(item.value);
  for (const item of props.markTimes) useX(item.t);

  if (
    !finite(xMin) ||
    !finite(xMax) ||
    !finite(yMin) ||
    !finite(yMax)
  ) {
    return undefined;
  }
  // 单点或水平直线时人为撑开范围，避免坐标轴退化为零跨度
  if (xMax === xMin) {
    xMin -= 1000;
    xMax += 1000;
  }
  if (yMax === yMin) {
    yMin -= 1;
    yMax += 1;
  }
  const yPadding = (yMax - yMin) * 0.06;
  return {
    xMax,
    xMin,
    yMax: yMax + yPadding,
    yMin: yMin - yPadding,
  };
}

function markerLabel(label: string, color: string) {
  return {
    show: true,
    formatter: () => label,
    color,
    fontSize: 11,
    backgroundColor: 'rgba(255, 255, 255, 0.85)',
    padding: [2, 4],
  };
}

/** 生成标线模拟系列；默认 markLines/markTimes 为空时不追加任何系列 */
function buildMarkerSeries(): SeriesItem[] {
  if (props.markLines.length === 0 && props.markTimes.length === 0) {
    return [];
  }
  const bounds = collectMarkerBounds();
  if (!bounds) return [];

  const markers: SeriesItem[] = [];
  props.markLines.forEach((item, index) => {
    const color = item.color ?? '#fa8c16';
    markers.push({
      name: `${MARKER_SERIES_PREFIX}line-${index}`,
      type: 'line',
      yAxisIndex: 0,
      showSymbol: false,
      connectNulls: false,
      silent: true,
      legendHoverLink: false,
      tooltip: { show: false },
      lineStyle: {
        color,
        type: item.dashed ? 'dashed' : 'solid',
        width: 1.5,
      },
      itemStyle: { color },
      endLabel: markerLabel(item.label, color),
      data: [
        [bounds.xMin, item.value],
        [bounds.xMax, item.value],
      ],
      z: 10,
    } as SeriesItem);
  });
  props.markTimes.forEach((item, index) => {
    const color = item.color ?? '#fa541c';
    markers.push({
      name: `${MARKER_SERIES_PREFIX}time-${index}`,
      type: 'line',
      yAxisIndex: 0,
      showSymbol: false,
      connectNulls: false,
      silent: true,
      legendHoverLink: false,
      tooltip: { show: false },
      lineStyle: {
        color,
        type: item.dashed ? 'dashed' : 'solid',
        width: 1.5,
      },
      itemStyle: { color },
      endLabel: markerLabel(item.label, color),
      data: [
        [item.t, bounds.yMin],
        [item.t, bounds.yMax],
      ],
      z: 11,
    } as SeriesItem);
  });
  return markers;
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
    if (hasRange) {
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

  // 标线追加在真实序列之后；默认空数组时不会追加任何系列
  result.push(...buildMarkerSeries());
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
          !(item as TooltipParam).seriesName?.startsWith(MARKER_SERIES_PREFIX) &&
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
        typeof name === 'string' &&
        !name.endsWith('·范围') &&
        !name.startsWith(MARKER_SERIES_PREFIX),
    );
  return {
    animation: false,
    color: Object.values(props.colors),
    // 有水平标线时右侧留出标签空间，避免“事发阈值”等标签贴边被裁剪
    grid: {
      bottom: 76,
      containLabel: true,
      left: 12,
      right: props.markLines.length > 0 ? 76 : 12,
      top: 42,
    },
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
    () => props.markLines,
    () => props.markTimes,
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
