package cn.iocoder.yudao.module.collector.dal.mysql.health;

import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthAggregateAbnormalRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthAggregateSummaryRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthAlarmRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthAlarmSummaryRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthDeviceRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthHeartbeatRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthPartitionRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.health.CollectorHealthRawPolicyRow;
import cn.iocoder.yudao.module.collector.service.partition.CollectorPartitionDdlSupport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 采集运行详情只读查询。
 *
 * <p>全部方法都是固定条数的聚合 / JOIN 查询，不按通道循环追加 SQL，因此不存在随通道数增长的 N+1。
 * 唯一涉及的动态表名来自 {@link CollectorPartitionDdlSupport#TABLE} 代码常量，不接受任何外部输入。</p>
 */
@Mapper
public interface CollectorHealthMapper {

    /**
     * 设备与设备状态（可能没有状态行）。
     *
     * <p>{@code last_poll_at} 只取状态行的 update_time：无状态行（设备从未轮询）时为 NULL，
     * 不回落设备配置的 update_time，避免把配置变更时间伪装成轮询时间。</p>
     */
    @Select("""
            SELECT d.id, d.device_name AS name, d.poll_interval_ms,
                   s.online, s.last_success_at,
                   s.last_error, s.consecutive_failures, s.last_latency_ms,
                   s.update_time AS last_poll_at
            FROM collector_device d
            LEFT JOIN collector_device_status s ON s.device_id = d.id
            WHERE d.id = #{deviceId} AND d.deleted = 0
            """)
    CollectorHealthDeviceRow selectDevice(@Param("deviceId") Long deviceId);

    /**
     * 最新一条心跳（进程级，与设备无关）；无心跳返回 null。
     */
    @Select("""
            SELECT instance_id, heartbeat_time, status, write_lag_ms,
                   device_total, device_online, collector_version
            FROM collector_heartbeat
            ORDER BY heartbeat_time DESC, id DESC
            LIMIT 1
            """)
    CollectorHealthHeartbeatRow selectLatestHeartbeat();

    /**
     * 活动报警汇总：只统计 {@code level > 0} 的通道（level 取值为 0/2/3）。
     */
    @Select("""
            SELECT COUNT(*) AS active_count, COALESCE(MAX(level), 0) AS highest_level
            FROM collector_alarm_state
            WHERE device_id = #{deviceId} AND level > 0
            """)
    CollectorHealthAlarmSummaryRow selectAlarmSummary(@Param("deviceId") Long deviceId);

    /**
     * 活动报警明细：一次 LEFT JOIN 带出设备 / 传感器 / 通道名称，最多 20 条。
     */
    @Select("""
            SELECT a.channel_id, a.device_id, a.sensor_id, a.level, a.threshold_type, a.since,
                   a.last_value, a.last_threshold, a.last_sample_time,
                   d.device_name AS device_name, s.sensor_name AS sensor_name,
                   c.channel_name AS channel_name
            FROM collector_alarm_state a
            LEFT JOIN collector_device d ON d.id = a.device_id AND d.deleted = 0
            LEFT JOIN collector_sensor s ON s.id = a.sensor_id AND s.deleted = 0
            LEFT JOIN collector_channel c ON c.id = a.channel_id AND c.deleted = 0
            WHERE a.device_id = #{deviceId} AND a.level > 0
            ORDER BY a.level DESC, a.updated_time DESC, a.channel_id
            LIMIT 20
            """)
    List<CollectorHealthAlarmRow> selectActiveAlarms(@Param("deviceId") Long deviceId);

    /**
     * 聚合水位按 {@code agg_type} 一次分组汇总（不按类型或通道逐条查询）。
     */
    @Select("""
            SELECT agg_type,
                   CAST(COUNT(*) AS integer) AS row_count,
                   CAST(COUNT(*) FILTER (WHERE status <> 0) AS integer) AS abnormal_count,
                   MAX(retry_count) AS max_retry_count,
                   MAX(last_success_time) AS last_success_time,
                   MIN(watermark_time) AS min_watermark_time,
                   MAX(watermark_time) AS max_watermark_time
            FROM collector_aggregate_watermark
            WHERE device_id = #{deviceId}
            GROUP BY agg_type
            """)
    List<CollectorHealthAggregateSummaryRow> selectAggregateSummary(@Param("deviceId") Long deviceId);

    /**
     * 聚合水位异常明细（{@code status <> 0}），一次 JOIN 带出设备与通道名称，最多 20 条。
     */
    @Select("""
            SELECT w.agg_type, w.channel_id, w.device_id, w.status, w.retry_count,
                   w.last_error, w.watermark_time,
                   d.device_name AS device_name, c.channel_name AS channel_name
            FROM collector_aggregate_watermark w
            LEFT JOIN collector_device d ON d.id = w.device_id AND d.deleted = 0
            LEFT JOIN collector_channel c ON c.id = w.channel_id AND c.deleted = 0
            WHERE w.device_id = #{deviceId} AND w.status <> 0
            ORDER BY w.update_time DESC, w.agg_type, w.channel_id
            LIMIT 20
            """)
    List<CollectorHealthAggregateAbnormalRow> selectAggregateAbnormal(@Param("deviceId") Long deviceId);

    /**
     * raw 保留策略行；0 行 / 多行 / 非法 leadDays 由 Service 判定为明确异常状态。
     */
    @Select("""
            SELECT id, partition_lead_days
            FROM collector_retention_policy
            WHERE deleted = 0 AND data_class = 'sample_raw'
            ORDER BY id
            """)
    List<CollectorHealthRawPolicyRow> selectRawPolicyRows();

    /**
     * raw 表已有日分区目录（只读 catalog，不触碰任何数据与 DEFAULT 分区内容）。
     */
    @Select("SELECT c.relname AS name, c.relispartition AS partition_flag,"
            + " pg_get_expr(c.relpartbound, c.oid) AS bound"
            + " FROM pg_inherits i"
            + " JOIN pg_class c ON c.oid = i.inhrelid"
            + " JOIN pg_class p ON p.oid = i.inhparent"
            + " WHERE p.oid = '" + CollectorPartitionDdlSupport.TABLE + "'::regclass"
            + " AND c.relname ~ '^" + CollectorPartitionDdlSupport.PARTITION_PREFIX + "[0-9]{8}$'")
    List<CollectorHealthPartitionRow> selectRawPartitions();

}
