<script lang="ts" setup>
import { computed } from 'vue';

import { useMonitor } from '../use-monitor';
import TelemetryCard from './telemetry-card.vue';

/** 实时遥测总览：固定 6 列卡片 + 分页，末页不满用虚线占位卡补齐，0 通道整区空态 */
defineOptions({ name: 'KioskTelemetryPanel' });

const store = useMonitor();

const cards = store.cards;
const page = store.page;
const pageCount = store.pageCount;
const pageCards = store.pageCards;
const placeholderCount = store.placeholderCount;

const pageText = computed(
  () => `${pageCount.value === 0 ? 0 : page.value + 1} / ${pageCount.value}`,
);
const countText = computed(
  () => `共 ${cards.value.length} 项参数 · 每页 6 项`,
);

function slotOf(id: number) {
  return store.selectedIds.value.indexOf(id);
}
</script>

<template>
  <section class="kiosk-glass kiosk-flow telemetry">
    <div class="sec-head">
      <i class="bar"></i>
      <h2>实时遥测总览</h2>
      <span class="count">{{ countText }}</span>
      <div class="spacer"></div>
      <div class="pager">
        <span class="idx">{{ pageText }}</span>
        <button
          type="button"
          aria-label="上一页"
          :disabled="page === 0"
          @click="store.setPage(page - 1)"
        >
          ‹
        </button>
        <button
          type="button"
          aria-label="下一页"
          :disabled="page >= pageCount - 1"
          @click="store.setPage(page + 1)"
        >
          ›
        </button>
      </div>
    </div>

    <div v-if="cards.length === 0" class="cards-empty">
      <strong>暂无采集通道</strong>
      <span>当前设备未配置采集通道，请先在管理端完成配置。</span>
    </div>
    <div v-else class="cards">
      <TelemetryCard
        v-for="(card, index) in pageCards"
        :key="card.id"
        :channel="card"
        :order="index"
        :slot-index="slotOf(card.id)"
        :spark-points="store.sparkValues.value.get(card.id) ?? []"
        @toggle="store.toggleChannel(card.id)"
      />
      <div v-for="n in placeholderCount" :key="`ph-${n}`" class="card ph">
        <div class="row1">
          <span class="name">预留参数</span>
          <span class="delta">--</span>
        </div>
        <div class="val"><b>--</b><i>--</i></div>
        <div class="spark">
          <svg
            class="ghost-svg"
            viewBox="0 0 100 36"
            preserveAspectRatio="none"
          >
            <path
              d="M0,25 L100,25"
              stroke="rgba(255,255,255,.07)"
              stroke-width="1"
              stroke-dasharray="3 4"
              vector-effect="non-scaling-stroke"
            />
            <path
              d="M0,31 L14,29 L28,31 L42,27 L56,30 L70,28 L84,31 L100,29"
              fill="none"
              stroke="rgba(255,255,255,.10)"
              stroke-width="1.2"
              stroke-dasharray="4 4"
              vector-effect="non-scaling-stroke"
            />
          </svg>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.telemetry {
  display: flex;
  height: 150px;
  flex: none;
  flex-direction: column;
  padding: 11px 13px 12px;
}

.sec-head {
  display: flex;
  height: 20px;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.sec-head .bar {
  width: 3px;
  height: 13px;
  background: linear-gradient(180deg, var(--amber), var(--orange));
  border-radius: 2px;
  box-shadow: 0 0 9px rgb(255 122 26 / 75%);
}

.sec-head h2 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.8px;
}

.sec-head .count {
  margin-left: 2px;
  color: var(--txt3);
  font-size: 9.5px;
  letter-spacing: 0.3px;
}

.sec-head .spacer {
  margin-left: auto;
}

.pager {
  display: flex;
  align-items: center;
  gap: 5px;
}

.pager .idx {
  color: var(--txt2);
  font-size: 9.5px;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.5px;
}

.pager button {
  display: grid;
  width: 20px;
  height: 20px;
  padding: 0;
  color: #b8c6d6;
  font-size: 11px;
  line-height: 1;
  cursor: pointer;
  background: rgb(255 255 255 / 5%);
  border: 1px solid rgb(255 255 255 / 10%);
  border-radius: 6px;
  place-items: center;
  transition: 0.15s;
}

.pager button:hover:not(:disabled) {
  color: #cffbea;
  background: rgb(52 211 153 / 16%);
  border-color: rgb(52 211 153 / 55%);
}

.pager button:disabled {
  cursor: default;
  opacity: 0.28;
}

.cards {
  display: grid;
  min-height: 0;
  flex: 1;
  gap: 9px;
  grid-template-columns: repeat(6, 1fr);
}

.cards-empty {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #65727f;
  font-size: 10px;
  border: 1px dashed rgb(255 255 255 / 12%);
  border-radius: 13px;
  background-image: repeating-linear-gradient(
    135deg,
    rgb(255 255 255 / 1.8%) 0 8px,
    transparent 8px 16px
  );
}

.cards-empty strong {
  color: #9ba8b6;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

/* 占位卡：虚线边框 + 斜纹底纹，不响应点击 */
.card.ph {
  position: relative;
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: 9px 10px 8px;
  cursor: default;
  background-color: rgb(255 255 255 / 1.2%);
  background-image: repeating-linear-gradient(
    135deg,
    rgb(255 255 255 / 1.8%) 0 8px,
    transparent 8px 16px
  );
  border: 1px dashed rgb(255 255 255 / 12%);
  border-radius: 13px;
  overflow: hidden;
}

.card.ph .row1 {
  display: flex;
  height: 14px;
  align-items: center;
  gap: 5px;
}

.card.ph .name {
  color: #65727f;
  font-size: 10.5px;
  letter-spacing: 0.2px;
}

.card.ph .delta {
  margin-left: auto;
  color: #4c5865;
  font-size: 9px;
}

.card.ph .val {
  display: flex;
  align-items: baseline;
  gap: 3px;
  margin-top: 4px;
}

.card.ph .val b {
  color: #3e4a55;
  font-size: 24px;
  font-weight: 600;
  letter-spacing: 1px;
  line-height: 1.08;
}

.card.ph .val i {
  color: var(--txt3);
  font-size: 9px;
  font-style: normal;
}

.card.ph .spark {
  min-height: 18px;
  flex: 1;
  margin: 3px -2px 0;
}

.ghost-svg {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
