import type { VbenFormSchema } from '#/adapter/form';
import type { CollectorRetentionApi } from '#/api/collector/retention';

import { formatDateTime } from '@vben/utils';

import { z } from '#/adapter/form';

/** 原始数据分区表使用的数据类编码 */
export const RAW_DATA_CLASS = 'sample_raw';

/** 保留策略表格列；固定 8 行，不分页 */
export function usePolicyColumns() {
  return [
    { key: 'dataClass', title: '数据类型', width: 210, fixed: 'left' as const },
    { key: 'retentionDays', title: '保留天数', width: 120 },
    { key: 'action', title: '清理方式', width: 160 },
    { key: 'partitionLeadDays', title: '分区预建', width: 100 },
    { key: 'enabled', title: '清理开关', width: 100 },
    { key: 'lastRunAt', title: '最近执行时间', width: 180 },
    { key: 'lastRunResult', title: '最近执行结果', width: 260 },
    { key: 'actions', title: '操作', width: 90, fixed: 'right' as const },
  ];
}

/** 预检结果表格列 */
export function usePreviewColumns() {
  return [
    { key: 'dataClass', title: '数据类型', width: 160 },
    { key: 'retentionDays', title: '保留天数', width: 100 },
    { key: 'cutoff', title: '截止时间', width: 180 },
    { key: 'candidateRows', title: '预计删除', width: 120 },
    { key: 'candidatePartitions', title: '拟 DROP 分区', width: 340 },
    { key: 'status', title: '状态', width: 160 },
    { key: 'warnings', title: '警告', width: 260 },
  ];
}

/** 清理结果表格列 */
export function useCleanupColumns() {
  return [
    { key: 'dataClass', title: '数据类型', width: 160 },
    { key: 'outcome', title: '结果', width: 130 },
    { key: 'cutoff', title: '截止时间', width: 180 },
    { key: 'deletedRows', title: '删除行数', width: 110 },
    { key: 'droppedPartitions', title: '已 DROP 分区', width: 300 },
    { key: 'message', title: '摘要', width: 280 },
  ];
}

const RETENTION_OUTCOME_CONFIG: Record<
  string,
  { color: string; text: string }
> = {
  SUCCESS: { color: 'success', text: '成功' },
  PARTIAL: { color: 'warning', text: '部分成功' },
  FAILED: { color: 'error', text: '失败' },
  SKIPPED_DISABLED: { color: 'default', text: '已停用跳过' },
  SKIPPED_PERMANENT: { color: 'default', text: '永久保留跳过' },
  SKIPPED_LOCKED: { color: 'warning', text: '锁占用跳过' },
};

/** 清理结果分类 → 中文与状态色 */
export function getRetentionOutcomeConfig(
  outcome?: null | string,
): { color: string; text: string } {
  return (
    RETENTION_OUTCOME_CONFIG[outcome || ''] || {
      color: 'default',
      text: outcome || '-',
    }
  );
}

/** 清理方式 → 中文与状态色；dataClass/action 均由后端固定，只读展示 */
export function getRetentionActionConfig(action?: null | string): {
  color: string;
  text: string;
} {
  if (action === 'drop_partition') {
    return { color: 'orange', text: '删除过期日分区' };
  }
  if (action === 'delete_rows') {
    return { color: 'blue', text: '分批删除过期行' };
  }
  return { color: 'default', text: action || '-' };
}

/** 最近执行结果文本 → 状态色与摘要；后端写入的是中文摘要，按前缀归类 */
export function getLastRunResultConfig(result?: null | string): {
  color: string;
  summary: string;
  text: string;
} {
  if (!result) {
    return { color: 'default', text: '暂无执行记录', summary: '' };
  }
  if (result.startsWith('部分成功')) {
    return { color: 'warning', text: '部分成功', summary: result };
  }
  if (result.startsWith('失败')) {
    return { color: 'error', text: '失败', summary: result };
  }
  if (result.startsWith('成功')) {
    return { color: 'success', text: '成功', summary: result };
  }
  if (result.includes('停用')) {
    return { color: 'default', text: '已停用跳过', summary: result };
  }
  if (result.includes('永久保留')) {
    return { color: 'default', text: '永久保留跳过', summary: result };
  }
  if (result.includes('锁被占用')) {
    return { color: 'warning', text: '锁占用跳过', summary: result };
  }
  if (result.includes('跳过') || result.startsWith('未执行')) {
    return { color: 'warning', text: '已跳过', summary: result };
  }
  return { color: 'default', text: '已记录', summary: result };
}

/** 保留天数展示；0 表示永久保留 */
export function formatRetentionDays(days?: null | number): string {
  if (days === null || days === undefined) {
    return '-';
  }
  return days === 0 ? '永久保留' : `${days} 天`;
}

/** 分区预建仅对 sample_raw 有意义 */
export function isRawDataClass(dataClass?: null | string): boolean {
  return dataClass === RAW_DATA_CLASS;
}

/** 毫秒时间戳格式化；空值显示 - */
export function formatRetentionTime(value?: null | number): string {
  return value === null || value === undefined ? '-' : formatDateTime(value);
}

