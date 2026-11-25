import dayjs from 'dayjs';

import { formatShanghaiDateTime } from '#/views/collector/dashboard/time';

/** 后端 LocalDateTime 可能以 epoch millis、Date 或 ISO 字符串返回 */
export type TimeInput = Date | null | number | string | undefined;

interface ApiErrorLike {
  data?: { msg?: string };
  message?: string;
  response?: { data?: { code?: number; msg?: string } };
}

/** 统一转换为 epoch millis；空值或非法值一律返回 undefined，不伪造时间 */
export function toMillis(value: TimeInput): number | undefined {
  if (value === null || value === undefined || value === '') {
    return undefined;
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : undefined;
  }
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.valueOf() : undefined;
}

/** 按 Asia/Shanghai 展示时间；空值展示 -- */
export function formatShanghaiTime(value: TimeInput): string {
  return formatShanghaiDateTime(toMillis(value));
}

/** 持续时长：从开始时刻到当前，按天/时/分/秒给出紧凑中文描述 */
export function formatDuration(since: TimeInput, now = Date.now()): string {
  const sinceMs = toMillis(since);
  if (sinceMs === undefined) {
    return '--';
  }
  const seconds = Math.floor(Math.max(0, now - sinceMs) / 1000);
  const days = Math.floor(seconds / 86_400);
  const hours = Math.floor((seconds % 86_400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const secs = seconds % 60;
  if (days > 0) {
    return `${days} 天 ${hours} 小时`;
  }
  if (hours > 0) {
    return `${hours} 小时 ${minutes} 分`;
  }
  if (minutes > 0) {
    return `${minutes} 分 ${secs} 秒`;
  }
  return `${secs} 秒`;
}

/** 数值展示：最多 3 位小数并去掉末尾 0；空值展示 -- */
export function formatNumeric(value?: null | number): string {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return '--';
  }
  return Number.parseFloat(value.toFixed(3)).toString();
}

/** 提取后端错误信息；响应拦截器会把业务错误转成异常 */
export function resolveApiErrorMessage(
  error: unknown,
  fallback = '请稍后重试',
): string {
  const candidate = error as ApiErrorLike | undefined;
  return (
    candidate?.response?.data?.msg ??
    candidate?.data?.msg ??
    candidate?.message ??
    fallback
  );
}
