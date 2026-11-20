<script lang="ts" setup>
import type { CollectorRetentionApi } from '#/api/collector/retention';

import { computed, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Alert, Empty, Table, Tag } from 'antdv-next';

import {
  formatPartitionRange,
  formatRetentionTime,
  getRetentionOutcomeConfig,
  resolveCleanupOverall,
  summarizePreview,
  useCleanupColumns,
} from '../data';

interface CleanupDrawerData {
  result: CollectorRetentionApi.RetentionCleanupResult;
  /** 执行前预检快照，用于失败时保留影响范围上下文 */
  preview?: CollectorRetentionApi.RetentionPreview;
}

const result = ref<CollectorRetentionApi.RetentionCleanupResult>();
const preview = ref<CollectorRetentionApi.RetentionPreview>();

const overall = computed(() => resolveCleanupOverall(result.value));
const previewSummaryText = computed(() => {
  const snapshot = preview.value;
  if (!snapshot) {
    return '';
  }
  const summary = summarizePreview(snapshot);
  return `执行前预检（${snapshot.today}）：预计删除 ${summary.candidateRows} 行，拟 DROP ${summary.candidatePartitions} 个分区，跳过 ${summary.skippedClasses} 类。`;
});
const cleanupColumns = useCleanupColumns();

const [Drawer, drawerApi] = useVbenDrawer({
  title: '清理结果',
  footer: false,
  class: '!w-[1080px] !max-w-[96vw]',
  onOpenChange(isOpen: boolean) {
    const data = isOpen
      ? (drawerApi.getData() as CleanupDrawerData | undefined)
      : undefined;
    result.value = data?.result;
    preview.value = data?.preview;
  },
});
</script>

<template>
  <Drawer>
    <div v-if="result" class="mx-4 flex flex-col gap-3">
      <Alert
        :type="overall.type"
        show-icon
        :message="overall.text"
        :description="
          result.hasFailure
            ? '存在失败或部分成功的数据类，请查看下方明细，必要时人工处理。'
            : undefined
        "
      />

      <Alert
        v-if="result.lockNotAcquired"
        type="warning"
        show-icon
        message="已有清理任务在执行，本次整体跳过"
        description="未获取到清理锁，本次没有执行任何删除或 DROP，请稍后重试。"
      />

      <div class="flex flex-wrap items-center gap-2 text-sm">
        <Tag>{{ `清理日期：${result.today}` }}</Tag>
        <Tag color="blue">{{ `删除总行数：${result.totalDeletedRows}` }}</Tag>
        <Tag color="orange">
          {{ `DROP 分区数：${result.droppedPartitionCount}` }}
        </Tag>
        <Tag :color="result.hasFailure ? 'error' : 'success'">
          {{ result.hasFailure ? '存在失败/部分成功' : '无失败' }}
        </Tag>
      </div>

      <div v-if="previewSummaryText" class="text-xs text-gray-500">
        {{ previewSummaryText }}
      </div>

      <Empty
        v-if="!result.results?.length"
        description="本次未返回任何数据类清理结果"
      />

      <Table
        v-else
        :columns="cleanupColumns"
        :data-source="result.results"
        :pagination="false"
        :scroll="{ x: 1160 }"
        row-key="dataClass"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'dataClass'">
            <div class="font-medium">{{ record.displayName }}</div>
            <div class="text-xs text-gray-500">{{ record.dataClass }}</div>
          </template>
          <template v-else-if="column.key === 'outcome'">
            <Tag :color="getRetentionOutcomeConfig(record.outcome).color">
              {{ getRetentionOutcomeConfig(record.outcome).text }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'cutoff'">
            {{ formatRetentionTime(record.cutoff) }}
          </template>
          <template v-else-if="column.key === 'deletedRows'">
            {{ `${record.deletedRows ?? 0} 行` }}
          </template>
          <template v-else-if="column.key === 'droppedPartitions'">
            <div
              v-if="record.droppedPartitions?.length"
              class="flex flex-col gap-1"
            >
              <div
                v-for="partition in record.droppedPartitions"
                :key="partition.partitionName"
              >
                <div class="font-mono text-xs">
                  {{ partition.partitionName }}
                </div>
                <div class="text-xs text-gray-500">
                  {{ formatPartitionRange(partition) }}
                </div>
              </div>
            </div>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'message'">
            <div class="text-xs">{{ record.message || '-' }}</div>
            <ul
              v-if="record.warnings?.length"
              class="m-0 mt-1 list-disc pl-4 text-xs text-orange-600"
            >
              <li v-for="(warning, index) in record.warnings" :key="index">
                {{ warning }}
              </li>
            </ul>
          </template>
        </template>
      </Table>
    </div>
  </Drawer>
</template>
