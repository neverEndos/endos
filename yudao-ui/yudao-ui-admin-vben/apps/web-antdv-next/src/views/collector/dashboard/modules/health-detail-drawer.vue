<script lang="ts" setup>
import type { CollectorApi } from '#/api/collector';

import { computed, onBeforeUnmount, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Alert, Button, Card, Empty, Spin, Table, Tag } from 'antdv-next';
import dayjs from 'dayjs';

import { getHealthOverview } from '#/api/collector';

interface HealthDrawerData {
  deviceId: number;
}

const overview = ref<CollectorApi.HealthOverview>();
const loading = ref(false);
const loadError = ref<string>();
const lastSuccessAt = ref<number>();

let currentDeviceId: number | undefined;
let inFlight = false;
let destroyed = false;
let requestSeq = 0;

const PROCESS_STATUS_TEXT: Record<string, string> = {
  degraded: '降级运行',
  running: '运行中',
  starting: '启动中',
  stopped: '已停止',
};
const PROCESS_STATUS_COLOR: Record<string, string> = {
  degraded: 'warning',
  running: 'success',
  starting: 'processing',
  stopped: 'error',
};
const ALARM_LEVEL_TEXT: Record<number, string> = {
  0: '无活动报警',
  2: '预警',
  3: '异常',
};
const ALARM_LEVEL_COLOR: Record<number, string> = {
  0: 'default',
  2: 'warning',
  3: 'error',
};
const AGG_TYPE_TEXT: Record<string, string> = {
  day: '日聚合',
  hour: '小时聚合',
  minute: '分钟聚合',
  second: '秒聚合',
};
const AGG_TYPE_ORDER: Record<string, number> = {
  second: 0,
  minute: 1,
  hour: 2,
  day: 3,
};

const aggregateColumns = [
  { key: 'aggType', title: '聚合类型', width: 100 },
  { key: 'rowCount', title: '水位行数', width: 100 },
  { key: 'abnormalCount', title: '异常行数', width: 100 },
  { key: 'maxRetryCount', title: '最大重试', width: 100 },
  { key: 'lastSuccessTime', title: '最近成功时间', width: 170 },
  { key: 'minWatermarkTime', title: '最小水位', width: 170 },
  { key: 'maxWatermarkTime', title: '最大水位', width: 170 },
  { key: 'status', title: '状态', width: 120 },
];
const aggregateAbnormalColumns = [
  { key: 'aggType', title: '聚合类型', width: 100 },
  { key: 'deviceName', title: '设备', width: 120 },
  { key: 'channelName', title: '通道', width: 140 },
  { key: 'status', title: '库内状态', width: 100 },
  { key: 'retryCount', title: '重试次数', width: 100 },
  { key: 'watermarkTime', title: '水位时间', width: 170 },
  { key: 'lastError', title: '最近错误', width: 200 },
];
const alarmColumns = [
  { key: 'level', title: '级别', width: 90 },
  { key: 'deviceName', title: '设备', width: 120 },
  { key: 'sensorName', title: '传感器', width: 120 },
  { key: 'channelName', title: '通道', width: 140 },
  { key: 'thresholdType', title: '阈值类型', width: 120 },
  { key: 'lastValue', title: '工程值', width: 110 },
  { key: 'lastThreshold', title: '阈值', width: 110 },
  { key: 'since', title: '开始时间', width: 170 },
  { key: 'lastSampleTime', title: '最近采样', width: 170 },
];
const retentionColumns = [
  { key: 'displayName', title: '数据类', width: 180 },
  { key: 'retentionDays', title: '保留天数', width: 110 },
  { key: 'enabled', title: '过期清理', width: 100 },
  { key: 'lastRunAt', title: '最近执行', width: 170 },
  { key: 'lastRunResult', title: '执行结果', width: 220 },
];

