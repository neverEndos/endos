package cn.iocoder.yudao.module.collector.dal.dataobject.channel;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.iocoder.yudao.module.collector.dal.mysql.handler.CollectorJsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Map;

/**
 * 采集通道 DO
 */
@TableName(value = "collector_channel", autoResultMap = true)
@KeySequence("collector_channel_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CollectorChannelDO extends BaseDO {

    @TableId
    private Long id;
    /**
     * 所属设备编号（冗余，与传感器所属设备一致）
     */
    private Long deviceId;
    /**
     * 所属传感器编号
     */
    private Long sensorId;
    /**
     * 生成该通道的模板编号（可空）
     */
    private Long sensorTemplateId;
    /**
     * 通道编码（传感器内唯一）
     */
    private String channelCode;
    /**
     * 通道名称
     */
    private String channelName;
    /**
     * 是否启用：0 否、1 是
     */
    private Integer enabled;
    /**
     * 通用值类型：首期仅 number
     */
    private String valueType;
    /**
     * 值类别：number 数值、enum 枚举
     */
    private String valueKind;
    /**
     * 协议专用寻址/解析参数 JSON
     */
    @TableField(typeHandler = CollectorJsonbTypeHandler.class)
    private Map<String, Object> channelConfig;
    /**
     * 数值缩放系数
     */
    private Double scale;
    /**
     * 工程值偏移量
     */
    private Double valueOffset;
    /**
     * 工程单位
     */
    private String unit;
    /**
     * 图标标识
     */
    private String icon;
    /**
     * 展示小数位
     */
    private Integer decimalPlaces;
    /**
     * 变化死区
     */
    private Double deadband;
    /**
     * 量程下限
     */
    private Double valueMin;
    /**
     * 量程上限
     */
    private Double valueMax;
    /**
     * 下严重阈值
     */
    private Double thresholdLowError;
    /**
     * 下警告阈值
     */
    private Double thresholdLowWarning;
    /**
     * 上警告阈值
     */
    private Double thresholdHighWarning;
    /**
     * 上严重阈值
     */
    private Double thresholdHighError;
    /**
     * 下严重阈值开关：0 否、1 是
     */
    private Integer thresholdLowErrorEnabled;
    /**
     * 下警告阈值开关：0 否、1 是
     */
    private Integer thresholdLowWarningEnabled;
    /**
     * 上警告阈值开关：0 否、1 是
     */
    private Integer thresholdHighWarningEnabled;
    /**
     * 上严重阈值开关：0 否、1 是
     */
    private Integer thresholdHighErrorEnabled;
    /**
     * 报警恢复死区
     */
    private Double alarmDeadband;
    /**
     * 是否启用阈值报警：0 否、1 是
     */
    private Integer alarmEnabled;
    /**
     * 独立轮询周期（NULL 继承设备）
     */
    private Integer pollIntervalMs;
    /**
     * 描述
     */
    private String description;
    /**
     * 排序值
     */
    private Integer sort;

}
