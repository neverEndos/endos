<script lang="ts" setup>
import type { CollectorApi } from '#/api/collector';

import { computed, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

const protocol = ref<CollectorApi.Protocol>();

const [Drawer, drawerApi] = useVbenDrawer({
  title: '协议 Schema',
  footer: false,
  onOpenChange(isOpen: boolean) {
    protocol.value = isOpen
      ? (drawerApi.getData() as CollectorApi.Protocol)
      : undefined;
  },
});

const connectionSchemaText = computed(() =>
  JSON.stringify(protocol.value?.connectionSchema ?? {}, null, 2),
);
const channelSchemaText = computed(() =>
  JSON.stringify(protocol.value?.channelSchema ?? {}, null, 2),
);
</script>

<template>
  <Drawer class="!w-[680px]">
    <div class="flex flex-col gap-4">
      <div class="text-sm text-gray-500">
        协议：{{ protocol?.protocolName }}（{{ protocol?.protocolCode }}）
      </div>
      <div>
        <div class="mb-1 font-medium">连接参数 Schema</div>
        <pre
          class="max-h-[300px] overflow-auto rounded bg-gray-50 p-3 text-xs"
        >{{ connectionSchemaText }}</pre>
      </div>
      <div>
        <div class="mb-1 font-medium">通道参数 Schema</div>
        <pre
          class="max-h-[300px] overflow-auto rounded bg-gray-50 p-3 text-xs"
        >{{ channelSchemaText }}</pre>
      </div>
    </div>
  </Drawer>
</template>