const process = computed(() => overview.value?.process);
const device = computed(() => overview.value?.device);
const alarms = computed(() => overview.value?.alarms ?? []);
const alarmTotal = computed(() => overview.value?.alarmTotal ?? 0);
const alarmTruncated = computed(() => alarmTotal.value > alarms.value.length);
const highestLevel = computed(() => overview.value?.alarm.highestLevel ?? 0);
const aggregates = computed(() =>
  [...(overview.value?.aggregate ?? [])].sort(
    (a, b) =>
      (AGG_TYPE_ORDER[a.aggType ?? ''] ?? 99) -
      (AGG_TYPE_ORDER[b.aggType ?? ''] ?? 99),
  ),
);
const aggregateAbnormals = computed(
  () => overview.value?.aggregateAbnormal ?? [],
);
function isAggregateUninitialized(item: CollectorApi.HealthAggregateSummary) {
  // initialized=false 或水位行数为 0 都视为尚未初始化，不能当作「全部正常」
  return item.initialized === false || !item.rowCount;
}
const allAggregatesUninitialized = computed(
  () =>
    aggregates.value.length === 0 ||
    aggregates.value.every((item) => isAggregateUninitialized(item)),
);
const hasUninitializedAggregate = computed(() =>
  aggregates.value.some((item) => isAggregateUninitialized(item)),
);
const aggregateAbnormalEmptyText = computed(() =>
  hasUninitializedAggregate.value
    ? '当前无聚合水位异常明细（部分聚合类型尚未初始化，结论不完整）'
    : '当前无聚合水位异常',
);
const retentions = computed(() => overview.value?.retention ?? []);
const rawPartition = computed(() => overview.value?.rawPartition);
const rawStatus = computed(() => rawStatusMeta(rawPartition.value?.status));
const lastSuccessText = computed(() =>
  lastSuccessAt.value
    ? dayjs(lastSuccessAt.value).format('YYYY-MM-DD HH:mm:ss')
    : '--',
);
const deviceOnlineText = computed(() => {
  const online = device.value?.online;
  if (online === null || online === undefined) return '--';
  return online ? '在线' : '离线';
});
const deviceOnlineColor = computed(() => {
  const online = device.value?.online;
  if (online === null || online === undefined) return 'default';
  return online ? 'success' : 'error';
});

function formatDateTime(value?: null | number) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '--';
}

function formatDate(value?: null | string) {
  return value || '--';
}

function processStatusText(status?: null | string) {
  if (!status) return '--';
  return PROCESS_STATUS_TEXT[status] ?? status;
}

function processStatusColor(status?: null | string) {
  if (!status) return 'default';
  return PROCESS_STATUS_COLOR[status] ?? 'default';
}

function alarmLevelText(level?: null | number) {
  if (level === null || level === undefined) return '--';
  return ALARM_LEVEL_TEXT[level] ?? `级别 ${level}`;
}

function alarmLevelColor(level?: null | number) {
  if (level === null || level === undefined) return 'default';
  return ALARM_LEVEL_COLOR[level] ?? 'default';
}

function aggTypeText(type?: null | string) {
  if (!type) return '--';
  return AGG_TYPE_TEXT[type] ?? type;
}

/** retry_count 为后端预留字段，当前恒为 0；0 或 null 一律显示 --，不编造重试信息 */
function formatRetryCount(value?: null | number) {
  return value ? String(value) : '--';
}

function formatMetric(value?: null | number) {
  return value === null || value === undefined ? '--' : String(value);
}

function formatMs(value?: null | number) {
  return value === null || value === undefined ? '--' : `${value} ms`;
}

function rawStatusMeta(status?: null | string): {
  color: string;
  text: string;
  tip?: string;
} {
  switch (status) {
    case 'ok': {
      return { color: 'success', text: '正常' };
    }
    case 'policy_missing': {
      return {
        color: 'error',
        text: '策略缺失',
        tip: '未找到 raw 分区策略，无法确认目标区间的日分区是否预建。',
      };
    }
    case 'policy_duplicate': {
      return {
        color: 'error',
        text: '策略重复',
        tip: 'raw 分区策略存在多条记录，无法确定预建天数，未套用任何默认值。',
      };
    }
    case 'policy_invalid': {
      return {
        color: 'error',
        text: '策略非法',
        tip: 'partition_lead_days 为 NULL 或超出 0~30，无法确认预建分区，未套用任何默认值。',
      };
    }
    default: {
      return { color: 'default', text: status || '--' };
    }
  }
}

