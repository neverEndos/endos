<script lang="ts" setup>
import type { CollectorApi } from '#/api/collector';

import { ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Alert, message } from 'antdv-next';

import { useVbenForm } from '#/adapter/form';
import { getChannel, updateChannel } from '#/api/collector';

import { formatJson, parseJsonObject } from '../../shared';
import { useChannelFormSchema } from '../data';

const emit = defineEmits(['success']);
const channel = ref<CollectorApi.Channel>();

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 110,
  },
  layout: 'horizontal',
  schema: useChannelFormSchema(),
  showDefaultActions: false,
});

const [Drawer, drawerApi] = useVbenDrawer({
  title: '通道通用编辑',
  footer: true,
  class: '!w-[680px]',
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    const values = await formApi.getValues();
    let channelConfig: Record<string, any>;
    try {
      channelConfig = parseJsonObject(values.channelConfig);
    } catch (error) {
      message.error(`通道参数（JSON）不合法：${(error as Error).message}`);
      return;
    }
    const payload: CollectorApi.ChannelUpdate = {
      id: channel.value!.id!,
      channelName: values.channelName.trim(),
      enabled: values.enabled,
      unit: values.unit,
      icon: values.icon,
      decimalPlaces: values.decimalPlaces,
      pollIntervalMs: values.pollIntervalMs,
      description: values.description,
      sort: values.sort,
      channelConfig,
    };
    drawerApi.lock();
    try {
      await updateChannel(payload);
      await drawerApi.close();
      emit('success');
      message.success('通道保存成功');
    } finally {
      drawerApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      channel.value = undefined;
      return;
    }
    const data = drawerApi.getData() as CollectorApi.Channel;
    if (!data?.id) {
      return;
    }
    drawerApi.lock();
    try {
      channel.value = await getChannel(data.id);
      drawerApi.setState({
        title: `通道通用编辑 - ${channel.value.channelName}`,
      });
      await formApi.setValues({
        ...channel.value,
        channelConfig: formatJson(channel.value.channelConfig),
      });
    } finally {
      drawerApi.unlock();
    }
  },
});
</script>

<template>
  <Drawer>
    <div class="mx-4 flex flex-col gap-3">
      <Alert
        message="变化死区与报警死区请在“阈值配置”中维护；归属、编码和值类别不可修改。"
        show-icon
        type="info"
      />
      <Form />
    </div>
  </Drawer>
</template>
