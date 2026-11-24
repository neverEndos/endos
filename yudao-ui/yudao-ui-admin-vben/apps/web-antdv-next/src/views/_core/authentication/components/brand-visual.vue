<script lang="ts" setup>
import BrandAmbient from './brand-ambient.vue';
import BrandMark from './brand-mark.vue';

defineOptions({ name: 'BrandVisual' });

withDefaults(defineProps<{ appName?: string }>(), {
  appName: '',
});

/** 品牌文案集中维护在定制组件内，不修改公共语言包 */
const BRAND_COPY = {
  fullName: '智能采集与状态监测平台',
  motto: '以衡度量，以知预见',
  footer: 'INDUSTRIAL SENSING · EDGE MONITORING',
};

const CAPABILITIES = [
  '自动采集',
  '阈值研判',
  '趋势分析',
  '运行诊断',
];
</script>

<template>
  <aside class="brand-visual" aria-label="品牌介绍">
    <BrandAmbient />

    <div class="brand-panel-content">
      <header class="brand-identity">
        <BrandMark />
        <div class="brand-identity-text">
          <h1 class="brand-name">{{ appName }}</h1>
          <p class="brand-full-name">{{ BRAND_COPY.fullName }}</p>
        </div>
      </header>

      <div class="brand-hero">
        <p class="brand-motto">{{ BRAND_COPY.motto }}</p>
        <ul class="brand-capabilities">
          <li
            v-for="capability in CAPABILITIES"
            :key="capability"
            class="brand-capability"
          >
            <span class="brand-capability-dot" aria-hidden="true"></span>
            {{ capability }}
          </li>
        </ul>
      </div>

      <footer class="brand-footer">{{ BRAND_COPY.footer }}</footer>
    </div>
  </aside>
</template>

<style scoped>
.brand-visual {
  position: relative;
  height: 100%;
  overflow: hidden;
  color: var(--brand-text, #e2e8f0);
  background: linear-gradient(
    145deg,
    var(--brand-bg-1, #070b14) 0%,
    var(--brand-bg-2, #0b1622) 52%,
    var(--brand-bg-3, #070a12) 100%
  );
}

.brand-panel-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 100%;
  padding: clamp(28px, 4.5vw, 72px) clamp(28px, 4vw, 64px);
  gap: clamp(16px, 3vh, 32px);
}

.brand-identity {
  display: flex;
  align-items: center;
  gap: clamp(12px, 1.4vw, 20px);
  min-width: 0;
}

.brand-identity-text {
  min-width: 0;
}

.brand-name {
  margin: 0;
  font-size: clamp(2.1rem, 3.4vw, 3.1rem);
  font-weight: 700;
  line-height: 1.08;
  letter-spacing: 0.08em;
  color: var(--brand-text, #e2e8f0);
}

.brand-full-name {
  margin: 8px 0 0;
  font-size: clamp(0.9rem, 1.15vw, 1.05rem);
  letter-spacing: 0.14em;
  color: var(--brand-text-muted, rgb(226 232 240 / 65%));
}

.brand-hero {
  display: flex;
  flex-direction: column;
  gap: clamp(14px, 2.4vh, 26px);
  max-width: 640px;
}

.brand-motto {
  margin: 0;
  font-size: clamp(1.2rem, 1.7vw, 1.65rem);
  font-weight: 600;
  letter-spacing: 0.12em;
  color: var(--brand-text, #e2e8f0);
}

.brand-motto::before {
  display: inline-block;
  width: 18px;
  height: 2px;
  margin-right: 12px;
  vertical-align: middle;
  content: '';
  background: linear-gradient(90deg, var(--brand-accent, #22d3ee), transparent);
}

.brand-capabilities {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: clamp(10px, 1.6vh, 18px) clamp(20px, 3vw, 44px);
  margin: 0;
  padding: 0;
  list-style: none;
}

.brand-capability {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: clamp(0.9rem, 1.05vw, 1rem);
  letter-spacing: 0.08em;
  color: var(--brand-text-muted, rgb(226 232 240 / 65%));
}

.brand-capability-dot {
  width: 6px;
  height: 6px;
  flex-shrink: 0;
  background: var(--brand-accent, #22d3ee);
  border-radius: 50%;
  box-shadow: 0 0 8px var(--brand-focus, rgb(34 211 238 / 32%));
}

.brand-footer {
  margin: 0;
  padding-top: clamp(12px, 2vh, 20px);
  font-size: clamp(0.66rem, 0.8vw, 0.78rem);
  letter-spacing: 0.22em;
  color: var(--brand-text-muted, rgb(226 232 240 / 65%));
  border-top: 1px solid var(--brand-line, rgb(148 163 184 / 18%));
}

/* 单列布局下品牌区收敛为顶部标识，避免挤占表单空间 */
@media (max-width: 767.98px) {
  .brand-visual {
    height: auto;
  }

  .brand-panel-content {
    justify-content: flex-start;
    height: auto;
    padding: 24px 20px 20px;
    gap: 0;
  }

  .brand-name {
    font-size: 2rem;
  }

  .brand-hero,
  .brand-footer {
    display: none;
  }
}

/* 低高度屏幕（如 1024×600）收紧纵向留白 */
@media (min-width: 768px) and (max-height: 680px) {
  .brand-panel-content {
    padding-top: 32px;
    padding-bottom: 28px;
    gap: 12px;
  }

  .brand-hero {
    gap: 14px;
  }

  .brand-footer {
    padding-top: 10px;
  }
}
</style>
