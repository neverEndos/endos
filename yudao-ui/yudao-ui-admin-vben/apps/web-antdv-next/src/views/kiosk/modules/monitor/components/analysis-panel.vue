<script lang="ts" setup>
import { computed } from 'vue';

import { formatMoment, formatNumber, RANGE_SPECS, SLOT_COLORS } from '../chart-utils';
import { useMonitor } from '../use-monitor';

/** 数据分析：当前选中通道在当前窗口的六项聚合 + 底部对比通道虚线槽 */
defineOptions({ name: 'KioskAnalysisPanel' });

const store = useMonitor();

const analysisChannel = store.analysisChannel;
const analysisStats = store.analysisStats;

const currentIdx = computed(() => {
  const length = store.selectedChannels.value.length;
  if (length === 0) return 0;
  return Math.min(store.analysisIdx.value, length - 1);
});

const subText = computed(() => {
  const channel = analysisChannel.value;
  if (!channel) return '暂无通道';
  return `${channel.name} · ${RANGE_SPECS[store.range.value].label}窗口`;
});

const idxText = computed(
  () => `${store.selectedChannels.value.length === 0 ? 0 : currentIdx.value + 1} / ${store.selectedChannels.value.length}`,
);

interface Row {
  label: string;
  text: string;
  unit?: string;
}

const rows = computed<Row[]>(() => {
  const channel = analysisChannel.value;
  const stats = analysisStats.value;
  if (!channel || !stats) {
    return [
      { label: '最大值', text: '--' },
      { label: '最大值时刻', text: '--' },
      { label: '最小值', text: '--' },
      { label: '最小值时刻', text: '--' },
      { label: '平均值', text: '--' },
      { label: '峰峰值', text: '--' },
    ];
  }
  const nowMs = Date.now();
  const dec = channel.decimalPlaces;
  return [
    { label: '最大值', text: formatNumber(stats.max, dec), unit: channel.unit },
    { label: '最大值时刻', text: formatMoment(stats.maxT, nowMs) },
    { label: '最小值', text: formatNumber(stats.min, dec), unit: channel.unit },
    { label: '最小值时刻', text: formatMoment(stats.minT, nowMs) },
    { label: '平均值', text: formatNumber(stats.avg, dec), unit: channel.unit },
    { label: '峰峰值', text: formatNumber(stats.pp, dec), unit: channel.unit },
  ];
});

/** 底部虚线槽：双选时可点击切到另一条曲线，单选时提示加入 Y2 */
interface DashInfo {
  clickable: boolean;
  text: string;
  color?: string;
  otherIdx?: number;
}

const dashInfo = computed<DashInfo>(() => {
  const list = store.selectedChannels.value;
  if (list.length === 0) return { clickable: false, text: '暂无采集通道' };
  if (list.length === 1) {
    return {
      clickable: false,
      text: '未选择对比通道 · 再点一张卡片加入 Y2',
    };
  }
  const otherIdx = (currentIdx.value + 1) % list.length;
  const other = list[otherIdx]!;
  const value =
    other.valueKind === 'enum'
      ? (other.valueText ?? '--')
      : formatNumber(other.valueNum, other.decimalPlaces);
  return {
    clickable: true,
    color: SLOT_COLORS[otherIdx],
    otherIdx,
    text: `对比 Y${otherIdx + 1} · ${other.name} ${value} ${other.unit}`,
  };
});

function handleDashClick() {
  if (!dashInfo.value.clickable) return;
  store.setAnalysisIdx(dashInfo.value.otherIdx!);
}
</script>

