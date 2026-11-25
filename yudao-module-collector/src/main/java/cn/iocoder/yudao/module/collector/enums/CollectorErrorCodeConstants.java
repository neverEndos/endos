package cn.iocoder.yudao.module.collector.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Collector 错误码枚举类
 *
 * collector 采集器，使用 1-023-000-000 ~ 1-024-000-000 段
 */
public interface CollectorErrorCodeConstants {

    // ========== 协议 1-023-000-000 ==========
    ErrorCode PROTOCOL_NOT_EXISTS = new ErrorCode(1_023_000_000, "协议不存在");
    ErrorCode PROTOCOL_NOT_ENABLED = new ErrorCode(1_023_000_001, "协议({})已停用，不允许新增引用");
    ErrorCode COLLECTOR_SCHEMA_INVALID = new ErrorCode(1_023_000_002, "协议参数不符合 JSON Schema：{}");

    // ========== 设备 1-023-001-000 ==========
    ErrorCode DEVICE_NOT_EXISTS = new ErrorCode(1_023_001_000, "采集设备不存在");
    ErrorCode DEVICE_CODE_DUPLICATE = new ErrorCode(1_023_001_001, "设备编码({})已存在");
    ErrorCode DEVICE_HAS_CHILDREN = new ErrorCode(1_023_001_002, "设备下存在传感器、通道或轮询策略，无法删除");

    // ========== 传感器类型 1-023-002-000 ==========
    ErrorCode SENSOR_TYPE_NOT_EXISTS = new ErrorCode(1_023_002_000, "传感器类型不存在");
    ErrorCode SENSOR_TYPE_NOT_ENABLED = new ErrorCode(1_023_002_001, "传感器类型({})已停用，不允许新增传感器");
    ErrorCode SENSOR_TYPE_CODE_DUPLICATE = new ErrorCode(1_023_002_002, "传感器类型编码({})已存在");
    ErrorCode SENSOR_TYPE_HAS_CHILDREN = new ErrorCode(1_023_002_003, "传感器类型下存在参数模板或传感器，无法删除");
    ErrorCode SENSOR_TYPE_PROTOCOL_CHANGE_FORBIDDEN = new ErrorCode(1_023_002_004, "传感器类型下存在参数模板或传感器，不允许修改协议");

    // ========== 传感器参数模板 1-023-003-000 ==========
    ErrorCode SENSOR_TEMPLATE_NOT_EXISTS = new ErrorCode(1_023_003_000, "传感器参数模板不存在");
    ErrorCode SENSOR_TEMPLATE_CODE_DUPLICATE = new ErrorCode(1_023_003_001, "该传感器类型下通道编码({})已存在");
    ErrorCode SENSOR_TEMPLATE_HAS_CHILDREN = new ErrorCode(1_023_003_002, "参数模板已被通道引用，无法删除");
    ErrorCode SENSOR_TEMPLATE_VALUE_KIND_INVALID = new ErrorCode(1_023_003_003, "值类别({})不合法，仅支持 number、enum");
    ErrorCode SENSOR_TEMPLATE_RANGE_INVALID = new ErrorCode(1_023_003_004, "量程不合法：下限必须小于等于上限且为有限数值");
    ErrorCode SENSOR_TEMPLATE_RATIO_INVALID = new ErrorCode(1_023_003_005, "阈值比例不合法：必须为 0~1 的有限数值，且按低严重/低警告/高警告/高严重递增");
    ErrorCode SENSOR_TEMPLATE_SCALE_INVALID = new ErrorCode(1_023_003_006, "缩放系数与偏移量必须为有限数值");
    ErrorCode SENSOR_TEMPLATE_SWITCH_INVALID = new ErrorCode(1_023_003_007, "报警与阈值开关只能为 0 或 1");
    ErrorCode SENSOR_TEMPLATE_DECIMAL_INVALID = new ErrorCode(1_023_003_008, "展示小数位必须在 0~6 之间");
    ErrorCode SENSOR_TEMPLATE_TYPE_CHANGE_FORBIDDEN = new ErrorCode(1_023_003_009, "参数模板已被通道引用，不允许修改所属传感器类型");

    // ========== 传感器 1-023-004-000 ==========
    ErrorCode SENSOR_NOT_EXISTS = new ErrorCode(1_023_004_000, "传感器不存在");
    ErrorCode SENSOR_CODE_DUPLICATE = new ErrorCode(1_023_004_001, "同一设备下传感器编码({})已存在");
    ErrorCode SENSOR_HAS_CHILDREN = new ErrorCode(1_023_004_002, "传感器下存在通道或轮询策略，无法删除");
    ErrorCode SENSOR_TYPE_CHANGE_FORBIDDEN = new ErrorCode(1_023_004_003, "传感器下已存在通道，不允许修改传感器类型");

    // ========== 通道 1-023-005-000 ==========
    ErrorCode CHANNEL_NOT_EXISTS = new ErrorCode(1_023_005_000, "通道不存在");
    ErrorCode CHANNEL_THRESHOLD_INVALID = new ErrorCode(1_023_005_001, "通道阈值不合法：数值必须为有限值、量程下限小于等于上限、四档阈值按顺序递增且开关只能为 0/1");
    ErrorCode CHANNEL_HAS_CHILDREN = new ErrorCode(1_023_005_002, "通道已被轮询策略引用，无法删除");
    ErrorCode CHANNEL_VALUE_INVALID = new ErrorCode(1_023_005_003, "通道数值参数不合法：缩放系数、偏移量、死区必须为有限数值");
    ErrorCode CHANNEL_NOT_IN_DEVICE = new ErrorCode(1_023_005_004, "通道({})不存在或不属于设备({})");
    ErrorCode CHANNEL_ENUM_THRESHOLD_FORBIDDEN = new ErrorCode(1_023_005_005, "枚举通道({})不允许修改数值阈值、报警开关与死区");

