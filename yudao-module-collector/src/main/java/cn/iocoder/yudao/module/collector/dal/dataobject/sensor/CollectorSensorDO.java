package cn.iocoder.yudao.module.collector.dal.dataobject.sensor;

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
 * 采集传感器 DO
 */
@TableName(value = "collector_sensor", autoResultMap = true)
@KeySequence("collector_sensor_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CollectorSensorDO extends BaseDO {

    @TableId
    private Long id;
    /**
     * 所属设备编号
     */
    private Long deviceId;
    /**
     * 传感器类型编号（可空）
     */
    private Long sensorTypeId;
    /**
     * 传感器编码（设备内唯一）
     */
    private String sensorCode;
    /**
     * 传感器名称
     */
    private String sensorName;
    /**
     * 协议编码（由传感器类型带出）
     */
    private String protocolCode;
    /**
     * 协议连接参数 JSON
     */
    @TableField(typeHandler = CollectorJsonbTypeHandler.class)
    private Map<String, Object> connectionConfig;
    /**
     * 是否启用：0 否、1 是
     */
    private Integer enabled;
    /**
     * 描述
     */
    private String description;
    /**
     * 排序值
     */
    private Integer sort;

}
