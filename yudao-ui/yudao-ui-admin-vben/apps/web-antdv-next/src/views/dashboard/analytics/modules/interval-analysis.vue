<script lang="ts" setup>
import type { ThresholdRule } from '../data';

import type { CollectorApi } from '#/api/collector';

import { computed } from 'vue';

import {
  analyzeEnumInterval,
  analyzeInterval,
  formatIntervalDuration,
  formatNumeric,
  formatPercent,
  formatShanghaiMdHm,
} from '../data';

defineOptions({ name: 'ScreenIntervalAnalysis' });

const props = withDefaults(
  defineProps<{
    channel: CollectorApi.DashboardChannel;
    /** 分析窗口起点（epoch millis） */
    from: number;
    /** 启用的四档阈值；未启用的档位不参与越限统计 */
    rules?: ThresholdRule[];
    series?: CollectorApi.TrendSeries;
    /** 通道阈值矩阵；四档全部展示，未启用档位不参与统计 */
    threshold?: CollectorApi.ThresholdMatrixChannel;
    /** 分析窗口终点（epoch millis） */
    to: number;
    unit?: string;
  }>(),
  {
    rules: () => [],
    series: undefined,
    threshold: undefined,
    unit: '',
  },
);

const isEnum = computed(() => props.channel.valueKind === 'enum');
const unitText = computed(() => props.unit?.trim() || '');
const analysis = computed(() =>
  isEnum.value
    ? undefined
    : analyzeInterval(props.series, props.from, props.to, props.rules),
);
const enumAnalysis = computed(() =>
  isEnum.value ? analyzeEnumInterval(props.series, props.channel) : undefined,
);

const bucketText = computed(() => {
  if (analysis.value) {
    return `${analysis.value.bucketSeconds} 秒${
      analysis.value.bucketEstimated ? '（由相邻点间隔估算）' : '（接口返回）'
    }`;
  }
  if (enumAnalysis.value) {
    return `${enumAnalysis.value.bucketSeconds} 秒（接口返回或按间隔估算）`;
  }
  return '--';
});

function withUnit(value: string): string {
  return unitText.value ? `${value} ${unitText.value}` : value;
}

function signedText(value?: number): string {
  if (value === undefined || !Number.isFinite(value)) return '--';
  return `${value > 0 ? '+' : ''}${formatNumeric(value)}`;
}

function signedPercent(value?: number): string {
  if (value === undefined || !Number.isFinite(value)) return '--';
  return `${value > 0 ? '+' : ''}${formatPercent(value)}`;
}

const qualityLabel = computed(() => {
  const quality = props.channel.quality;
  if (quality === 0) return '正常';
  if (quality === 1) return '不确定';
  if (quality === 2) return '坏质量';
  return '未知（无最新采样质量）';
});

const metricItems = computed(() => {
  const value = analysis.value;
  if (!value) return [];
  return [
    {
      hint: `桶起点 ${formatShanghaiMdHm(value.maxTime)}`,
      key: 'max',
      label: '最大值',
      value: withUnit(formatNumeric(value.maxValue)),
    },
    {
      hint: `桶起点 ${formatShanghaiMdHm(value.minTime)}`,
      key: 'min',
      label: '最小值',
      value: withUnit(formatNumeric(value.minValue)),
    },
    {
      hint: '按桶内有效样本数加权',
      key: 'mean',
      label: '区间均值',
      value: withUnit(formatNumeric(value.mean)),
    },
    {
      hint: `最近 ${formatShanghaiMdHm(value.last)} 桶`,
      key: 'last',
      label: '最新值',
      value: withUnit(formatNumeric(value.last)),
    },
    {
      hint: '最大值 − 最小值',
      key: 'amplitude',
      label: '振幅',
      value: withUnit(formatNumeric(value.amplitude)),
    },
    {
      hint: '最新值相对首值',
      key: 'change',
      label: '变化率',
      value: signedPercent(value.changeRate),
    },
    {
      hint: `最新 ${formatNumeric(value.last)} − 均值 ${formatNumeric(
        value.mean,
      )}`,
      key: 'vs-mean',
      label: '最新 vs 均值',
      value: `${signedText(value.latestVsMean)}
        ${signedPercent(value.latestVsMeanPercent)}`.trim(),
    },
    {
      hint: `最新 ${formatNumeric(value.last)} − 首值 ${formatNumeric(
        value.first,
      )}`,
      key: 'vs-first',
      label: '最新 vs 首值',
      value: `${signedText(value.latestVsFirst)}
        ${signedPercent(value.latestVsFirstPercent)}`.trim(),
    },
    {
      hint: '有效桶 / 期望桶（期望 = ⌈窗口 ÷ 桶宽⌉）',
      key: 'buckets',
      label: '有效桶',
      value: `${value.validBuckets} / ${value.expectedBuckets}`,
    },
    {
      hint: '有效桶占比的补数',
      key: 'missing',
      label: '缺测率',
      value: formatPercent(value.missingRate * 100),
    },
    {
      hint:
        value.validSamples === undefined
          ? '接口未返回 count，按桶等权'
          : 'quality=0 且数值非空的样本数',
      key: 'samples',
      label: '有效样本',
      value:
        value.validSamples === undefined
          ? '--'
          : formatNumeric(value.validSamples),
    },
    {
      hint: '越限桶数 × 桶宽',
      key: 'window',
      label: '分析窗口',
      value: formatIntervalDuration(value.windowSeconds),
    },
  ];
});

