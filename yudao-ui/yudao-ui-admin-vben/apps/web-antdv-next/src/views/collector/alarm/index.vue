<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CollectorApi } from '#/api/collector';

import { Page } from '@vben/common-ui';

import { Tag } from 'antdv-next';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getAlarmPage } from '#/api/collector';

import {
  getAlarmEventTypeConfig,
  getAlarmLevelConfig,
  useGridColumns,
  useGridFormSchema,
} from './data';

const [Grid] = useVbenVxeGrid({
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
          return await getAlarmPage({
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
  } as VxeTableGridOptions<CollectorApi.Alarm>,
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="报警记录">
      <template #level="{ row }">
        <Tag :color="getAlarmLevelConfig(row.level).color">
          {{ getAlarmLevelConfig(row.level).text }}
        </Tag>
      </template>
      <template #eventType="{ row }">
        <Tag :color="getAlarmEventTypeConfig(row.eventType).color">
          {{ getAlarmEventTypeConfig(row.eventType).text }}
        </Tag>
      </template>
    </Grid>
  </Page>
</template>
