<script lang="ts" setup>
import type { CollectorApi } from '#/api/collector';

import { computed, ref } from 'vue';

import { useAccess } from '@vben/access';
import { useVbenDrawer } from '@vben/common-ui';

import { Alert, App, Button, Empty, Modal, Spin, Tag } from 'antdv-next';

import {
  acknowledgeAlarm,
  getTrendSeries,
} from '#/api/collector';

import {
  buildChannelColors,
  formatTrendValue,
  hasAnyPoint,
} from '../trend/data';
import TrendChart from '../trend/modules/trend-chart.vue';
import { formatAlarmType, getAlarmLevelConfig } from '../alarm/data';
import {
  formatDuration,
  formatNumeric,
  formatShanghaiTime,
  resolveApiErrorMessage,
  toMillis,
} from './utils';

defineOptions({ name: 'CollectorActiveAlarmDrawer' });

const emit = defineEmits<{
  success: [];
}>();

const { hasAccessByCodes } = useAccess();
// 使用 App 上下文的消息实例，避免静态 message 在渲染期取不到 holder
const { message } = App.useApp();
const hasAckAccess = computed(() =>
  hasAccessByCodes(['collector:alarm:acknowledge']),
);
const hasTrendAccess = computed(() =>
  hasAccessByCodes(['collector:trend:query']),
);

interface DrawerData {
  alarm: CollectorApi.AlarmState;
  readonly?: boolean;
}

const alarm = ref<CollectorApi.AlarmState>();
const readonly = ref(false);
const acknowledgeLoading = ref(false);

const levelConfig = computed(() => getAlarmLevelConfig(alarm.value?.level));
const activeAlarmId = computed(() => alarm.value?.activeAlarmId ?? undefined);
const canAcknowledge = computed(
  () =>
    hasAckAccess.value &&
    !readonly.value &&
    (alarm.value?.level ?? 0) > 0,
);
const acknowledgeDisabledReason = computed(() => {
  if (!activeAlarmId.value) {
    return '未找到对应的活动报警事件，无法确认';
  }
  return '';
});

// ===== 趋势 =====
type TrendState = 'error' | 'idle' | 'loaded' | 'loading';

const trendState = ref<TrendState>('idle');
const trendResult = ref<CollectorApi.TrendSeriesResult>();
const trendError = ref<string>();
const trendRange = ref<{ from: number; to: number }>();

const chartSeries = computed(() => {
  const current = alarm.value;
  if (!current || !trendResult.value) return [];
  return trendResult.value.series.filter(
    (series) => series.channelId === current.channelId && series.kind !== 'raw',
  );
});
const hasTrendData = computed(() => hasAnyPoint(chartSeries.value));
const chartColors = computed(() => {
  const channelId = alarm.value?.channelId;
  return channelId ? buildChannelColors([channelId]) : {};
});
const rangeSeconds = computed(() => {
  const range = trendRange.value;
  return range ? Math.round((range.to - range.from) / 1000) : 0;
});

/** 当前图表真实数据 y 范围；无有效点时为 null */
const dataValueRange = computed((): null | { max: number; min: number } => {
  let min = Number.POSITIVE_INFINITY;
  let max = Number.NEGATIVE_INFINITY;
  for (const series of chartSeries.value) {
    for (const point of series.points) {
      for (const candidate of [point.avg, point.value, point.min, point.max]) {
        if (
          candidate === null ||
          candidate === undefined ||
          !Number.isFinite(candidate)
        ) {
          continue;
        }
        min = Math.min(min, candidate);
        max = Math.max(max, candidate);
      }
    }
  }
  return Number.isFinite(min) && Number.isFinite(max) ? { max, min } : null;
});

/** 阈值标线只在当前数据范围内绘制；越界不画并给出提示 */
const markLines = computed(() => {
  const threshold = alarm.value?.lastThreshold;
  const range = dataValueRange.value;
  if (threshold === null || threshold === undefined || !range) return [];
  if (!Number.isFinite(threshold)) return [];
  if (threshold < range.min || threshold > range.max) return [];
  return [
    {
      value: threshold,
      label: '当前阈值',
      color: '#fa8c16',
      dashed: true,
    },
  ];
});

const thresholdNotice = computed(() => {
  const threshold = alarm.value?.lastThreshold;
  const range = dataValueRange.value;
  if (threshold === null || threshold === undefined || !range) return '';
  if (!Number.isFinite(threshold)) return '';
  if (threshold >= range.min && threshold <= range.max) return '';
  return `阈值 ${formatTrendValue(threshold)} 不在当前数据范围（${formatTrendValue(
    range.min,
  )} ~ ${formatTrendValue(range.max)}）内，未绘制阈值标线`;
});

