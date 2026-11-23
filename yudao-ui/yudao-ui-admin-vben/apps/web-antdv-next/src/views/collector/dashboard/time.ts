const SHANGHAI_DATE_TIME_FORMATTER = new Intl.DateTimeFormat('zh-CN', {
  day: '2-digit',
  hour: '2-digit',
  hourCycle: 'h23',
  minute: '2-digit',
  month: '2-digit',
  second: '2-digit',
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
});

/**
 * 将后端 epoch millis 固定按 Asia/Shanghai 展示，避免浏览器本地时区造成偏移。
 * 空值与非法数值一律展示 --，不得补成 epoch 0 或伪造时间。
 */
export function formatShanghaiDateTime(value?: null | number): string {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return '--';
  }
  const parts = SHANGHAI_DATE_TIME_FORMATTER.formatToParts(new Date(value));
  const part = (type: string) =>
    parts.find((item) => item.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')} ${part('hour')}:${part('minute')}:${part('second')}`;
}
