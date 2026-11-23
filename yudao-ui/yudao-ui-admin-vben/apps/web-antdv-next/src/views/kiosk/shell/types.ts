export type KioskConnectionState =
  | 'connecting'
  | 'degraded'
  | 'idle'
  | 'offline'
  | 'online';

/** 模块向固定 Shell 汇报的真实全局状态；null 表示尚未取得数据。 */
export interface KioskGlobalStatus {
  activeAlarmCount: null | number;
  alarmDataStale: boolean;
  connection: KioskConnectionState;
  highestAlarmLevel: null | number;
}

export const initialKioskGlobalStatus: KioskGlobalStatus = {
  activeAlarmCount: null,
  alarmDataStale: false,
  connection: 'idle',
  highestAlarmLevel: null,
};
