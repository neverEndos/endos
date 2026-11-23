<script lang="ts" setup>
import type { KioskModuleDefinition } from '../registry';
import type { KioskGlobalStatus } from './types';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import LongPressExit from '../components/long-press-exit.vue';

const props = defineProps<{
  activeKey?: string;
  kioskMode: boolean;
  modules: KioskModuleDefinition[];
  status: KioskGlobalStatus;
  title: string;
}>();

const emit = defineEmits<{
  select: [key: string];
}>();

const clockNow = ref(new Date());
let clockTimer: ReturnType<typeof setInterval> | undefined;

const timeText = computed(() =>
  new Intl.DateTimeFormat('zh-CN', {
    day: '2-digit',
    hour: '2-digit',
    hour12: false,
    minute: '2-digit',
    month: '2-digit',
    second: '2-digit',
    timeZone: 'Asia/Shanghai',
  }).format(clockNow.value),
);

const connectionText = computed(() => {
  const labels = {
    connecting: '连接中',
    degraded: '部分中断',
    idle: '未连接',
    offline: '连接中断',
    online: '已连接',
  };
  return labels[props.status.connection];
});

const alarmText = computed(() => {
  if (props.status.activeAlarmCount === null) return '活动报警 --';
  if (props.status.activeAlarmCount === 0) return '无活动报警';
  const level = props.status.highestAlarmLevel === 3 ? '异常' : '预警';
  return `活动报警 ${props.status.activeAlarmCount} · 最高${level}`;
});

onMounted(() => {
  clockTimer = setInterval(() => {
    if (!document.hidden) clockNow.value = new Date();
  }, 1000);
});

onBeforeUnmount(() => {
  if (clockTimer !== undefined) clearInterval(clockTimer);
});
</script>

<template>
  <div class="kiosk-shell">
    <aside class="kiosk-navigation" aria-label="Kiosk 模块导航">
      <div class="brand" aria-label="采集器 Kiosk">
        <span class="brand-mark" aria-hidden="true">K</span>
        <span>采集终端</span>
      </div>
      <nav class="module-list">
        <button
          v-for="module in modules"
          :key="module.key"
          class="module-button"
          :class="{ active: module.key === activeKey }"
          type="button"
          :aria-current="module.key === activeKey ? 'page' : undefined"
          @click="emit('select', module.key)"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <template v-if="module.icon === 'devices'">
              <rect x="3" y="4" width="18" height="12" rx="2" />
              <path d="M8 20h8M12 16v4M7 10h2l1.5-3 3 6 1.5-3h2" />
            </template>
            <path v-else d="M5 5h14v14H5z" />
          </svg>
          <span>{{ module.title }}</span>
        </button>
      </nav>
      <div class="navigation-note">只读监视</div>
    </aside>

    <div class="kiosk-main">
      <header class="status-bar">
        <h1>{{ title }}</h1>
        <div class="global-status" aria-live="polite">
          <span class="status-item" :class="`state-${status.connection}`">
            <span class="status-dot" aria-hidden="true"></span>
            {{ connectionText }}
          </span>
          <span
            class="status-item"
            :class="{
              'has-alarm': (status.activeAlarmCount ?? 0) > 0,
              'is-stale': status.alarmDataStale,
            }"
          >
            {{ alarmText }}
            <small v-if="status.alarmDataStale">（旧数据）</small>
          </span>
          <time class="clock" :datetime="clockNow.toISOString()">
            {{ timeText }}
          </time>
          <LongPressExit v-if="kioskMode" />
        </div>
      </header>
      <main class="kiosk-content">
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped>
.kiosk-shell {
  display: grid;
  grid-template-columns: 156px minmax(0, 1fr);
  width: 100vw;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  color: #e5edf8;
  font-family:
    Inter, "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
  background: #08111f;
}

.kiosk-navigation {
  display: flex;
  min-height: 0;
  flex-direction: column;
  padding: 14px 10px;
  background: #0d1929;
  border-right: 1px solid #233149;
}

.brand {
  display: flex;
  min-height: 48px;
  align-items: center;
  gap: 10px;
  padding: 0 8px 14px;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.04em;
  border-bottom: 1px solid #233149;
}

.brand-mark {
  display: grid;
  width: 34px;
  height: 34px;
  color: #082f49;
  background: #67e8f9;
  border-radius: 9px;
  place-items: center;
}

.module-list {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  padding-top: 16px;
  overflow-y: auto;
}

.module-button {
  display: flex;
  min-height: 56px;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  color: #a9bad0;
  font-size: 15px;
  text-align: left;
  touch-action: manipulation;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 10px;
}

.module-button svg {
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  fill: none;
  stroke: currentcolor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.7;
}

.module-button.active {
  color: #ecfeff;
  background: #164e63;
  border-color: #0e7490;
}

.module-button:focus-visible,
.module-button:active {
  border-color: #67e8f9;
}

.navigation-note {
  padding: 12px 8px 0;
  color: #71839b;
  font-size: 12px;
  border-top: 1px solid #233149;
}

.kiosk-main {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
}

.status-bar {
  display: flex;
  min-height: 64px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 14px 8px 20px;
  background: #0d1929;
  border-bottom: 1px solid #233149;
}

.status-bar h1 {
  flex: 0 0 auto;
  margin: 0;
  overflow: hidden;
  font-size: 20px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.global-status {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: flex-end;
  gap: 14px;
  font-size: 13px;
}

.status-item {
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  color: #a9bad0;
  white-space: nowrap;
}

.status-item small {
  margin-left: 3px;
  color: #fbbf24;
}

.status-item.state-online {
  color: #6ee7b7;
}

.status-item.state-degraded,
.status-item.state-offline,
.status-item.has-alarm {
  color: #fbbf24;
}

.status-item.state-offline,
.status-item.has-alarm {
  color: #fda4af;
}

.status-dot {
  width: 8px;
  height: 8px;
  margin-right: 6px;
  background: currentcolor;
  border-radius: 50%;
}

.clock {
  min-height: 44px;
  align-content: center;
  color: #d7e3f4;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.kiosk-content {
  min-width: 0;
  min-height: 0;
  flex: 1;
  overflow: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
}

@media (max-width: 900px) {
  .kiosk-shell {
    grid-template-columns: 124px minmax(0, 1fr);
  }

  .kiosk-navigation {
    padding-inline: 8px;
  }

  .brand {
    gap: 7px;
    padding-inline: 4px;
    font-size: 14px;
  }

  .brand-mark {
    width: 30px;
    height: 30px;
  }

  .module-button {
    gap: 7px;
    padding-inline: 9px;
    font-size: 14px;
  }

  .status-bar {
    gap: 7px;
    padding-left: 14px;
  }

  .status-bar h1 {
    font-size: 18px;
  }

  .global-status {
    gap: 8px;
    font-size: 12px;
  }
}

@media (max-width: 640px) {
  .kiosk-shell {
    grid-template-columns: 88px minmax(0, 1fr);
  }

  .brand > span:last-child,
  .navigation-note {
    display: none;
  }

  .brand {
    justify-content: center;
  }

  .module-button {
    flex-direction: column;
    justify-content: center;
    gap: 2px;
    font-size: 12px;
  }

  .status-bar h1 {
    display: none;
  }

  .global-status {
    width: 100%;
    justify-content: space-between;
  }

  .clock {
    font-size: 11px;
  }
}
</style>
