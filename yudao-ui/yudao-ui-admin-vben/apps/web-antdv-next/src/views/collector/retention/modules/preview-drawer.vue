<script lang="ts" setup>
import type { CollectorRetentionApi } from '#/api/collector/retention';

import { computed, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Alert, Button, Empty, Spin, Table, Tag } from 'antdv-next';

import { previewRetentionCleanup } from '#/api/collector/retention';

import {
  formatPartitionRange,
  formatRetentionDays,
  formatRetentionTime,
  resolveApiErrorMessage,
  summarizePreview,
  usePreviewColumns,
} from '../data';

interface PreviewDrawerData {
  /** 清理失败时回放执行前的预检快照 */
  preview?: CollectorRetentionApi.RetentionPreview;
  errorMessage?: string;
}

const previewData =
  ref<CollectorRetentionApi.RetentionPreview>();
const loading = ref(false);
const errorMessage = ref<string>();
const contextErrorMessage = ref<string>();

let loadGeneration = 0;

const summary = computed(() => summarizePreview(previewData.value));

const previewColumns = usePreviewColumns();

const [Drawer, drawerApi] = useVbenDrawer({
  title: '清理预检',
  footer: false,
  class: '!w-[1080px] !max-w-[96vw]',
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      loadGeneration += 1;
      previewData.value = undefined;
      errorMessage.value = undefined;
      contextErrorMessage.value = undefined;
      loading.value = false;
      return;
    }
    const data = drawerApi.getData() as PreviewDrawerData | undefined;
    loadGeneration += 1;
    contextErrorMessage.value = data?.errorMessage;
    if (data?.preview) {
      previewData.value = data.preview;
      errorMessage.value = undefined;
      return;
    }
    previewData.value = undefined;
    await loadPreview();
  },
});

async function loadPreview() {
  const generation = ++loadGeneration;
  loading.value = true;
  errorMessage.value = undefined;
  try {
    const preview = await previewRetentionCleanup();
    if (generation !== loadGeneration) {
      return;
    }
    previewData.value = preview;
  } catch (error) {
    if (generation !== loadGeneration) {
      return;
    }
    // 预检失败时保持空态并显示后端中文错误，不展示任何推算数字
    previewData.value = undefined;
    errorMessage.value = resolveApiErrorMessage(error, '预检失败，请稍后重试');
  } finally {
    if (generation === loadGeneration) {
      loading.value = false;
    }
  }
}

/** raw 按分区清理，不统计行数 */
function isPartitionAction(
  item: CollectorRetentionApi.RetentionPreviewClass,
): boolean {
  return item.action === 'drop_partition';
}

function candidateRowsText(
  item: CollectorRetentionApi.RetentionPreviewClass,
): string {
  if (item.skipped) {
    return '-';
  }
  if (isPartitionAction(item)) {
    return '按分区清理';
  }
  if (item.candidateRows === null || item.candidateRows === undefined) {
    return '-';
  }
  return `${item.candidateRows} 行`;
}

function hasCandidates(
  item: CollectorRetentionApi.RetentionPreviewClass,
): boolean {
  return (
    (item.candidateRows ?? 0) > 0 || (item.candidatePartitions?.length ?? 0) > 0
  );
}
</script>

<template>
  <Drawer>
    <div class="mx-4 flex flex-col gap-3">
      <Alert
        type="info"
        show-icon
        message="安全说明"
        description="DEFAULT 分区不 DROP 也不逐行删除；活动报警不删除；待执行/未结束命令不删除。"
      />

      <Alert
        v-if="contextErrorMessage"
        type="error"
        show-icon
        :message="`清理执行失败：${contextErrorMessage}`"
        description="以下为执行前预检快照，可作为失败排查的上下文。"
      />

      <Spin :spinning="loading">
        <div v-if="errorMessage" class="flex flex-col gap-2">
          <Alert
            type="error"
            show-icon
            :message="`预检失败：${errorMessage}`"
          />
          <div>
            <Button
              v-access:code="['collector:retention:query']"
              size="small"
              @click="loadPreview"
            >
              重试
            </Button>
          </div>
        </div>

        <Empty
          v-else-if="!previewData"
          :description="loading ? '正在统计清理范围，请稍候...' : '暂无预检结果'"
        />

        <Empty
          v-else-if="!loading && previewData?.classes?.length === 0"
          description="预检未返回任何数据类"
        />

        <template v-else-if="previewData">
          <div class="mb-2 flex flex-wrap items-center gap-2 text-sm">
            <Tag>{{ `预检日期：${previewData.today}` }}</Tag>
            <Tag color="blue">
              {{ `预计删除 ${summary.candidateRows} 行` }}
            </Tag>
            <Tag color="orange">
              {{ `拟 DROP ${summary.candidatePartitions} 个分区` }}
            </Tag>
            <Tag color="default">
              {{ `跳过 ${summary.skippedClasses} 类` }}
            </Tag>
          </div>
          <Table
            :columns="previewColumns"
            :data-source="previewData.classes"
            :pagination="false"
            :scroll="{ x: 1320 }"
            row-key="dataClass"
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
              <template v-else-if="column.key === 'cutoff'">
                {{ formatRetentionTime(record.cutoff) }}
              </template>
              <template v-else-if="column.key === 'candidateRows'">
                {{ candidateRowsText(record) }}
              </template>
              <template v-else-if="column.key === 'candidatePartitions'">
                <div
                  v-if="record.candidatePartitions?.length"
                  class="flex flex-col gap-1"
                >
                  <div
                    v-for="partition in record.candidatePartitions"
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
              <template v-else-if="column.key === 'status'">
                <Tag v-if="record.skipped" color="default">跳过</Tag>
                <Tag v-else-if="hasCandidates(record)" color="blue">待清理</Tag>
                <Tag v-else color="success">无候选数据</Tag>
                <div
                  v-if="record.skipped && record.skipReason"
                  class="mt-1 text-xs text-gray-500"
                >
                  {{ record.skipReason }}
                </div>
              </template>
              <template v-else-if="column.key === 'warnings'">
                <ul
                  v-if="record.warnings?.length"
                  class="m-0 list-disc pl-4 text-xs text-orange-600"
                >
                  <li v-for="(warning, index) in record.warnings" :key="index">
                    {{ warning }}
                  </li>
                </ul>
                <span v-else>-</span>
              </template>
            </template>
          </Table>
        </template>
      </Spin>
    </div>
  </Drawer>
</template>
