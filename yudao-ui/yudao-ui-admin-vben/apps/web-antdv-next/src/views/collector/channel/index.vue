<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CollectorApi } from '#/api/collector';

import { ref } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';

import { Tag, message } from 'antdv-next';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteChannel,
  getChannelPage,
  getDeviceSimpleList,
  getSensorSimpleList,
} from '#/api/collector';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';
import ThresholdForm from './modules/threshold-form.vue';

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [ThresholdDrawer, thresholdDrawerApi] = useVbenDrawer({
  connectedComponent: ThresholdForm,
  destroyOnClose: true,
});

const deviceNameMap = ref<Record<number, string>>({});
const sensorNameMap = ref<Record<number, string>>({});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 打开通道通用编辑抽屉 */
function handleEdit(row: CollectorApi.Channel) {
  formDrawerApi.setData(row).open();
}

/** 打开阈值配置抽屉 */
function handleThreshold(row: CollectorApi.Channel) {
  thresholdDrawerApi.setData(row).open();
}

/** 删除通道 */
async function handleDelete(row: CollectorApi.Channel) {
  const hideLoading = message.loading({
    content: `正在删除通道【${row.channelName}】...`,
    duration: 0,
  });
  try {
    await deleteChannel(row.id!);
    message.success(`删除通道【${row.channelName}】成功`);
    handleRefresh();
  } finally {
    hideLoading();
  }
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(
      (id?: number) => (id ? deviceNameMap.value[id] || '-' : '-'),
      (id?: number) => (id ? sensorNameMap.value[id] || '-' : '-'),
    ),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getChannelPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
        },
      },
    },
    rowConfig: {
      keyField: 'id',
      isHover: true,
    },
    toolbarConfig: {
      refresh: true,
      search: true,
    },
  } as VxeTableGridOptions<CollectorApi.Channel>,
});

/** 加载设备与传感器名称映射 */
async function initNameMaps() {
  try {
    const [devices, sensors] = await Promise.all([
      getDeviceSimpleList(),
      getSensorSimpleList(),
    ]);
    deviceNameMap.value = Object.fromEntries(
      devices.map((item) => [item.id, item.deviceName]),
    );
    sensorNameMap.value = Object.fromEntries(
      sensors.map((item) => [item.id, item.sensorName]),
    );
    gridApi.query();
  } catch (error) {
    console.error('加载设备或传感器失败', error);
  }
}

initNameMaps();
</script>

<template>
  <Page auto-content-height>
    <FormDrawer @success="handleRefresh" />
    <ThresholdDrawer @success="handleRefresh" />
    <Grid table-title="采集通道列表">
      <template #valueKind="{ row }">
        <Tag :color="row.valueKind === 'enum' ? 'orange' : 'blue'">
          {{ row.valueKind === 'enum' ? '枚举' : '数值' }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '编辑',
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['collector:channel:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '阈值配置',
              type: 'link',
              auth: ['collector:channel:update'],
              onClick: handleThreshold.bind(null, row),
            },
            {
              label: '删除',
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['collector:channel:delete'],
              popConfirm: {
                title: `确认删除通道【${row.channelName}】吗？`,
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
