<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CollectorApi } from '#/api/collector';

import { ref } from 'vue';

import { confirm, Page, useVbenModal } from '@vben/common-ui';

import { message } from 'antdv-next';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteSensor,
  generateChannels,
  getChannelPage,
  getDeviceSimpleList,
  getSensorPage,
  getSensorTypeSimpleList,
} from '#/api/collector';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

const deviceNameMap = ref<Record<number, string>>({});
const typeNameMap = ref<Record<number, string>>({});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 新增传感器 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑传感器 */
function handleEdit(row: CollectorApi.Sensor) {
  formModalApi.setData(row).open();
}

/** 为传感器生成通道 */
async function handleGenerateChannels(row: CollectorApi.Sensor) {
  await confirm(
    `确认按传感器类型模板为【${row.sensorName}】生成通道吗？已存在的通道会跳过。`,
  );
  const hideLoading = message.loading({
    content: '正在生成通道...',
    duration: 0,
  });
  try {
    const result = await generateChannels(row.id!);
    message.success(
      `生成完成：新建 ${result.createdCount} 个，跳过 ${result.skippedCount} 个`,
    );
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 删除传感器 */
async function handleDelete(row: CollectorApi.Sensor) {
  const hideLoading = message.loading({
    content: `正在删除传感器【${row.sensorName}】...`,
    duration: 0,
  });
  try {
    await deleteSensor(row.id!);
    message.success(`删除传感器【${row.sensorName}】成功`);
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 为传感器补充通道数（后端分页接口不返回统计） */
async function fillChannelCounts(list: CollectorApi.Sensor[]) {
  return await Promise.all(
    list.map(async (item) => {
      const channelPage = await getChannelPage({
        pageNo: 1,
        pageSize: 1,
        sensorId: item.id,
      });
      return {
        ...item,
        channelCount: channelPage.total,
      };
    }),
  );
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(
      (id?: number) => (id ? deviceNameMap.value[id] || '-' : '-'),
      (id?: number) => (id ? typeNameMap.value[id] || '-' : '-'),
    ),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          const pageResult = await getSensorPage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
          return {
            list: await fillChannelCounts(pageResult.list),
            total: pageResult.total,
          };
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
  } as VxeTableGridOptions<CollectorApi.Sensor>,
});

/** 加载设备与类型名称映射 */
async function initNameMaps() {
  try {
    const [devices, types] = await Promise.all([
      getDeviceSimpleList(),
      getSensorTypeSimpleList(),
    ]);
    deviceNameMap.value = Object.fromEntries(
      devices.map((item) => [item.id, item.deviceName]),
    );
    typeNameMap.value = Object.fromEntries(
      types.map((item) => [item.id, item.typeName]),
    );
    gridApi.query();
  } catch (error) {
    console.error('加载设备或传感器类型失败', error);
  }
}

initNameMaps();
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="采集传感器列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新增传感器',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['collector:sensor:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '生成通道',
              type: 'link',
              auth: ['collector:sensor:create'],
              onClick: handleGenerateChannels.bind(null, row),
            },
            {
              label: '编辑',
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['collector:sensor:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '删除',
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['collector:sensor:delete'],
              popConfirm: {
                title: `确认删除传感器【${row.sensorName}】吗？存在通道时需先删除通道。`,
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
