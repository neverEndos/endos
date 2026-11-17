<script lang="ts" setup>
import type { CollectorApi } from '#/api/collector';

import { computed, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import {
  Alert,
  Empty,
  InputNumber,
  message,
  Modal,
  Spin,
  Switch,
  Tag,
} from 'antdv-next';

import {
  getChannelListByDevice,
  updateChannelThresholdBatch,
} from '#/api/collector';

import {
  isThresholdOrderValid,
  THRESHOLD_FIELDS,
  toIntegerSwitch,
  toSwitchValue,
} from '../../shared';

type EditableChannel = Omit<
  CollectorApi.ThresholdMatrixChannel,
  | 'alarmEnabled'
  | 'thresholdHighErrorEnabled'
  | 'thresholdHighWarningEnabled'
  | 'thresholdLowErrorEnabled'
  | 'thresholdLowWarningEnabled'
> & {
  alarmEnabled: boolean;
  thresholdHighErrorEnabled: boolean;
  thresholdHighWarningEnabled: boolean;
  thresholdLowErrorEnabled: boolean;
  thresholdLowWarningEnabled: boolean;
};

interface EditableSensor {
  channels: EditableChannel[];
  id: number;
  model?: null | string;
  name: string;
}

const thresholdColumns = [
  {
    enabledField: 'thresholdLowErrorEnabled',
    icon: '↓↓',
    label: '异常下限',
    severity: 'error',
    valueField: 'thresholdLowError',
  },
  {
    enabledField: 'thresholdLowWarningEnabled',
    icon: '↓',
    label: '预警下限',
    severity: 'warning',
    valueField: 'thresholdLowWarning',
  },
  {
    enabledField: 'thresholdHighWarningEnabled',
    icon: '↑',
    label: '预警上限',
    severity: 'warning',
    valueField: 'thresholdHighWarning',
  },
  {
    enabledField: 'thresholdHighErrorEnabled',
    icon: '↑↑',
    label: '异常上限',
    severity: 'error',
    valueField: 'thresholdHighError',
  },
] as const;

const device = ref<CollectorApi.Device>();
const sensors = ref<EditableSensor[]>([]);
const loading = ref(false);
const initialized = ref(false);
const originalSnapshot = ref('');

const totalChannels = computed(() =>
  sensors.value.reduce((total, sensor) => total + sensor.channels.length, 0),
);
const isDirty = computed(
  () =>
    initialized.value &&
    originalSnapshot.value !== JSON.stringify(sensors.value),
);

/** 读取设备完整矩阵，并以当前状态作为未保存判断的基线 */
async function loadMatrix(deviceId: number) {
  loading.value = true;
  try {
    const result = await getChannelListByDevice(deviceId);
    sensors.value = result.sensors.map((sensor) => ({
      id: sensor.id,
      model: sensor.model,
      name: sensor.name,
      channels: sensor.channels.map((channel) => ({
        ...channel,
        thresholdLowErrorEnabled: toSwitchValue(
          channel.thresholdLowErrorEnabled,
        ),
        thresholdLowWarningEnabled: toSwitchValue(
          channel.thresholdLowWarningEnabled,
        ),
        thresholdHighWarningEnabled: toSwitchValue(
          channel.thresholdHighWarningEnabled,
        ),
        thresholdHighErrorEnabled: toSwitchValue(
          channel.thresholdHighErrorEnabled,
        ),
        alarmEnabled: toSwitchValue(channel.alarmEnabled),
        deadband: channel.deadband ?? 0,
      })),
    }));
    originalSnapshot.value = JSON.stringify(sensors.value);
    initialized.value = true;
  } catch (error: any) {
    sensors.value = [];
    initialized.value = false;
    message.error(error?.message || '阈值矩阵加载失败');
  } finally {
    loading.value = false;
    drawerApi.setState({ showConfirmButton: totalChannels.value > 0 });
  }
}

/** 数值通道才参与报警；分组总开关批量作用于组内全部数值通道 */
function getNumberChannels(sensor: EditableSensor) {
  return sensor.channels.filter((channel) => channel.valueKind !== 'enum');
}

function isSensorAlarmEnabled(sensor: EditableSensor) {
  const channels = getNumberChannels(sensor);
  return (
    channels.length > 0 && channels.every((channel) => channel.alarmEnabled)
  );
}

function setSensorAlarm(sensor: EditableSensor, enabled: boolean) {
  getNumberChannels(sensor).forEach((channel) => {
    channel.alarmEnabled = enabled;
  });
}

function isOutOfRange(channel: EditableChannel, value?: null | number) {
  return (
    value !== null &&
    value !== undefined &&
    ((channel.valueMin !== null &&
      channel.valueMin !== undefined &&
      value < channel.valueMin) ||
      (channel.valueMax !== null &&
        channel.valueMax !== undefined &&
        value > channel.valueMax))
  );
}

function hasOutOfRangeThreshold() {
  return sensors.value.some((sensor) =>
    getNumberChannels(sensor).some((channel) =>
      THRESHOLD_FIELDS.some((field) => isOutOfRange(channel, channel[field])),
    ),
  );
}

/** 校验所有非空阈值保持四档顺序，死区必须为非负有限数 */
function validateMatrix(): null | string {
  for (const sensor of sensors.value) {
    for (const channel of getNumberChannels(sensor)) {
      if (
        channel.deadband === null ||
        channel.deadband === undefined ||
        !Number.isFinite(channel.deadband) ||
        channel.deadband < 0
      ) {
        return `通道【${channel.channelName}】的变化死区必须为非负数`;
      }
      for (const field of THRESHOLD_FIELDS) {
        const value = channel[field];
        if (value !== null && value !== undefined && !Number.isFinite(value)) {
          return `通道【${channel.channelName}】存在无效阈值`;
        }
      }
      if (!isThresholdOrderValid(channel)) {
        return `通道【${channel.channelName}】的四档阈值必须按异常下限 ≤ 预警下限 ≤ 预警上限 ≤ 异常上限排列`;
      }
    }
  }
  return null;
}

const [Drawer, drawerApi] = useVbenDrawer({
  title: '阈值矩阵',
  class: '!w-[1280px] !max-w-[96vw]',
  contentClass: '!p-0',
  confirmText: '保存',
  cancelText: '取消',
  destroyOnClose: true,
  async onBeforeClose() {
    if (!isDirty.value) {
      return true;
    }
    return await new Promise<boolean>((resolve) => {
      Modal.confirm({
        title: '放弃未保存的修改？',
        content: '当前阈值矩阵有未保存修改，关闭后将丢失。',
        okText: '放弃修改',
        okType: 'danger',
        cancelText: '继续编辑',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });
  },
  async onConfirm() {
    const errorMessage = validateMatrix();
    if (errorMessage) {
      message.error(errorMessage);
      return;
    }
    if (!device.value?.id) {
      return;
    }
    const channels: CollectorApi.ChannelThresholdBatchItem[] =
      sensors.value.flatMap((sensor) =>
        getNumberChannels(sensor).map((channel) => ({
          channelId: channel.id,
          thresholdLowError: channel.thresholdLowError ?? null,
          thresholdLowWarning: channel.thresholdLowWarning ?? null,
          thresholdHighWarning: channel.thresholdHighWarning ?? null,
          thresholdHighError: channel.thresholdHighError ?? null,
          thresholdLowErrorEnabled: toIntegerSwitch(
            channel.thresholdLowErrorEnabled,
          ),
          thresholdLowWarningEnabled: toIntegerSwitch(
            channel.thresholdLowWarningEnabled,
          ),
          thresholdHighWarningEnabled: toIntegerSwitch(
            channel.thresholdHighWarningEnabled,
          ),
          thresholdHighErrorEnabled: toIntegerSwitch(
            channel.thresholdHighErrorEnabled,
          ),
          alarmEnabled: toIntegerSwitch(channel.alarmEnabled),
          deadband: channel.deadband ?? 0,
        })),
      );
    if (channels.length === 0) {
      message.warning('当前设备没有可保存的数值通道');
      return;
    }
    // 超出量程只高亮并提示，不阻断保存，兼容需要保留越界阈值的现场配置。
    if (hasOutOfRangeThreshold()) {
      message.warning('部分阈值超出通道量程，将按当前值保存');
    }
    drawerApi.lock();
    try {
      const count = await updateChannelThresholdBatch({
        deviceId: device.value.id,
        channels,
      });
      message.success(`已保存 ${count} 个数值通道的阈值配置`);
      await loadMatrix(device.value.id);
    } catch (error: any) {
      // 保存失败不重建本地状态，保留用户输入以便修正后重试。
      message.error(error?.message || '阈值保存失败，请检查后重试');
    } finally {
      drawerApi.unlock();
    }
  },
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      device.value = undefined;
      sensors.value = [];
      initialized.value = false;
      originalSnapshot.value = '';
      return;
    }
    const data = drawerApi.getData() as CollectorApi.Device;
    if (!data?.id) {
      return;
    }
    device.value = data;
    drawerApi.setState({ title: `阈值矩阵 - ${data.deviceName}` });
    drawerApi.lock();
    try {
      await loadMatrix(data.id);
    } finally {
      drawerApi.unlock();
    }
  },
});
</script>

<template>
  <Drawer>
    <Spin :spinning="loading" :classes="{ root: 'min-h-48' }">
      <div class="flex flex-col gap-3 p-4">
        <Alert type="info" show-icon>
          <template #message>
            <div class="flex flex-wrap items-center gap-2">
              <span>
                共
                {{ totalChannels }}
                个通道；留空表示未配置，超出量程仅提示不阻断保存。
              </span>
              <Tag v-if="isDirty" color="warning">有未保存修改</Tag>
            </div>
          </template>
        </Alert>

        <Empty
          v-if="!loading && totalChannels === 0"
          class="py-16"
          description="当前设备还没有通道，请先在传感器页面生成通道"
        />

        <div v-else class="overflow-auto rounded-md border border-border">
          <table class="threshold-matrix">
            <thead>
              <tr>
                <th class="channel-column">通道</th>
                <th
                  v-for="column in thresholdColumns"
                  :key="column.valueField"
                  :class="`threshold-head threshold-head--${column.severity}`"
                >
                  <span class="threshold-direction">{{ column.icon }}</span>
                  <span>{{ column.label }}</span>
                </th>
                <th class="deadband-column">变化死区</th>
              </tr>
            </thead>
            <tbody v-for="sensor in sensors" :key="sensor.id">
              <tr class="sensor-row">
                <td :colspan="6">
                  <div class="flex items-center justify-between gap-4">
                    <div class="min-w-0">
                      <span class="font-medium">{{ sensor.name }}</span>
                      <span v-if="sensor.model" class="ml-2 opacity-70">
                        {{ sensor.model }}
                      </span>
                      <span class="ml-2 text-xs opacity-60">
                        {{ sensor.channels.length }} 个通道
                      </span>
                    </div>
                    <div class="flex shrink-0 items-center gap-2 text-xs">
                      <span>报警总开关</span>
                      <Switch
                        size="small"
                        :checked="isSensorAlarmEnabled(sensor)"
                        :disabled="getNumberChannels(sensor).length === 0"
                        @update:checked="
                          setSensorAlarm(sensor, $event === true)
                        "
                      />
                    </div>
                  </div>
                </td>
              </tr>
              <tr
                v-for="channel in sensor.channels"
                :key="channel.id"
                class="channel-row"
              >
                <td class="channel-column">
                  <div class="flex items-center gap-2">
                    <span class="font-medium">{{ channel.channelName }}</span>
                    <Tag v-if="channel.valueKind === 'enum'" color="orange">
                      枚举
                    </Tag>
                  </div>
                  <div class="mt-1 text-xs opacity-60">
                    <span v-if="channel.unit">{{ channel.unit }} · </span>
                    量程 {{ channel.valueMin ?? '--' }} ~
                    {{ channel.valueMax ?? '--' }}
                  </div>
                </td>
                <td
                  v-for="column in thresholdColumns"
                  :key="column.valueField"
                  :class="`threshold-cell threshold-cell--${column.severity}`"
                >
                  <div v-if="channel.valueKind === 'enum'" class="enum-cell">
                    <InputNumber :value="null" disabled placeholder="--" />
                    <Switch size="small" :checked="false" disabled />
                  </div>
                  <div v-else class="threshold-editor">
                    <InputNumber
                      v-model:value="channel[column.valueField]"
                      class="threshold-input"
                      :class="{
                        'threshold-input--muted': !channel[column.enabledField],
                        'threshold-input--out-range': isOutOfRange(
                          channel,
                          channel[column.valueField],
                        ),
                      }"
                      :controls="false"
                      :precision="channel.decimalPlaces ?? undefined"
                      placeholder="--"
                    />
                    <Switch
                      v-model:checked="channel[column.enabledField]"
                      size="small"
                    />
                  </div>
                </td>
                <td class="deadband-column">
                  <InputNumber
                    v-if="channel.valueKind !== 'enum'"
                    v-model:value="channel.deadband"
                    class="deadband-input"
                    :controls="false"
                    :min="0"
                    :precision="channel.decimalPlaces ?? undefined"
                    placeholder="0"
                  />
                  <InputNumber
                    v-else
                    :value="null"
                    class="deadband-input"
                    disabled
                    placeholder="--"
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </Spin>
  </Drawer>