<template>
  <section class="kiosk-glass channels">
    <div class="ch-head">
      <div class="ch-title">
        <h3>数据分析</h3>
        <div class="ch-sub">{{ subText }}</div>
      </div>
      <div class="pg">
        <span class="idx">{{ idxText }}</span>
        <button
          type="button"
          aria-label="上一张卡片"
          :disabled="store.selectedChannels.value.length < 2"
          @click="store.stepAnalysis(-1)"
        >
          ‹
        </button>
        <button
          type="button"
          aria-label="下一张卡片"
          :disabled="store.selectedChannels.value.length < 2"
          @click="store.stepAnalysis(1)"
        >
          ›
        </button>
      </div>
    </div>

    <div class="ana-list">
      <div v-for="row in rows" :key="row.label" class="ana-row">
        <i class="ring"></i>
        <span class="lab">{{ row.label }}</span>
        <span class="val">{{ row.text }}<em v-if="row.unit">{{ row.unit }}</em></span>
      </div>
    </div>

    <div
      class="ana-dash"
      :class="{ can: dashInfo.clickable }"
      @click="handleDashClick"
    >
      <i
        v-if="dashInfo.color"
        :style="{ background: dashInfo.color, boxShadow: `0 0 7px ${dashInfo.color}` }"
      ></i>
      {{ dashInfo.text }}
    </div>
  </section>
</template>

<style scoped>
.channels {
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: 14px 15px 13px;
}

.ch-head {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.ch-title {
  min-width: 0;
  flex: 1;
}

.ch-title h3 {
  margin: 0;
  color: #f4f8fd;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.7px;
  line-height: 1.3;
}

.ch-sub {
  margin-top: 3px;
  overflow: hidden;
  color: #8e9aa6;
  font-size: 9.5px;
  letter-spacing: 0.25px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ch-head .pg {
  display: flex;
  flex: none;
  align-items: center;
  gap: 4px;
  padding-top: 1px;
}

.ch-head .pg .idx {
  margin-right: 2px;
  color: var(--txt3);
  font-size: 9px;
  font-variant-numeric: tabular-nums;
}

.ch-head .pg button {
  display: grid;
  width: 18px;
  height: 18px;
  padding: 0;
  color: #b8c6d6;
  font-size: 10px;
  cursor: pointer;
  background: rgb(255 255 255 / 5%);
  border: 1px solid rgb(255 255 255 / 10%);
  border-radius: 5px;
  place-items: center;
  transition: 0.15s;
}

.ch-head .pg button:hover:not(:disabled) {
  color: #cffbea;
  background: rgb(52 211 153 / 16%);
  border-color: rgb(52 211 153 / 50%);
}

.ch-head .pg button:disabled {
  cursor: default;
  opacity: 0.3;
}

.ana-list {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  margin-top: 6px;
}

.ana-row {
  display: flex;
  min-height: 0;
  flex: 1;
  align-items: center;
  gap: 9px;
}

.ana-row .ring {
  position: relative;
  width: 16px;
  height: 16px;
  flex: none;
  border: 3px solid rgb(52 211 153 / 55%);
  border-radius: 50%;
}

.ana-row .ring::after {
  position: absolute;
  width: 5px;
  height: 5px;
  margin: auto;
  content: "";
  background: #34d399;
  border-radius: 50%;
  box-shadow: 0 0 7px rgb(52 211 153 / 95%);
  inset: 0;
}

.ana-row .lab {
  color: #c6d2df;
  font-size: 10.5px;
  letter-spacing: 0.35px;
  white-space: nowrap;
}

.ana-row .val {
  margin-left: auto;
  overflow: hidden;
  color: #f1f5ff;
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.1px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ana-row .val em {
  margin-left: 3px;
  color: #8e9aa6;
  font-size: 9px;
  font-style: normal;
  letter-spacing: 0.2px;
}

.ana-dash {
  display: flex;
  height: 26px;
  flex: none;
  align-items: center;
  justify-content: center;
  padding: 0 10px;
  margin-top: 4px;
  overflow: hidden;
  color: #7e8a96;
  font-size: 9.5px;
  letter-spacing: 0.3px;
  white-space: nowrap;
  border: 1px dashed rgb(255 255 255 / 16%);
  border-radius: 12px;
  gap: 6px;
}

.ana-dash i {
  width: 5px;
  height: 5px;
  flex: none;
  border-radius: 50%;
}

.ana-dash.can {
  cursor: pointer;
  transition: 0.18s;
}

.ana-dash.can:hover {
  color: #e0cbb4;
  border-color: rgb(255 166 89 / 45%);
}
</style>
