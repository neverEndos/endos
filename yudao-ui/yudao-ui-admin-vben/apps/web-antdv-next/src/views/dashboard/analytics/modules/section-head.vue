<script lang="ts" setup>
import { cn } from '@vben/utils';

defineOptions({ name: 'ScreenSectionHead' });

withDefaults(
  defineProps<{
    /** 全大写式编号，如 SENSOR-01 */
    code?: string;
    /** 是否让右侧状态点呼吸（仅用于活动报警等有信息意义的场景） */
    pulse?: boolean;
    statusText?: string;
    statusTone?: 'error' | 'muted' | 'ok' | 'warn';
    subtitle?: string;
    title: string;
  }>(),
  {
    code: '',
    pulse: false,
    statusText: '',
    statusTone: 'muted',
    subtitle: '',
  },
);
</script>

<template>
  <div class="section-head">
    <div class="section-title-wrap">
      <span class="section-bar" aria-hidden="true"></span>
      <div class="section-title-block">
        <span class="section-title">{{ title }}</span>
        <span v-if="subtitle" class="section-subtitle">{{ subtitle }}</span>
      </div>
      <span v-if="code" class="section-code">{{ code }}</span>
    </div>
    <div class="section-right">
      <span
        v-if="statusText"
        :class="
          cn('status-dot', {
            'status-dot--error': statusTone === 'error',
            'status-dot--ok': statusTone === 'ok',
            'status-dot--pulse': pulse,
            'status-dot--warn': statusTone === 'warn',
          })
        "
        aria-hidden="true"
      ></span>
      <span v-if="statusText" class="status-text">{{ statusText }}</span>
      <slot name="right"></slot>
    </div>
  </div>
</template>

<style scoped>
.section-head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
}

.section-title-wrap {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.section-bar {
  width: 3px;
  height: 30px;
  background: linear-gradient(180deg, var(--scr-primary, #22d3ee), transparent);
  border-radius: 2px;
  box-shadow: 0 0 8px var(--scr-primary, #22d3ee);
}

.section-title-block {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.section-title {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 15px;
  font-weight: 600;
  color: var(--scr-ink, #e6eef9);
  letter-spacing: 1px;
  white-space: nowrap;
}

.section-subtitle {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 9px;
  color: var(--scr-muted, #64748b);
  text-transform: uppercase;
  letter-spacing: 2.4px;
}

.section-code {
  padding: 1px 6px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 9px;
  color: var(--scr-ink-soft, #b7c6dd);
  letter-spacing: 1px;
  white-space: nowrap;
  background: rgb(34 211 238 / 6%);
  border: 1px solid var(--scr-border, rgb(72 104 158 / 24%));
  border-radius: 3px;
}

.section-right {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  font-size: 11px;
  color: var(--scr-muted, #64748b);
}

.status-dot {
  width: 6px;
  height: 6px;
  background: var(--scr-muted, #64748b);
  border-radius: 999px;
}

.status-dot--ok {
  background: var(--scr-okay, #2dd4bf);
  box-shadow: 0 0 6px var(--scr-okay, #2dd4bf);
}

.status-dot--warn {
  background: var(--scr-warning, #fbbf24);
  box-shadow: 0 0 6px var(--scr-warning, #fbbf24);
}

.status-dot--error {
  background: var(--scr-error, #f43f5e);
  box-shadow: 0 0 8px var(--scr-error, #f43f5e);
}

.status-dot--pulse {
  animation: status-breathe 1.6s ease-in-out infinite;
}

.status-text {
  white-space: nowrap;
}

@keyframes status-breathe {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }

  50% {
    opacity: 0.35;
    transform: scale(0.78);
  }
}

@media (prefers-reduced-motion: reduce) {
  .status-dot--pulse {
    animation: none;
  }
}
</style>
