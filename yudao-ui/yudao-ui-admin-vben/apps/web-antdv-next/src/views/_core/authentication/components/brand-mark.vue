<script lang="ts" setup>
defineOptions({ name: 'BrandMark' });
</script>

<template>
  <span class="brand-mark" aria-hidden="true">
    <!-- 品牌标识：外环 + 折线波形，纯内联 SVG，不依赖外部图片或字体 -->
    <svg class="brand-mark-svg" viewBox="0 0 64 64" fill="none">
      <circle
        class="brand-mark-ring"
        cx="32"
        cy="32"
        pathLength="100"
        r="27"
        stroke-width="2.4"
      />
      <path
        class="brand-mark-wave"
        d="M12 38 L22 38 L27 24 L33 44 L39 30 L44 38 L52 38"
        pathLength="100"
        stroke-width="2.4"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
      <circle class="brand-mark-dot" cx="52" cy="38" fill="currentColor" r="2.6" />
    </svg>
  </span>
</template>

<style scoped>
.brand-mark {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  color: var(--brand-accent, #22d3ee);
}

.brand-mark-svg {
  display: block;
  width: clamp(56px, 5vw, 72px);
  height: clamp(56px, 5vw, 72px);
}

.brand-mark-ring,
.brand-mark-wave {
  stroke: currentColor;
  stroke-dasharray: 100;
  stroke-dashoffset: 100;
  animation: brand-mark-draw 1.2s cubic-bezier(0.22, 0.61, 0.36, 1) forwards;
}

.brand-mark-wave {
  animation-delay: 120ms;
}

.brand-mark-ring {
  opacity: 0.85;
}

.brand-mark-dot {
  opacity: 0;
  animation: brand-mark-dot-in 300ms ease-out 1.1s forwards;
}

@keyframes brand-mark-draw {
  to {
    stroke-dashoffset: 0;
  }
}

@keyframes brand-mark-dot-in {
  from {
    opacity: 0;
    transform: scale(0.6);
  }

  to {
    opacity: 1;
    transform: scale(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .brand-mark-ring,
  .brand-mark-wave {
    stroke-dashoffset: 0;
    animation: none;
  }

  .brand-mark-dot {
    opacity: 1;
    animation: none;
  }
}
</style>
