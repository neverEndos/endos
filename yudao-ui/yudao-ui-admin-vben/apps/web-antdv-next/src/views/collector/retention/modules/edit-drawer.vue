<script lang="ts" setup>
import type { CollectorRetentionApi } from '#/api/collector/retention';

import { computed, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Alert, message, Tag } from 'antdv-next';

import { useVbenForm } from '#/adapter/form';
import { updateRetentionPolicy } from '#/api/collector/retention';

import {
  getRetentionActionConfig,
  isRawDataClass,
  usePolicyFormSchema,
} from '../data';

const emit = defineEmits(['success']);
const policy = ref<CollectorRetentionApi.RetentionPolicy>();
const shortening = ref(false);

const actionConfig = computed(() =>
  getRetentionActionConfig(policy.value?.action),
);

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 120,
  },
  layout: 'horizontal',
  schema: usePolicyFormSchema(),
  showDefaultActions: false,
  handleValuesChange: (values) => {
    const original = policy.value?.retentionDays;
    const next = values.retentionDays;
    // 仅在实际缩短且不是切到永久保留时提示，避免误报
    shortening.value =
      typeof next === 'number' &&
      next > 0 &&
      original !== undefined &&
      next < original;
  },
});

const [Drawer, drawerApi] = useVbenDrawer({
  title: '保留策略',
  class: '!w-[620px]',
  confirmText: '保存',
  async onConfirm() {
    if (!policy.value?.id) {
      return;
    }
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    const values = await formApi.getValues();
    const payload: CollectorRetentionApi.RetentionPolicyUpdate = {
      id: policy.value.id,
      retentionDays: values.retentionDays,
      enabled: values.enabled ? 1 : 0,
      description: values.description?.trim() || undefined,
    };
    if (isRawDataClass(policy.value.dataClass)) {
      payload.partitionLeadDays = values.partitionLeadDays;
    }
    drawerApi.lock();
    try {
      // 仅保存策略，不触发任何 DELETE / DROP
      await updateRetentionPolicy(payload);
      await drawerApi.close();
      emit('success');
      message.success('已保存，下次定时或手动清理时生效');
    } finally {
      drawerApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      policy.value = undefined;
      shortening.value = false;
      return;
    }
    const data = drawerApi.getData() as
      | CollectorRetentionApi.RetentionPolicy
      | undefined;
    if (!data?.id) {
      return;
    }
    policy.value = data;
    drawerApi.setState({ title: `保留策略 - ${data.displayName}` });
    await formApi.setValues({
      dataClass: data.dataClass,
      retentionDays: data.retentionDays,
      partitionLeadDays: data.partitionLeadDays ?? 0,
      enabled: data.enabled === 1,
      description: data.description ?? '',
    });
    shortening.value = false;
  },
});
</script>

<template>
  <Drawer>
    <div class="mx-4 flex flex-col gap-3">
      <div class="rounded border p-3 text-sm">
        <div class="flex flex-wrap items-center gap-2">
          <span class="text-gray-500">数据类型</span>
          <span class="font-medium">{{ policy?.displayName }}</span>
          <span class="text-xs text-gray-500">{{ policy?.dataClass }}</span>
        </div>
        <div class="mt-2 flex flex-wrap items-center gap-2">
          <span class="text-gray-500">清理方式</span>
          <Tag :color="actionConfig.color">{{ actionConfig.text }}</Tag>
          <span class="text-xs text-gray-500">{{ policy?.action }}</span>
        </div>
        <div class="mt-2 text-xs text-gray-500">
          数据类型与清理方式由系统固定，不可修改。
        </div>
      </div>

      <Alert
        v-if="shortening"
        type="warning"
        show-icon
        message="缩短保留期后，下次清理将删除更多数据"
        description="保存只更新策略，不会立即执行清理；新值将在下次定时或手动清理时生效。"
      />

      <Form />
    </div>
  </Drawer>
</template>
