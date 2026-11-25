<script lang="ts" setup>
import type { CollectorApi } from '#/api/collector';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import { useAccess } from '@vben/access';
import { useVbenDrawer } from '@vben/common-ui';

import { Empty, Tag } from 'antdv-next';

import { formatAlarmType, getAlarmLevelConfig } from '../alarm/data';
import ActiveAlarmDrawer from './active-alarm-drawer.vue';
import {
  formatDuration,
  formatNumeric,
  formatShanghaiTime,
  toMillis,
} from './utils';

defineOptions({ name: 'CollectorActiveAlarmPanel' });

const props = withDefaults(
  defineProps<{
    /** 当前活动报警（由父级轮询后传入） */
    alarms: CollectorApi.AlarmState[];
    /** 深色大屏样式 */
    dark?: boolean;
    /** 接口失败信息；保留旧数据时仍会展示 */
    error?: string;
    loading?: boolean;
    /** 列表最大高度 */
    maxHeight?: string;
    /** 只读模式：大屏使用，详情抽屉不提供任何写操作（如报警确认） */
    readonly?: boolean;
    /** 是否显示组件自带的标题与数量；嵌入已有标题的卡片时关闭 */
    showHeader?: boolean;
    title?: string;
  }>(),
  {
    dark: false,
    error: undefined,
    loading: false,
    maxHeight: '320px',
    readonly: false,
    showHeader: true,
    title: '当前报警',
  },
);

const emit = defineEmits<{
  /** 详情抽屉确认成功后通知父级重新拉取 */
  refresh: [];
}>();

const { hasAccessByCodes } = useAccess();
const hasAlarmAccess = computed(() =>
  hasAccessByCodes(['collector:alarm:query']),
);

/** 持续时长每秒本地推进；页面隐藏时暂停，避免后台无意义刷新 */
const clockNow = ref(Date.now());
let clockTimer: ReturnType<typeof setInterval> | undefined;

onMounted(() => {
  clockTimer = setInterval(() => {
    if (!document.hidden) {
      clockNow.value = Date.now();
    }
  }, 1000);
});

onBeforeUnmount(() => {
  if (clockTimer !== undefined) clearInterval(clockTimer);
});

/** 按级别降序、开始时间升序排列；0 级状态不属于活动报警，直接过滤 */
const sortedAlarms = computed(() =>
  [...props.alarms]
    .filter((alarm) => (alarm.level ?? 0) > 0)
    .sort(
      (left, right) =>
        (right.level ?? 0) - (left.level ?? 0) ||
        (toMillis(left.since) ?? 0) - (toMillis(right.since) ?? 0),
    ),
);

const [DrawerComp, drawerApi] = useVbenDrawer({
  connectedComponent: ActiveAlarmDrawer,
  destroyOnClose: true,
});

function openDetail(alarm: CollectorApi.AlarmState) {
  drawerApi.setData({ alarm, readonly: props.readonly }).open();
}
</script>

<template>
  <div
    class="active-alarm-panel"
    :class="{ 'active-alarm-panel--dark': dark }"
  >
    <DrawerComp @success="emit('refresh')" />
    <div v-if="showHeader" class="panel-head">
      <span class="panel-title">{{ title }}</span>
      <span v-if="hasAlarmAccess" class="panel-count">
        活动 {{ sortedAlarms.length }} 条
      </span>
    </div>

    <div v-if="!hasAlarmAccess" class="panel-state">
      当前账号没有查看报警的权限（collector:alarm:query）
    </div>

    <template v-else>
      <!-- 接口失败时保留上一次成功列表，错误提示与列表同时展示 -->
      <div v-if="error" class="panel-state panel-state--error">
        当前报警加载失败：{{ error }}
        <span v-if="sortedAlarms.length > 0">（列表为上一次成功数据）</span>
        <span v-else>（暂无可保留的报警数据）</span>
      </div>

      <div
        v-if="loading && sortedAlarms.length === 0 && !error"
        class="panel-state"
      >
        正在加载当前报警…
      </div>

      <Empty
        v-else-if="!loading && !error && sortedAlarms.length === 0"
        class="panel-empty"
        description="当前没有活动报警"
      />

      <div v-if="sortedAlarms.length > 0" class="panel-list" :style="{ maxHeight }">
      <button
        v-for="alarm in sortedAlarms"
        :key="alarm.channelId"
        type="button"
        class="alarm-row"
        data-testid="active-alarm-row"
        @click="openDetail(alarm)"
      >
        <div class="row-head">
          <Tag :color="getAlarmLevelConfig(alarm.level).color">
            {{ getAlarmLevelConfig(alarm.level).text }}
          </Tag>
          <span class="channel-name">
            {{ alarm.channelName || `通道 ${alarm.channelId}` }}
          </span>
          <span class="entity">
            {{ alarm.deviceName || `设备 ${alarm.deviceId}` }} /
            {{ alarm.sensorName || `传感器 ${alarm.sensorId}` }}
          </span>
          <span class="duration">
            持续 {{ formatDuration(alarm.since, clockNow) }}
          </span>
        </div>
        <div class="row-meta">
          <span>阈值类型：{{ formatAlarmType(alarm.thresholdType) }}</span>
          <span>最近值：{{ formatNumeric(alarm.lastValue) }}</span>
          <span>阈值：{{ formatNumeric(alarm.lastThreshold) }}</span>
          <span>最近采样：{{ formatShanghaiTime(alarm.lastSampleTime) }}</span>
        </div>
      </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.active-alarm-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
}

.panel-count {
  font-size: 12px;
  opacity: 0.6;
}

.panel-state {
  padding: 16px 12px;
  font-size: 13px;
  opacity: 0.7;
}

.panel-state--error {
  color: #fda4af;
  opacity: 1;
}

.panel-empty {
  padding: 24px 0;
}

.panel-list {
  min-height: 0;
  overflow-y: auto;
}

.alarm-row {
  display: block;
  width: 100%;
  padding: 10px 8px;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-bottom: 1px solid hsl(var(--border));
}

.alarm-row:last-child {
  border-bottom: 0;
}

.alarm-row:hover {
  background: hsl(var(--accent) / 35%);
}

.row-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.channel-name {
  font-weight: 600;
}

.entity,
.duration {
  font-size: 12px;
  opacity: 0.65;
}

.duration {
  margin-left: auto;
  white-space: nowrap;
}

.row-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.7;
}

/* 深色大屏样式：不依赖主题切换，显式给出可读配色 */
.active-alarm-panel--dark .panel-title {
  color: #e5edf8;
}

.active-alarm-panel--dark .panel-count,
.active-alarm-panel--dark .entity,
.active-alarm-panel--dark .duration,
.active-alarm-panel--dark .row-meta {
  color: #8fa3ba;
  opacity: 1;
}

.active-alarm-panel--dark .channel-name {
  color: #dbe7f5;
}

.active-alarm-panel--dark .alarm-row {
  border-bottom-color: #223047;
}

.active-alarm-panel--dark .alarm-row:hover {
  background: #162033;
}

.active-alarm-panel--dark .panel-state {
  color: #a9bad0;
  opacity: 1;
}

.active-alarm-panel--dark .panel-state--error {
  color: #fda4af;
}
</style>
