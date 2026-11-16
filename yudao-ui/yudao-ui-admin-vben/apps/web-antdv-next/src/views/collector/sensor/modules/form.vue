<script lang="ts" setup>
import type { CollectorApi } from '#/api/collector';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Button, Table, Tag, message } from 'antdv-next';

import { useVbenForm } from '#/adapter/form';
import {
  createSensor,
  generateChannelPreview,
  getSensor,
  updateSensor,
} from '#/api/collector';

import {
  formatJson,
  generateStatusColor,
  parseJsonObject,
  resolveGenerateStatus,
} from '../../shared';
import { useFormSchema } from '../data';

const emit = defineEmits(['success']);
const formData = ref<CollectorApi.Sensor>();
const previewRows = ref<CollectorApi.GenerateChannelPreview[]>([]);
const previewLoading = ref(false);
const getTitle = computed(() => {
  return formData.value?.id ? '编辑采集传感器' : '新增采集传感器';
});

const previewSummary = computed(() => {
  const summary = { INVALID: 0, NEW: 0, SKIP: 0 };
  for (const row of previewRows.value) {
    summary[resolveGenerateStatus(row)]++;
  }
  return summary;
});

const previewColumns = [
  { dataIndex: 'channelCode', key: 'channelCode', title: '通道编码', width: 150 },
  { dataIndex: 'channelName', key: 'channelName', title: '通道名称' },
  { dataIndex: 'valueKind', key: 'valueKind', title: '值类别', width: 90 },
  { dataIndex: 'unit', key: 'unit', title: '单位', width: 80 },
  { key: 'status', title: '状态', width: 90 },
];

const [Form, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
    formItemClass: 'col-span-2',
    labelWidth: 120,
  },
  layout: 'horizontal',
  schema: useFormSchema(),
  showDefaultActions: false,
});

/** 预览按传感器类型模板生成的通道 */
async function handlePreview() {
  const values = await formApi.getValues();
  if (!values.sensorTypeId) {
    message.warning('请先选择传感器类型');
    return;
  }
  previewLoading.value = true;
  try {
    previewRows.value = await generateChannelPreview(values.sensorTypeId);
  } finally {
    previewLoading.value = false;
  }
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) {
      return;
    }
    const values = await formApi.getValues();
    let connectionConfig: Record<string, any>;
    try {
      connectionConfig = parseJsonObject(values.connectionConfig);
    } catch (error) {
      message.error(`连接参数（JSON）不合法：${(error as Error).message}`);
      return;
    }
    const payload = {
      ...values,
      connectionConfig,
    } as CollectorApi.SensorSave;
    modalApi.lock();
    try {
      if (formData.value?.id) {
        delete payload.autoGenerateChannels;
        await updateSensor(payload);
      } else {
        await createSensor(payload);
      }
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
      previewRows.value = [];
      return;
    }
    previewRows.value = [];
    const data = modalApi.getData() as CollectorApi.Sensor;
    if (!data?.id) {
      return;
    }
    modalApi.lock();
    try {
      formData.value = await getSensor(data.id);
      await formApi.setValues({
        ...formData.value,
        connectionConfig: formatJson(formData.value.connectionConfig),
      });
    } finally {
      modalApi.unlock();
    }
  },
});
</script>

<template>
  <Modal class="w-[760px]" :title="getTitle">
    <Form class="mx-4" />
    <div v-if="!formData?.id" class="mx-4 mb-2 rounded border p-3">
      <div class="mb-2 flex items-center gap-3">
        <Button
          v-access:code="['collector:sensor:create']"
          :loading="previewLoading"
          type="dashed"
          @click="handlePreview"
        >
          预览生成通道
        </Button>
        <span v-if="previewRows.length" class="text-sm">
          NEW
          <Tag color="success">{{ previewSummary.NEW }}</Tag>
          SKIP
          <Tag color="warning">{{ previewSummary.SKIP }}</Tag>
          INVALID
          <Tag color="error">{{ previewSummary.INVALID }}</Tag>
        </span>
      </div>
      <Table
        v-if="previewRows.length"
        :columns="previewColumns"
        :data-source="previewRows"
        :pagination="false"
        row-key="sensorTemplateId"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'valueKind'">
            {{ record.valueKind === 'enum' ? '枚举' : '数值' }}
          </template>
          <template v-else-if="column.key === 'status'">
            <Tag :color="generateStatusColor(resolveGenerateStatus(record))">
              {{ resolveGenerateStatus(record) }}
            </Tag>
          </template>
        </template>
      </Table>
      <div v-else class="text-xs text-gray-500">
        选择传感器类型后，可预览按该类型参数模板将生成的通道。
      </div>
    </div>
  </Modal>
</template>
