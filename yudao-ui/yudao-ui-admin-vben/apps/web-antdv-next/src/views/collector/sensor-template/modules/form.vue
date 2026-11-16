<script lang="ts" setup>
import type { CollectorApi } from '#/api/collector';

import { ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { message } from 'antdv-next';

import { useVbenForm } from '#/adapter/form';
import {
  createSensorTemplate,
  getSensorTemplate,
  updateSensorTemplate,
} from '#/api/collector';

import {
  formatJson,
  parseJsonObject,
  toIntegerSwitch,
  toSwitchValue,
} from '../../shared';
import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<CollectorApi.SensorTemplate>();

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

/** 编辑时把后端 0/1 开关与 JSON 对象转换为表单控件值 */
function toFormValues(template: CollectorApi.SensorTemplate) {
  return {
    ...template,
    alarmEnabled: toSwitchValue(template.alarmEnabled),
    thresholdLowErrorEnabled: toSwitchValue(template.thresholdLowErrorEnabled),
    thresholdLowWarningEnabled: toSwitchValue(
      template.thresholdLowWarningEnabled,
    ),
    thresholdHighWarningEnabled: toSwitchValue(
      template.thresholdHighWarningEnabled,
    ),
    thresholdHighErrorEnabled: toSwitchValue(
      template.thresholdHighErrorEnabled,
    ),
    channelConfig: formatJson(template.channelConfig),
  };
}

const [Drawer, drawerApi] = useVbenDrawer({
  title: '传感器参数模板',
  footer: true,
  class: '!w-[680px]',
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    let channelConfig: Record<string, any>;
    try {
      channelConfig = parseJsonObject(
        (await formApi.getValues()).channelConfig,
      );
    } catch (error) {
      message.error(`通道参数（JSON）不合法：${(error as Error).message}`);
      return;
    }
    const values = await formApi.getValues();
    const payload = {
      ...values,
      // 量程使用全量更新语义：清空后必须显式提交 null，不能沿用旧值。
      valueMin: values.valueMin ?? null,
      valueMax: values.valueMax ?? null,
      channelConfig,
      alarmEnabled: toIntegerSwitch(values.alarmEnabled),
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
    } as CollectorApi.SensorTemplate;
    drawerApi.lock();
    try {
      await (formData.value?.id
        ? updateSensorTemplate(payload)
        : createSensorTemplate(payload));
      await drawerApi.close();
      emit('success');
      message.success('操作成功');
    } finally {
      drawerApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      formData.value = undefined;
      return;
    }
    drawerApi.setState({
      title: '传感器参数模板',
    });
    const data = drawerApi.getData() as CollectorApi.SensorTemplate;
    if (!data?.id) {
      return;
    }
    drawerApi.lock();
    try {
      formData.value = await getSensorTemplate(data.id);
      await formApi.setValues(toFormValues(formData.value));
    } finally {
      drawerApi.unlock();
    }
  },
});
</script>

<template>
  <Drawer>
    <Form class="mx-4" />
  </Drawer>
</template>