const markTimes = computed(() => {
  const since = toMillis(alarm.value?.since);
  const range = trendRange.value;
  if (since === undefined || !range) return [];
  if (since < range.from || since > range.to) return [];
  return [
    {
      t: since,
      label: '报警开始',
      color: '#fa541c',
      dashed: false,
    },
  ];
});

const trendRangeText = computed(() =>
  trendRange.value
    ? `${formatShanghaiTime(trendRange.value.from)} ~ ${formatShanghaiTime(
        trendRange.value.to,
      )}`
    : '--',
);

function resetTrend() {
  trendState.value = 'idle';
  trendResult.value = undefined;
  trendError.value = undefined;
  trendRange.value = undefined;
}

 /** 以 since 为中心前后 30 分钟，右边界截断到当前时间；无有效 since 时退化为最近 1 小时 */
function resolveTrendRange() {
  const now = Date.now();
  const since = toMillis(alarm.value?.since);
  if (since === undefined) {
    return { from: now - 3600 * 1000, to: now };
  }
  const from = since - 30 * 60 * 1000;
  const to = Math.min(since + 30 * 60 * 1000, now);
  if (to <= from) {
    return { from: now - 3600 * 1000, to: now };
  }
  return { from, to };
}

async function loadTrend() {
  const current = alarm.value;
  if (!current || !hasTrendAccess.value) return;
  if (!current.channelId) {
    trendState.value = 'error';
    trendError.value = '该报警没有关联通道，无法查看趋势';
    return;
  }
  const range = resolveTrendRange();
  trendState.value = 'loading';
  trendError.value = undefined;
  try {
    const body = await getTrendSeries({
      channelIds: [current.channelId],
      from: range.from,
      granularity: 'auto',
      to: range.to,
    });
    if (!body) {
      throw new Error('趋势接口返回为空');
    }
    trendResult.value = body;
    trendRange.value = range;
    trendState.value = 'loaded';
  } catch (error) {
    trendState.value = 'error';
    trendError.value = resolveApiErrorMessage(error, '趋势查询失败');
  }
}