/**
 * 四档阈值全部展示：启用档位展示越限桶数/次数/时长，未启用档位明确标注，
 * 避免用户误以为“没有越限”与“未启用”是同一件事。
 */
const thresholdRows = computed(() => {
  const source = props.threshold;
  const stats = new Map(
    (analysis.value?.thresholds ?? []).map((item) => [item.key, item]),
  );
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
  ].map((item) => ({ ...item, stat: stats.get(item.key) }));
});
const hasThresholdSource = computed(() => Boolean(props.threshold));
const hasEnabledThresholds = computed(() =>
  thresholdRows.value.some((item) => item.enabled),
);
</script>

<template>
  <div class="interval-analysis">
    <div class="bucket-note">
      桶宽 {{ bucketText }} · 缺测率按“有效桶 / 期望桶”推导；越限按桶均值判定，
      相邻越限桶按桶宽累计时长。
    </div>

    <!-- 枚举通道：分布 + 最近切换时间 -->
    <template v-if="isEnum">
      <div v-if="enumAnalysis" class="enum-block">
        <div class="enum-head">
          <span>
            枚举分布 · 最近码
            <strong>{{ enumAnalysis.latestCode }}</strong>
          </span>
          <span>
            最近切换
            {{
              enumAnalysis.lastChangeTime
                ? formatShanghaiMdHm(enumAnalysis.lastChangeTime)
                : '区间内未发生切换'
            }}
          </span>
        </div>
        <div class="enum-list">
          <div
            v-for="entry in enumAnalysis.entries"
            :key="entry.code"
            class="enum-row"
          >
            <span class="enum-label">{{ entry.label }}</span>
            <svg
              class="bar-svg"
              preserveAspectRatio="none"
              viewBox="0 0 100 8"
              aria-hidden="true"
            >
              <rect class="bar-track" height="8" width="100" x="0" y="0" />
              <rect
                :width="Math.max(0, Math.min(100, entry.percent))"
                height="8"
                x="0"
                y="0"
                fill="#3987e5"
              />
            </svg>
            <span class="enum-count">
              {{ formatNumeric(entry.count) }} · {{ formatPercent(entry.percent) }}
            </span>
          </div>
        </div>
        <div class="bucket-note">
          枚举分布按聚合桶均值四舍五入归并到枚举码，占比以桶内有效样本数加权，
          属于近似结果（趋势接口不返回逐样本枚举文本）。
        </div>
      </div>
      <div v-else class="interval-empty">该枚举通道在分析区间内没有可用数据。</div>
    </template>

    <!-- 数值通道：统计指标 + 趋势 + 阈值越限 -->
    <template v-else>
      <div v-if="analysis" class="metric-block">
        <div class="metric-grid">
          <div v-for="item in metricItems" :key="item.key" class="metric-cell">
            <span class="metric-label">{{ item.label }}</span>
            <span class="metric-value">{{ item.value }}</span>
            <span class="metric-hint">{{ item.hint }}</span>
          </div>
        </div>

        <div class="trend-row">
          <span class="trend-chip">区间趋势：{{ analysis.trend }}</span>
          <span class="trend-note">
            首/末三分之一桶均值比较，相对差 ≥5% 判定上升/下降；少于 6 个桶时样本不足。
          </span>
        </div>

        <div class="threshold-block">
          <div class="threshold-head">
            <span class="threshold-title">阈值越限 · THRESHOLD</span>
            <span class="threshold-note">
              {{
                !hasThresholdSource
                  ? '阈值矩阵不可用（需 collector:channel:query）'
                  : hasEnabledThresholds
                    ? '按桶均值判定；相邻越限桶合并为一次越限'
                    : '四档阈值均未启用'
              }}
            </span>
          </div>
          <div class="threshold-list">
            <div
              v-for="item in thresholdRows"
              :key="item.key"
              class="threshold-row"
              :class="{ 'threshold-row--off': !item.enabled }"
            >
              <span class="threshold-label" :style="{ color: item.color }">
                {{ item.label }}
                <small>{{ item.value }}</small>
              </span>
              <svg
                class="bar-svg"
                preserveAspectRatio="none"
                viewBox="0 0 100 8"
                aria-hidden="true"
              >
                <rect class="bar-track" height="8" width="100" x="0" y="0" />
                <rect
                  v-if="item.enabled && item.stat"
                  :fill="item.color"
                  :width="Math.max(0, Math.min(100, item.stat.percent))"
                  height="8"
                  x="0"
                  y="0"
                />
              </svg>
              <span class="threshold-value">
                <template v-if="!item.enabled">未启用</template>
                <template v-else-if="item.stat">
                  {{ item.stat.buckets }} 桶 · 越限
                  {{ item.stat.occurrences }} 次 ·
                  {{ formatIntervalDuration(item.stat.seconds) }}
                  （{{ formatPercent(item.stat.percent) }}）
                </template>
                <template v-else>区间内无可用数据</template>
              </span>
            </div>
          </div>
        </div>

        <div class="quality-row">
          <span>
            最新采样质量：<strong>{{ qualityLabel }}</strong>
          </span>
          <span class="quality-note">
            区间坏质量占比：不可用（趋势接口只返回 quality=0 的有效样本计数，
            无按质量分级的样本比例）
          </span>
        </div>
      </div>
      <div v-else class="interval-empty">该数值通道在分析区间内没有可用数据。</div>
    </template>
  </div>
