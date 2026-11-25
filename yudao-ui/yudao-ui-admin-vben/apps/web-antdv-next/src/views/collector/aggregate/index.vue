<script lang="ts" setup>
import type { Dayjs } from 'dayjs';

import type { CollectorApi } from '#/api/collector';

import { computed, onMounted, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';

import {
  Alert,
  App,
  Button,
  Card,
  DateRangePicker,
  Empty,
  Modal,
  Select,
  Spin,
  Switch,
  Table,
  Tag,
} from 'antdv-next';
import dayjs from 'dayjs';

import {
  backfillAggregate,
  getChannelListByDevice,
  getDeviceSimpleList,
} from '#/api/collector';

import {
  formatNumeric,
  resolveApiErrorMessage,
} from '../components/utils';

defineOptions({ name: 'CollectorAggregate' });

const AGG_TYPE_OPTIONS: Array<{
  label: string;
  value: CollectorApi.AggregateType;
}> = [
  { label: '秒聚合', value: 'second' },
  { label: '分钟聚合', value: 'minute' },
  { label: '小时聚合', value: 'hour' },
  { label: '天聚合', value: 'day' },
];

const AGG_TYPE_LABELS: Record<string, string> = {
  second: '秒聚合',
  minute: '分钟聚合',
  hour: '小时聚合',
  day: '天聚合',
};

/** 与后端 backfill 接口的粒度上限保持一致；超出会被后端拒绝 */
const AGG_LIMITS: Record<
  CollectorApi.AggregateType,
  { maxChannels: number; maxRangeSeconds: number; rangeText: string }
> = {
  second: {
    maxChannels: 5,
    maxRangeSeconds: 3 * 60 * 60,
    rangeText: '3 小时',
  },
  minute: {
    maxChannels: 20,
    maxRangeSeconds: 2 * 24 * 60 * 60,
    rangeText: '2 天',
  },
  hour: {
    maxChannels: 50,
    maxRangeSeconds: 30 * 24 * 60 * 60,
    rangeText: '30 天',
  },
  day: {
    maxChannels: 50,
    maxRangeSeconds: 30 * 24 * 60 * 60,
    rangeText: '30 天',
  },
};

const { hasAccessByCodes } = useAccess();
const { message } = App.useApp();
const hasRebuildAccess = computed(() =>
  hasAccessByCodes(['collector:aggregate:rebuild']),
);
const hasForceCleanAccess = computed(() =>
  hasAccessByCodes(['collector:aggregate:force-clean']),
);
const hasDeviceAccess = computed(() =>
  hasAccessByCodes(['collector:device:query']),
);

const devices = ref<CollectorApi.DeviceSimple[]>([]);
const sensors = ref<CollectorApi.DeviceThresholdMatrix['sensors']>([]);
const devicesLoading = ref(false);
const channelsLoading = ref(false);
const devicesError = ref<string>();
const channelsError = ref<string>();

const submitting = ref(false);
const result = ref<CollectorApi.AggregateBackfillResult>();
const submitError = ref<string>();

const form = ref<{
  aggType: CollectorApi.AggregateType;
  cascade: boolean;
  channelIds: number[];
  deviceId?: number;
  forceClean: boolean;
  fromRaw: boolean;
  range: [Dayjs, Dayjs];
}>({
  aggType: 'minute',
  cascade: false,
  channelIds: [],
  deviceId: undefined,
  forceClean: false,
  fromRaw: false,
  range: [dayjs().subtract(24, 'hour'), dayjs()],
});

const deviceOptions = computed(() =>
  devices.value.map((device) => ({
    label: `${device.deviceName}（${device.deviceCode}）`,
    value: device.id,
  })),
);

const channelOptions = computed(() =>
  sensors.value.flatMap((sensor) =>
    sensor.channels.map((channel) => ({
      label: `${sensor.name} / ${channel.channelName}${
        channel.unit ? `（${channel.unit}）` : ''
      }`,
      value: channel.id,
    })),
  ),
);

const totalChannelCount = computed(
  () =>
    sensors.value.reduce(
      (sum, sensor) => sum + sensor.channels.length,
      0,
    ),
);

const currentLimit = computed(() => AGG_LIMITS[form.value.aggType]);

const rangeHint = computed(
  () =>
    `${AGG_TYPE_LABELS[form.value.aggType]}单次最多 ${currentLimit.value.rangeText}、` +
    `${currentLimit.value.maxChannels} 个通道；留空通道表示该设备全部通道`,
);

const resultColumns = [
  { dataIndex: 'aggType', key: 'aggType', title: '粒度' },
  { dataIndex: 'channelCount', key: 'channelCount', title: '通道数' },
  { dataIndex: 'bucketCount', key: 'bucketCount', title: '聚合桶数' },
  { dataIndex: 'rowCount', key: 'rowCount', title: '源行数' },
  {
    dataIndex: 'skippedEmptyBatches',
    key: 'skippedEmptyBatches',
    title: '跳过空批次',
  },
  {
    dataIndex: 'preservedBuckets',
    key: 'preservedBuckets',
    title: '保留旧桶数',
  },
  {
    dataIndex: 'durationMillis',
    key: 'durationMillis',
    title: '耗时(毫秒)',
  },
];

function formatCount(value?: null | number): string {
  if (value === null || value === undefined) return '--';
  return String(value);
}

async function loadDevices() {
  devicesLoading.value = true;
  devicesError.value = undefined;
  try {
    devices.value = await getDeviceSimpleList();
    if (devices.value.length > 0 && form.value.deviceId === undefined) {
      form.value.deviceId = devices.value[0]!.id;
      await loadChannels(form.value.deviceId);
    }
  } catch (error) {
    devicesError.value = resolveApiErrorMessage(
      error,
      '加载采集设备失败，请确认账号具备 collector:device:query 权限',
    );
    devices.value = [];
  } finally {
    devicesLoading.value = false;
  }
}

async function loadChannels(deviceId?: number) {
  sensors.value = [];
  form.value.channelIds = [];
  channelsError.value = undefined;
  if (deviceId === undefined) {
    return;
  }
  channelsLoading.value = true;
  try {
    const matrix = await getChannelListByDevice(deviceId);
    sensors.value = matrix?.sensors ?? [];
  } catch (error) {
    channelsError.value = resolveApiErrorMessage(error, '加载通道失败');
    sensors.value = [];
  } finally {
    channelsLoading.value = false;
  }
}

function handleDeviceChange(value: number) {
  form.value.deviceId = value;
  void loadChannels(value);
}

function handleChannelChange(values: number[]) {
  form.value.channelIds = values;
}

/** 粒度变化后清理不适用于当前上限的选择，避免用户带着非法值提交 */
function handleAggTypeChange(value: CollectorApi.AggregateType) {
  form.value.aggType = value;
  const limit = AGG_LIMITS[value].maxChannels;
  if (form.value.channelIds.length > limit) {
    form.value.channelIds = form.value.channelIds.slice(0, limit);
    message.warning(`当前粒度最多选择 ${limit} 个通道，已截断`);
  }
  if (!hasForceCleanAccess.value) {
    form.value.forceClean = false;
  }
  validateRange(value);
}

function validateRange(aggType: CollectorApi.AggregateType): boolean {
  const range = form.value.range;
  if (!range || range.length !== 2) {
    message.warning('请选择时间范围');
    return false;
  }
  const seconds = Math.round((range[1].valueOf() - range[0].valueOf()) / 1000);
  if (seconds <= 0) {
    message.warning('开始时间必须早于结束时间');
    return false;
  }
  const limit = AGG_LIMITS[aggType];
  if (seconds > limit.maxRangeSeconds) {
    message.warning(
      `${AGG_TYPE_LABELS[aggType]}单次最多 ${limit.rangeText}，请缩小时间范围或改用更粗粒度`,
    );
    return false;
  }
  return true;
}

function validateChannels(): boolean {
  const limit = currentLimit.value.maxChannels;
  const selected = form.value.channelIds.length;
  if (selected > limit) {
    message.warning(`当前粒度最多选择 ${limit} 个通道`);
    return false;
  }
  if (selected === 0 && totalChannelCount.value > limit) {
    message.warning(
      `该设备共 ${totalChannelCount.value} 个通道，超过当前粒度上限 ${limit}，请手动选择通道`,
    );
    return false;
  }
  return true;
}

function confirmSubmit(): Promise<boolean> {
  const forceClean = hasForceCleanAccess.value && form.value.forceClean;
  return new Promise((resolve) => {
    Modal.confirm({
      title: forceClean ? '确认强制清空并回填？' : '确认执行聚合回填？',
      okText: forceClean ? '强制清空并回填' : '开始回填',
      okType: forceClean ? 'danger' : 'primary',
      cancelText: '取消',
      width: 520,
      content: [
        `${AGG_TYPE_LABELS[form.value.aggType]}：${form.value.range[0].format(
          'YYYY-MM-DD HH:mm',
        )} ~ ${form.value.range[1].format(
          'YYYY-MM-DD HH:mm',
        )}（左闭右开，结束时刻本身不包含）`,
        `通道：${
          form.value.channelIds.length > 0
            ? `${form.value.channelIds.length} 个指定通道`
            : `该设备全部通道（共 ${totalChannelCount.value} 条）`
        }`,
        form.value.fromRaw ? '将从 raw 原始数据重算' : '将使用下层聚合作源',
        form.value.cascade ? '将按 second→minute→hour→day 级联重建' : '',
        '回填会覆盖目标区间内已有聚合，结果不可回滚。',
        forceClean
          ? '强制清空会按所选时间区间整段清空目标聚合（包含无源旧桶），且不可恢复。'
          : '',
        '',
        '回填为同步执行，板端可能超时；建议缩小范围或分批执行。是否继续？',
      ]
        .filter(Boolean)
        .join('\n'),
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
}

async function handleSubmit() {
  if (!hasRebuildAccess.value) {
    message.warning('当前账号没有聚合回填执行权限（collector:aggregate:rebuild）');
    return;
  }
  if (form.value.deviceId === undefined) {
    message.warning('请先选择采集设备');
    return;
  }
  if (!validateRange(form.value.aggType) || !validateChannels()) {
    return;
  }
  const confirmed = await confirmSubmit();
  if (!confirmed) return;

  const [from, to] = form.value.range;
  const params: CollectorApi.AggregateBackfillParams = {
    aggType: form.value.aggType,
    from: from.format('YYYY-MM-DDTHH:mm:ss'),
    to: to.format('YYYY-MM-DDTHH:mm:ss'),
    channelIds:
      form.value.channelIds.length > 0 ? [...form.value.channelIds] : [],
    fromRaw: form.value.fromRaw,
    cascade: form.value.cascade,
    forceClean: hasForceCleanAccess.value && form.value.forceClean,
  };

  submitting.value = true;
  result.value = undefined;
  submitError.value = undefined;
  try {
    const body = await backfillAggregate(params);
    if (!body) {
      throw new Error('回填接口返回为空');
    }
    result.value = body;
    message.success('聚合回填完成');
  } catch (error) {
    submitError.value = resolveApiErrorMessage(
      error,
      '回填失败或请求超时，请缩小范围或分批重试',
    );
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  void loadDevices();
});
</script>

<template>
  <Page auto-content-height>
    <div class="flex h-full flex-col gap-3 overflow-auto">
      <div>
        <h2 class="m-0 text-lg font-semibold">聚合回填</h2>
        <div class="mt-1 text-xs opacity-60">
          按粒度、设备与时间范围同步回填历史聚合；回填会覆盖目标区间内已有聚合，结果不可回滚。
        </div>
      </div>

      <Alert
        type="warning"
        show-icon
        message="同步执行，板端可能超时"
        description="一次请求在服务端同步跑完；请按粒度上限缩小时间范围或分批执行。请求超时后后端可能仍在执行，请稍后核对结果，切勿重复提交。"
      />

      <Alert
        v-if="!hasRebuildAccess"
        type="info"
        show-icon
        message="当前账号没有聚合回填执行权限（collector:aggregate:rebuild），可以查看页面但无法提交。"
      />

      <Alert
        v-if="devicesError"
        type="error"
        show-icon
        :message="devicesError"
      />

      <Card v-if="!devicesLoading && devices.length === 0 && !devicesError">
        <Empty
          :description="
            hasDeviceAccess
              ? '暂无可用采集设备，请先在「采集设备」中完成配置'
              : '当前账号缺少 collector:device:query 权限，无法加载设备'
          "
        />
      </Card>

      <Spin v-else :spinning="devicesLoading">
        <Card size="small">
          <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            <div>
              <div class="mb-1 text-sm font-medium">聚合粒度</div>
              <Select
                class="w-full"
                :options="AGG_TYPE_OPTIONS"
                :value="form.aggType"
                @change="handleAggTypeChange"
              />
            </div>
            <div>
              <div class="mb-1 text-sm font-medium">采集设备</div>
              <Select
                class="w-full"
                :loading="devicesLoading"
                :options="deviceOptions"
                :value="form.deviceId"
                placeholder="请选择采集设备"
                :show-search="{ optionFilterProp: 'label' }"
                @change="handleDeviceChange"
              />
            </div>
            <div>
              <div class="mb-1 text-sm font-medium">
                时间范围（左闭右开）
              </div>
              <DateRangePicker
                v-model:value="form.range"
                class="w-full"
                format="YYYY-MM-DD HH:mm"
                show-time
                :placeholder="['开始时间', '结束时间']"
              />
              <div class="mt-1 text-xs opacity-60">
                左闭右开 [开始时间, 结束时间)，结束时刻本身不包含。
              </div>
            </div>
            <div class="md:col-span-2 xl:col-span-3">
              <div class="mb-1 flex flex-wrap items-center justify-between gap-1">
                <span class="text-sm font-medium">通道</span>
                <span class="text-xs opacity-60">
                  留空表示该设备全部通道；当前可选
                  {{ totalChannelCount }} 条
                </span>
              </div>
              <Select
                class="w-full"
                mode="multiple"
                :max-tag-count="4"
                :loading="channelsLoading"
                :options="channelOptions"
                :value="form.channelIds"
                placeholder="选择通道；留空表示全部"
                :show-search="{ optionFilterProp: 'label' }"
                @change="handleChannelChange"
              />
              <div v-if="channelsError" class="mt-1 text-xs text-red-500">
                {{ channelsError }}
              </div>
              <div
                v-else-if="
                  !channelsLoading &&
                  form.deviceId !== undefined &&
                  channelOptions.length === 0
                "
                class="mt-1 text-xs opacity-60"
              >
                该设备暂无通道；请先在「采集通道」中完成配置
              </div>
            </div>
          </div>

          <div class="mt-4 flex flex-wrap items-center gap-x-6 gap-y-2 text-sm">
            <label class="flex items-center gap-2">
              <Switch v-model:checked="form.fromRaw" />
              从 raw 重算
            </label>
            <label class="flex items-center gap-2">
              <Switch v-model:checked="form.cascade" />
              级联重建上层
            </label>
            <label
              v-if="hasForceCleanAccess"
              class="flex items-center gap-2"
            >
              <Switch v-model:checked="form.forceClean" />
              <span class="text-red-500">强制清空</span>
            </label>
          </div>
          <div
            v-if="hasForceCleanAccess && form.forceClean"
            class="mt-2 text-xs text-red-500"
          >
            强制清空会按所选时间区间整段清空目标聚合（包含无源旧桶），且不可恢复；提交前会再次确认。
          </div>

          <div class="mt-3 text-xs opacity-60">{{ rangeHint }}</div>

          <div class="mt-4 flex items-center gap-2">
            <Button
              type="primary"
              :loading="submitting"
              :disabled="!hasRebuildAccess || submitting"
              @click="handleSubmit"
            >
              开始回填
            </Button>
            <span class="text-xs opacity-60">
              回填不会改变报警判定；仅重算所选区间的聚合数据
            </span>
          </div>
        </Card>
      </Spin>

      <Alert
        v-if="submitError"
        type="error"
        show-icon
        :message="`回填失败：${submitError}`"
        description="可缩小时间范围、减少通道或分批重试；若请求超时，后端可能仍在执行，请稍后核对结果，不要立即重复提交。"
      />

      <Card v-if="result" size="small">
        <template #title>回填结果</template>
        <div class="grid grid-cols-2 gap-3 text-sm md:grid-cols-4">
          <div>
            <div class="text-xs opacity-60">聚合粒度</div>
            <div class="mt-1 font-medium">
              {{ AGG_TYPE_LABELS[result.aggType ?? ''] ?? result.aggType ?? '--' }}
            </div>
          </div>
          <div>
            <div class="text-xs opacity-60">处理通道数</div>
            <div class="mt-1 font-medium">
              {{ formatCount(result.channelCount) }}
            </div>
          </div>
          <div>
            <div class="text-xs opacity-60">聚合桶数</div>
            <div class="mt-1 font-medium">
              {{ formatCount(result.bucketCount) }}
            </div>
          </div>
          <div>
            <div class="text-xs opacity-60">源行数</div>
            <div class="mt-1 font-medium">
              {{ formatCount(result.rowCount) }}
            </div>
          </div>
          <div>
            <div class="text-xs opacity-60">耗时</div>
            <div class="mt-1 font-medium">
              {{ formatNumeric(result.durationMillis) }} 毫秒
            </div>
          </div>
          <div>
            <div class="text-xs opacity-60">跳过空批次</div>
            <div class="mt-1 font-medium">
              {{ formatCount(result.skippedEmptyBatches) }}
            </div>
            <div class="mt-1 text-xs opacity-60">
              整批源数据为空且未开启强制清空，因而跳过删除/写入的批次数
            </div>
          </div>
          <div>
            <div class="text-xs opacity-60">保留旧桶数</div>
            <div class="mt-1 font-medium">
              {{ formatCount(result.preservedBuckets) }}
            </div>
            <div class="mt-1 text-xs opacity-60">
              本轮无源数据而保留的旧聚合桶数，不会被删除
            </div>
          </div>
        </div>

        <div class="mt-4">
          <div class="mb-2 flex flex-wrap items-center gap-2">
            <span class="text-sm font-medium">各层结果</span>
            <Tag color="blue">共 {{ result.levels?.length ?? 0 }} 层</Tag>
          </div>
          <Table
            v-if="(result.levels?.length ?? 0) > 0"
            :columns="resultColumns"
            :data-source="result.levels"
            :pagination="false"
            row-key="aggType"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'aggType'">
                {{
                  AGG_TYPE_LABELS[record.aggType ?? ''] ??
                    record.aggType ??
                    '-'
                }}
              </template>
            </template>
          </Table>
          <div v-else class="text-xs opacity-60">
            本次仅执行根层，未返回级联层结果
          </div>
        </div>
      </Card>
    </div>
  </Page>
</template>
