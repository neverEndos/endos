<script lang="ts" setup>
import type { CollectorRetentionApi } from '#/api/collector/retention';

import { h, onMounted, ref } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';

import { Alert, Button, Card, message, Modal, Table, Tag } from 'antdv-next';

import {
  executeRetentionCleanup,
  getRetentionPolicyList,
  previewRetentionCleanup,
} from '#/api/collector/retention';

import {
  formatRetentionDays,
  formatRetentionTime,
  getLastRunResultConfig,
  getRetentionActionConfig,
  isRawDataClass,
  resolveApiErrorMessage,
  summarizePreview,
  usePolicyColumns,
} from './data';
import CleanupResultDrawer from './modules/cleanup-result-drawer.vue';
import EditDrawer from './modules/edit-drawer.vue';
import PreviewDrawer from './modules/preview-drawer.vue';

defineOptions({ name: 'CollectorRetention' });

const policies = ref<CollectorRetentionApi.RetentionPolicy[]>([]);
const loading = ref(false);
const loadError = ref<string>();
const cleanupLoading = ref(false);

const policyColumns = usePolicyColumns();

const [EditDrawerComp, editDrawerApi] = useVbenDrawer({
  connectedComponent: EditDrawer,
  destroyOnClose: true,
});

const [PreviewDrawerComp, previewDrawerApi] = useVbenDrawer({
  connectedComponent: PreviewDrawer,
  destroyOnClose: true,
});

const [CleanupDrawerComp, cleanupDrawerApi] = useVbenDrawer({
  connectedComponent: CleanupResultDrawer,
  destroyOnClose: true,
});

async function loadPolicies() {
  loading.value = true;
  loadError.value = undefined;
  try {
    policies.value = await getRetentionPolicyList();
  } catch (error) {
    // 加载失败时清空列表，不保留上一次数据造成误导
    policies.value = [];
    loadError.value = resolveApiErrorMessage(error, '加载保留策略失败');
  } finally {
    loading.value = false;
  }
}

function handleEdit(row: CollectorRetentionApi.RetentionPolicy) {
  editDrawerApi.setData(row).open();
}

function handlePreview() {
  previewDrawerApi.setData({}).open();
}

/** 二次确认：展示预检影响范围，取消则不执行清理 */
function confirmCleanup(
  preview: CollectorRetentionApi.RetentionPreview,
): Promise<boolean> {
  const summary = summarizePreview(preview);
  return new Promise((resolve) => {
    Modal.confirm({
      title: '确认立即执行清理？',
      width: 480,
      okText: '确认清理',
      okType: 'danger',
      cancelText: '取消',
      content: h(
        'div',
        { class: 'whitespace-pre-line text-sm' },
        [
          `预检日期：${preview.today}`,
          `预计删除：${summary.candidateRows} 行`,
          `拟 DROP 分区：${summary.candidatePartitions} 个`,
          `跳过数据类：${summary.skippedClasses} 类`,
          '',
          '清理将立即执行且不可撤销，是否继续？',
        ].join('\n'),
      ),
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
}

/** 立即清理：预检 → 影响范围确认 → 执行 → 结果抽屉 */
async function handleCleanup() {
  if (cleanupLoading.value) {
    return;
  }
  cleanupLoading.value = true;
  try {
    let preview: CollectorRetentionApi.RetentionPreview;
    try {
      preview = await previewRetentionCleanup();
    } catch (error) {
      message.error(
        `清理预检失败：${resolveApiErrorMessage(error, '请稍后重试')}`,
      );
      return;
    }
    const confirmed = await confirmCleanup(preview);
    if (!confirmed) {
      return;
    }
    try {
      const result = await executeRetentionCleanup();
      if (!result) {
        throw new Error('清理接口返回为空');
      }
      cleanupDrawerApi.setData({ preview, result }).open();
      await loadPolicies();
    } catch (error) {
      const errorMessage = resolveApiErrorMessage(error, '请稍后重试');
      message.error(`清理执行失败：${errorMessage}`);
      // 保留执行前预检快照，便于定位失败范围
      previewDrawerApi.setData({ errorMessage, preview }).open();
    }
  } finally {
    cleanupLoading.value = false;
  }
}

onMounted(() => {
  void loadPolicies();
});
</script>

<template>
  <Page auto-content-height>
    <EditDrawerComp @success="loadPolicies" />
    <PreviewDrawerComp />
    <CleanupDrawerComp />

    <div class="flex h-full flex-col gap-3 overflow-auto">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 class="m-0 text-lg font-semibold">保留策略</h2>
          <div class="mt-1 text-xs opacity-60">
            固定 8 个数据类；保留天数 0 表示永久保留；修改策略不会立即清理数据。
          </div>
          <div class="mt-1 text-xs opacity-60">
            下次定时执行：每天 02:40（Asia/Shanghai）
          </div>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <Button :loading="loading" @click="loadPolicies">刷新</Button>
          <Button
            v-access:code="['collector:retention:query']"
            @click="handlePreview"
          >
            清理预检
          </Button>
          <Button
            v-access:code="['collector:retention:cleanup']"
            type="primary"
            danger
            :loading="cleanupLoading"
            :disabled="cleanupLoading"
            @click="handleCleanup"
          >
            立即清理
          </Button>
        </div>
      </div>

      <Alert
        v-if="loadError"
        type="error"
        show-icon
        :message="`加载保留策略失败：${loadError}`"
      />

      <Card size="small" class="min-h-0 flex-1">
        <Table
          :columns="policyColumns"
          :data-source="policies"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 1220 }"
          row-key="id"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'dataClass'">
              <div class="font-medium">{{ record.displayName }}</div>
              <div class="text-xs text-gray-500">{{ record.dataClass }}</div>
            </template>
            <template v-else-if="column.key === 'retentionDays'">
              {{ formatRetentionDays(record.retentionDays) }}
            </template>
            <template v-else-if="column.key === 'action'">
              <Tag :color="getRetentionActionConfig(record.action).color">
                {{ getRetentionActionConfig(record.action).text }}
              </Tag>
              <div class="mt-1 text-xs text-gray-500">{{ record.action }}</div>
            </template>
            <template v-else-if="column.key === 'partitionLeadDays'">
              <span v-if="isRawDataClass(record.dataClass)">
                {{
                  record.partitionLeadDays === null ||
                  record.partitionLeadDays === undefined
                    ? '-'
                    : `${record.partitionLeadDays} 天`
                }}
              </span>
              <span v-else>—</span>
            </template>
            <template v-else-if="column.key === 'enabled'">
              <Tag :color="record.enabled === 1 ? 'success' : 'default'">
                {{ record.enabled === 1 ? '启用' : '停用' }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'lastRunAt'">
              {{ formatRetentionTime(record.lastRunAt) }}
            </template>
            <template v-else-if="column.key === 'lastRunResult'">
              <Tag :color="getLastRunResultConfig(record.lastRunResult).color">
                {{ getLastRunResultConfig(record.lastRunResult).text }}
              </Tag>
              <div
                v-if="getLastRunResultConfig(record.lastRunResult).summary"
                class="mt-1 break-all text-xs text-gray-500"
              >
                {{ getLastRunResultConfig(record.lastRunResult).summary }}
              </div>
            </template>
            <template v-else-if="column.key === 'actions'">
              <Button
                v-access:code="['collector:retention:update']"
                type="link"
                size="small"
                @click="handleEdit(record)"
              >
                编辑
              </Button>
            </template>
          </template>
        </Table>
      </Card>
    </div>
  </Page>
</template>
