<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CollectorApi } from '#/api/collector';

import { ref } from 'vue';

import { Page, useVbenDrawer, useVbenModal } from '@vben/common-ui';

import { Tag, message } from 'antdv-next';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteSensorTemplate,
  getSensorTemplatePage,
  getSensorTypeSimpleList,
} from '#/api/collector';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';
import ThresholdPreview from './modules/threshold-preview.vue';

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [ThresholdPreviewModal, thresholdPreviewApi] = useVbenModal({
  connectedComponent: ThresholdPreview,
  destroyOnClose: true,
});

const sensorTypeNameMap = ref<Record<number, string>>({});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 新增参数模板 */
function handleCreate() {
  formDrawerApi.setData(null).open();
}

/** 编辑参数模板 */
function handleEdit(row: CollectorApi.SensorTemplate) {
  formDrawerApi.setData(row).open();
}

/** 预览四档阈值 */
function handlePreviewThreshold(row: CollectorApi.SensorTemplate) {
  thresholdPreviewApi.setData(row).open();
}

/** 删除参数模板 */
async function handleDelete(row: CollectorApi.SensorTemplate) {
  const hideLoading = message.loading({
    content: `正在删除参数模板【${row.channelName}】...`,
    duration: 0,
  });
  try {
    await deleteSensorTemplate(row.id!);
    message.success(`删除参数模板【${row.channelName}】成功`);
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
      (id?: number) => (id ? sensorTypeNameMap.value[id] || '-' : '-'),
    ),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          return await getSensorTemplatePage({
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
  } as VxeTableGridOptions<CollectorApi.SensorTemplate>,
});

/** 加载传感器类型名称映射 */
async function initSensorTypeNameMap() {
  try {
    const list = await getSensorTypeSimpleList();
    sensorTypeNameMap.value = Object.fromEntries(
      list.map((item) => [item.id, item.typeName]),
    );
    gridApi.query();
  } catch (error) {
    console.error('加载传感器类型失败', error);
  }
}

initSensorTypeNameMap();
</script>

<template>
  <Page auto-content-height>
    <FormDrawer @success="handleRefresh" />
    <ThresholdPreviewModal />
    <Grid table-title="传感器参数模板列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新增参数模板',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['collector:sensor-template:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #valueKind="{ row }">
        <Tag :color="row.valueKind === 'enum' ? 'orange' : 'blue'">
          {{ row.valueKind === 'enum' ? '枚举' : '数值' }}
        </Tag>
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '预览阈值',
              type: 'link',
              auth: ['collector:sensor-template:query'],
              onClick: handlePreviewThreshold.bind(null, row),
            },
            {
              label: '编辑',
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['collector:sensor-template:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '删除',
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['collector:sensor-template:delete'],
              popConfirm: {
                title: `确认删除参数模板【${row.channelName}】吗？`,
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
