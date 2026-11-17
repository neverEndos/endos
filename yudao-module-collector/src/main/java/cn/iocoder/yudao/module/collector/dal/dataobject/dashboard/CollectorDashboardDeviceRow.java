package cn.iocoder.yudao.module.collector.dal.dataobject.dashboard;

import lombok.Data;

import java.time.LocalDateTime;

/** 看板设备、状态与最近心跳的单次联查结果。 */
@Data
public class CollectorDashboardDeviceRow {

    private Long id;
    private String name;
    private Integer online;
    private LocalDateTime lastPollAt;
    private Integer lastWriteLagMs;
    private Integer consecutiveFailures;
    private Integer pollIntervalMs;
    private LocalDateTime statusUpdateTime;

}
