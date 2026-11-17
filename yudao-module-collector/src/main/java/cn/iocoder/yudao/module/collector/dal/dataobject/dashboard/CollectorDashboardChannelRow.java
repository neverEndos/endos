package cn.iocoder.yudao.module.collector.dal.dataobject.dashboard;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 通道配置与最新值的单次 LEFT JOIN 查询结果。 */
@Data
public class CollectorDashboardChannelRow {

    private Long id;
    private Long sensorId;
    private String name;
    private String unit;
    private String valueKind;
    private BigDecimal valueNum;
    private String valueText;
    private Integer quality;
    private LocalDateTime sampleTime;
    /** 通道级轮询周期预留字段；v1 仍按设备级间隔判定陈旧，暂不使用。 */
    private Integer pollIntervalMs;

}
