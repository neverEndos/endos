import type { CollectorApi } from '#/api/collector';

import { h } from 'vue';

import { Tag } from 'antdv-next';

/** 启用/禁用单选项，值使用后端约定的 0/1 */
export const ENABLED_OPTIONS = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
];

/** 值类别单选项 */
export const VALUE_KIND_OPTIONS = [
  { label: '数值', value: 'number' },
  { label: '枚举', value: 'enum' },
];

/** 四档阈值默认比例：10% / 30% / 70% / 90% */
export const DEFAULT_THRESHOLD_RATIOS = {
  lowError: 0.1,
  lowWarning: 0.3,
  highWarning: 0.7,
  highError: 0.9,
};

/** 后端返回的 0/1 开关转换为表单 Switch 使用的布尔值 */
export function toSwitchValue(value?: boolean | number | null): boolean {
  return value === true || value === 1;
}

/** 表单 Switch 布尔值转换为后端约定的 0/1 */
export function toIntegerSwitch(value?: boolean | null): number {
  return value ? 1 : 0;
}

/** 对象转格式化 JSON 文本 */
export function formatJson(value?: null | Record<string, any>): string {
  return JSON.stringify(value ?? {}, null, 2);
}

/** 解析 JSON 对象文本；非法内容抛出可读错误 */
export function parseJsonObject(
  text?: null | string,
): Record<string, any> {
  if (!text || !text.trim()) {
    return {};
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new Error('不是合法的 JSON');
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('必须是 JSON 对象（以 { 开头）');
  }
  return parsed as Record<string, any>;
}

/** 值类别的表格渲染 */
export function renderValueKind(valueKind?: string) {
  const isEnum = valueKind === 'enum';
  return h(
    Tag,
    { color: isEnum ? 'orange' : 'blue' },
    () => (isEnum ? '枚举' : '数值'),
  );
}

/** 启用状态的表格渲染 */
export function renderEnabled(enabled?: number) {
  const isEnabled = enabled === 1;
  return h(
    Tag,
    { color: isEnabled ? 'success' : 'default' },
    () => (isEnabled ? '启用' : '禁用'),
  );
}

/** 生成通道预览行的状态：INVALID / SKIP / NEW */
export function resolveGenerateStatus(
  row: CollectorApi.GenerateChannelPreview,
): 'INVALID' | 'NEW' | 'SKIP' {
  const missingNumberRange =
    row.valueKind === 'number' &&
    (row.valueMin === null ||
      row.valueMin === undefined ||
      row.valueMax === null ||
      row.valueMax === undefined);
  if (!row.channelCode || missingNumberRange) {
    return 'INVALID';
  }
  return row.skipped ? 'SKIP' : 'NEW';
}

/** 生成通道预览状态对应的 Tag 颜色 */
export function generateStatusColor(status: 'INVALID' | 'NEW' | 'SKIP') {
  if (status === 'NEW') {
    return 'success';
  }
  if (status === 'SKIP') {
    return 'warning';
  }
  return 'error';
}
