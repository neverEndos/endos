<script lang="ts" setup>
import type { KioskModuleDefinition } from '../../registry';

import { provide } from 'vue';

import AnalysisPanel from './components/analysis-panel.vue';
import MonitorTopbar from './components/monitor-topbar.vue';
import TelemetryPanel from './components/telemetry-panel.vue';
import WaveChart from './components/wave-chart.vue';
import { monitorStoreKey, useMonitorStore } from './use-monitor';

defineOptions({ name: 'KioskMonitor' });

defineProps<{
  activeKey?: string;
  kioskMode: boolean;
  modules: KioskModuleDefinition[];
}>();

const emit = defineEmits<{
  select: [key: string];
}>();

const store = useMonitorStore();
provide(monitorStoreKey, store);
</script>

<template>
  <div class="monitor-page">
    <MonitorTopbar
      :active-key="activeKey"
      :kiosk-mode="kioskMode"
      :modules="modules"
      @select="emit('select', $event)"
    />
    <main class="monitor-main">
      <TelemetryPanel />
      <div class="monitor-bottom">
        <WaveChart />
        <AnalysisPanel />
      </div>
    </main>
  </div>
</template>

<style scoped>
.monitor-page {
  display: flex;
  width: 100%;
  height: 100%;
  flex-direction: column;
}

.monitor-main {
  position: relative;
  z-index: 2;
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 12px;
  padding: 12px 16px 14px;
}

.monitor-bottom {
  display: grid;
  min-height: 0;
  flex: 1;
  gap: 12px;
  grid-template-columns: 660px 1fr;
}
</style>
