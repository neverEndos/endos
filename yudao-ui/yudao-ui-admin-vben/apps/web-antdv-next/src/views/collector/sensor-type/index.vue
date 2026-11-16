<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CollectorApi } from '#/api/collector';

import { Page, useVbenModal } from '@vben/common-ui';

import { message } from 'antdv-next';

import { ACTION_ICON, TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteSensorType,
  getSensorPage,
  getSensorTemplatePage,
  getSensorTypePage,
} from '#/api/collector';

import { useGridColumns, useGridFormSchema } from './data';
import Form from './modules/form.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

/** 刷新表格 */
function handleRefresh() {
  gridApi.query();
}

/** 新增传感器类型 */
function handleCreate() {
  formModalApi.setData(null).open();
}

/** 编辑传感器类型 */
function handleEdit(row: CollectorApi.SensorType) {
  formModalApi.setData(row).open();
}

/** 删除传感器类型 */
async function handleDelete(row: CollectorApi.SensorType) {
  const hideLoading = message.loading({
    content: `正在删除传感器类型【${row.typeName}】...`,
    duration: 0,
  });
  try {
    await deleteSensorType(row.id!);
    message.success(`删除传感器类型【${row.typeName}】成功`);
    handleRefresh();
  } finally {
    hideLoading();
  }
}

/** 为类型补充模板数、传感器数（后端分页接口不返回统计） */
async function fillTypeCounts(list: CollectorApi.SensorType[]) {
  return await Promise.all(
    list.map(async (item) => {
      const [templatePage, sensorPage] = await Promise.all([
        getSensorTemplatePage({
          pageNo: 1,
          pageSize: 1,
          sensorTypeId: item.id,
        }),
        getSensorPage({
          pageNo: 1,
          pageSize: 1,
          sensorTypeId: item.id,
        }),
      ]);
      return {
        ...item,
        templateCount: templatePage.total,
        sensorCount: sensorPage.total,
      };
    }),
  );
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          const pageResult = await getSensorTypePage({
            pageNo: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
          });
          return {
            list: await fillTypeCounts(pageResult.list),
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
  } as VxeTableGridOptions<CollectorApi.SensorType>,
});
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="handleRefresh" />
    <Grid table-title="传感器类型列表">
      <template #toolbar-tools>
        <TableAction
          :actions="[
            {
              label: '新增传感器类型',
              type: 'primary',
              icon: ACTION_ICON.ADD,
              auth: ['collector:sensor-type:create'],
              onClick: handleCreate,
            },
          ]"
        />
      </template>
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '编辑',
              type: 'link',
              icon: ACTION_ICON.EDIT,
              auth: ['collector:sensor-type:update'],
              onClick: handleEdit.bind(null, row),
            },
            {
              label: '删除',
              type: 'link',
              danger: true,
              icon: ACTION_ICON.DELETE,
              auth: ['collector:sensor-type:delete'],
              popConfirm: {
                title: `确认删除传感器类型【${row.typeName}】吗？`,
                confirm: handleDelete.bind(null, row),
              },
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
