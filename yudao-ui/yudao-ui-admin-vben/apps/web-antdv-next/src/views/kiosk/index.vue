<script lang="ts" setup>
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAccessStore } from '@vben/stores';

import { getAccessibleKioskModules } from './registry';
import KioskShell from './shell/kiosk-shell.vue';

defineOptions({ name: 'KioskIndex' });

const route = useRoute();
const router = useRouter();
const accessStore = useAccessStore();

const activeKey = ref<string>();

const accessibleModules = computed(() =>
  getAccessibleKioskModules(accessStore.accessCodes),
);
const activeModule = computed(() =>
  accessibleModules.value.find((module) => module.key === activeKey.value),
);
const kioskMode = computed(() => route.query.kiosk === '1');

function requestedModuleKey() {
  const requested = route.query.module;
  return typeof requested === 'string' ? requested : undefined;
}

/** 未授权或未知模块回退到首个可访问模块，同时保留 kiosk 等其他 query。 */
async function resolveRequestedModule() {
  const modules = accessibleModules.value;
  if (modules.length === 0) {
    activeKey.value = undefined;
    return;
  }
  const requested = requestedModuleKey();
  const target =
    modules.find((module) => module.key === requested) ?? modules[0]!;
  activeKey.value = target.key;
  if (requested !== target.key) {
    await router.replace({
      query: { ...route.query, module: target.key },
    });
  }
}

function selectModule(key: string) {
  if (key === activeKey.value) return;
  if (!accessibleModules.value.some((module) => module.key === key)) return;
  void router.push({ query: { ...route.query, module: key } });
}

watch(
  [() => route.query.module, () => accessStore.accessCodes],
  () => void resolveRequestedModule(),
  { deep: true, immediate: true },
);
</script>

<template>
  <KioskShell>
    <component
      :is="activeModule.component"
      v-if="activeModule"
      :key="activeModule.key"
      :active-key="activeKey"
      :kiosk-mode="kioskMode"
      :modules="accessibleModules"
      @select="selectModule"
    />
    <section v-else class="forbidden" role="alert">
      <strong>403</strong>
      <h2>当前账号没有可用的 Kiosk 模块</h2>
      <p>请联系管理员授予对应模块所需的只读权限。</p>
    </section>
  </KioskShell>
</template>

<style scoped>
.forbidden {
  display: grid;
  width: 100%;
  height: 100%;
  align-content: center;
  justify-items: center;
  padding: 32px;
  color: #a9bad0;
  text-align: center;
}

.forbidden strong {
  color: #fda4af;
  font-size: 56px;
}

.forbidden h2 {
  margin: 12px 0 4px;
  color: #e5edf8;
  font-size: 20px;
}

.forbidden p {
  margin: 8px 0;
}
</style>
