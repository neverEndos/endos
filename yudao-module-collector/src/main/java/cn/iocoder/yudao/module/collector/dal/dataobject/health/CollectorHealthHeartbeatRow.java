package cn.iocoder.yudao.module.collector.dal.dataobject.health;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集运行详情 - 最新心跳行。
 *
 * <p>只读取采集运行时真正写入的列；{@code cpu_percent / mem_percent / disk_* / temperature_c /
 * config_*} 当前未写入，本行不包含这些字段，避免接口返回假值。</p>
 */
@Data
public class CollectorHealthHeartbeatRow {

    /** 采集器实例编号 */
    private String instanceId;
    /** 心跳时间 */
    private LocalDateTime heartbeatTime;
    /** 状态：starting / running / degraded / stopped */
    private String status;
    /** 最近写入延迟（毫秒） */
    private Integer writeLagMs;
    /** 配置设备总数 */
    private Integer deviceTotal;
    /** 在线设备数 */
    private Integer deviceOnline;
    /** 采集器版本 */
    private String collectorVersion;

}