</template>

<style scoped>
.threshold-matrix {
  width: 100%;
  min-width: 1120px;
  border-spacing: 0;
  border-collapse: separate;
  table-layout: fixed;
  font-size: 13px;
}

.threshold-matrix th,
.threshold-matrix td {
  padding: 10px;
  text-align: left;
  vertical-align: middle;
  border-right: 1px solid hsl(var(--border));
  border-bottom: 1px solid hsl(var(--border));
}

.threshold-matrix tr > :last-child {
  border-right: 0;
}

.threshold-matrix tbody:last-child tr:last-child > td {
  border-bottom: 0;
}

.threshold-matrix thead th {
  position: sticky;
  z-index: 2;
  top: 0;
  font-weight: 600;
  background: hsl(var(--muted));
}

.channel-column {
  width: 215px;
}

.deadband-column {
  width: 145px;
}

.sensor-row td {
  background: hsl(var(--accent) / 65%);
}

.channel-row:hover > td {
  background: hsl(var(--accent) / 35%);
}

.threshold-head {
  width: 190px;
  text-align: center !important;
}

.threshold-direction {
  display: inline-block;
  min-width: 25px;
  margin-right: 5px;
  font-size: 16px;
  font-weight: 700;
  line-height: 1;
}

.threshold-head--error,
.threshold-head--error .threshold-direction {
  color: hsl(var(--destructive));
  background: hsl(var(--destructive) / 10%);
}

.threshold-head--warning,
.threshold-head--warning .threshold-direction {
  color: #b76e00;
  background: rgb(250 173 20 / 12%);
}

.threshold-cell--error {
  background: hsl(var(--destructive) / 4%);
}

.threshold-cell--warning {
  background: rgb(250 173 20 / 4%);
}

.threshold-editor,
.enum-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.threshold-input {
  width: 132px;
  min-width: 0;
}

.threshold-cell--error .threshold-input {
  border-color: hsl(var(--destructive) / 45%);
}

.threshold-cell--warning .threshold-input {
  border-color: rgb(250 173 20 / 55%);
}

.threshold-input--muted {
  opacity: 0.55;
}

.threshold-input--out-range {
  border-color: hsl(var(--destructive)) !important;
  box-shadow: 0 0 0 2px hsl(var(--destructive) / 12%);
}

.deadband-input {
  width: 100%;
}

:deep(.ant-input-number) {
  width: 100%;
}

:deep(.ant-input-number-input) {
  padding-right: 8px;
}

:global(html.dark) .threshold-head--warning,
:global(html.dark) .threshold-head--warning .threshold-direction {
  color: #ffd666;
}
</style>
