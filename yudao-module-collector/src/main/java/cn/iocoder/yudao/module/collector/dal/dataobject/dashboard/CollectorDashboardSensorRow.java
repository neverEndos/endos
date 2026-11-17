package cn.iocoder.yudao.module.collector.dal.dataobject.dashboard;

import lombok.Data;

/** 看板传感器及型号联查结果。 */
@Data
public class CollectorDashboardSensorRow {

    private Long id;
    private String name;
    private String model;

}
