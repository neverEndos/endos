package cn.iocoder.yudao.module.collector.dal.dataobject.health;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集运行详情 - 聚合水位异常明细行（{@code status <> 0}），一次 JOIN 带出设备与通道名称。
 */
@Data
public class CollectorHealthAggregateAbnormalRow {

    /** 聚合类型 */
    private String aggType;
    /** 通道编号 */
    private Long channelId;
    /** 设备编号 */
    private Long deviceId;
    /** 状态：1 运行中、2 失败（均为预留，当前流程未写入） */
    private Integer status;
    /** 重试次数（预留，当前恒为 0） */
    private Integer retryCount;
    /** 最近一次错误信息（预留，当前未写入） */
    private String lastError;
    /** 最新已成功提交桶的结束时间 */
    private LocalDateTime watermarkTime;
    /** 设备名称 */
    private String deviceName;
    /** 通道名称 */
    private String channelName;

}
