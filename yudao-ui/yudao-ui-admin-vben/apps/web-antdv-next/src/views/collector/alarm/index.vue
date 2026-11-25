<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CollectorApi } from '#/api/collector';

import { computed } from 'vue';

import { useAccess } from '@vben/access';
import { Page, useVbenModal } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { App, Modal, Tag } from 'antdv-next';

import { TableAction, useVbenVxeGrid } from '#/adapter/vxe-table';
import { acknowledgeAlarm, getAlarmPage } from '#/api/collector';

import { resolveApiErrorMessage } from '../components/utils';
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
const { message } = App.useApp();

/** 查看时刻趋势复用趋势接口权限；无权限时不渲染操作列，避免点击后才 403 */
const hasTrendAccess = computed(() =>
  hasAccessByCodes(['collector:trend:query']),
);
/** 确认按钮独立权限；有任一操作权限时才渲染操作列 */
const hasAckAccess = computed(() =>
  hasAccessByCodes(['collector:alarm:acknowledge']),
);
const showActionColumn = computed(
  () => hasTrendAccess.value || hasAckAccess.value,
);

const [TrendModal, trendModalApi] = useVbenModal({
  connectedComponent: AlarmTrendDialog,
  destroyOnClose: true,
});

/** 以当前报警行打开时刻趋势弹窗 */
function handleViewTrend(row: CollectorApi.Alarm) {
  trendModalApi.setData(row).open();
}

/** 仅活动且非恢复事件可确认；二次确认后调用确认接口并刷新列表 */
function handleAcknowledge(row: CollectorApi.Alarm) {
  Modal.confirm({
    title: '确认该报警？',
    content:
      '确认后状态将标记为已确认，但不会清除报警；数值恢复正常后仍由采集器自动恢复。',
    okText: '确认报警',
    cancelText: '取消',
    onOk: async () => {
      try {
        await acknowledgeAlarm(row.id);
        message.success('报警已确认');
        await gridApi.query();
      } catch (error) {
        message.error(`确认失败：${resolveApiErrorMessage(error)}`);
        throw error;
      }
    },
  });
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
  },
  gridOptions: {
    columns: useGridColumns(showActionColumn.value),
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
              label: '确认',
              type: 'link',
              auth: ['collector:alarm:acknowledge'],
              ifShow: () =>
                row.status === 0 && row.eventType !== 'cleared',
              onClick: handleAcknowledge.bind(null, row),
            },
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
