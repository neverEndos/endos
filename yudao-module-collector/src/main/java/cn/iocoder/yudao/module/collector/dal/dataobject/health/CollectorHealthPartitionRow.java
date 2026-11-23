package cn.iocoder.yudao.module.collector.dal.dataobject.health;

import lombok.Data;

/**
 * 采集运行详情 - raw 日分区 catalog 行。
 */
@Data
public class CollectorHealthPartitionRow {

    /** 子关系名，形如 collector_sample_raw_p20261123 */
    private String name;
    /** 是否为分区（relispartition） */
    private Boolean partitionFlag;
    /** pg_get_expr(relpartbound) 原文 */
    private String bound;

}