function confirmAcknowledge() {
  return new Promise<boolean>((resolve) => {
    Modal.confirm({
      title: '确认该报警？',
      content:
        '确认后状态将标记为已确认，但不会清除报警；数值恢复正常后仍由采集器自动恢复。',
      okText: '确认报警',
      cancelText: '取消',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
}

async function handleAcknowledge() {
  const id = activeAlarmId.value;
  if (!id) {
    message.warning('未找到对应的活动报警事件，无法确认');
    return;
  }
  const confirmed = await confirmAcknowledge();
  if (!confirmed) return;
  acknowledgeLoading.value = true;
  try {
    await acknowledgeAlarm(id);
    message.success('报警已确认');
    emit('success');
    await drawerApi.close();
  } catch (error) {
    message.error(`确认失败：${resolveApiErrorMessage(error)}`);
  } finally {
    acknowledgeLoading.value = false;
  }
}

const [Drawer, drawerApi] = useVbenDrawer({
  title: '当前报警详情',
  class: '!w-[780px] max-w-[94vw]',
  footer: false,
  onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      alarm.value = undefined;
      readonly.value = false;
      resetTrend();
      return;
    }
    const data = drawerApi.getData() as DrawerData | undefined;
    alarm.value = data?.alarm;
    readonly.value = Boolean(data?.readonly);
    resetTrend();
  },
});
</script>

<template>
  <Drawer>
    <div v-if="alarm" class="mx-4 flex min-w-0 flex-col gap-3">
      <div class="flex flex-wrap items-center gap-2">
        <Tag :color="levelConfig.color">{{ levelConfig.text }}</Tag>
        <Tag color="blue">
          {{ formatAlarmType(alarm.thresholdType) }}
        </Tag>
        <span class="min-w-0 flex-1 truncate text-sm font-semibold">
          {{ alarm.channelName || `通道 ${alarm.channelId}` }}
        </span>
      </div>

      <div
        class="grid grid-cols-2 gap-x-4 gap-y-2 rounded border p-3 text-sm md:grid-cols-3"
      >
        <div class="min-w-0">
          <span class="text-gray-500">设备：</span>
          <span class="font-medium">
            {{ alarm.deviceName || `设备 ${alarm.deviceId}` }}
          </span>
        </div>
        <div class="min-w-0">
          <span class="text-gray-500">传感器：</span>
          <span class="font-medium">
            {{ alarm.sensorName || `传感器 ${alarm.sensorId}` }}
          </span>
        </div>
        <div class="min-w-0">
          <span class="text-gray-500">通道：</span>
          <span class="font-medium">
            {{ alarm.channelName || `通道 ${alarm.channelId}` }}
          </span>
        </div>
        <div>
          <span class="text-gray-500">级别：</span>
          <span class="font-medium">{{ levelConfig.text }}</span>
        </div>
        <div>
          <span class="text-gray-500">阈值类型：</span>
          <span class="font-medium">
            {{ formatAlarmType(alarm.thresholdType) }}
          </span>
        </div>
        <div>
          <span class="text-gray-500">持续时长：</span>
          <span class="font-medium">{{ formatDuration(alarm.since) }}</span>
        </div>
        <div>
          <span class="text-gray-500">最近值：</span>
          <span class="font-medium">{{ formatNumeric(alarm.lastValue) }}</span>
        </div>
        <div>
          <span class="text-gray-500">阈值：</span>
          <span class="font-medium">
            {{ formatNumeric(alarm.lastThreshold) }}
          </span>
        </div>
        <div>
          <span class="text-gray-500">最近采样：</span>
          <span class="font-medium">
            {{ formatShanghaiTime(alarm.lastSampleTime) }}
          </span>
        </div>
        <div>
          <span class="text-gray-500">开始时间：</span>
          <span class="font-medium">{{ formatShanghaiTime(alarm.since) }}</span>
        </div>
        <div>
          <span class="text-gray-500">状态更新时间：</span>
          <span class="font-medium">
            {{ formatShanghaiTime(alarm.updatedTime) }}
          </span>
        </div>
      </div>

      <div class="flex flex-wrap items-center gap-2 border-t pt-3">
        <Button
          v-if="canAcknowledge"
          type="primary"
          :loading="acknowledgeLoading"
          :disabled="!activeAlarmId || acknowledgeLoading"
          :title="acknowledgeDisabledReason || undefined"
          data-testid="active-alarm-acknowledge"
          @click="handleAcknowledge"
        >
          确认
        </Button>
        <span
          v-if="!readonly && !hasAckAccess"
          class="text-xs text-gray-500"
        >
          当前账号没有报警确认权限（collector:alarm:acknowledge）
        </span>
        <span v-if="readonly" class="text-xs text-gray-500">
          大屏为只读视图，不能确认报警
        </span>
        <Button
          v-if="hasTrendAccess"
          :loading="trendState === 'loading'"
          data-testid="active-alarm-trend"
          @click="loadTrend"
        >
          查看近 1 小时趋势
        </Button>
        <span v-else class="text-xs text-gray-500">
          当前账号没有趋势查询权限（collector:trend:query）
        </span>
        <Button class="ml-auto" @click="drawerApi.close()">关闭</Button>
      </div>

      <div
        v-if="hasTrendAccess && trendState !== 'idle'"
        class="flex flex-col gap-2"
      >
        <div class="flex flex-wrap items-center gap-2 text-xs">
          <Tag v-if="trendRange" color="blue">范围：{{ trendRangeText }}</Tag>
          <span class="text-gray-500">
            以报警开始时间为中心，前后各 30 分钟，右边界不超过当前时间
          </span>
        </div>

        <Alert
          v-if="trendState === 'error'"
          type="error"
          show-icon
          :message="`趋势查询失败：${trendError}`"
        >
          <template #action>
            <Button size="small" @click="loadTrend">重试</Button>
          </template>
        </Alert>
        <Alert
          v-else-if="thresholdNotice"
          type="warning"
          show-icon
          :message="thresholdNotice"
        />

        <Spin :spinning="trendState === 'loading'">
          <div class="relative h-[320px] w-full">
            <TrendChart
              v-if="hasTrendData"
              class="h-full"
              :colors="chartColors"
              :mark-lines="markLines"
              :mark-times="markTimes"
              :range-seconds="rangeSeconds"
              :series="chartSeries"
            />
            <div
              v-else-if="trendState !== 'loading' && trendState !== 'error'"
              class="absolute inset-0 flex items-center justify-center"
            >
              <Empty description="该时间范围无采样数据" />
            </div>
          </div>
        </Spin>
      </div>
    </div>
  </Drawer>
</template>
