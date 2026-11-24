<script lang="ts" setup>
import { usePreferences } from '@vben/preferences';

import BrandVisual from './brand-visual.vue';

defineOptions({ name: 'BrandAuthLayout' });

withDefaults(defineProps<{ appName?: string }>(), {
  appName: '',
});

const { isDark } = usePreferences();
</script>

<template>
  <div
    class="brand-auth-layout"
    :class="isDark ? 'brand-theme-dark' : 'brand-theme-light'"
  >
    <BrandVisual class="brand-auth-aside" :app-name="appName" />

    <section class="brand-auth-main">
      <div class="brand-auth-main-glow" aria-hidden="true"></div>
      <div class="brand-auth-card">
        <slot></slot>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ---------- 主题令牌 ---------- */
.brand-theme-dark {
  --brand-bg-1: #070b14;
  --brand-bg-2: #0b1622;
  --brand-bg-3: #070a12;
  --brand-accent: #22d3ee;
  --brand-accent-2: #38bdf8;
  --brand-alert: #f97316;
  --brand-text: #e2e8f0;
  --brand-text-muted: rgb(226 232 240 / 65%);
  --brand-line: rgb(148 163 184 / 18%);
  --brand-glass: rgb(255 255 255 / 4%);
  --brand-glass-border: rgb(148 163 184 / 18%);
  --brand-card-shadow: 0 18px 60px rgb(0 0 0 / 28%);
  --brand-main-bg: #070c16;
  --brand-main-glow: rgb(34 211 238 / 10%);
  --brand-input-bg: rgb(7 13 24 / 72%);
  --brand-input-border: rgb(148 163 184 / 22%);
  --brand-input-text: #e2e8f0;
  --brand-button-from: #22d3ee;
  --brand-button-to: #38bdf8;
  --brand-button-text: #04141c;
  --brand-checkbox-text: #04141c;
  --brand-focus: rgb(34 211 238 / 32%);
  --brand-glow-a: rgb(34 211 238 / 42%);
  --brand-glow-b: rgb(56 189 248 / 32%);
  --brand-particle: rgb(148 163 184 / 85%);
}

.brand-theme-light {
  --brand-bg-1: #f6f8fb;
  --brand-bg-2: #edf2f8;
  --brand-bg-3: #f6f8fb;
  --brand-accent: #0ea5b7;
  --brand-accent-2: #0284c7;
  --brand-alert: #ea580c;
  --brand-text: #0f172a;
  --brand-text-muted: rgb(15 23 42 / 62%);
  --brand-line: rgb(15 23 42 / 8%);
  --brand-glass: rgb(255 255 255 / 80%);
  --brand-glass-border: rgb(15 23 42 / 8%);
  --brand-card-shadow: 0 18px 50px rgb(15 23 42 / 10%);
  --brand-main-bg: #f6f8fb;
  --brand-main-glow: rgb(14 165 183 / 8%);
  --brand-input-bg: rgb(255 255 255 / 92%);
  --brand-input-border: rgb(15 23 42 / 12%);
  --brand-input-text: #0f172a;
  --brand-button-from: #0e7490;
  --brand-button-to: #0369a1;
  --brand-button-text: #ffffff;
  --brand-checkbox-text: #04141c;
  --brand-focus: rgb(14 165 183 / 26%);
  --brand-glow-a: rgb(14 165 183 / 16%);
  --brand-glow-b: rgb(2 132 199 / 12%);
  --brand-particle: rgb(15 23 42 / 45%);
}

/* ---------- 布局骨架 ---------- */
.brand-auth-layout {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
  width: 100%;
  height: 100dvh;
  overflow: hidden;
  color: var(--brand-text);
  background: var(--brand-main-bg);
}

/* 768–1023：收紧左侧品牌区的占比，给表单留出稳定宽度 */
@media (min-width: 768px) and (max-width: 1023.98px) {
  .brand-auth-layout {
    grid-template-columns: minmax(0, 56fr) minmax(0, 44fr);
  }
}

/* <768：单列，品牌区收为顶部标识，表单可纵向滚动 */
@media (max-width: 767.98px) {
  .brand-auth-layout {
    display: flex;
    flex-direction: column;
    height: auto;
    min-height: 100dvh;
    overflow-x: hidden;
    overflow-y: auto;
  }

  .brand-auth-aside {
    flex: none;
  }

  .brand-auth-main {
    flex: 1;
    min-height: auto;
  }
}

.brand-auth-main {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: 0;
  padding: clamp(20px, 3.4vh, 40px) clamp(20px, 2.6vw, 44px);
  overflow: hidden;
  background:
    radial-gradient(
      circle at 82% 8%,
      var(--brand-main-glow) 0%,
      transparent 44%
    ),
    linear-gradient(160deg, var(--brand-main-bg) 0%, var(--brand-bg-2) 120%);
}

.brand-auth-main-glow {
  position: absolute;
  right: -18%;
  bottom: -24%;
  width: 42vw;
  height: 42vw;
  max-width: 560px;
  max-height: 560px;
  background: radial-gradient(
    circle,
    var(--brand-main-glow) 0%,
    transparent 70%
  );
  border-radius: 50%;
  opacity: 0.7;
  pointer-events: none;
}

/* ---------- 玻璃卡片 ---------- */
.brand-auth-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 408px;
  padding: clamp(22px, 2.4vw, 38px) clamp(22px, 2.2vw, 34px);
  background: var(--brand-glass);
  border: 1px solid var(--brand-glass-border);
  border-radius: 20px;
  box-shadow: var(--brand-card-shadow);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  animation: brand-card-enter 300ms cubic-bezier(0.22, 0.61, 0.36, 1) both;
}

@keyframes brand-card-enter {
  from {
    opacity: 0;
    transform: translateY(12px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .brand-auth-card {
    animation: brand-card-fade 150ms ease-out both;
  }
}

@keyframes brand-card-fade {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}
</style>
