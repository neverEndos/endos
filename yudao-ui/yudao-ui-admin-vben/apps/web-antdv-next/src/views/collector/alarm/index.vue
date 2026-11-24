<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CollectorApi } from '#/api/collector';

import { computed } from 'vue';

import { useAccess } from '@vben/access';
import { Page, useVbenModal } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { Tag } from 'antdv-next';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { getAlarmPage } from '#/api/collector';

import {
  formatAlarmEntityName,
  getAlarmEventTypeConfig,
  getAlarmLevelConfig,
  getAlarmStatusConfig,
  useGridColumns,
  useGridFormSchema,
} from './data';
import AlarmTrendDialog from './modules/alarm-trend-dialog.vue';

const { hasAccessByCodes } = useAccess();

/** 查看时刻趋势复用趋势接口权限；无权限时不渲染操作列，避免点击后才 403 */
const hasTrendAccess = computed(() =>
  hasAccessByCodes(['collector:trend:query']),
);

const [TrendModal, trendModalApi] = useVbenModal({
  connectedComponent: AlarmTrendDialog,
  destroyOnClose: true,
});

/** 以当前报警行打开时刻趋势弹窗 */
function handleViewTrend(row: CollectorApi.Alarm) {
  trendModalApi.setData(row).open();
}

const [Grid] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(hasTrendAccess.value),
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
    <TrendModal />
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
      <template #actions="{ row }">
        <TableAction
          :actions="[
            {
              label: '查看时刻趋势',
              type: 'link',
              auth: ['collector:trend:query'],
              disabled: !row.channelId,
              tooltip: row.channelId ? undefined : '该报警没有关联通道',
              onClick: handleViewTrend.bind(null, row),
            },
          ]"
        />
      </template>
    </Grid>
  </Page>
</template>
