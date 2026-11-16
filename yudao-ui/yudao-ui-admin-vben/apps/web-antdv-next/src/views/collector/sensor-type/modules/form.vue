<script lang="ts" setup>
import type { CollectorApi } from '#/api/collector';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'antdv-next';

import { useVbenForm } from '#/adapter/form';
import {
  createSensorType,
  getSensorType,
  updateSensorType,
} from '#/api/collector';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<CollectorApi.SensorType>();
const getTitle = computed(() => {
  return formData.value?.id ? '编辑传感器类型' : '新增传感器类型';
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 100,
  },
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
});

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    modalApi.lock();
    const data = (await formApi.getValues()) as CollectorApi.SensorType;
    try {
      await (formData.value?.id
        ? updateSensorType(data)
        : createSensorType(data));
      await modalApi.close();
      emit('success');
      message.success('操作成功');
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      formData.value = undefined;
      return;
    }
    const data = modalApi.getData() as CollectorApi.SensorType;
    if (!data?.id) {
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getSensorType(data.id);
      await formApi.setValues(formData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal class="w-[620px]" :title="getTitle">
    <Form class="mx-4" />
  </Modal>
</template>
