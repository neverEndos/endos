package cn.iocoder.yudao.module.collector.dal.dataobject.sensortemplate;

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
 * 传感器参数模板 DO
 *
 * 类型下的通道编码、量程、比例与四档阈值开关；生成实例通道时复制。
 */
@TableName(value = "collector_sensor_template", autoResultMap = true)
@KeySequence("collector_sensor_template_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CollectorSensorTemplateDO extends BaseDO {

    @TableId
    private Long id;
    /**
     * 所属传感器类型编号
     */
    private Long sensorTypeId;
    /**
     * 通道编码（类型内唯一）
     */
    private String channelCode;
    /**
     * 通道名称
     */
    private String channelName;
    /**
     * 值类别：number 数值、enum 枚举
     */
    private String valueKind;
    /**
     * 工程单位
     */
    private String unit;
    /**
     * 图标标识
     */
    private String icon;
    /**
     * 量程下限
     */
    private Double valueMin;
    /**
     * 量程上限
     */
    private Double valueMax;
    /**
     * 数值缩放系数
     */
    private Double scale;
    /**
     * 工程值偏移量
     */
    private Double valueOffset;
    /**
     * 展示小数位
     */
    private Integer decimalPlaces;
    /**
     * 下严重阈值比例
     */
    private Double thresholdLowErrorRatio;
    /**
     * 下警告阈值比例
     */
    private Double thresholdLowWarningRatio;
    /**
     * 上警告阈值比例
     */
    private Double thresholdHighWarningRatio;
    /**
     * 上严重阈值比例
     */
    private Double thresholdHighErrorRatio;
    /**
     * 是否启用阈值报警：0 否、1 是
     */
    private Integer alarmEnabled;
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
     * 协议专用模板参数 JSON
     */
    @TableField(typeHandler = CollectorJsonbTypeHandler.class)
    private Map<String, Object> channelConfig;
    /**
     * 排序值
     */
    private Integer sort;
    /**
     * 是否启用：0 否、1 是
     */
    private Integer enabled;

}
