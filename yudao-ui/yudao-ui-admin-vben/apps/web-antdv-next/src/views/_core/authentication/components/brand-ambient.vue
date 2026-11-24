<script lang="ts" setup>
import { computed } from 'vue';

defineOptions({ name: 'BrandAmbient' });

/**
 * 环境动效由三层组成：径向光晕呼吸、传感波形流动、粒子网格呼吸。
 * 全部使用 CSS transform / opacity 动画，不引入 canvas、动画库或 JS 定时器。
 */
const PARTICLES_PER_ROW = 24;
const PARTICLE_ROWS = 14;

const particles = computed(() => {
  const total = PARTICLES_PER_ROW * PARTICLE_ROWS;
  return Array.from({ length: total }, (_, index) => {
    // 使用序号推导伪随机参数，保证每次渲染的节奏稳定、交错自然
    const durationSeed = ((index * 73) % 101) / 100;
    const delaySeed = ((index * 47) % 97) / 96;
    const duration = (3 + durationSeed * 3).toFixed(2);
    const delay = (delaySeed * 5.9).toFixed(2);
    return `--brand-particle-duration: ${duration}s; --brand-particle-delay: ${delay}s;`;
  });
});
</script>

<template>
  <div class="brand-ambient" aria-hidden="true">
    <!-- 1. 两个径向渐变光晕，18s / 26s 反向呼吸 -->
    <div class="brand-glow brand-glow-a"></div>
    <div class="brand-glow brand-glow-b"></div>

    <!-- 2. 三条复合正弦波形，translateX 0 → -50% 无缝循环 -->
    <div class="brand-waves">
      <svg
        class="brand-wave brand-wave-a"
        preserveAspectRatio="none"
        viewBox="0 0 2880 600"
      >
        <path
          d="M0,300 C120,260 240,260 360,300 S600,340 720,300 S960,260 1080,300 S1320,340 1440,300 S1680,260 1800,300 S2040,340 2160,300 S2400,260 2520,300 S2760,340 2880,300"
          vector-effect="non-scaling-stroke"
        />
      </svg>
      <svg
        class="brand-wave brand-wave-b"
        preserveAspectRatio="none"
        viewBox="0 0 2880 600"
      >
        <path
          d="M0,430 C120,370 240,370 360,430 S600,490 720,430 S960,370 1080,430 S1320,490 1440,430 S1680,370 1800,430 S2040,490 2160,430 S2400,370 2520,430 S2760,490 2880,430"
          vector-effect="non-scaling-stroke"
        />
      </svg>
      <svg
        class="brand-wave brand-wave-c"
        preserveAspectRatio="none"
        viewBox="0 0 2880 600"
      >
        <path
          d="M0,180 C120,150 240,150 360,180 S600,210 720,180 S960,150 1080,180 S1320,210 1440,180 S1680,150 1800,180 S2040,210 2160,180 S2400,150 2520,180 S2760,210 2880,180"
          vector-effect="non-scaling-stroke"
        />
      </svg>
    </div>

    <!-- 3. 24 × 14 粒子网格，3–6s 随机呼吸、交错延迟 -->
    <div class="brand-particles">
      <span
        v-for="(style, index) in particles"
        :key="index"
        class="brand-particle"
        :style="style"
      ></span>
    </div>
  </div>
</template>

<style scoped>
.brand-ambient {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

/* ---------- 光晕 ---------- */
.brand-glow {
  position: absolute;
  border-radius: 50%;
  opacity: 0.25;
  transform: scale(0.9);
  animation-name: brand-glow-breathe;
  animation-timing-function: ease-in-out;
  animation-iteration-count: infinite;
  will-change: transform, opacity;
}

.brand-glow-a {
  top: -14%;
  left: -10%;
  width: 46vw;
  height: 46vw;
  background: radial-gradient(
    circle,
    var(--brand-glow-a, rgb(34 211 238 / 45%)) 0%,
    transparent 68%
  );
  animation-duration: 18s;
}

.brand-glow-b {
  right: -16%;
  bottom: -18%;
  width: 52vw;
  height: 52vw;
  background: radial-gradient(
    circle,
    var(--brand-glow-b, rgb(56 189 248 / 35%)) 0%,
    transparent 70%
  );
  animation-direction: reverse;
  animation-duration: 26s;
}

@keyframes brand-glow-breathe {
  0%,
  100% {
    opacity: 0.25;
    transform: scale(0.9);
  }

  50% {
    opacity: 0.45;
    transform: scale(1.1);
  }
}

/* ---------- 波形 ---------- */
.brand-waves {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.brand-wave {
  position: absolute;
  top: 0;
  left: 0;
  width: 200%;
  height: 100%;
  animation-name: brand-wave-flow;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
  will-change: transform;
}

.brand-wave path {
  stroke: var(--brand-accent, #22d3ee);
  stroke-width: 1.4;
  fill: none;
}

.brand-wave-a {
  opacity: 0.3;
  animation-duration: 22s;
}

.brand-wave-b {
  opacity: 0.12;
  animation-duration: 14s;
}

.brand-wave-b path {
  stroke: var(--brand-accent-2, #38bdf8);
}

.brand-wave-c {
  opacity: 0.18;
  animation-duration: 8s;
}

@keyframes brand-wave-flow {
  from {
    transform: translate3d(0, 0, 0);
  }

  to {
    transform: translate3d(-50%, 0, 0);
  }
}

/* ---------- 粒子 ---------- */
.brand-particles {
  position: absolute;
  inset: 4% 3%;
  display: grid;
  grid-template-columns: repeat(24, minmax(0, 1fr));
  grid-template-rows: repeat(14, minmax(0, 1fr));
  place-items: center;
}

.brand-particle {
  width: 2px;
  height: 2px;
  background: var(--brand-particle, rgb(148 163 184 / 85%));
  border-radius: 50%;
  opacity: 0.1;
  animation-name: brand-particle-breathe;
  animation-timing-function: ease-in-out;
  animation-iteration-count: infinite;
  animation-duration: var(--brand-particle-duration, 4s);
  animation-delay: var(--brand-particle-delay, 0s);
}

@keyframes brand-particle-breathe {
  0%,
  100% {
    opacity: 0.1;
  }

  50% {
    opacity: 0.5;
  }
}

/* 移动端粒子数量减半 */
@media (max-width: 767.98px) {
  .brand-particle:nth-child(even) {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .brand-glow {
    animation: none;
    opacity: 0.3;
    transform: none;
  }

  .brand-wave {
    animation: none;
  }

  .brand-particle {
    animation: none;
    opacity: 0.25;
  }
}
</style>
