import type { RouteRecordRaw } from 'vue-router';

const route: RouteRecordRaw = {
  component: () => import('#/views/kiosk/index.vue'),
  meta: {
    hideInMenu: true,
    noBasicLayout: true,
    title: '采集器终端',
  },
  name: 'KioskIndex',
  path: '/kiosk',
};

export default route;