async function loadOverview() {
  const deviceId = currentDeviceId;
  if (deviceId === undefined || destroyed || inFlight) return;
  inFlight = true;
  const seq = ++requestSeq;
  loading.value = true;
  loadError.value = undefined;
  try {
    const body = await getHealthOverview(deviceId);
    // 关闭抽屉 / 切换设备后的迟到响应一律丢弃
    if (destroyed || seq !== requestSeq || currentDeviceId !== deviceId) return;
    if (!body) {
      throw new Error('运行详情接口返回为空');
    }
    overview.value = body;
    lastSuccessAt.value = Date.now();
  } catch {
    if (destroyed || seq !== requestSeq || currentDeviceId !== deviceId) return;
    // 失败时保留上一次成功结果，只在 UI 上标注可能已过期
    loadError.value = '加载采集运行详情失败，请稍后重试';
  } finally {
    if (seq === requestSeq) {
      inFlight = false;
      loading.value = false;
    }
  }
}

const [Drawer, drawerApi] = useVbenDrawer({
  title: '采集运行详情',
  footer: false,
  class: '!w-[840px] !max-w-[96vw]',
  onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      // 关闭后丢弃一切在途响应，避免请求落地污染状态
      requestSeq += 1;
      destroyed = true;
      return;
    }
    destroyed = false;
    const data = drawerApi.getData() as HealthDrawerData | undefined;
    currentDeviceId = data?.deviceId;
    overview.value = undefined;
    loadError.value = undefined;
    lastSuccessAt.value = undefined;
    void loadOverview();
  },
});

onBeforeUnmount(() => {
  destroyed = true;
  requestSeq += 1;
});
</script>