    // ========== 聚合 1-023-006-000 ==========
    ErrorCode AGGREGATE_BACKFILL_INVALID = new ErrorCode(1_023_006_000, "聚合回填参数不合法：{}");
    ErrorCode AGGREGATE_FORCE_CLEAN_FORBIDDEN = new ErrorCode(1_023_006_001,
            "没有聚合强制清空权限（collector:aggregate:force-clean）");

    // ========== 趋势 1-023-007-000 ==========
    ErrorCode TREND_QUERY_INVALID = new ErrorCode(1_023_007_000, "趋势查询参数不合法：{}");
    ErrorCode TREND_GRANULARITY_NOT_ALLOWED = new ErrorCode(1_023_007_001, "粒度({})在所选时间范围不可用，{}");
    ErrorCode TREND_RAW_OVERLAY_RANGE_EXCEEDED = new ErrorCode(1_023_007_002,
            "叠加原始点仅支持 {} 小时以内的时间范围");

    // ========== 分区维护 1-023-008-000 ==========
    ErrorCode PARTITION_POLICY_NOT_EXISTS = new ErrorCode(1_023_008_000,
            "采集 raw 分区策略缺失：collector_retention_policy 中 data_class='sample_raw' 的记录不存在");
    ErrorCode PARTITION_POLICY_DUPLICATE = new ErrorCode(1_023_008_001,
            "采集 raw 分区策略不唯一：collector_retention_policy 中 data_class='sample_raw' 的记录数为 {}");
    ErrorCode PARTITION_POLICY_INVALID = new ErrorCode(1_023_008_002,
            "采集 raw 分区策略的 partition_lead_days({}) 超出 0~30 范围");
    ErrorCode PARTITION_DEFAULT_CONFLICT = new ErrorCode(1_023_008_003,
            "默认分区存在冲突数据，需要受控迁移");
    ErrorCode PARTITION_MAINTAIN_FAILED = new ErrorCode(1_023_008_004, "分区维护失败：{}");
    ErrorCode PARTITION_DEFAULT_PARTITION_INVALID = new ErrorCode(1_023_008_005,
            "默认分区对象({})不可用：{}，需人工确认");

    // ========== 数据保留清理 1-023-009-000 ==========
    ErrorCode RETENTION_POLICY_NOT_EXISTS = new ErrorCode(1_023_009_000, "采集数据保留策略不存在");
    ErrorCode RETENTION_POLICY_FIELD_INVALID = new ErrorCode(1_023_009_001, "保留策略字段不合法：{}");
    ErrorCode RETENTION_POLICY_LEAD_DAYS_FORBIDDEN = new ErrorCode(1_023_009_002,
            "仅 sample_raw 允许修改分区预建天数，数据类({})不支持");
    ErrorCode RETENTION_POLICY_UNSUPPORTED = new ErrorCode(1_023_009_003,
            "不支持的保留数据类或清理动作：{}");
    ErrorCode RETENTION_PREVIEW_FAILED = new ErrorCode(1_023_009_005, "保留策略预检失败：{}");
    ErrorCode RETENTION_CLEANUP_FAILED = new ErrorCode(1_023_009_006, "数据保留清理失败：{}");

    // ========== 通道历史数据查询与导出 1-023-010-000 ==========
    ErrorCode HISTORY_QUERY_INVALID = new ErrorCode(1_023_010_000, "历史查询参数不合法：{}");
    ErrorCode HISTORY_RANGE_EXCEEDED = new ErrorCode(1_023_010_001,
            "历史查询时间范围不能超过 365 天，请缩小时间范围");
    ErrorCode HISTORY_GRANULARITY_INVALID = new ErrorCode(1_023_010_002,
            "历史查询粒度({})不合法，仅支持 raw、second、minute、hour、day");
    ErrorCode HISTORY_EXPORT_ROW_LIMIT_EXCEEDED = new ErrorCode(1_023_010_003,
            "导出行数({})超过上限 {} 行，请缩小时间范围或提高聚合分辨率");
    ErrorCode HISTORY_CHANNEL_NOT_EXISTS = new ErrorCode(1_023_010_004, "历史查询通道({})不存在");

    // ========== 报警确认 1-023-011-000 ==========
    ErrorCode ALARM_NOT_EXISTS = new ErrorCode(1_023_011_000, "报警事件不存在");
    ErrorCode ALARM_CLEARED_EVENT_NOT_ACKNOWLEDGEABLE = new ErrorCode(1_023_011_001,
            "恢复事件不允许确认");
    ErrorCode ALARM_ALREADY_ACKNOWLEDGED = new ErrorCode(1_023_011_002, "该报警已确认");
    ErrorCode ALARM_ACKNOWLEDGE_STATUS_INVALID = new ErrorCode(1_023_011_003,
            "该报警已结束，无法确认");

}
