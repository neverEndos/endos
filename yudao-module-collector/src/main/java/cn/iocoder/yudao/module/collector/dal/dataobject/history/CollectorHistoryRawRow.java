package cn.iocoder.yudao.module.collector.dal.dataobject.history;

import lombok.Data;

import java.time.LocalDateTime;

/** 原始采样行（历史查询 raw 粒度）。 */
@Data
public class CollectorHistoryRawRow {

    private Long channelId;

    /** 采样时间（Asia/Shanghai 墙钟）。 */
    private LocalDateTime collectTime;

    private Double value;

    private Long rawValue;

    /** 0 good、1 uncertain、2 bad。 */
    private Integer quality;

}