/** 分区边界展示；清理结果不含 bound，因此只依赖 from/to */
export function formatPartitionRange(
  partition: CollectorRetentionApi.RetentionPartition,
): string {
  const from = formatRetentionTime(partition.from);
  const to = formatRetentionTime(partition.to);
  if (from === '-' && to === '-') {
    return partition.bound || '-';
  }
  return `${from} ~ ${to}`;
}

/** 预检影响范围汇总；仅统计未跳过数据类，不做任何兜底假数据 */
export function summarizePreview(
  preview?: CollectorRetentionApi.RetentionPreview,
) {
  let candidateRows = 0;
  let candidatePartitions = 0;
  let skippedClasses = 0;
  let pendingClasses = 0;
  for (const item of preview?.classes ?? []) {
    if (item.skipped) {
      skippedClasses += 1;
      continue;
    }
    const rows = item.candidateRows ?? 0;
    const partitions = item.candidatePartitions?.length ?? 0;
    candidateRows += rows;
    candidatePartitions += partitions;
    if (rows > 0 || partitions > 0) {
      pendingClasses += 1;
    }
  }
  return { candidatePartitions, candidateRows, pendingClasses, skippedClasses };
}

/**
 * 整体清理结论：存在 FAILED 或 PARTIAL 时不得显示为成功。
 * 只有 PARTIAL 而无 FAILED 时也要明确展示「部分成功」。
 */
export function resolveCleanupOverall(
  result?: CollectorRetentionApi.RetentionCleanupResult,
): { text: string; type: 'error' | 'success' | 'warning' } {
  if (!result) {
    return { text: '暂无清理结果', type: 'warning' };
  }
  if (result.lockNotAcquired) {
    return { text: '本次清理已跳过：已有清理任务在执行', type: 'warning' };
  }
  const classes = result.results ?? [];
  const failed = classes.filter((item) => item.outcome === 'FAILED').length;
  const partial = classes.filter((item) => item.outcome === 'PARTIAL').length;
  const succeeded = classes.filter((item) => item.outcome === 'SUCCESS').length;
  if (failed > 0) {
    return {
      text:
        succeeded + partial > 0
          ? `部分成功/失败：${failed} 类失败，${succeeded} 类成功，${partial} 类部分成功`
          : `清理失败：${failed} 类失败`,
      type: 'error',
    };
  }
  if (partial > 0) {
    return {
      text: `部分成功：${partial} 类部分成功，${succeeded} 类成功`,
      type: 'warning',
    };
  }
  if (result.hasFailure) {
    return { text: '清理存在异常，请查看各类结果明细', type: 'error' };
  }
  if (succeeded === 0) {
    return { text: '本次没有需要清理的数据类', type: 'warning' };
  }
  return { text: `清理成功：${succeeded} 类已完成`, type: 'success' };
}

interface ApiErrorLike {
  message?: string;
  response?: { data?: { msg?: string } };
}

/** 优先展示后端中文错误，避免把请求细节直接抛给用户 */
export function resolveApiErrorMessage(
  error: unknown,
  fallback = '请稍后重试',
): string {
  const candidate = error as ApiErrorLike | undefined;
  return candidate?.response?.data?.msg ?? candidate?.message ?? fallback;
}

/** 编辑抽屉表单；dataClass 只作为分区预建字段的显示条件，不提交给后端 */
export function usePolicyFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'dataClass',
      component: 'Input',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'retentionDays',
      label: '保留天数',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 0,
        max: 3650,
        precision: 0,
        placeholder: '0 表示永久保留',
      },
      rules: z
        .number()
        .int('保留天数必须为整数')
        .min(0, '保留天数不能小于 0')
        .max(3650, '保留天数不能大于 3650'),
      help: '0 表示永久保留；缩短保留期会使下次清理删除更多数据',
    },
    {
      fieldName: 'partitionLeadDays',
      label: '分区预建天数',
      component: 'InputNumber',
      componentProps: {
        class: '!w-full',
        min: 0,
        max: 30,
        precision: 0,
        placeholder: '提前预建的天数',
      },
      rules: z
        .number()
        .int('分区预建天数必须为整数')
        .min(0, '分区预建天数不能小于 0')
        .max(30, '分区预建天数不能大于 30'),
      dependencies: {
        triggerFields: ['dataClass'],
        show: (values) => isRawDataClass(values.dataClass),
      },
    },
    {
      fieldName: 'enabled',
      label: '清理开关',
      component: 'Switch',
      componentProps: { class: '!w-auto' },
      rules: z.boolean().default(true),
      help: '停用后定时与手动清理都会跳过该数据类',
    },
    {
      fieldName: 'description',
      label: '策略说明',
      component: 'TextArea',
      componentProps: {
        rows: 4,
        maxlength: 255,
        showCount: true,
        placeholder: '请输入策略说明，最多 255 个字符',
      },
      rules: z.string().max(255, '策略说明不能超过 255 个字符').nullish(),
    },
  ];
}
