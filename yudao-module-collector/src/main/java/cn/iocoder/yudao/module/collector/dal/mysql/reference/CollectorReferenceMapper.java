package cn.iocoder.yudao.module.collector.dal.mysql.reference;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 采集器配置表的外部引用计数 Mapper。
 *
 * 仅用于删除前的下级引用校验，避免为尚未实现的轮询/报警模块引入 DO。
 */
@Mapper
public interface CollectorReferenceMapper {

    @Select("SELECT COUNT(*) FROM collector_poll_schedule WHERE device_id = #{deviceId} AND deleted = 0")
    long selectPollScheduleCountByDeviceId(@Param("deviceId") Long deviceId);

    @Select("SELECT COUNT(*) FROM collector_poll_schedule WHERE sensor_id = #{sensorId} AND deleted = 0")
    long selectPollScheduleCountBySensorId(@Param("sensorId") Long sensorId);

    @Select("SELECT COUNT(*) FROM collector_poll_schedule WHERE channel_id = #{channelId} AND deleted = 0")
    long selectPollScheduleCountByChannelId(@Param("channelId") Long channelId);

}
