package cn.iocoder.yudao.module.collector.dal.mysql.sample;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.collector.dal.dataobject.sample.CollectorSampleRawDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CollectorSampleRawMapper extends BaseMapperX<CollectorSampleRawDO> {

    @Select("""
            SELECT sample_id, channel_id, device_id, collect_time, value, raw_value, quality
            FROM collector_sample_raw
            WHERE collect_time > #{lastSampleTime}
               OR (collect_time = #{lastSampleTime} AND sample_id > #{lastSampleId})
            ORDER BY collect_time, sample_id
            LIMIT #{limit}
            """)
    List<CollectorSampleRawDO> selectListAfter(@Param("lastSampleTime") LocalDateTime lastSampleTime,
                                               @Param("lastSampleId") Long lastSampleId,
                                               @Param("limit") Integer limit);

    @Select("""
            SELECT sample_id, channel_id, device_id, collect_time, value, raw_value, quality
            FROM collector_sample_raw
            ORDER BY collect_time DESC, sample_id DESC
            LIMIT 1
            """)
    CollectorSampleRawDO selectLatest();

}
