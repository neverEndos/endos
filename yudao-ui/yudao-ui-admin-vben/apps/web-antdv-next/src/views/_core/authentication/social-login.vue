<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { AuthenticationLogin, Verification, z } from '@vben/common-ui';
import { LOGIN_PATH } from '@vben/constants';
import { isCaptchaEnable } from '@vben/hooks';
import { $t } from '@vben/locales';
import {
  clearSocialCallback,
  getSocialCallback,
  getUrlValue,
} from '@vben/utils';

import { checkCaptcha, getCaptcha } from '#/api/core/auth';
import { useAuthStore } from '#/store';

defineOptions({ name: 'SocialLogin' });

const authStore = useAuthStore();
const route = useRoute();
const router = useRouter();
const captchaEnable = isCaptchaEnable();

const loginRef = ref();
const verifyRef = ref();

const captchaType = 'blockPuzzle'; // 验证码类型：'blockPuzzle' | 'clickWord'

/** 尝试登录：当账号已经绑定，socialLogin 会直接获得 token */
const socialType = Number(getUrlValue('type'));
const redirect = (route.query.redirect as string) || getUrlValue('redirect');
const { code: socialCode, state: socialState } = getSocialCallback();
async function tryLogin() {
  // 尝试登录
  await authStore.authLogin('social', {
    type: socialType,
    code: socialCode,
    state: socialState,
  });
}

/** 处理登录 */
async function handleLogin(values: any) {
  // 如果开启验证码，则先验证验证码
  if (captchaEnable) {
    verifyRef.value.show();
    return;
  }

  // 无验证码，直接登录
  await authStore.authLogin('username', {
    ...values,
    socialType,
    socialCode,
    socialState,
  });
}

/** 验证码通过，执行登录 */
async function handleVerifySuccess({ captchaVerification }: any) {
  try {
    await authStore.authLogin('username', {
      ...(await loginRef.value.getFormApi().getValues()),
      captchaVerification,
      socialType,
      socialCode,
      socialState,
    });
  } catch (error) {
    console.error('Error in handleLogin:', error);
  }
}

/** 组件挂载时获取租户信息 */
onMounted(async () => {
  // 缺少回调参数（例如说，清理地址栏后刷新、直接访问），无法社交登录，退回登录页，并保留 redirect
  if (!socialType || !socialCode || !socialState) {
    clearSocialCallback(router.options.history);
    await router.replace({
      path: LOGIN_PATH,
      query: redirect ? { redirect } : {},
    });
    return;
  }
  // 用于登录后，基于 redirect 的重定向：回填到路由的 query，由路由守卫 decode 后跳转
  // 注意：认证布局以 fullPath 作为组件 key，回填后会重建本组件，由新的组件继续登录
  if (redirect && route.query.redirect !== redirect) {
    await router.replace({ query: { ...route.query, redirect } });
    return;
  }
  // 清理地址栏中的回调参数，避免 code 泄露或重复使用
  clearSocialCallback(router.options.history);

  await tryLogin();
});

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      component: 'VbenInput',
      componentProps: {
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
  <div>
    <AuthenticationLogin
      ref="loginRef"
      :form-schema="formSchema"
      :loading="authStore.loginLoading"
      :show-code-login="false"
      :show-qrcode-login="false"
      :show-third-party-login="false"
      :show-register="false"
      @submit="handleLogin"
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
