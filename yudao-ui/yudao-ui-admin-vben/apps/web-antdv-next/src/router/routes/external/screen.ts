import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    component: () => import('#/views/dashboard/analytics/index.vue'),
    meta: {
      hideInMenu: true,
      noBasicLayout: true,
      title: '采集器运行大屏',
    },
    name: 'CollectorScreen',
    path: '/screen',
  },
];

export default routes;
