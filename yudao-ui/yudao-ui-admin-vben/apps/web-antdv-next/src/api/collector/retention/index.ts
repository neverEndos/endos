import { requestClient } from '#/api/request';

/**
 * 采集数据保留策略 API。
 *
 * <p>后端固定 8 个数据类，顺序由服务端注册表决定，前端不排序、不补行。
 * LocalDateTime 一律以毫秒时间戳传输，LocalDate 以 yyyy-MM-dd 传输。</p>
 */
export namespace CollectorRetentionApi {
  /** 清理结果分类 */
  export type RetentionOutcome =
    | 'FAILED'
    | 'PARTIAL'
    | 'SKIPPED_DISABLED'
    | 'SKIPPED_LOCKED'
    | 'SKIPPED_PERMANENT'
    | 'SUCCESS';

  /** 预检与清理结果共用的分区项；清理结果的 bound 恒为 null */
  export interface RetentionPartition {
    partitionName: string;
    bound?: null | string;
    /** epoch millis */
    from?: null | number;
    /** epoch millis */
    to?: null | number;
  }

  /** 保留策略 */
  export interface RetentionPolicy {
    id: number;
    dataClass: string;
    displayName: string;
    /** 0 表示永久保留 */
    retentionDays: number;
    /** 未来分区预建天数，仅 sample_raw 有意义 */
    partitionLeadDays?: null | number;
    /** 清理动作：drop_partition / delete_rows */
    action: string;
    /** 0 停用、1 启用 */
    enabled: number;
    description?: null | string;
    /** epoch millis */
    lastRunAt?: null | number;
    lastRunResult?: null | string;
  }

  /** 保留策略更新参数 */
  export interface RetentionPolicyUpdate {
    id: number;
    retentionDays: number;
    /** 仅 sample_raw 允许传值 */
    partitionLeadDays?: number;
    enabled: number;
    description?: string;
  }

  /** 单个数据类预检结果 */
  export interface RetentionPreviewClass {
    dataClass: string;
    displayName: string;
    retentionDays: number;
    enabled: boolean;
    action: string;
    /** epoch millis；策略异常时可能为空 */
    cutoff?: null | number;
    skipped: boolean;
    skipReason?: null | string;
    /** raw 按分区清理，恒为 null */
    candidateRows?: null | number;
    candidatePartitions: RetentionPartition[];
    warnings: string[];
  }

  /** 保留清理预检结果 */
  export interface RetentionPreview {
    /** yyyy-MM-dd */
    today: string;
    classes: RetentionPreviewClass[];
  }

  /** 单个数据类清理结果 */
  export interface RetentionCleanupClassResult {
    dataClass: string;
    displayName: string;
    action: string;
    /** epoch millis */
    cutoff?: null | number;
    outcome: RetentionOutcome;
    deletedRows: number;
    droppedPartitions: RetentionPartition[];
    warnings: string[];
    message?: null | string;
  }

  /** 保留清理执行结果 */
  export interface RetentionCleanupResult {
    /** yyyy-MM-dd */
    today: string;
    lockNotAcquired: boolean;
    totalDeletedRows: number;
    droppedPartitionCount: number;
    hasFailure: boolean;
    results: RetentionCleanupClassResult[];
  }
}

/** 查询保留策略列表；固定 8 条，顺序由后端决定 */
export function getRetentionPolicyList() {
  return requestClient.get<CollectorRetentionApi.RetentionPolicy[]>(
    '/collector/retention/list',
  );
}

/** 更新保留策略；只允许修改保留天数、分区预建天数、开关与说明 */
export function updateRetentionPolicy(
  data: CollectorRetentionApi.RetentionPolicyUpdate,
) {
  return requestClient.put<boolean>('/collector/retention/update', data);
}

/** 只读预检：统计各类候选删除行数与候选 DROP 分区 */
export function previewRetentionCleanup() {
  return requestClient.get<CollectorRetentionApi.RetentionPreview>(
    '/collector/retention/preview',
  );
}

/** 立即执行一次全量清理；同步接口，耗时可能较长 */
export function executeRetentionCleanup() {
  return requestClient.post<CollectorRetentionApi.RetentionCleanupResult>(
    '/collector/retention/cleanup',
    undefined,
    // 同步清理可能超过默认 30 秒，避免前端先超时导致结果无法展示
    { timeout: 5 * 60 * 1000 },
  );
}
