<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { AuthenticationLogin, Verification, z } from '@vben/common-ui';
import { isCaptchaEnable } from '@vben/hooks';
import { $t } from '@vben/locales';
import { buildSocialRedirectUri } from '@vben/utils';

import {
  checkCaptcha,
  getCaptcha,
  socialAuthRedirect,
} from '#/api/core/auth';
import { useAuthStore } from '#/store';

defineOptions({ name: 'Login' });

/** 登录文案集中维护在页面组件内，不改动公共语言包 */
const LOGIN_COPY = {
  title: '欢迎回来',
  subtitle: '登录以查看设备状态与采集数据',
  submit: '登 录',
};

const { query } = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const captchaEnable = isCaptchaEnable();

const loginRef = ref();
const verifyRef = ref();
const loginRoot = ref<HTMLElement | null>(null);

const captchaType = 'blockPuzzle'; // 验证码类型：'blockPuzzle' | 'clickWord'

/** 处理登录 */
async function handleLogin(values: any) {
  // 如果开启验证码，则先验证验证码
  if (captchaEnable) {
    verifyRef.value.show();
    return;
  }
  // 无验证码，直接登录
  await authStore.authLogin('username', values);
}

/** 验证码通过，执行登录 */
async function handleVerifySuccess({ captchaVerification }: any) {
  try {
    await authStore.authLogin('username', {
      ...(await loginRef.value.getFormApi().getValues()),
      captchaVerification,
    });
  } catch (error) {
    console.error('Error in handleLogin:', error);
  }
}

/** 处理第三方登录 */
const redirect = query?.redirect;
async function handleThirdLogin(type: number) {
  if (type <= 0) {
    return;
  }
  try {
    // 计算 redirectUri
    // tricky: type、redirect 需要先 encode 一次，否则钉钉回调会丢失。配合 social-login.vue#getUrlValue() 使用
    const redirectUri = buildSocialRedirectUri(
      router.resolve('/auth/social-login').href,
      { type, redirect: (redirect as string) || '/' },
    );

    // 进行跳转
    window.location.href = await socialAuthRedirect(type, redirectUri);
  } catch (error) {
    console.error('第三方登录处理失败:', error);
  }
}

/**
 * 通用组件将“忘记密码”渲染成不可聚焦的 span。
 * 这里仅在该登录页内补充 role/tabindex 与键盘触发，保证 Tab 顺序可达，不修改公共组件。
 */
let forgetPasswordEl: HTMLElement | null = null;

function handleForgetPasswordKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' && event.key !== ' ') {
    return;
  }
  event.preventDefault();
  (event.currentTarget as HTMLElement | null)?.click();
}

onMounted(async () => {
  await nextTick();
  const el = loginRoot.value?.querySelector<HTMLElement>('.vben-link');
  if (!el || el.dataset.brandFocusable === 'true') {
    return;
  }
  forgetPasswordEl = el;
  el.dataset.brandFocusable = 'true';
  el.tabIndex = 0;
  el.setAttribute('role', 'link');
  el.addEventListener('keydown', handleForgetPasswordKeydown);
});

onUnmounted(() => {
  forgetPasswordEl?.removeEventListener(
    'keydown',
    handleForgetPasswordKeydown,
  );
  forgetPasswordEl = null;
});

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      component: 'VbenInput',
      componentProps: {
        'aria-label': '用户名',
        'autocomplete': 'username',
        placeholder: $t('authentication.usernameTip'),
      },
      fieldName: 'username',
      label: $t('authentication.username'),
      rules: z
        .string()
        .min(1, { message: $t('authentication.usernameTip') })
        .default(import.meta.env.VITE_APP_DEFAULT_USERNAME),
    },
    {
      component: 'VbenInputPassword',
      componentProps: {
        'aria-label': '密码',
        'autocomplete': 'current-password',
        placeholder: $t('authentication.passwordTip'),
      },
      fieldName: 'password',
      label: $t('authentication.password'),
      rules: z
        .string()
        .min(1, { message: $t('authentication.passwordTip') })
        .default(import.meta.env.VITE_APP_DEFAULT_PASSWORD),
    },
  ];
});
</script>

<template>
  <div ref="loginRoot" class="brand-login">
    <AuthenticationLogin
      ref="loginRef"
      :form-schema="formSchema"
      :loading="authStore.loginLoading"
      :show-code-login="false"
      :show-forget-password="true"
      :show-qrcode-login="false"
      :show-register="false"
      :show-remember-me="true"
      :show-third-party-login="false"
      :sub-title="LOGIN_COPY.subtitle"
      :submit-button-text="LOGIN_COPY.submit"
      :title="LOGIN_COPY.title"
      @submit="handleLogin"
      @third-login="handleThirdLogin"
    />
    <Verification
      ref="verifyRef"
      v-if="captchaEnable"
      :captcha-type="captchaType"
      :check-captcha-api="checkCaptcha"
      :get-captcha-api="getCaptcha"
      :img-size="{ width: '400px', height: '200px' }"
      mode="pop"
      @on-success="handleVerifySuccess"
    />
  </div>
</template>

