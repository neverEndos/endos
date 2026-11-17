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

}
