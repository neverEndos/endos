package cn.iocoder.yudao.module.collector.dal.mysql.history;

import cn.iocoder.yudao.module.collector.dal.dataobject.history.CollectorHistoryAggregateRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.history.CollectorHistoryRawRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通道历史数据只读 SQL。
 *
 * <p><b>注入红线</b>：聚合表名只接受 Service 中 {@code CollectorHistoryService.Granularity}
 * 枚举常量（{@code ${table}}），绝不接受请求参数拼接；其余条件全部使用 {@code #{}} 预编译。</p>
 *
 * <p><b>分区裁剪</b>：{@code collector_sample_raw} 按 {@code collect_time} 做 RANGE 分区，
 * 以下所有 raw 查询都强制携带 {@code collect_time} 区间条件，避免全分区扫描。</p>
 *
 * <p><b>排序</b>：所有查询固定按时间升序（ASC），与接口契约一致。</p>
 */
@Mapper
public interface CollectorHistoryMapper {

    // ==================== raw ====================

    @Select("""
            <script>
            SELECT COUNT(*) FROM collector_sample_raw
            WHERE channel_id = #{channelId}
              AND collect_time &gt;= #{from}
              AND collect_time &lt; #{to}
            </script>
            """)
    long countRaw(@Param("channelId") Long channelId,
                  @Param("from") LocalDateTime from,
                  @Param("to") LocalDateTime to);

    @Select("""
            <script>
            SELECT channel_id, collect_time, value, raw_value, quality
            FROM collector_sample_raw
            WHERE channel_id = #{channelId}
              AND collect_time &gt;= #{from}
              AND collect_time &lt; #{to}
            ORDER BY collect_time ASC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<CollectorHistoryRawRow> selectRawPage(@Param("channelId") Long channelId,
                                               @Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to,
                                               @Param("offset") long offset,
                                               @Param("limit") int limit);

    /**
     * 导出用的键集分页：以 {@code collect_time} 严格大于上一批最后一行作为游标。
     * 主键 {@code (channel_id, collect_time)} 保证同一通道内 collect_time 唯一，
     * 因此不会漏行也不会重复，且无需深分页 OFFSET。
     */
    @Select("""
            <script>
            SELECT channel_id, collect_time, value, raw_value, quality
            FROM collector_sample_raw
            WHERE channel_id = #{channelId}
              AND collect_time &gt;= #{from}
              AND collect_time &lt; #{to}
            <if test="cursor != null">
              AND collect_time &gt; #{cursor}
            </if>
            ORDER BY collect_time ASC
            LIMIT #{limit}
            </script>
            """)
    List<CollectorHistoryRawRow> selectRawBatch(@Param("channelId") Long channelId,
                                                @Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to,
                                                @Param("cursor") LocalDateTime cursor,
                                                @Param("limit") int limit);

    // ==================== second / minute / hour ====================

    @Select("""
            <script>
            SELECT COUNT(*) FROM ${table}
            WHERE channel_id = #{channelId}
              AND bucket_time &gt;= #{from}
              AND bucket_time &lt; #{to}
            </script>
            """)
    long countAggregate(@Param("table") String table,
                        @Param("channelId") Long channelId,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

    @Select("""
            <script>
            SELECT channel_id, bucket_time, sample_count, good_count, bad_count,
                value_avg, value_min, value_max, value_first, value_last
            FROM ${table}
            WHERE channel_id = #{channelId}
              AND bucket_time &gt;= #{from}
              AND bucket_time &lt; #{to}
            ORDER BY bucket_time ASC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<CollectorHistoryAggregateRow> selectAggregatePage(@Param("table") String table,
                                                           @Param("channelId") Long channelId,
                                                           @Param("from") LocalDateTime from,
                                                           @Param("to") LocalDateTime to,
                                                           @Param("offset") long offset,
                                                           @Param("limit") int limit);

    @Select("""
            <script>
            SELECT channel_id, bucket_time, sample_count, good_count, bad_count,
                value_avg, value_min, value_max, value_first, value_last
            FROM ${table}
            WHERE channel_id = #{channelId}
              AND bucket_time &gt;= #{from}
              AND bucket_time &lt; #{to}
            <if test="cursor != null">
              AND bucket_time &gt; #{cursor}
            </if>
            ORDER BY bucket_time ASC
            LIMIT #{limit}
            </script>
            """)
    List<CollectorHistoryAggregateRow> selectAggregateBatch(@Param("table") String table,
                                                            @Param("channelId") Long channelId,
                                                            @Param("from") LocalDateTime from,
                                                            @Param("to") LocalDateTime to,
                                                            @Param("cursor") LocalDateTime cursor,
                                                            @Param("limit") int limit);

    // ==================== day ====================

    /**
     * day 表时间列为 {@code bucket_date}(date)；统一投影为 timestamp，
     * 使 day 与其它粒度共用 {@link CollectorHistoryAggregateRow#getBucketTime()}。
     * 结束条件用 {@code bucket_date::timestamp < to}，正确处理非零点结束时间。
     */
    @Select("""
            <script>
            SELECT COUNT(*) FROM collector_aggregate_day
            WHERE channel_id = #{channelId}
              AND bucket_date &gt;= CAST(#{from} AS date)
              AND bucket_date::timestamp &lt; #{to}
            </script>
            """)
    long countDay(@Param("channelId") Long channelId,
                  @Param("from") LocalDateTime from,
                  @Param("to") LocalDateTime to);

    @Select("""
            <script>
            SELECT channel_id, bucket_date::timestamp AS bucket_time,
                sample_count, good_count, bad_count,
                value_avg, value_min, value_max, value_first, value_last
            FROM collector_aggregate_day
            WHERE channel_id = #{channelId}
              AND bucket_date &gt;= CAST(#{from} AS date)
              AND bucket_date::timestamp &lt; #{to}
            ORDER BY bucket_date ASC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<CollectorHistoryAggregateRow> selectDayPage(@Param("channelId") Long channelId,
                                                     @Param("from") LocalDateTime from,
                                                     @Param("to") LocalDateTime to,
                                                     @Param("offset") long offset,
                                                     @Param("limit") int limit);

    @Select("""
            <script>
            SELECT channel_id, bucket_date::timestamp AS bucket_time,
                sample_count, good_count, bad_count,
                value_avg, value_min, value_max, value_first, value_last
            FROM collector_aggregate_day
            WHERE channel_id = #{channelId}
              AND bucket_date &gt;= CAST(#{from} AS date)
              AND bucket_date::timestamp &lt; #{to}
            <if test="cursor != null">
              AND bucket_date::timestamp &gt; #{cursor}
            </if>
            ORDER BY bucket_date ASC
            LIMIT #{limit}
            </script>
            """)
    List<CollectorHistoryAggregateRow> selectDayBatch(@Param("channelId") Long channelId,
                                                      @Param("from") LocalDateTime from,
                                                      @Param("to") LocalDateTime to,
                                                      @Param("cursor") LocalDateTime cursor,
                                                      @Param("limit") int limit);

}
