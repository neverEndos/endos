<script lang="ts" setup>
import type { CollectorApi } from '#/api/collector';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import {
  Alert,
  Button,
  Card,
  Empty,
  Spin,
  Tag,
} from 'antdv-next';

import {
  getAlarmPage,
  getAlarmStateList,
  getDashboardLatest,
  getDeviceSimpleList,
  getHealthSummary,
} from '#/api/collector';
import {
  getAlarmEventTypeConfig,
  getAlarmLevelConfig,
} from '#/views/collector/alarm/data';
import ActiveAlarmPanel from '#/views/collector/components/active-alarm-panel.vue';
import {
  formatShanghaiTime,
  resolveApiErrorMessage,
} from '#/views/collector/components/utils';

defineOptions({ name: 'Workspace' });

interface DeviceOverviewItem {
  deviceId: number;
  deviceName: string;
  error?: string;
  loading: boolean;
  online: boolean | null;
}

interface QuickEntry {
  code: string;
  color: string;
  description: string;
  path: string;
  title: string;
}

const router = useRouter();
const userStore = useUserStore();
const { hasAccessByCodes } = useAccess();

const hasHealthAccess = computed(() =>
  hasAccessByCodes(['collector:health:query']),
);
const hasAlarmAccess = computed(() =>
  hasAccessByCodes(['collector:alarm:query']),
);
const hasDeviceAccess = computed(() =>
  hasAccessByCodes(['collector:device:query']),
);
const hasDashboardAccess = computed(() =>
  hasAccessByCodes(['collector:dashboard:query']),
);

const quickEntryDefs: QuickEntry[] = [
  {
    code: 'collector:dashboard:query',
    color: '#1677ff',
    description: '逐设备查看各通道最新采样',
    path: '/collector/dashboard',
    title: '最新值看板',
  },
  {
    code: 'collector:device:query',
    color: '#13c2c2',
    description: '管理采集设备与轮询参数',
    path: '/collector/device',
    title: '设备管理',
  },
  {
    code: 'collector:alarm:query',
    color: '#fa541c',
    description: '查看报警事件与当前活动报警',
    path: '/collector/alarm',
    title: '报警记录',
  },
  {
    code: 'collector:trend:query',
    color: '#722ed1',
    description: '分析通道历史趋势与波动区间',
    path: '/collector/trend',
    title: '趋势分析',
  },
  {
    code: 'collector:aggregate:query',
    color: '#fa8c16',
    description: '按粒度重算历史聚合数据',
    path: '/collector/aggregate',
    title: '聚合回填',
  },
  {
    code: 'collector:retention:query',
    color: '#52c41a',
    description: '查看数据保留与分区策略',
    path: '/collector/retention',
    title: '保留策略',
  },
];

const quickEntries = computed(() =>
  quickEntryDefs.filter((entry) => hasAccessByCodes([entry.code])),
);

// ===== 运行摘要 =====
const health = ref<CollectorApi.HealthSummary>();
const healthLoading = ref(false);
const healthError = ref<string>();

// ===== 当前报警 =====
const alarms = ref<CollectorApi.AlarmState[]>([]);
const alarmsLoading = ref(false);
const alarmsError = ref<string>();

// ===== 最近报警事件 =====
const recent = ref<CollectorApi.Alarm[]>([]);
const recentLoading = ref(false);
const recentError = ref<string>();

// ===== 设备概览 =====
const devices = ref<CollectorApi.DeviceSimple[]>([]);
const devicesLoading = ref(false);
const devicesError = ref<string>();
const overviewItems = ref<DeviceOverviewItem[]>([]);
const overviewLoading = ref(false);
const overviewError = ref<string>();

const refreshing = ref(false);

const MAX_OVERVIEW_DEVICES = 10;

const nickname = computed(
  () => userStore.userInfo?.nickname || userStore.userInfo?.username || '管理员',
);

const onlineRatioText = computed(() => {
  const process = health.value?.process;
  if (!process || !process.deviceTotal) {
    return '--';
  }
  return `${process.deviceOnline ?? '--'} / ${process.deviceTotal}`;
});

const heartbeatText = computed(() => {
  const process = health.value?.process;
  if (!process) {
    return healthError.value ? '--' : '尚未收到心跳';
  }
  return formatShanghaiTime(process.heartbeatTime);
});

const writeLagText = computed(() => {
  const lag = health.value?.process?.writeLagMs;
  return lag === null || lag === undefined ? '--' : `${lag} ms`;
});

