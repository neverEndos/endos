<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CollectorApi } from '#/api/collector';

import { Page } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { Tag } from 'antdv-next';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getAlarmPage } from '#/api/collector';

import {
  formatAlarmEntityName,
  getAlarmEventTypeConfig,
  getAlarmLevelConfig,
  getAlarmStatusConfig,
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
      <template #status="{ row }">
        <Tag :color="getAlarmStatusConfig(row.status, row.eventType).color">
          {{ getAlarmStatusConfig(row.status, row.eventType).text }}
        </Tag>
      </template>
      <template #eventType="{ row }">
        <Tag :color="getAlarmEventTypeConfig(row.eventType).color">
          {{ getAlarmEventTypeConfig(row.eventType).text }}
        </Tag>
      </template>
      <template #deviceName="{ row }">
        {{ formatAlarmEntityName(row.deviceName, '设备', row.deviceId) }}
      </template>
      <template #sensorName="{ row }">
        {{ formatAlarmEntityName(row.sensorName, '传感器', row.sensorId) }}
      </template>
      <template #channelName="{ row }">
        {{ formatAlarmEntityName(row.channelName, '通道', row.channelId) }}
      </template>
      <template #recoveryTime="{ row }">
        <span v-if="row.eventType === 'cleared'">
          {{ formatDateTime(row.clearedAt) }}
        </span>
        <span v-else>-</span>
      </template>
    </Grid>
  </Page>
</template>
