package cn.iocoder.yudao.module.collector.dal.dataobject.sensortype;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 传感器类型/型号 DO
 */
@TableName("collector_sensor_type")
@KeySequence("collector_sensor_type_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CollectorSensorTypeDO extends BaseDO {

    @TableId
    private Long id;
    /**
     * 类型/型号业务编码（业务唯一）
     */
    private String typeCode;
    /**
     * 类型/型号展示名称
     */
    private String typeName;
    /**
     * 协议编码（逻辑关联 collector_protocol.protocol_code）
     */
    private String protocolCode;
    /**
     * 制造商
     */
    private String manufacturer;
    /**
     * 型号说明、固件差异与字段映射备注
     */
    private String description;
    /**
     * 是否启用：0 否、1 是
     */
    private Integer enabled;
    /**
     * 排序值
     */
    private Integer sort;

}