const activeAlarmCount = computed(() =>
  health.value ? String(health.value.alarm.activeCount) : '--',
);

const highestLevel = computed(() => health.value?.alarm.highestLevel ?? 0);
const highestLevelConfig = computed(() => getAlarmLevelConfig(highestLevel.value));

const PROCESS_STATUS_TEXT: Record<string, string> = {
  degraded: '降级运行',
  running: '运行中',
  starting: '启动中',
  stopped: '已停止',
};
const processStatusText = computed(() => {
  if (!health.value) return '--';
  const status = health.value.process?.status;
  if (!status) return '尚未收到心跳';
  return PROCESS_STATUS_TEXT[status] ?? status;
});

/** 当前报警条数用于卡片右上角提示；面板内部不再重复标题 */
const activeAlarmListCount = computed(
  () => alarms.value.filter((alarm) => (alarm.level ?? 0) > 0).length,
);

const overviewSummary = computed(() => {
  if (devices.value.length > MAX_OVERVIEW_DEVICES) {
    const online = overviewItems.value.filter((item) => item.online === true).length;
    return `共 ${devices.value.length} 台设备，其中 ${online} 台在线；超过 ${MAX_OVERVIEW_DEVICES} 台时仅显示汇总，请前往最新值看板逐台查看。`;
  }
  return '';
});

function openPath(path: string) {
  void router.push(path);
}

function openAlarmRecord() {
  void router.push('/collector/alarm');
}

async function loadHealth() {
  if (!hasHealthAccess.value) return;
  const deviceId = devices.value[0]?.id;
  if (deviceId === undefined) {
    healthError.value = '暂无采集设备，无法加载运行摘要';
    return;
  }
  healthLoading.value = true;
  try {
    const body = await getHealthSummary(deviceId);
    if (!body) {
      throw new Error('运行摘要接口返回为空');
    }
    health.value = body;
    healthError.value = undefined;
  } catch (error) {
    // 保留上一次成功的运行摘要，仅提示本次刷新失败
    healthError.value = resolveApiErrorMessage(error, '运行摘要加载失败');
  } finally {
    healthLoading.value = false;
  }
}

async function loadAlarms() {
  if (!hasAlarmAccess.value) return;
  alarmsLoading.value = true;
  try {
    alarms.value = await getAlarmStateList();
    alarmsError.value = undefined;
  } catch (error) {
    // 保留上一次成功数据，不把列表清空成“无报警”
    alarmsError.value = resolveApiErrorMessage(error, '当前报警加载失败');
  } finally {
    alarmsLoading.value = false;
  }
}

async function loadRecent() {
  if (!hasAlarmAccess.value) return;
  recentLoading.value = true;
  try {
    const page = await getAlarmPage({ pageNo: 1, pageSize: 5 });
    recent.value = page?.list ?? [];
    recentError.value = undefined;
  } catch (error) {
    recentError.value = resolveApiErrorMessage(error, '最近报警加载失败');
  } finally {
    recentLoading.value = false;
  }
}

async function loadOverview() {
  overviewError.value = undefined;
  if (!hasDeviceAccess.value || !hasDashboardAccess.value) {
    overviewItems.value = [];
    return;
  }
  const list = devices.value;
  if (list.length === 0) {
    overviewItems.value = [];
    return;
  }
  const previous = new Map(
    overviewItems.value.map((item) => [item.deviceId, item]),
  );
  overviewItems.value = list.map((device) => {
    const item = previous.get(device.id);
    return {
      deviceId: device.id,
      deviceName: device.deviceName,
      error: item?.error,
      loading: true,
      online: item?.online ?? null,
    };
  });
  if (list.length > MAX_OVERVIEW_DEVICES) {
    // 超过上限只展示汇总，不再逐台请求，避免打开工作台即产生大量请求
    overviewItems.value = overviewItems.value.map((item) => ({
      ...item,
      loading: false,
    }));
    return;
  }
  overviewLoading.value = true;
  try {
    const results = await Promise.allSettled(
      list.map((device) => getDashboardLatest(device.id)),
    );
    overviewItems.value = overviewItems.value.map((item, index) => {
      const result = results[index];
      if (result?.status === 'fulfilled' && result.value) {
        return {
          ...item,
          error: undefined,
          loading: false,
          online: result.value.device.online,
        };
      }
      return {
        ...item,
        error: resolveApiErrorMessage(
          result?.status === 'rejected' ? result.reason : undefined,
          '最新值加载失败',
        ),
        loading: false,
      };
    });
  } finally {
    overviewLoading.value = false;
  }
}

