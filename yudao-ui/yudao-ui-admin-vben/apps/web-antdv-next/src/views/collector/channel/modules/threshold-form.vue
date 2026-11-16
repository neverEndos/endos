<script lang="ts" setup>
import type { CollectorApi } from '#/api/collector';

import { computed, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Alert, Button, message } from 'antdv-next';

import { useVbenForm } from '#/adapter/form';
import {
  getChannel,
  previewThreshold,
  updateChannelThreshold,
} from '#/api/collector';

import {
  DEFAULT_THRESHOLD_RATIOS,
  toIntegerSwitch,
  toSwitchValue,
} from '../../shared';
import { useThresholdFormSchema } from '../data';

const emit = defineEmits(['success']);
const channel = ref<CollectorApi.Channel>();

const isEnumChannel = computed(() => channel.value?.valueKind === 'enum');

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 110,
  },
  layout: 'horizontal',
  schema: useThresholdFormSchema(),
  showDefaultActions: false,
});

/** 按模板默认比例自动计算四档阈值 */
async function handleAutoCalculate() {
  const values = await formApi.getValues();
  if (values.valueMin === null || values.valueMin === undefined) {
    message.warning('请先填写量程下限');
    return;
  }
  if (values.valueMax === null || values.valueMax === undefined) {
    message.warning('请先填写量程上限');
    return;
  }
  const preview = await previewThreshold({
    valueMin: values.valueMin,
    valueMax: values.valueMax,
    thresholdLowErrorRatio: DEFAULT_THRESHOLD_RATIOS.lowError,
    thresholdLowWarningRatio: DEFAULT_THRESHOLD_RATIOS.lowWarning,
    thresholdHighWarningRatio: DEFAULT_THRESHOLD_RATIOS.highWarning,
    thresholdHighErrorRatio: DEFAULT_THRESHOLD_RATIOS.highError,
  });
  await formApi.setValues({
    thresholdLowError: preview.thresholdLowError,
    thresholdLowWarning: preview.thresholdLowWarning,
    thresholdHighWarning: preview.thresholdHighWarning,
    thresholdHighError: preview.thresholdHighError,
  });
  message.success('已按默认比例 0.1 / 0.3 / 0.7 / 0.9 填充四档阈值');
}

const [Drawer, drawerApi] = useVbenDrawer({
  title: '通道阈值配置',
  footer: true,
  class: '!w-[680px]',
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    const values = await formApi.getValues();
    const payload: CollectorApi.ChannelThresholdUpdate = {
      id: channel.value!.id!,
      valueMin: values.valueMin,
      valueMax: values.valueMax,
      thresholdLowError: values.thresholdLowError,
      thresholdLowWarning: values.thresholdLowWarning,
      thresholdHighWarning: values.thresholdHighWarning,
      thresholdHighError: values.thresholdHighError,
      thresholdLowErrorEnabled: toIntegerSwitch(
        values.thresholdLowErrorEnabled,
      ),
      thresholdLowWarningEnabled: toIntegerSwitch(
        values.thresholdLowWarningEnabled,
      ),
      thresholdHighWarningEnabled: toIntegerSwitch(
        values.thresholdHighWarningEnabled,
      ),
      thresholdHighErrorEnabled: toIntegerSwitch(
        values.thresholdHighErrorEnabled,
      ),
      alarmEnabled: toIntegerSwitch(values.alarmEnabled),
      alarmDeadband: values.alarmDeadband ?? 0,
    };
    drawerApi.lock();
    try {
      await updateChannelThreshold(payload);
      await drawerApi.close();
      emit('success');
      message.success('阈值保存成功');
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
        title: `通道阈值配置 - ${channel.value.channelName}`,
      });
      await formApi.setValues({
        ...channel.value,
        thresholdLowErrorEnabled: toSwitchValue(
          channel.value.thresholdLowErrorEnabled,
        ),
        thresholdLowWarningEnabled: toSwitchValue(
          channel.value.thresholdLowWarningEnabled,
        ),
        thresholdHighWarningEnabled: toSwitchValue(
          channel.value.thresholdHighWarningEnabled,
        ),
        thresholdHighErrorEnabled: toSwitchValue(
          channel.value.thresholdHighErrorEnabled,
        ),
        alarmEnabled: toSwitchValue(channel.value.alarmEnabled),
        alarmDeadband: channel.value.alarmDeadband ?? 0,
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
        v-if="isEnumChannel"
        message="枚举通道不参与数值阈值报警，四档阈值与报警开关已置灰。"
        show-icon
        type="warning"
      />
      <div v-else>
        <Button
          v-access:code="['collector:channel:query']"
          type="dashed"
          @click="handleAutoCalculate"
        >
          按模板比例自动计算
        </Button>
        <span class="ml-2 text-xs text-gray-500">
          默认比例 0.1 / 0.3 / 0.7 / 0.9
        </span>
      </div>
      <Form />
    </div>
  </Drawer>
</template>
