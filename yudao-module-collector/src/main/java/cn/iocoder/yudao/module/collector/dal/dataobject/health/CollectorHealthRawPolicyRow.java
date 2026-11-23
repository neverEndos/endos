package cn.iocoder.yudao.module.collector.dal.dataobject.health;

import lombok.Data;

/**
 * 采集运行详情 - raw 分区策略行（只读：id 与 partition_lead_days）。
 *
 * <p>单独读取而不复用策略列表快照，是因为快照会按数据类去重，无法区分「策略重复」异常。</p>
 */
@Data
public class CollectorHealthRawPolicyRow {

    /** 策略编号 */
    private Long id;
    /** 未来分区预建天数 */
    private Integer partitionLeadDays;

}
