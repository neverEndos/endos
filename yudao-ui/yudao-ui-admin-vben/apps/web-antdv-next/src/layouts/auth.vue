<script lang="ts" setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';

import { AuthPageLayout } from '@vben/layouts';
import { preferences } from '@vben/preferences';

import { $t } from '#/locales';
import BrandAuthLayout from '#/views/_core/authentication/components/brand-auth-layout.vue';

const route = useRoute();
/** 仅登录页使用定制品牌布局，其余认证页（注册/扫码/忘记密码等）保持原布局 */
const isLogin = computed(() => route.name === 'Login');

const appName = computed(() => preferences.app.name);
const logo = computed(() => preferences.logo.source);
const logoDark = computed(() => preferences.logo.sourceDark);
</script>

<template>
  <BrandAuthLayout v-if="isLogin" :app-name="appName">
    <!-- RouterView 放在右侧表单区；KeepAlive 名称与登录页保持一致 -->
    <RouterView v-slot="{ Component, route: childRoute }">
      <KeepAlive :include="['Login']">
        <component :is="Component" :key="childRoute.fullPath" />
      </KeepAlive>
    </RouterView>
  </BrandAuthLayout>

  <AuthPageLayout
    v-else
    :app-name="appName"
    :logo="logo"
    :logo-dark="logoDark"
    :page-description="$t('authentication.pageDesc')"
    :page-title="$t('authentication.pageTitle')"
  >
    <!-- 自定义工具栏 -->
    <!-- <template #toolbar></template> -->
  </AuthPageLayout>
</template>
