<script lang="ts" setup>
import { onBeforeUnmount, ref } from 'vue';

const HOLD_DURATION_MS = 1200;
const EXIT_URL = 'http://127.0.0.1:8899/exit';
const CIRCUMFERENCE = 2 * Math.PI * 20;

const progress = ref(0);
const exiting = ref(false);
const exitFailed = ref(false);

let animationFrame: null | number = null;
let startedAt = 0;
let activePointerId: null | number = null;
let triggered = false;

function cancelHold() {
  if (animationFrame !== null) cancelAnimationFrame(animationFrame);
  animationFrame = null;
  activePointerId = null;
  if (!triggered) progress.value = 0;
}

function updateProgress() {
  if (triggered) return;
  progress.value = Math.min(
    1,
    (performance.now() - startedAt) / HOLD_DURATION_MS,
  );
  if (progress.value >= 1) {
    triggered = true;
    activePointerId = null;
    animationFrame = null;
    void requestExit();
    return;
  }
  animationFrame = requestAnimationFrame(updateProgress);
}

function handlePointerDown(event: PointerEvent) {
  if (exiting.value || event.button !== 0) return;
  event.preventDefault();
  cancelHold();
  triggered = false;
  exitFailed.value = false;
  startedAt = performance.now();
  activePointerId = event.pointerId;
  animationFrame = requestAnimationFrame(updateProgress);
}

function handlePointerEnd(event: PointerEvent) {
  if (activePointerId !== null && event.pointerId !== activePointerId) return;
  cancelHold();
}

async function requestExit() {
  exiting.value = true;
  try {
    const response = await fetch(EXIT_URL, { method: 'GET' });
    if (!response.ok) throw new Error(`退出服务返回 ${response.status}`);
  } catch {
    // 本地服务不可用时尝试关闭由桌面快捷方式打开的窗口，不弹原生确认框。
    exitFailed.value = true;
    window.close();
  }
}

onBeforeUnmount(cancelHold);
</script>

<template>
  <button
    class="exit-button"
    :class="{ exiting }"
    type="button"
    aria-label="长按退出 Kiosk"
    :title="exitFailed ? '退出服务不可用，已尝试关闭窗口' : '长按 1.2 秒退出'"
    @contextmenu.prevent
    @pointercancel="handlePointerEnd"
    @pointerdown="handlePointerDown"
    @pointerleave="handlePointerEnd"
    @pointerup="handlePointerEnd"
  >
    <svg class="progress-ring" viewBox="0 0 48 48" aria-hidden="true">
      <circle class="progress-track" cx="24" cy="24" r="20" />
      <circle
        class="progress-value"
        cx="24"
        cy="24"
        r="20"
        :stroke-dasharray="CIRCUMFERENCE"
        :stroke-dashoffset="CIRCUMFERENCE * (1 - progress)"
      />
    </svg>
    <svg class="power-icon" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 2v10M5.6 5.6a9 9 0 1 0 12.8 0" />
    </svg>
  </button>
</template>

<style scoped>
.exit-button {
  position: relative;
  display: grid;
  flex: 0 0 48px;
  width: 48px;
  height: 48px;
  padding: 0;
  color: #d7e3f4;
  cursor: pointer;
  touch-action: none;
  user-select: none;
  background: transparent;
  border: 0;
  border-radius: 50%;
  opacity: 0.55;
  place-items: center;
  -webkit-tap-highlight-color: transparent;
}

.exit-button:active,
.exit-button.exiting {
  opacity: 1;
}

.progress-ring,
.power-icon {
  position: absolute;
  width: 44px;
  height: 44px;
}

.progress-ring {
  transform: rotate(-90deg);
}

.progress-track,
.progress-value {
  fill: none;
  stroke-width: 2;
}

.progress-track {
  stroke: rgb(255 255 255 / 10%);
}

.progress-value {
  stroke: #67e8f9;
  transition: stroke-dashoffset 40ms linear;
}

.power-icon {
  width: 23px;
  height: 23px;
  fill: none;
  stroke: currentcolor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}
</style>