<template>
  <Drawer>
    <div class="mx-4 flex flex-col gap-3 pb-4">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div class="text-xs opacity-60">
          数据最后成功请求时间：{{ lastSuccessText }}（Asia/Shanghai）
        </div>
        <Button size="small" :loading="loading" @click="void loadOverview()">
          刷新
        </Button>
      </div>

      <Alert
        v-if="loadError && !overview"
        type="error"
        show-icon
        :message="loadError"
        description="未获取到运行详情，不会展示任何推断出的正常结论。"
      />
      <Alert
        v-else-if="loadError"
        type="warning"
        show-icon
        message="运行详情刷新失败，当前展示的数据可能已过期"
        :description="`最后成功请求于 ${lastSuccessText}（Asia/Shanghai）。`"
      />
      <Alert
        v-else-if="loading && overview"
        type="info"
        show-icon
        message="正在刷新运行详情…"
      />

      <Spin :spinning="loading && !overview">
        <div v-if="overview" class="flex flex-col gap-3">
          <!-- 1. 采集进程 -->
          <Card size="small">
            <template #title>采集进程</template>
            <template #extra>
              <Tag color="purple">进程级</Tag>
            </template>
            <Alert
              v-if="!process"
              type="warning"
              show-icon
              message="尚未收到心跳"
              description="采集器进程从未上报心跳，不能据此判断采集进程运行正常。"
            />
            <div
              v-else
              class="grid grid-cols-2 gap-3 text-sm md:grid-cols-3"
            >
              <div>
                <div class="text-xs opacity-60">心跳状态</div>
                <div class="mt-1">
                  <Tag :color="processStatusColor(process.status)">
                    {{ processStatusText(process.status) }}
                  </Tag>
                </div>
              </div>
              <div>
                <div class="text-xs opacity-60">最新心跳时间</div>
                <div class="mt-1 font-medium">
                  {{ formatDateTime(process.heartbeatTime) }}
                </div>
              </div>
              <div>
                <div class="text-xs opacity-60">实例编号</div>
                <div class="mt-1 break-all font-medium">
                  {{ process.instanceId || '--' }}
                </div>
              </div>
              <div>
                <div class="text-xs opacity-60">在线设备 / 配置总数</div>
                <div class="mt-1 font-medium">
                  {{
                    process.deviceTotal
                      ? `${process.deviceOnline ?? '--'}/${process.deviceTotal}`
                      : '--'
                  }}
                </div>
              </div>
              <div>
                <div class="text-xs opacity-60">数据库写入延迟</div>
                <div class="mt-1 font-medium">
                  {{ formatMs(process.writeLagMs) }}
                </div>
              </div>
              <div>
                <div class="text-xs opacity-60">采集器版本</div>
                <div class="mt-1 font-medium">
                  {{ process.collectorVersion || '--' }}
                </div>
              </div>
            </div>
          </Card>

          <!-- 2. 当前设备 -->
          <Card size="small">
            <template #title>当前设备</template>
            <template #extra>
              <Tag color="blue">当前设备</Tag>
            </template>
            <div class="grid grid-cols-2 gap-3 text-sm md:grid-cols-3">
              <div>
                <div class="text-xs opacity-60">设备</div>
                <div class="mt-1 font-medium">
                  {{ device?.deviceName || '--' }}
                  <span class="ml-1 text-xs opacity-60">
                    #{{ device?.deviceId ?? '--' }}
                  </span>
                </div>
              </div>
              <div>
                <div class="text-xs opacity-60">在线状态</div>
                <div class="mt-1">
                  <Tag :color="deviceOnlineColor">
                    {{ deviceOnlineText }}
                  </Tag>
                </div>
              </div>
              <div>
                <div class="text-xs opacity-60">最近轮询时间</div>
                <div class="mt-1 font-medium">
                  {{ formatDateTime(device?.lastPollAt) }}
                </div>
              </div>
              <div>
                <div class="text-xs opacity-60">最近采集成功时间</div>
                <div class="mt-1 font-medium">
                  {{ formatDateTime(device?.lastSuccessAt) }}
                </div>
              </div>
              <div>
                <div class="text-xs opacity-60">最近请求耗时</div>
                <div class="mt-1 font-medium">
                  {{ formatMs(device?.lastLatencyMs) }}
                </div>
              </div>
              <div>
                <div class="text-xs opacity-60">连续失败次数</div>
                <div class="mt-1 font-medium">
                  {{ formatMetric(device?.consecutiveFailures) }}
                </div>
              </div>
              <div>
                <div class="text-xs opacity-60">设备轮询周期</div>
                <div class="mt-1 font-medium">
                  {{ formatMs(device?.pollIntervalMs) }}
                </div>
              </div>
              <div class="col-span-2">
                <div class="text-xs opacity-60">最近错误</div>
                <div class="mt-1 break-all font-medium">
                  {{ device?.lastError || '--' }}
                </div>
              </div>
            </div>
          </Card>

          <!-- 3. 活动报警 -->
          <Card size="small">
            <template #title>
              <div class="flex flex-wrap items-center gap-2">
                <span>活动报警</span>
                <Tag :color="alarmLevelColor(highestLevel)">
                  最高级别：{{ alarmLevelText(highestLevel) }}
                </Tag>
              </div>
            </template>
            <template #extra>
              <Tag color="blue">当前设备</Tag>
            </template>
            <Empty
              v-if="alarms.length === 0"
              description="当前无活动报警"
            />
            <template v-else>
              <div v-if="alarmTruncated" class="mb-2 text-xs opacity-60">
                {{
                  `仅显示前 ${alarms.length} 条（共 ${alarmTotal} 条）`
                }}
              </div>
              <Table
                :columns="alarmColumns"
                :data-source="alarms"
                :pagination="false"
                :row-key="
                  (record) =>
                    `${record.deviceId ?? ''}-${record.channelId ?? ''}`
                "
                :scroll="{ x: 1100 }"
                size="small"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'level'">
                    <Tag :color="alarmLevelColor(record.level)">
                      {{ alarmLevelText(record.level) }}
                    </Tag>
                  </template>
                  <template v-else-if="column.key === 'deviceName'">
                    {{ record.deviceName || '--' }}
                  </template>
                  <template v-else-if="column.key === 'sensorName'">
                    {{ record.sensorName || '--' }}
                  </template>
                  <template v-else-if="column.key === 'channelName'">
                    {{ record.channelName || '--' }}
                  </template>
                  <template v-else-if="column.key === 'thresholdType'">
                    {{ record.thresholdType || '--' }}
                  </template>
                  <template v-else-if="column.key === 'lastValue'">
                    {{ formatMetric(record.lastValue) }}
                  </template>
                  <template v-else-if="column.key === 'lastThreshold'">
                    {{ formatMetric(record.lastThreshold) }}
                  </template>
                  <template v-else-if="column.key === 'since'">
                    {{ formatDateTime(record.since) }}
                  </template>
                  <template v-else-if="column.key === 'lastSampleTime'">
                    {{ formatDateTime(record.lastSampleTime) }}
                  </template>
                </template>
              </Table>
            </template>
          </Card>

          <!-- 4. 聚合水位 -->
          <Card size="small">
            <template #title>聚合水位</template>
            <template #extra>
              <Tag color="orange">全局任务</Tag>
            </template>
            <Alert
              v-if="allAggregatesUninitialized"
              type="warning"
              show-icon
              message="聚合水位尚未初始化，无法判断聚合任务是否正常"
              description="聚合水位表无有效行，不能展示为「全部正常」。"
            />
            <template v-else>
              <Table
                :columns="aggregateColumns"
                :data-source="aggregates"
                :pagination="false"
                :scroll="{ x: 960 }"
                row-key="aggType"
                size="small"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'aggType'">
                    {{ aggTypeText(record.aggType) }}
                  </template>
                  <template v-else-if="column.key === 'rowCount'">
                    {{
                      isAggregateUninitialized(record)
                        ? '--'
                        : formatMetric(record.rowCount)
                    }}
                  </template>
                  <template v-else-if="column.key === 'abnormalCount'">
                    {{
                      isAggregateUninitialized(record)
                        ? '--'
                        : formatMetric(record.abnormalCount)
                    }}
                  </template>
                  <template v-else-if="column.key === 'maxRetryCount'">
                    {{ formatRetryCount(record.maxRetryCount) }}
                  </template>
                  <template v-else-if="column.key === 'lastSuccessTime'">
                    {{ formatDateTime(record.lastSuccessTime) }}
                  </template>
                  <template v-else-if="column.key === 'minWatermarkTime'">
                    {{ formatDateTime(record.minWatermarkTime) }}
                  </template>
                  <template v-else-if="column.key === 'maxWatermarkTime'">
                    {{ formatDateTime(record.maxWatermarkTime) }}
                  </template>
                  <template v-else-if="column.key === 'status'">
                    <Tag
                      :color="
                        isAggregateUninitialized(record) ? 'warning' : 'success'
                      "
                    >
                      {{
                        isAggregateUninitialized(record)
                          ? '尚未初始化'
                          : '已初始化'
                      }}
                    </Tag>
                  </template>
                </template>
              </Table>

              <div class="mt-3 text-xs font-medium opacity-70">
                聚合水位异常明细
              </div>
              <Empty
                v-if="aggregateAbnormals.length === 0"
                :description="aggregateAbnormalEmptyText"
              />
              <Table
                v-else
                class="mt-2"
                :columns="aggregateAbnormalColumns"
                :data-source="aggregateAbnormals"
                :pagination="false"
                :row-key="
                  (record) =>
                    `${record.aggType ?? ''}-${record.deviceId ?? ''}-${record.channelId ?? ''}`
                "
                :scroll="{ x: 1000 }"
                size="small"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'aggType'">
                    {{ aggTypeText(record.aggType) }}
                  </template>
                  <template v-else-if="column.key === 'deviceName'">
                    {{ record.deviceName || '--' }}
                  </template>
                  <template v-else-if="column.key === 'channelName'">
                    {{ record.channelName || '--' }}
                  </template>
                  <template v-else-if="column.key === 'status'">
                    {{ formatMetric(record.status) }}
                  </template>
                  <template v-else-if="column.key === 'retryCount'">
                    {{ formatRetryCount(record.retryCount) }}
                  </template>
                  <template v-else-if="column.key === 'watermarkTime'">
                    {{ formatDateTime(record.watermarkTime) }}
                  </template>
                  <template v-else-if="column.key === 'lastError'">
                    {{ record.lastError || '--' }}
                  </template>
                </template>
              </Table>
            </template>
          </Card>

          <!-- 5. 保留策略 -->
          <Card size="small">
            <template #title>保留策略</template>
            <template #extra>
              <Tag color="orange">全局任务</Tag>
            </template>
            <Empty
              v-if="retentions.length === 0"
              description="暂无保留策略数据"
            />
            <Table
              v-else
              :columns="retentionColumns"
              :data-source="retentions"
              :pagination="false"
              :scroll="{ x: 780 }"
              row-key="id"
              size="small"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'displayName'">
                  <div class="font-medium">{{ record.displayName || '--' }}</div>
                  <div class="text-xs opacity-60">{{ record.dataClass }}</div>
                </template>
                <template v-else-if="column.key === 'retentionDays'">
                  {{
                    record.retentionDays === 0
                      ? '永久保留'
                      : record.retentionDays === null ||
                          record.retentionDays === undefined
                        ? '--'
                        : `${record.retentionDays} 天`
                  }}
                </template>
                <template v-else-if="column.key === 'enabled'">
                  <Tag :color="record.enabled ? 'success' : 'default'">
                    {{ record.enabled ? '已启用' : '已停用' }}
                  </Tag>
                </template>
                <template v-else-if="column.key === 'lastRunAt'">
                  {{ formatDateTime(record.lastRunAt) }}
                </template>
                <template v-else-if="column.key === 'lastRunResult'">
                  <span class="break-all">{{ record.lastRunResult || '--' }}</span>
                </template>
              </template>
            </Table>
          </Card>

          <!-- 6. raw 分区 -->
          <Card size="small">
            <template #title>raw 分区</template>
            <template #extra>
              <Tag color="orange">全局任务</Tag>
            </template>
            <template v-if="rawPartition">
              <div class="flex flex-wrap items-center gap-2 text-sm">
                <Tag :color="rawStatus.color">
                  {{ rawStatus.text }}
                </Tag>
                <span class="text-xs opacity-60">
                  期望区间：{{ formatDate(rawPartition.expectedFrom) }} ~
                  {{ formatDate(rawPartition.expectedTo) }}
                </span>
              </div>
              <Alert
                v-if="rawStatus.tip"
                class="mt-2"
                type="error"
                show-icon
                :message="rawStatus.tip"
              />
              <div
                v-else
                class="mt-3 grid grid-cols-2 gap-3 text-sm md:grid-cols-4"
              >
                <div>
                  <div class="text-xs opacity-60">预建天数</div>
                  <div class="mt-1 font-medium">
                    {{ formatMetric(rawPartition.leadDays) }}
                  </div>
                </div>
                <div>
                  <div class="text-xs opacity-60">期望分区数</div>
                  <div class="mt-1 font-medium">
                    {{ formatMetric(rawPartition.expectedCount) }}
                  </div>
                </div>
                <div>
                  <div class="text-xs opacity-60">已存在分区数</div>
                  <div class="mt-1 font-medium">
                    {{ formatMetric(rawPartition.existingCount) }}
                  </div>
                </div>
                <div>
                  <div class="text-xs opacity-60">缺失分区数</div>
                  <div class="mt-1 font-medium">
                    {{ rawPartition.missingDates?.length ?? 0 }}
                  </div>
                </div>
              </div>
              <div
                v-if="rawPartition.status === 'ok'"
                class="mt-3 text-xs"
              >
                <template v-if="(rawPartition.missingDates?.length ?? 0) === 0">
                  <Tag color="success">目标区间内分区已全部预建</Tag>
                </template>
                <template v-else>
                  <div class="mb-1 opacity-60">缺失日期：</div>
                  <div class="flex flex-wrap gap-1">
                    <Tag
                      v-for="date in rawPartition.missingDates"
                      :key="date"
                      color="error"
                    >
                      {{ date }}
                    </Tag>
                  </div>
                </template>
              </div>
            </template>
            <Empty
              v-else
              description="暂无 raw 分区信息"
            />
          </Card>

          <div class="text-xs opacity-50">时间均按 Asia/Shanghai 展示。</div>
        </div>

        <div
          v-else-if="loading"
          class="flex min-h-[200px] items-center justify-center text-xs opacity-60"
        >
          正在加载运行详情…
        </div>

        <Empty
          v-else-if="!loadError"
          description="暂无运行详情数据"
        />
      </Spin>
    </div>
  </Drawer>
</template>
