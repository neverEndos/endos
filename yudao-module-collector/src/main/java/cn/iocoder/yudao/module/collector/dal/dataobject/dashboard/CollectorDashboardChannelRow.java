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

}
