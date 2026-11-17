package cn.iocoder.yudao.module.collector.dal.dataobject.sample;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 原始采样 DO（报警引擎只读）
 */
@Data
@TableName("collector_sample_raw")
public class CollectorSampleRawDO {

    /**
     * 全局递增采样编号
     */
    private Long sampleId;
    /**
     * 通道编号
     */
    private Long channelId;
    /**
     * 设备编号
     */
    private Long deviceId;
    /**
     * 采样时间
     */
    private LocalDateTime collectTime;
    /**
     * 工程值
     */
    private Double value;
    /**
     * 整数原始值
     */
    private Long rawValue;
    /**
     * 质量码：0 good、1 uncertain、2 bad
     */
    private Integer quality;

}