<style scoped>
.brand-login {
  width: 100%;
  color: var(--brand-text, #e2e8f0);
}

/* ---------- 标题 ---------- */
.brand-login :deep(.mb-7) {
  margin-bottom: 18px;
}

.brand-login :deep(h2) {
  margin: 0 0 8px;
  font-size: clamp(1.5rem, 2.2vw, 1.9rem);
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: 0.08em;
  color: var(--brand-text, #e2e8f0);
}

.brand-login :deep(h2 + p) {
  margin: 0;
  font-size: 0.875rem;
  letter-spacing: 0.04em;
  color: var(--brand-text-muted, rgb(226 232 240 / 65%));
}

/* ---------- 表单字段 ---------- */
.brand-login :deep(.form-is-required) {
  padding-bottom: 14px;
}

.brand-login :deep(input) {
  height: 44px;
  color: var(--brand-input-text, #e2e8f0);
  background-color: var(--brand-input-bg, rgb(7 13 24 / 72%));
  border: 1px solid var(--brand-input-border, rgb(148 163 184 / 22%));
  border-radius: 10px;
  transition:
    background-color 120ms ease,
    border-color 120ms ease,
    box-shadow 120ms ease;
}

.brand-login :deep(input::placeholder) {
  color: var(--brand-text-muted, rgb(226 232 240 / 65%));
}

.brand-login :deep(input:focus-visible) {
  outline: none;
  border-color: var(--brand-accent, #22d3ee);
  box-shadow:
    0 0 0 2px var(--brand-focus, rgb(34 211 238 / 32%)),
    0 0 18px var(--brand-focus, rgb(34 211 238 / 32%));
}

/* 保留校验失败状态的可辨识度 */
.brand-login :deep(input.border-destructive),
.brand-login :deep(input[aria-invalid='true']) {
  border-color: var(--brand-alert, #f97316);
}

.brand-login :deep(input.border-destructive:focus-visible),
.brand-login :deep(input[aria-invalid='true']:focus-visible) {
  box-shadow:
    0 0 0 2px rgb(249 115 22 / 28%),
    0 0 18px rgb(249 115 22 / 24%);
}

/* 字段交错入场，80ms 一档 */
.brand-login :deep(input) {
  animation: brand-field-enter 260ms cubic-bezier(0.22, 0.61, 0.36, 1) both;
  animation-delay: 80ms;
}

.brand-login :deep(input[autocomplete='current-password']) {
  animation-delay: 160ms;
}

@keyframes brand-field-enter {
  from {
    opacity: 0;
    transform: translateY(8px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ---------- 记住我 / 忘记密码 ---------- */
.brand-login :deep(.mb-6) {
  margin-bottom: 18px;
}

.brand-login :deep(label) {
  font-size: 0.85rem;
  color: var(--brand-text-muted, rgb(226 232 240 / 65%));
}

.brand-login :deep([data-slot='checkbox']) {
  background-color: transparent;
  border-color: var(--brand-input-border, rgb(148 163 184 / 22%));
}

.brand-login :deep([data-slot='checkbox'][data-state='checked']) {
  color: var(--brand-checkbox-text, #04141c);
  background-color: var(--brand-accent, #22d3ee);
  border-color: var(--brand-accent, #22d3ee);
}

.brand-login :deep([data-slot='checkbox']:focus-visible) {
  outline: 2px solid var(--brand-accent, #22d3ee);
  outline-offset: 2px;
  box-shadow: none;
}

.brand-login :deep(.vben-link) {
  color: var(--brand-text-muted, rgb(226 232 240 / 65%));
  transition: color 120ms ease;
}

.brand-login :deep(.vben-link:hover) {
  color: var(--brand-accent, #22d3ee);
}

.brand-login :deep(.vben-link:focus-visible) {
  color: var(--brand-accent, #22d3ee);
  outline: 2px solid var(--brand-accent, #22d3ee);
  outline-offset: 3px;
  border-radius: 4px;
}

/* ---------- 登录按钮 ---------- */
.brand-login :deep(button[aria-label='login']) {
  position: relative;
  height: 44px;
  overflow: hidden;
  font-weight: 600;
  letter-spacing: 0.18em;
  color: var(--brand-button-text, #04141c);
  background-color: transparent;
  background-image: linear-gradient(
    120deg,
    var(--brand-button-from, #22d3ee) 0%,
    var(--brand-button-to, #38bdf8) 100%
  );
  border: none;
  border-radius: 10px;
  box-shadow: 0 10px 26px var(--brand-focus, rgb(34 211 238 / 32%));
  transition:
    transform 120ms ease,
    filter 200ms ease,
    box-shadow 200ms ease;
}

.brand-login :deep(button[aria-label='login']:hover:not(:disabled)) {
  filter: brightness(1.04);
}

.brand-login :deep(button[aria-label='login']:active:not(:disabled)) {
  transform: scale(0.98);
}

.brand-login :deep(button[aria-label='login']:focus-visible) {
  outline: 2px solid var(--brand-accent, #22d3ee);
  outline-offset: 3px;
}

.brand-login :deep(button[aria-label='login'])::after {
  position: absolute;
  inset: -40% -20%;
  pointer-events: none;
  content: '';
  background: linear-gradient(
    105deg,
    transparent 35%,
    rgb(255 255 255 / 50%) 50%,
    transparent 65%
  );
  opacity: 0;
  transform: translateX(-120%) rotate(6deg);
}

.brand-login :deep(button[aria-label='login']:hover:not(:disabled))::after {
  animation: brand-button-shimmer 2s ease-in-out;
}

@keyframes brand-button-shimmer {
  0% {
    opacity: 0;
    transform: translateX(-120%) rotate(6deg);
  }

  20% {
    opacity: 0.9;
  }

  100% {
    opacity: 0;
    transform: translateX(120%) rotate(6deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .brand-login :deep(input) {
    animation: brand-field-fade 150ms ease-out both;
    animation-delay: 0ms !important;
  }

  .brand-login :deep(button[aria-label='login'])::after {
    animation: none;
  }

  .brand-login :deep(button[aria-label='login']:active:not(:disabled)) {
    transform: none;
  }
}

@keyframes brand-field-fade {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}
</style>
