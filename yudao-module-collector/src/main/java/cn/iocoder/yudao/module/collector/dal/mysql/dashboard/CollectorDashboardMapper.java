package cn.iocoder.yudao.module.collector.dal.mysql.dashboard;

import cn.iocoder.yudao.module.collector.dal.dataobject.dashboard.CollectorDashboardChannelRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.dashboard.CollectorDashboardDeviceRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.dashboard.CollectorDashboardSensorRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 最新值看板聚合查询；每次请求固定三条 SQL，不按传感器或通道追加查询。 */
@Mapper
public interface CollectorDashboardMapper {

    @Select("""
            SELECT d.id, d.device_name AS name, d.poll_interval_ms,
                   COALESCE(s.online, 0) AS online,
                   COALESCE(s.update_time, d.update_time) AS last_poll_at,
                   s.update_time AS status_update_time,
                   COALESCE(s.consecutive_failures, 0) AS consecutive_failures,
                   h.write_lag_ms AS last_write_lag_ms
            FROM collector_device d
            LEFT JOIN collector_device_status s ON s.device_id = d.id
            LEFT JOIN LATERAL (
                SELECT h0.write_lag_ms
                FROM collector_heartbeat h0
                ORDER BY h0.heartbeat_time DESC, h0.id DESC
                LIMIT 1
            ) h ON TRUE
            WHERE d.id = #{deviceId} AND d.deleted = 0
            """)
    CollectorDashboardDeviceRow selectDeviceState(@Param("deviceId") Long deviceId);

    @Select("""
            SELECT s.id, s.sensor_name AS name, st.type_name AS model
            FROM collector_sensor s
            LEFT JOIN collector_sensor_type st
                ON st.id = s.sensor_type_id AND st.deleted = 0
            WHERE s.device_id = #{deviceId} AND s.deleted = 0
            ORDER BY s.sort, s.id
            """)
    List<CollectorDashboardSensorRow> selectSensors(@Param("deviceId") Long deviceId);

    @Select("""
            SELECT c.id, c.sensor_id, c.channel_name AS name, c.unit, c.value_kind,
                   l.value_num, l.value_text, l.quality, l.sample_time
            FROM collector_channel c
            LEFT JOIN collector_channel_latest l ON l.channel_id = c.id
            WHERE c.device_id = #{deviceId} AND c.deleted = 0
            ORDER BY c.sensor_id, c.sort, c.id
            """)
    List<CollectorDashboardChannelRow> selectChannelLatest(@Param("deviceId") Long deviceId);

}