</template>

<style scoped>
.interval-analysis {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.bucket-note {
  font-size: 10px;
  line-height: 1.5;
  color: #64748b;
}

.metric-block,
.enum-block {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 5px;
}

.metric-cell {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  padding: 6px 8px;
  background: rgb(17 28 50 / 55%);
  border: 1px solid rgb(72 104 158 / 20%);
  border-radius: 5px;
}

.metric-label {
  font-size: 9px;
  color: #64748b;
}

.metric-value {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: #e2e8f0;
  white-space: nowrap;
}

.metric-hint {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 9px;
  color: #64748b;
  white-space: nowrap;
}

.trend-row,
.quality-row,
.enum-head {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  align-items: baseline;
  font-size: 10px;
  line-height: 1.5;
  color: #64748b;
}

.trend-chip {
  padding: 1px 8px;
  font-size: 11px;
  color: #22d3ee;
  background: rgb(34 211 238 / 8%);
  border: 1px solid rgb(34 211 238 / 35%);
  border-radius: 4px;
}

.threshold-block {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.threshold-head {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  align-items: baseline;
  justify-content: space-between;
}

.threshold-title {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 10px;
  color: #94a3b8;
  letter-spacing: 1.4px;
}

.threshold-note {
  font-size: 9px;
  color: #64748b;
}

.threshold-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.threshold-row {
  display: grid;
  grid-template-columns:
    minmax(88px, 1.1fr) minmax(60px, 2fr)
    minmax(150px, 2.6fr);
  gap: 8px;
  align-items: center;
  padding: 4px 6px;
  background: rgb(17 28 50 / 42%);
  border: 1px solid rgb(72 104 158 / 16%);
  border-radius: 4px;
}

.threshold-row--off {
  opacity: 0.55;
}

.threshold-label {
  font-size: 10px;
  white-space: nowrap;
}

.threshold-label small {
  margin-left: 3px;
  font-size: 9px;
  color: #94a3b8;
}

.threshold-value {
  font-size: 10px;
  font-variant-numeric: tabular-nums;
  color: #cbd5e1;
}

.bar-svg {
  display: block;
  width: 100%;
  height: 8px;
  overflow: hidden;
  border-radius: 3px;
}

.bar-track {
  fill: rgb(10 19 34 / 90%);
}

.quality-row {
  align-items: flex-start;
}

.quality-note {
  color: #94a3b8;
}

.enum-head {
  justify-content: space-between;
}

.enum-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.enum-row {
  display: grid;
  grid-template-columns: minmax(90px, 1.2fr) minmax(80px, 3fr) minmax(86px, 1.4fr);
  gap: 8px;
  align-items: center;
  font-size: 10px;
  color: #cbd5e1;
}

.enum-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.enum-count {
  font-variant-numeric: tabular-nums;
  color: #94a3b8;
  text-align: right;
}

.interval-empty {
  padding: 10px 0;
  font-size: 12px;
  color: #94a3b8;
}

@media (max-width: 720px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .threshold-row,
  .enum-row {
    grid-template-columns: minmax(70px, 1fr) minmax(50px, 2fr);
  }

  .threshold-value,
  .enum-count {
    grid-column: 1 / -1;
    text-align: left;
  }
}

@media (prefers-reduced-motion: reduce) {
  .interval-analysis {
    transition: none;
  }
}
</style>
