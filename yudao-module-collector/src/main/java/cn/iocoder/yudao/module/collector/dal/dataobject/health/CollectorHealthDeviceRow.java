package cn.iocoder.yudao.module.collector.dal.dataobject.health;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集运行详情 - 设备与设备状态行（{@code collector_device} LEFT JOIN {@code collector_device_status}）。
 *
 * <p>设备状态行可以为空：此时 {@code online / lastSuccessAt / consecutiveFailures} 等均为 null，
 * 表示尚未上报状态，接口不得据此伪造在线或正常。</p>
 */
@Data
public class CollectorHealthDeviceRow {

    /** 设备编号 */
    private Long id;
    /** 设备名称 */
    private String name;
    /** 设备轮询周期（毫秒），来自 collector_device.poll_interval_ms */
    private Integer pollIntervalMs;
    /** 是否在线：0 否、1 是；无状态行时为 null */
    private Integer online;
    /** 最近上线时间 */
    private LocalDateTime lastOnlineAt;
    /** 最近离线时间 */
    private LocalDateTime lastOfflineAt;
    /** 最近一次采集成功时间 */
    private LocalDateTime lastSuccessAt;
    /** 最近一次错误信息 */
    private String lastError;
    /** 连续失败次数 */
    private Integer consecutiveFailures;
    /** 最近一次请求耗时（毫秒） */
    private Integer lastLatencyMs;
    /** 最近一次状态更新时间；无状态行时回落到设备自身 update_time */
    private LocalDateTime lastPollAt;

}
