<script lang="ts" setup>
import type { KioskModuleDefinition } from '../../../registry';
import type { SystemStatus } from '../use-monitor';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import LongPressExit from '../../../components/long-press-exit.vue';
import { formatClock } from '../chart-utils';
import { useMonitor } from '../use-monitor';

defineOptions({ name: 'KioskMonitorTopbar' });

const props = defineProps<{
  activeKey?: string;
  kioskMode: boolean;
  modules: KioskModuleDefinition[];
}>();

const emit = defineEmits<{
  select: [key: string];
}>();

const store = useMonitor();

/** 尚未实现的界面：仅展示，不可点击、点击无任何效果 */
const PLANNED_ITEMS = [
  {
    icon: 'trend',
    title: '趋势分析',
  },
  {
    icon: 'alarm',
    title: '报警记录',
  },
  {
    icon: 'config',
    title: '参数配置',
  },
  {
    icon: 'diagnosis',
    title: '设备诊断',
  },
] as const;

const navOpen = ref(false);
const now = ref(Date.now());
let clockTimer: ReturnType<typeof setInterval> | undefined;

const clock = computed(() => formatClock(now.value));

const statusText = computed(() => {
  const labels: Record<SystemStatus, string> = {
    connecting: '连接中',
    empty: '暂无设备',
    error: '连接中断',
    offline: '系统离线',
    online: '系统在线',
  };
  return labels[store.systemStatus.value];
});

function toggleNav() {
  navOpen.value = !navOpen.value;
}

function closeNav() {
  navOpen.value = false;
}

function selectModule(key: string) {
  closeNav();
  emit('select', key);
}

function handleDocumentClick() {
  closeNav();
}

onMounted(() => {
  clockTimer = setInterval(() => {
    if (!document.hidden) now.value = Date.now();
  }, 1000);
  document.addEventListener('click', handleDocumentClick);
});

onBeforeUnmount(() => {
  if (clockTimer !== undefined) clearInterval(clockTimer);
  document.removeEventListener('click', handleDocumentClick);
});
</script>

<template>
  <header class="topbar">
    <div class="logo">
      <svg width="19" height="19" viewBox="0 0 24 24" fill="none">
        <path
          d="M2 13h4l2.2-5.5L12 18l3.2-8L18 13h4"
          stroke="#fff"
          stroke-width="1.9"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </div>
    <div class="brand">
      <h1>工业遥测监控中心</h1>
      <span>GLASS HMI / DL-07</span>
    </div>

    <div class="status" :class="`is-${store.systemStatus.value}`">
      <i class="dot"></i>{{ statusText }}
    </div>
    <div class="clock">
      <b>{{ clock.time }}</b>
      <i>{{ clock.date }}</i>
    </div>

    <!-- 其他界面入口：放在最右侧，菜单向左展开 -->
    <div class="nav-wrap">
      <div class="nav-btn" :class="{ on: navOpen }" @click.stop="toggleNav">
        <svg
          width="12"
          height="12"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.9"
        >
          <rect x="3" y="3" width="7" height="7" rx="1.5" />
          <rect x="14" y="3" width="7" height="7" rx="1.5" />
          <rect x="3" y="14" width="7" height="7" rx="1.5" />
          <rect x="14" y="14" width="7" height="7" rx="1.5" />
        </svg>
        <span>其他界面</span>
        <svg
          class="chev"
          width="9"
          height="9"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2.6"
        >
          <path d="M6 9l6 6 6-6" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </div>
      <div class="nav-pop" :class="{ open: navOpen }" @click.stop>
        <div class="h">界面导航</div>
        <div
          v-for="module in props.modules"
          :key="module.key"
          class="nav-it"
          :class="{ now: module.key === props.activeKey }"
          @click="selectModule(module.key)"
        >
          <svg
            width="11"
            height="11"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <path
              d="M3 12h4l3-7 4 14 3-7h4"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
          {{ module.title }}
          <span v-if="module.key === props.activeKey" class="k">当前</span>
        </div>
        <div
          v-for="item in PLANNED_ITEMS"
          :key="item.title"
          class="nav-it disabled"
          aria-disabled="true"
        >
          <svg
            v-if="item.icon === 'trend'"
            width="11"
            height="11"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <path d="M4 19V5m0 14h16" stroke-linecap="round" />
            <path d="M7 15l4-5 3 3 5-7" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <svg
            v-else-if="item.icon === 'alarm'"
            width="11"
            height="11"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <path d="M12 3l9 16H3L12 3z" stroke-linejoin="round" />
            <path d="M12 9v4m0 3h.01" stroke-linecap="round" />
          </svg>
          <svg
            v-else-if="item.icon === 'config'"
            width="11"
            height="11"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <circle cx="12" cy="12" r="3" />
            <path d="M12 2v3m0 14v3M2 12h3m14 0h3" stroke-linecap="round" />
          </svg>
          <svg
            v-else
            width="11"
            height="11"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <path
              d="M12 21s-8-4.5-8-11a4.5 4.5 0 018-2.8A4.5 4.5 0 0120 10c0 6.5-8 11-8 11z"
              stroke-linejoin="round"
            />
          </svg>
          {{ item.title }}
        </div>
      </div>
    </div>

    <LongPressExit v-if="kioskMode" />
  </header>
