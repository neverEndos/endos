<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

/**
 * Kiosk 缩放外壳：画布固定 1024×600，按 min(vw/1024, vh/600) 等比缩放居中，
 * 任何分辨率下无滚动条、无裁切、不可触摸拖动。视觉基准为 docs/kiosk-design/04-半透明科技风.html。
 */
const DESIGN_WIDTH = 1024;
const DESIGN_HEIGHT = 600;

const scale = ref(1);
const viewportRef = ref<HTMLElement>();

/**
 * 逻辑视口尺寸：优先 visualViewport（DSF 缩放后是精确小数值，
 * 如 1024×600 @ DSF1.25 → 819.2×480），回退 documentElement 与 window
 */
function viewportSize() {
  const vv = window.visualViewport;
  const w = vv?.width ?? document.documentElement.clientWidth;
  const h = vv?.height ?? document.documentElement.clientHeight;
  return {
    w: w > 0 ? w : window.innerWidth,
    h: h > 0 ? h : window.innerHeight,
  };
}

function updateScale() {
  const { w, h } = viewportSize();
  scale.value = Math.min(w / DESIGN_WIDTH, h / DESIGN_HEIGHT);
}

/**
 * 舞台布局尺寸 = 缩放后的视觉尺寸。transform 不影响布局，
 * 若直接放 1024×600 画布，会在视口内产生可滚动溢出（触摸拖动的根源）；
 * 用舞台包住画布并以左上角为缩放原点，任何时刻布局都无溢出
 */
const stageStyle = computed(() => ({
  height: `${DESIGN_HEIGHT * scale.value}px`,
  width: `${DESIGN_WIDTH * scale.value}px`,
}));

/** 触摸拖动兜底拦截（非 passive）：不影响 pointer 事件、十字线与长按退出 */
function onTouchMove(event: TouchEvent) {
  event.preventDefault();
}

onMounted(() => {
  updateScale();
  window.addEventListener('resize', updateScale);
  window.visualViewport?.addEventListener('resize', updateScale);
  viewportRef.value?.addEventListener('touchmove', onTouchMove, {
    passive: false,
  });
  // 锁定文档级滚动与回弹，卸载时移除，不影响管理端页面
  document.documentElement.classList.add('kiosk-lock');
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateScale);
  window.visualViewport?.removeEventListener('resize', updateScale);
  viewportRef.value?.removeEventListener('touchmove', onTouchMove);
  document.documentElement.classList.remove('kiosk-lock');
});
</script>

<template>
  <div ref="viewportRef" class="kiosk-viewport">
    <div class="kiosk-stage" :style="stageStyle">
      <div
        class="kiosk-canvas"
        :style="{ transform: `scale(${scale})` }"
      >
        <slot></slot>
      </div>
    </div>
  </div>
</template>

<style>
/* 流光边框的 @property 与关键帧需全局注册，供各子组件 scoped 样式按名引用 */
@property --flow {
  syntax: '<angle>';
  inherits: false;
  initial-value: 0deg;
}

@keyframes kiosk-flow {
  to {
    --flow: 360deg;
  }
}

@keyframes kiosk-pulse {
  50% {
    box-shadow:
      0 0 0 6px rgb(52 211 153 / 5%),
      0 0 14px rgb(52 211 153 / 90%);
  }
}

@keyframes kiosk-fade {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

/* kiosk 挂载期间锁定文档级滚动与回弹（卸载时由 JS 移除该类，不影响管理端） */
html.kiosk-lock,
html.kiosk-lock body {
  overflow: hidden;
  overscroll-behavior: none;
}

/* 玻璃容器：kiosk 各面板共用（前缀避免污染全局） */
.kiosk-glass {
  position: relative;
  background: linear-gradient(180deg, var(--glass1), var(--glass2));
  border: 1px solid rgb(255 255 255 / 15%);
  border-radius: 18px;
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 5.5%),
    0 22px 44px -30px rgb(0 0 0 / 95%);
  backdrop-filter: blur(17px);
}

/* 环绕流光边框：不占用布局、不遮挡内容 */
.kiosk-flow::before {
  position: absolute;
  padding: 1px;
  pointer-events: none;
  content: "";
  background: conic-gradient(
    from var(--flow),
    rgba(111, 240, 190, 0) 0deg,
    rgba(111, 240, 190, 0.2) 18deg,
    rgba(111, 240, 190, 1) 42deg,
    rgba(52, 211, 153, 1) 52deg,
    rgba(255, 166, 89, 0.85) 78deg,
    rgba(255, 122, 26, 0.15) 104deg,
    rgba(111, 240, 190, 0) 150deg,
    rgba(111, 240, 190, 0) 360deg
  );
  border-radius: 19px;
  inset: -1px;
  animation: kiosk-flow 6s linear infinite;
  filter: drop-shadow(0 0 5px rgb(52 211 153 / 55%));
  -webkit-mask:
    linear-gradient(#000 0 0) content-box,
    linear-gradient(#000 0 0);
  mask:
    linear-gradient(#000 0 0) content-box,
    linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
}
</style>

<style scoped>
.kiosk-viewport {
  display: grid;
  width: 100vw;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  overscroll-behavior: none;
  touch-action: none;
  background: #05080d;
  place-items: center;
}

/* 舞台布局尺寸 = 缩放后视觉尺寸（由 JS 内联），从根源消除可滚动溢出 */
.kiosk-stage {
  position: relative;
  flex: none;
  overflow: hidden;
  overscroll-behavior: none;
  touch-action: none;
}

.kiosk-canvas {
  /* 设计 Token（04-半透明科技风 §1），子组件通过 var() 继承使用 */
  --green: #34d399;
  --mint: #6ff0be;
  --orange: #ff7a1a;
  --amber: #ffa659;
  --txt: #f1f5ff;
  --txt2: #8e9aa6;
  --txt3: #77859a;
  --glass1: rgb(232 242 255 / 5.8%);
  --glass2: rgb(232 242 255 / 2.6%);

  position: relative;
  display: flex;
  width: 1024px;
  height: 600px;
  flex: none;
  flex-direction: column;
  overflow: hidden;
  color: #f1f5ff;
  transform-origin: 0 0;
  font-family:
    Inter, "HarmonyOS Sans SC", "PingFang SC", "Microsoft YaHei", system-ui,
    sans-serif;
  background:
    radial-gradient(820px 520px at 12% -8%, rgb(28 64 58 / 55%), transparent 62%),
    radial-gradient(720px 520px at 102% 108%, rgb(40 30 60 / 42%), transparent 58%),
    radial-gradient(600px 400px at 60% 40%, rgb(20 28 36 / 60%), transparent 70%),
    #070a0f;
  -webkit-font-smoothing: antialiased;
}

/* 32×32 网格线，从中心向外淡出 */
.kiosk-canvas::after {
  position: absolute;
  pointer-events: none;
  content: "";
  background-image:
    linear-gradient(rgb(255 255 255 / 1.8%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(255 255 255 / 1.8%) 1px, transparent 1px);
  background-size: 32px 32px;
  inset: 0;
  mask-image: radial-gradient(circle at 50% 45%, #000 30%, transparent 85%);
}
</style>
