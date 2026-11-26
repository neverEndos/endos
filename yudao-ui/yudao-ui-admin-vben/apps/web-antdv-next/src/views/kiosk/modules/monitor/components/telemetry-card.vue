<script lang="ts" setup>
import type { MonitorChannel } from '../use-monitor';

import { computed } from 'vue';

import { formatNumber, SLOT_COLORS } from '../chart-utils';
import Sparkline from './sparkline.vue';

/** 单张遥测卡片：名称 + 状态 + 当前值 + 迷你趋势，选中态由 slotIndex 驱动 */
const props = defineProps<{
  channel: MonitorChannel;
  /** 卡片在当页的位置，用于流光错峰 */
  order: number;
  /** 抽稀后的 sparkline 序列 */
  sparkPoints: number[];
  /** 选中槽位：-1 未选中，0/1 对应 slot1/slot2 */
  slotIndex: number;
}>();

const emit = defineEmits<{
  toggle: [];
}>();

const selected = computed(() => props.slotIndex >= 0);
const ringColor = computed(() =>
  selected.value ? SLOT_COLORS[props.slotIndex] : undefined,
);

/** 第一行右侧状态：替代原型的涨跌幅位置，仅在异常时显示 */
const stateText = computed(() => {
  const channel = props.channel;
  if (!channel.sampleTime) return { color: '#77859A', text: '无数据' };
  if (channel.stale) return { color: '#FFA659', text: '陈旧' };
  if (channel.quality === 2) return { color: '#FF8A8A', text: '坏质量' };
  if (channel.quality === 1) return { color: '#8FB6FF', text: '不确定' };
  return undefined;
});

const valueText = computed(() => {
  const channel = props.channel;
  if (channel.valueKind === 'enum') return channel.valueText ?? '--';
  return formatNumber(channel.valueNum, channel.decimalPlaces);
});

const cardStyle = computed(() => {
  let style = `--accent:${props.channel.accent};--d:${(-(props.order % 6) * 1.3).toFixed(1)}s;`;
  const ring = ringColor.value;
  if (ring) {
    style +=
      `border-color:${ring};` +
      `box-shadow:0 0 0 1px ${ring}55,0 0 22px -6px ${ring}aa,inset 0 1px 0 rgba(255,255,255,.1);`;
  }
  return style;
});
</script>

<template>
  <div
    class="card"
    :class="{ sel: selected }"
    :style="cardStyle"
    role="button"
    tabindex="0"
    @click="emit('toggle')"
    @keydown.enter="emit('toggle')"
  >
    <div class="row1">
      <span
        v-if="selected"
        class="slot"
        :style="{
          color: ringColor,
          borderColor: `${ringColor}66`,
          background: `${ringColor}1f`,
        }"
        >{{ slotIndex + 1 }}</span>
      <span class="name">{{ channel.name }}</span>
      <span
        v-if="stateText"
        class="state"
        :style="{ color: stateText.color }"
        >{{ stateText.text }}</span>
    </div>
    <div class="val">
      <b>{{ valueText }}</b>
      <i>{{ channel.unit }}</i>
    </div>
    <div class="spark">
      <Sparkline
        :color="selected ? ringColor! : channel.accent"
        :gid="`sg-${channel.id}`"
        :points="sparkPoints"
      />
    </div>
  </div>
</template>

<style scoped>
.card {
  position: relative;
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: 9px 10px 8px;
  cursor: pointer;
  background: linear-gradient(
    180deg,
    rgb(232 242 255 / 10%),
    rgb(232 242 255 / 3.5%)
  );
  border: 1px solid rgb(255 255 255 / 17%);
  border-radius: 13px;
  overflow: hidden;
  transition:
    border-color 0.22s,
    background 0.22s,
    transform 0.22s,
    box-shadow 0.22s;
}

/* 卡片顶部色条 */
.card::before {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 2;
  width: 44px;
  height: 2px;
  content: "";
  background: var(--accent);
  border-radius: 1px;
  box-shadow: 0 0 10px var(--accent);
  opacity: 0.85;
  transition: 0.22s;
}

/* 卡片错峰流光边 */
.card::after {
  position: absolute;
  padding: 1px;
  pointer-events: none;
  content: "";
  background: conic-gradient(
    from var(--flow),
    transparent 0deg,
    rgba(255, 255, 255, 0) 140deg,
    var(--accent) 210deg,
    rgba(255, 255, 255, 0.9) 222deg,
    var(--accent) 234deg,
    transparent 290deg
  );
  border-radius: 13px;
  inset: 0;
  opacity: 0.45;
  animation: kiosk-flow 9s linear infinite;
  animation-delay: var(--d, 0s);
  -webkit-mask:
    linear-gradient(#000 0 0) content-box,
    linear-gradient(#000 0 0);
  mask:
    linear-gradient(#000 0 0) content-box,
    linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
}

.card:hover {
  border-color: rgb(255 255 255 / 28%);
  transform: translateY(-1px);
}

.card.sel::after {
  opacity: 1;
  animation-duration: 3.6s;
}

.row1 {
  display: flex;
  height: 14px;
  align-items: center;
  gap: 5px;
}

.slot {
  display: grid;
  width: 14px;
  height: 14px;
  flex: none;
  font-size: 8.5px;
  font-weight: 600;
  line-height: 1;
  border: 1px solid;
  border-radius: 4px;
  place-items: center;
}

.name {
  overflow: hidden;
  color: #9ba8b6;
  font-size: 10.5px;
  letter-spacing: 0.2px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card.sel .name {
  color: #e3f1ec;
}

.state {
  margin-left: auto;
  font-size: 9px;
  letter-spacing: 0.2px;
  white-space: nowrap;
}

.val {
  display: flex;
  align-items: baseline;
  gap: 3px;
  margin-top: 4px;
}

.val b {
  overflow: hidden;
  font-size: 26px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.3px;
  line-height: 1.08;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.val i {
  color: var(--txt3);
  font-size: 9px;
  font-style: normal;
  letter-spacing: 0.2px;
  white-space: nowrap;
}

.spark {
  min-height: 18px;
  flex: 1;
  margin: 3px -2px 0;
}
</style>
