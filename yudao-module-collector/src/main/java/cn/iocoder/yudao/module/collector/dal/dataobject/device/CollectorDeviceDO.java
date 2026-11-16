package cn.iocoder.yudao.module.collector.dal.dataobject.device;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 采集设备 DO
 */
@TableName("collector_device")
@KeySequence("collector_device_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CollectorDeviceDO extends BaseDO {

    @TableId
    private Long id;
    /**
     * 设备编码（业务唯一）
     */
    private String deviceCode;
    /**
     * 设备名称
     */
    private String deviceName;
    /**
     * 是否启用：0 否、1 是
     */
    private Integer enabled;
    /**
     * 设备默认轮询周期（毫秒）
     */
    private Integer pollIntervalMs;
    /**
     * 单次请求超时（毫秒）
     */
    private Integer timeoutMs;
    /**
     * 失败重试次数
     */
    private Integer retries;
    /**
     * 断线重连间隔（毫秒）
     */
    private Integer reconnectIntervalMs;
    /**
     * 描述
     */
    private String description;
    /**
     * 排序值
     */
    private Integer sort;

}
