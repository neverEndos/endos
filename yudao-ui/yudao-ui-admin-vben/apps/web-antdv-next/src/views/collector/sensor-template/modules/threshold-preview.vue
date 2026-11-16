<script lang="ts" setup>
import type { CollectorApi } from '#/api/collector';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'antdv-next';

import { previewThreshold } from '#/api/collector';

import { DEFAULT_THRESHOLD_RATIOS } from '../../shared';

const template = ref<CollectorApi.SensorTemplate>();
const preview = ref<CollectorApi.ThresholdPreview>();
const ratios = ref({
  lowError: DEFAULT_THRESHOLD_RATIOS.lowError,
  lowWarning: DEFAULT_THRESHOLD_RATIOS.lowWarning,
  highWarning: DEFAULT_THRESHOLD_RATIOS.highWarning,
  highError: DEFAULT_THRESHOLD_RATIOS.highError,
});

const [Modal, modalApi] = useVbenModal({
  title: '四档阈值预览',
  showConfirmButton: false,
  cancelText: '关闭',
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      template.value = undefined;
      preview.value = undefined;
      return;
    }
    template.value = modalApi.getData() as CollectorApi.SensorTemplate;
    ratios.value = {
      lowError:
        template.value.thresholdLowErrorRatio ?? DEFAULT_THRESHOLD_RATIOS.lowError,
      lowWarning:
        template.value.thresholdLowWarningRatio ??
        DEFAULT_THRESHOLD_RATIOS.lowWarning,
      highWarning:
        template.value.thresholdHighWarningRatio ??
        DEFAULT_THRESHOLD_RATIOS.highWarning,
      highError:
        template.value.thresholdHighErrorRatio ??
        DEFAULT_THRESHOLD_RATIOS.highError,
    };
    modalApi.lock();
    try {
      preview.value = await previewThreshold({
        valueMax: template.value.valueMax,
        valueMin: template.value.valueMin,
        thresholdHighErrorRatio: ratios.value.highError,
        thresholdHighWarningRatio: ratios.value.highWarning,
        thresholdLowErrorRatio: ratios.value.lowError,
        thresholdLowWarningRatio: ratios.value.lowWarning,
      });
    } catch {
      message.error('阈值预览失败，请检查量程与比例');
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal class="w-[560px]">
    <div v-if="template" class="flex flex-col gap-3 px-4 pb-2">
      <div class="text-sm text-gray-500">
        {{ template.channelName }}（量程 {{ template.valueMin ?? '-' }} ~
        {{ template.valueMax ?? '-' }} {{ template.unit || '' }}）
      </div>
      <div class="text-sm text-gray-500">
        比例：
        {{ ratios.lowError }} / {{ ratios.lowWarning }} /
        {{ ratios.highWarning }} / {{ ratios.highError }}
      </div>
      <div class="grid grid-cols-2 gap-3">
        <div class="rounded border p-3">
          <div class="text-xs text-gray-500">低低限</div>
          <div class="text-lg">{{ preview?.thresholdLowError ?? '-' }}</div>
        </div>
        <div class="rounded border p-3">
          <div class="text-xs text-gray-500">低限</div>
          <div class="text-lg">{{ preview?.thresholdLowWarning ?? '-' }}</div>
        </div>
        <div class="rounded border p-3">
          <div class="text-xs text-gray-500">高限</div>
          <div class="text-lg">{{ preview?.thresholdHighWarning ?? '-' }}</div>
        </div>
        <div class="rounded border p-3">
          <div class="text-xs text-gray-500">高高限</div>
          <div class="text-lg">{{ preview?.thresholdHighError ?? '-' }}</div>
        </div>
      </div>
    </div>
  </Modal>
</template>
