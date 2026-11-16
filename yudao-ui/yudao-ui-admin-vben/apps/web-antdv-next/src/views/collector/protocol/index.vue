<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CollectorApi } from '#/api/collector';

import { Page, useVbenDrawer } from '@vben/common-ui';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { getProtocolList } from '#/api/collector';

import { useGridColumns, useGridFormSchema } from './data';
import SchemaDrawer from './modules/schema-drawer.vue';

const [SchemaDrawerComp, schemaDrawerApi] = useVbenDrawer({
  connectedComponent: SchemaDrawer,
  destroyOnClose: true,
});

/** 查看协议 Schema */
function handleViewSchema(row: CollectorApi.Protocol) {
  schemaDrawerApi.setData(row).open();
}

const [Grid] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(),
    height: 'auto',
    keepSource: true,
    pagerConfig: {
      enabled: false,
    },
    proxyConfig: {
      ajax: {
        query: async (_params, formValues) => {
          const list = await getProtocolList({
            enabled: formValues.enabled,
          });
          return { list, total: list.length };
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
  } as VxeTableGridOptions<CollectorApi.Protocol>,
});
</script>

<template>
  <Page auto-content-height>
    <SchemaDrawerComp />
    <Grid table-title="采集协议列表">
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '查看 Schema',
              type: 'link',
              auth: ['collector:protocol:query'],
              onClick: handleViewSchema.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