async function loadDevices() {
  if (!hasDeviceAccess.value) {
    devices.value = [];
    return;
  }
  devicesLoading.value = true;
  devicesError.value = undefined;
  try {
    devices.value = await getDeviceSimpleList();
  } catch (error) {
    devices.value = [];
    devicesError.value = resolveApiErrorMessage(
      error,
      '采集设备加载失败',
    );
  } finally {
    devicesLoading.value = false;
  }
}

async function refreshAll() {
  if (refreshing.value) return;
  refreshing.value = true;
  try {
    await loadDevices();
    await Promise.all([
      loadHealth(),
      loadAlarms(),
      loadRecent(),
      loadOverview(),
    ]);
  } finally {
    refreshing.value = false;
  }
}

onMounted(() => {
  void refreshAll();
});
</script>

<template>
  <Page>
    <div class="flex flex-col gap-4">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 class="m-0 text-xl font-semibold">
            您好，{{ nickname }}
          </h1>
          <div class="mt-1 text-sm opacity-60">
            采集器运行入口：设备状态、当前报警与常用操作集中在此
          </div>
        </div>
        <Button :loading="refreshing" @click="refreshAll">刷新</Button>
      </div>

      <!-- 当前统计：依赖采集运行摘要，无权限时整组隐藏并给出说明 -->
      <Card v-if="hasHealthAccess" size="small" title="当前统计">
        <Alert
          v-if="healthError"
          type="warning"
          show-icon
          :message="`运行摘要刷新失败：${healthError}`"
          description="下方数值为上一次成功加载的数据；若从未成功加载则显示 --。"
          class="mb-3"
        />
        <Spin :spinning="healthLoading">
          <div class="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-5">
            <div>
              <div class="text-xs opacity-60">在线设备 / 设备总数</div>
              <div class="mt-1 text-lg font-semibold">{{ onlineRatioText }}</div>
            </div>
            <div>
              <div class="text-xs opacity-60">活动报警数</div>
              <div class="mt-1 flex items-center gap-2">
                <span class="text-lg font-semibold">{{ activeAlarmCount }}</span>
                <Tag
                  v-if="highestLevel > 0"
                  :color="highestLevelConfig.color"
                >
                  最高 {{ highestLevelConfig.text }}
                </Tag>
                <Tag v-else color="success">无活动报警</Tag>
              </div>
            </div>
            <div>
              <div class="text-xs opacity-60">采集器心跳时间</div>
              <div class="mt-1 font-medium">{{ heartbeatText }}</div>
            </div>
            <div>
              <div class="text-xs opacity-60">写入延迟（进程级）</div>
              <div class="mt-1 font-medium">{{ writeLagText }}</div>
            </div>
            <div>
              <div class="text-xs opacity-60">心跳状态</div>
              <div class="mt-1 font-medium">{{ processStatusText }}</div>
            </div>
          </div>
        </Spin>
        <div class="mt-2 text-xs opacity-50">
          运行摘要取自所选设备的健康接口；时间按 Asia/Shanghai 展示。
        </div>
      </Card>
      <Alert
        v-else
        type="info"
        show-icon
        message="运行摘要需要 collector:health:query 权限，当前账号不展示在线设备、活动报警与心跳统计。"
      />

      <!-- 快捷入口：按权限显示 -->
      <Card size="small" title="快捷入口">
        <div
          v-if="quickEntries.length > 0"
          class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6"
        >
          <button
            v-for="entry in quickEntries"
            :key="entry.path"
            type="button"
            class="quick-entry"
            :data-testid="`quick-entry-${entry.path}`"
            @click="openPath(entry.path)"
          >
            <span
              class="quick-dot"
              :style="{ backgroundColor: entry.color }"
              aria-hidden="true"
            ></span>
            <span class="quick-title">{{ entry.title }}</span>
            <span class="quick-desc">{{ entry.description }}</span>
          </button>
        </div>
        <Empty
          v-else
          description="当前账号没有可进入的采集器页面，请联系管理员授权"
        />
      </Card>

      <div class="grid gap-4 xl:grid-cols-2">
        <Card size="small">
          <template #title>
            <div class="flex items-center gap-2">
              <span>当前报警</span>
              <span v-if="hasAlarmAccess" class="text-xs font-normal opacity-60">
                活动 {{ activeAlarmListCount }} 条
              </span>
            </div>
          </template>
          <ActiveAlarmPanel
            :alarms="alarms"
            :error="alarmsError"
            :loading="alarmsLoading"
            max-height="360px"
            :show-header="false"
            @refresh="loadAlarms"
          />
          <div v-if="!hasAlarmAccess" class="mt-2 text-xs opacity-60">
            如需查看当前报警，请为账号授予 collector:alarm:query 权限。
          </div>
        </Card>

        <Card size="small" title="最近报警事件">
          <Alert
            v-if="recentError"
            type="warning"
            show-icon
            :message="`最近报警加载失败：${recentError}`"
            class="mb-2"
          />
          <Spin :spinning="recentLoading">
            <div v-if="recent.length > 0" class="flex flex-col">
              <button
                v-for="item in recent"
                :key="item.id"
                type="button"
                class="recent-row"
                @click="openAlarmRecord"
              >
                <Tag :color="getAlarmLevelConfig(item.level).color">
                  {{ getAlarmLevelConfig(item.level).text }}
                </Tag>
                <span class="min-w-0 flex-1 truncate">
                  {{ item.channelName || `通道 ${item.channelId}` }}
                </span>
                <Tag :color="getAlarmEventTypeConfig(item.eventType).color">
                  {{ getAlarmEventTypeConfig(item.eventType).text }}
                </Tag>
                <span class="text-xs opacity-60">
                  {{ formatShanghaiTime(item.triggeredAt) }}
                </span>
              </button>
            </div>
            <Empty
              v-else
              :description="
                hasAlarmAccess
                  ? '暂无报警事件'
                  : '当前账号没有查看报警记录的权限'
              "
              class="py-6"
            />
          </Spin>
          <div
            v-if="hasAlarmAccess && recent.length > 0"
            class="mt-2 text-right"
          >
            <Button type="link" size="small" @click="openAlarmRecord">
              查看全部报警记录
            </Button>
          </div>
        </Card>
      </div>

      <Card size="small" title="设备概览">
        <Alert
          v-if="devicesError"
          type="warning"
          show-icon
          :message="`设备加载失败：${devicesError}`"
          class="mb-2"
        />
        <Alert
          v-if="overviewError"
          type="warning"
          show-icon
          :message="overviewError"
          class="mb-2"
        />
        <div v-if="devices.length > MAX_OVERVIEW_DEVICES" class="text-sm">
          {{ overviewSummary }}
        </div>
        <Spin :spinning="devicesLoading || overviewLoading">
          <div
            v-if="devices.length <= MAX_OVERVIEW_DEVICES && overviewItems.length > 0"
            class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5"
          >
            <div
              v-for="item in overviewItems"
              :key="item.deviceId"
              class="rounded border p-3"
            >
              <div class="flex items-center justify-between gap-2">
                <span class="min-w-0 truncate font-medium">
                  {{ item.deviceName }}
                </span>
                <Tag
                  v-if="!item.loading && item.online !== null"
                  :color="item.online ? 'success' : 'error'"
                >
                  {{ item.online ? '在线' : '离线' }}
                </Tag>
                <Tag v-else-if="!item.loading" color="default">状态未知</Tag>
              </div>
              <div v-if="item.error" class="mt-1 text-xs text-red-500">
                最新值加载失败
              </div>
            </div>
          </div>
          <Empty
            v-else-if="
              !devicesLoading &&
              !overviewLoading &&
              devices.length === 0
            "
            :description="
              hasDeviceAccess
                ? '暂无采集设备，请先在「采集设备」中完成配置'
                : '当前账号没有查看采集设备的权限'
            "
            class="py-6"
          />
        </Spin>
        <div
          v-if="devices.length <= MAX_OVERVIEW_DEVICES && devices.length > 0"
          class="mt-2 text-xs opacity-60"
        >
          在线/离线按最新值接口逐设备获取；某一设备失败时保留上一次状态并标记失败。
        </div>
      </Card>
    </div>
  </Page>
</template>

<style scoped>
.quick-entry {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
  padding: 12px;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: 8px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.quick-entry:hover {
  border-color: hsl(var(--primary) / 45%);
  box-shadow: 0 4px 14px rgb(0 0 0 / 8%);
}

.quick-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
}

.quick-title {
  font-weight: 600;
}

.quick-desc {
  font-size: 12px;
  opacity: 0.6;
}

.recent-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  width: 100%;
  padding: 8px 4px;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-bottom: 1px solid hsl(var(--border));
}

.recent-row:last-child {
  border-bottom: 0;
}

.recent-row:hover {
  background: hsl(var(--accent) / 35%);
}

@media (prefers-reduced-motion: reduce) {
  .quick-entry {
    transition: none;
  }
}
</style>
