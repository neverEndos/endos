import { defineAsyncComponent } from 'vue';

/** Kiosk 模块声明。新增业务模块时在此注册，不在 Shell 中写死导航。 */
export interface KioskModuleDefinition {
  /** 异步加载的真实模块组件 */
  component: ReturnType<typeof defineAsyncComponent>;
  /** 深链接与 query 使用的稳定 key */
  key: string;
  /** 导航排序，数值小的在前 */
  order: number;
  /** 进入模块必须同时具备的全部权限 */
  permissions: string[];
  title: string;
}

export const kioskModules: KioskModuleDefinition[] = [
  {
    component: defineAsyncComponent(
      () => import('./modules/monitor/index.vue'),
    ),
    key: 'monitor',
    order: 10,
    permissions: [
      'collector:dashboard:query',
      'collector:device:query',
      'collector:trend:query',
      'collector:channel:query',
    ],
    title: '实时监控',
  },
];

/** 导航只由注册表和真实权限码生成，不依赖角色名称。 */
export function getAccessibleKioskModules(accessCodes: readonly string[]) {
  const granted = new Set(accessCodes);
  return kioskModules
    .filter((module) =>
      module.permissions.every((permission) => granted.has(permission)),
    )
    .sort((left, right) => left.order - right.order);
}
