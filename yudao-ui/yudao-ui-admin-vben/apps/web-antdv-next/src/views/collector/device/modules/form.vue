<script lang="ts" setup>
import type { CollectorApi } from '#/api/collector';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'antdv-next';

import { useVbenForm } from '#/adapter/form';
import {
  createDevice,
  getDevice,
  updateDevice,
} from '#/api/collector';

import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<CollectorApi.Device>();
const getTitle = computed(() => {
  return formData.value?.id ? '编辑采集设备' : '新增采集设备';
});

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 110,
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
    const data = (await formApi.getValues()) as CollectorApi.Device;
    try {
      await (formData.value?.id ? updateDevice(data) : createDevice(data));
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
    const data = modalApi.getData() as CollectorApi.Device;
    if (!data?.id) {
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getDevice(data.id);
      await formApi.setValues(formData.value);
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal class="w-[640px]" :title="getTitle">
    <Form class="mx-4" />
  </Modal>
</template>