</template>

<style scoped>
.topbar {
  position: relative;
  z-index: 5;
  display: flex;
  height: 62px;
  flex: none;
  align-items: center;
  gap: 12px;
  padding: 0 18px;
  background: linear-gradient(
    180deg,
    rgb(255 255 255 / 4.5%),
    rgb(255 255 255 / 1.2%)
  );
  border-bottom: 1px solid rgb(255 255 255 / 6%);
}

.logo {
  display: grid;
  width: 34px;
  height: 34px;
  flex: none;
  background: linear-gradient(150deg, #ff9a4d, #f2620f);
  border-radius: 11px;
  box-shadow:
    0 6px 18px -6px rgb(255 122 26 / 85%),
    inset 0 1px 0 rgb(255 255 255 / 35%);
  place-items: center;
}

.brand {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-right: auto;
}

.brand h1 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.6px;
}

.brand span {
  color: var(--txt3);
  font-size: 8.5px;
  letter-spacing: 1.6px;
}

.status {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #c8e9da;
  font-size: 11px;
}

.status .dot {
  width: 7px;
  height: 7px;
  background: var(--green);
  border-radius: 50%;
  box-shadow:
    0 0 0 3px rgb(52 211 153 / 16%),
    0 0 10px rgb(52 211 153 / 90%);
  animation: kiosk-pulse 2s infinite;
}

.status.is-offline,
.status.is-error {
  color: #e7b8b8;
}

.status.is-offline .dot,
.status.is-error .dot {
  background: #ff8a8a;
  box-shadow:
    0 0 0 3px rgb(255 138 138 / 16%),
    0 0 10px rgb(255 138 138 / 90%);
  animation: none;
}

.status.is-connecting,
.status.is-empty {
  color: var(--txt2);
}

.status.is-connecting .dot,
.status.is-empty .dot {
  background: #8e9aa6;
  box-shadow: 0 0 0 3px rgb(142 154 166 / 16%);
  animation: none;
}

.clock {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 1px;
  padding: 0 14px;
  border-right: 1px solid rgb(255 255 255 / 8%);
  border-left: 1px solid rgb(255 255 255 / 8%);
}

.clock b {
  font-size: 16px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.8px;
}

.clock i {
  color: var(--txt3);
  font-size: 8.5px;
  font-style: normal;
  letter-spacing: 0.6px;
}

.nav-wrap {
  position: relative;
}

.nav-btn {
  position: relative;
  display: flex;
  height: 28px;
  align-items: center;
  padding: 0 9px 0 11px;
  color: #d3deea;
  font-size: 11px;
  cursor: pointer;
  user-select: none;
  background: rgb(255 255 255 / 4.5%);
  border: 1px solid rgb(255 255 255 / 10%);
  border-radius: 9px;
  gap: 7px;
  transition: 0.18s;
}

.nav-btn:hover {
  color: #fff;
  background: rgb(52 211 153 / 12%);
  border-color: rgb(52 211 153 / 45%);
}

.nav-btn .chev {
  opacity: 0.7;
  transition: 0.2s;
}

.nav-btn.on {
  background: rgb(52 211 153 / 14%);
  border-color: rgb(52 211 153 / 60%);
}

.nav-btn.on .chev {
  transform: rotate(180deg);
}

.nav-pop {
  position: absolute;
  top: 36px;
  right: 0;
  z-index: 40;
  display: none;
  width: 176px;
  padding: 6px;
  background: rgb(14 19 25 / 97%);
  border: 1px solid rgb(255 255 255 / 10%);
  border-radius: 12px;
  box-shadow:
    0 24px 48px -18px rgb(0 0 0 / 95%),
    inset 0 1px 0 rgb(255 255 255 / 6%);
}

.nav-pop.open {
  display: block;
  animation: kiosk-fade 0.16s ease-out;
}

.nav-pop .h {
  padding: 4px 8px 6px;
  color: #5e6b78;
  font-size: 8.5px;
  letter-spacing: 1.2px;
}

.nav-it {
  display: flex;
  align-items: center;
  padding: 7px 8px;
  color: #c3cfdd;
  font-size: 11.5px;
  cursor: pointer;
  border-radius: 8px;
  gap: 8px;
}

.nav-it:hover {
  color: #fff;
  background: rgb(255 255 255 / 6%);
}

.nav-it.now {
  color: #b9ffe2;
  background: rgb(52 211 153 / 14%);
}

.nav-it.disabled {
  color: #6d7a88;
  cursor: default;
}

.nav-it.disabled:hover {
  color: #6d7a88;
  background: transparent;
}

.nav-it .k {
  margin-left: auto;
  color: #5e6b78;
  font-size: 9px;
}

/* 顶栏内的长按退出按钮缩小到与导航按钮同高 */
.topbar :deep(.exit-button.exit-button) {
  width: 28px;
  height: 28px;
  flex-basis: 28px;
}

.topbar :deep(.exit-button.exit-button .progress-ring) {
  width: 26px;
  height: 26px;
}

.topbar :deep(.exit-button.exit-button .power-icon) {
  width: 14px;
  height: 14px;
}
</style>
