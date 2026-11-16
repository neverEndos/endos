package cn.iocoder.yudao.module.collector.dal.dataobject.protocol;

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
 * 采集协议注册表 DO
 *
 * 协议行由 SQL 种子/运维维护，本模块只提供只读查询。
 */
@TableName(value = "collector_protocol", autoResultMap = true)
@KeySequence("collector_protocol_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CollectorProtocolDO extends BaseDO {

    @TableId
    private Long id;
    /**
     * 协议业务编码，例如 hsi_rs485、modbus_rtu
     */
    private String protocolCode;
    /**
     * 协议展示名称
     */
    private String protocolName;
    /**
     * 传输大类：serial/tcp/udp 等
     */
    private String transportType;
    /**
     * collector 驱动标识
     */
    private String driverKey;
    /**
     * 传感器连接参数 JSON Schema
     */
    @TableField(typeHandler = CollectorJsonbTypeHandler.class)
    private Map<String, Object> connectionSchema;
    /**
     * 通道寻址/解析参数 JSON Schema
     */
    @TableField(typeHandler = CollectorJsonbTypeHandler.class)
    private Map<String, Object> channelSchema;
    /**
     * 协议定义版本
     */
    private String protocolVersion;
    /**
     * 是否允许新增传感器引用：0 否、1 是
     */
    private Integer enabled;
    /**
     * 排序值
     */
    private Integer sort;
    /**
     * 协议说明
     */
    private String description;

}
