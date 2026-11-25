<script lang="ts" setup>
import type { EChartsOption, EchartsUIType } from '@vben/plugins/echarts';

import type { CollectorApi } from '#/api/collector';

import { onMounted, ref, watch } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import {
  escapeHtml,
  formatAxisTick,
  formatNumeric,
  formatShanghaiMdHm,
  hexToRgba,
  isFiniteNumber,
  seriesName,
  unitKey,
} from '../data';

defineOptions({ name: 'ScreenTrendChart' });

const props = defineProps<{
  /** 通道编号 → 稳定颜色；最多三条，颜色同时用于图例与折线 */
  colors: Record<number, string>;
  /** 查询时间范围秒数，用于时间轴标签格式 */
  rangeSeconds: number;
  series: CollectorApi.TrendSeries[];
}>();

/** 只声明实际构造的字段，整体交给 ECharts；避免与库类型逐项对齐 */
type SeriesItem = Record<string, unknown> & { name?: string };

interface TooltipParam {
  marker?: string;
  seriesName?: string;
  value?: [number, null | number];
}

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

function unitOf(series: CollectorApi.TrendSeries): string {
  return series.unit?.trim() || '';
}

/** 单序列时显示末端数值标签；多序列末端避免互相压字，交由图例与提示承担 */
function endLabelOf(name: string) {
  return {
    backgroundColor: 'rgba(7, 12, 24, 0.88)',
    borderRadius: 2,
    color: '#cbd5e1',
    fontSize: 10,
    formatter: (params: { value?: [number, null | number] }) =>
      `${name} ${formatNumeric(params.value?.[1])}`,
    padding: [2, 4],
    show: true,
  };
}

function buildSeries(): SeriesItem[] {
  const single = props.series.length === 1;
  return props.series.map((series) => {
    const color = props.colors[series.channelId] ?? '#3987e5';
    const points = series.points.map((point) => [
      point.t,
      isFiniteNumber(point.avg) ? point.avg : null,
    ]);
    const item: SeriesItem = {
      areaStyle: single
        ? {
            color: {
              colorStops: [
                { color: hexToRgba(color, 0.2), offset: 0 },
                { color: hexToRgba(color, 0.01), offset: 1 },
              ],
              type: 'linear',
              x: 0,
              x2: 0,
              y: 0,
              y2: 1,
            },
          }
        : undefined,
      connectNulls: false,
      data: points,
      emphasis: { focus: 'series' },
      itemStyle: { color },
      lineStyle: {
        // 渐变描边：从弱到强再回落到 85%，保留通道色相并增加层次
        color: {
          colorStops: [
            { color: hexToRgba(color, 0.32), offset: 0 },
            { color, offset: 0.55 },
            { color: hexToRgba(color, 0.82), offset: 1 },
          ],
          type: 'linear',
          x: 0,
          x2: 1,
          y: 0,
          y2: 0,
        },
        join: 'round',
        cap: 'round',
        width: 2,
      },
      name: seriesName(series),
      showSymbol: false,
      smooth: false,
      type: 'line',
      z: 3,
    };
    if (single) {
      item.endLabel = endLabelOf(seriesName(series));
    }
    return item;
  });
}

function buildTooltip(): EChartsOption['tooltip'] {
  const lookup = new Map<string, { color: string; unit: string }>();
  for (const series of props.series) {
    lookup.set(seriesName(series), {
      color: props.colors[series.channelId] ?? '',
      unit: unitOf(series),
    });
  }
  return {
    axisPointer: { type: 'cross' },
    confine: true,
    formatter: (rawParams: unknown) => {
      const params = (
        Array.isArray(rawParams) ? rawParams : [rawParams]
      ).filter(
        (item): item is TooltipParam =>
          !!item &&
          typeof item === 'object' &&
          Array.isArray((item as TooltipParam).value) &&
          isFiniteNumber((item as TooltipParam).value?.[1]),
      );
      if (params.length === 0) return '无有效数据';
      const time = params[0]?.value?.[0];
      const rows = params
        .map((param) => {
          const name = param.seriesName ?? '';
          const detail = lookup.get(name);
          const unit = detail?.unit ? ` ${detail.unit}` : '';
          const swatch = detail?.color
            ? `<span style="display:inline-block;width:7px;height:7px;border-radius:50%;background:${detail.color};margin-right:5px"></span>`
            : '';
          return `${swatch}${escapeHtml(name)}: ${formatNumeric(
            param.value?.[1],
          )}${unit}`;
        })
        .join('<br/>');
      return `${formatShanghaiMdHm(time)}<br/>${rows}`;
    },
    trigger: 'axis',
  } as EChartsOption['tooltip'];
}

function buildOption(): EChartsOption {
  const hasMultiple = props.series.length > 1;
  const units = [
    ...new Set(props.series.map((series) => unitKey(series.unit))),
  ];
  const yName = units.length === 1 ? units[0] : '';
  return {
    animation: false,
    color: Object.values(props.colors),
    grid: {
      bottom: 4,
      containLabel: true,
      left: 4,
      right: props.series.length === 1 ? 58 : 16,
      top: hasMultiple ? 30 : 8,
    },
    legend: hasMultiple
      ? {
          data: props.series.map((series) => seriesName(series)),
          icon: 'roundRect',
          itemHeight: 4,
          itemWidth: 14,
          textStyle: { color: '#b7c6dd', fontSize: 11 },
          top: 0,
          type: 'scroll',
        }
      : undefined,
    series: buildSeries() as unknown as EChartsOption['series'],
    tooltip: buildTooltip(),
    xAxis: {
      axisLabel: {
        color: '#7c8db5',
        fontSize: 10,
        formatter: (value: number | string) =>
          formatAxisTick(Number(value), props.rangeSeconds),
        hideOverlap: true,
      },
      axisLine: { lineStyle: { color: 'rgba(72, 104, 158, 0.35)' } },
      axisPointer: { label: { show: true } },
      axisTick: { show: false },
      splitLine: { show: false },
      type: 'time',
    },
    yAxis: {
      axisLabel: { color: '#7c8db5', fontSize: 10 },
      axisLine: { show: false },
      axisTick: { show: false },
      name: yName === '未指定单位' ? '' : yName,
      nameTextStyle: { color: '#64748b', fontSize: 10, padding: [0, 0, 0, 4] },
      scale: true,
      splitLine: {
        lineStyle: { color: 'rgba(148, 163, 184, 0.12)', type: 'solid' },
      },
      type: 'value',
    },
  };
}

async function renderChart() {
  await renderEcharts(buildOption());
}

watch(
  [() => props.series, () => props.colors, () => props.rangeSeconds],
  () => {
    void renderChart();
  },
  { deep: true },
);

onMounted(() => {
  void renderChart();
});
</script>

<template>
  <EchartsUI
    ref="chartRef"
    aria-label="采集通道趋势图，支持提示框查看各时刻数值"
    height="100%"
  />
</template>
