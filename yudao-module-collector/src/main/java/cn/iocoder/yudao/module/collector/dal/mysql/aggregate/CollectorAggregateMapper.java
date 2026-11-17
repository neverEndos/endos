package cn.iocoder.yudao.module.collector.dal.mysql.aggregate;

import cn.iocoder.yudao.module.collector.dal.dataobject.aggregate.CollectorAggregateBucketDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 多级聚合动态 SQL。动态表名、时间列只接受 Service 中的枚举常量，不接收 Web 输入。
 */
@Mapper
public interface CollectorAggregateMapper {

    @Select("""
            <script>
            SELECT channel_id, device_id,
                date_bin(CAST(#{bucketInterval} AS interval), ${timeColumn}, TIMESTAMP '2000-01-01 00:00:00') AS bucket_time,
            <choose>
                <when test="fromRaw">
                    COUNT(*)::integer AS sample_count,
                    COUNT(*) FILTER (WHERE quality = 0)::integer AS good_count,
                    COUNT(*) FILTER (WHERE quality = 0 AND value IS NOT NULL)::integer AS valid_count,
                    COUNT(*) FILTER (WHERE quality = 2)::integer AS bad_count,
                    SUM(value) FILTER (WHERE quality = 0 AND value IS NOT NULL)
                        / NULLIF(COUNT(*) FILTER (WHERE quality = 0 AND value IS NOT NULL), 0) AS value_avg,
                    MIN(value) FILTER (WHERE quality = 0 AND value IS NOT NULL) AS value_min,
                    MAX(value) FILTER (WHERE quality = 0 AND value IS NOT NULL) AS value_max,
                    SUM(value) FILTER (WHERE quality = 0 AND value IS NOT NULL) AS value_sum,
                    (array_agg(value ORDER BY ${timeColumn}, sample_id)
                        FILTER (WHERE quality = 0 AND value IS NOT NULL))[1] AS value_first,
                    (array_agg(value ORDER BY ${timeColumn} DESC, sample_id DESC)
                        FILTER (WHERE quality = 0 AND value IS NOT NULL))[1] AS value_last
                </when>
                <otherwise>
                    SUM(sample_count)::integer AS sample_count,
                    SUM(good_count)::integer AS good_count,
                    SUM(valid_count)::integer AS valid_count,
                    SUM(bad_count)::integer AS bad_count,
                    COUNT(*)::integer AS source_bucket_count,
                    SUM(value_sum) / NULLIF(SUM(valid_count), 0) AS value_avg,
                    MIN(value_min) AS value_min,
                    MAX(value_max) AS value_max,
                    SUM(value_sum) AS value_sum,
                    (array_agg(value_first ORDER BY ${timeColumn})
                        FILTER (WHERE value_first IS NOT NULL))[1] AS value_first,
                    (array_agg(value_last ORDER BY ${timeColumn} DESC)
                        FILTER (WHERE value_last IS NOT NULL))[1] AS value_last
                </otherwise>
            </choose>
            FROM ${sourceTable}
            WHERE channel_id = #{channelId}
              AND ${timeColumn} &gt;= #{from}
              AND ${timeColumn} &lt; #{to}
            -- 不能按 bucket_time 名称分组：下层表有同名列时会按输入列分组而非 date_bin 结果。
            GROUP BY 1, 2, 3
            ORDER BY 3
            </script>
            """)
    List<CollectorAggregateBucketDO> selectBuckets(@Param("sourceTable") String sourceTable,
                                                   @Param("timeColumn") String timeColumn,
                                                   @Param("bucketInterval") String bucketInterval,
                                                   @Param("fromRaw") boolean fromRaw,
                                                   @Param("channelId") Long channelId,
                                                   @Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to);

    @Insert("""
            <script>
            INSERT INTO ${targetTable} (
                channel_id, device_id,
                <choose><when test="day">bucket_date</when><otherwise>bucket_time</otherwise></choose>,
                sample_count, good_count, valid_count, bad_count, value_avg, value_min, value_max,
                value_sum, value_first, value_last, update_time
            ) VALUES
            <foreach collection="buckets" item="item" separator=",">
                (#{item.channelId}, #{item.deviceId}, #{item.bucketTime}, #{item.sampleCount},
                 #{item.goodCount}, #{item.validCount}, #{item.badCount}, #{item.valueAvg},
                 #{item.valueMin}, #{item.valueMax}, #{item.valueSum}, #{item.valueFirst},
                 #{item.valueLast}, CURRENT_TIMESTAMP)
            </foreach>
            ON CONFLICT (channel_id,
                <choose><when test="day">bucket_date</when><otherwise>bucket_time</otherwise></choose>) DO UPDATE SET
                device_id = EXCLUDED.device_id,
                sample_count = EXCLUDED.sample_count,
                good_count = EXCLUDED.good_count,
                valid_count = EXCLUDED.valid_count,
                bad_count = EXCLUDED.bad_count,
                value_avg = EXCLUDED.value_avg,
                value_min = EXCLUDED.value_min,
                value_max = EXCLUDED.value_max,
                value_sum = EXCLUDED.value_sum,
                value_first = EXCLUDED.value_first,
                value_last = EXCLUDED.value_last,
                update_time = CURRENT_TIMESTAMP
            </script>
            """)
    int upsertBuckets(@Param("targetTable") String targetTable, @Param("day") boolean day,
                      @Param("buckets") List<CollectorAggregateBucketDO> buckets);

    /** 显式回填前清空目标区间；定时任务不调用，避免删除仍保留的历史聚合。 */
    @Delete("""
            <script>
            DELETE FROM ${targetTable}
            WHERE channel_id = #{channelId}
              AND <choose><when test="day">bucket_date</when><otherwise>bucket_time</otherwise></choose>
                  &gt;= <choose><when test="day">CAST(#{from} AS date)</when><otherwise>#{from}</otherwise></choose>
              AND <choose><when test="day">bucket_date</when><otherwise>bucket_time</otherwise></choose>
                  &lt; <choose><when test="day">CAST(#{to} AS date)</when><otherwise>#{to}</otherwise></choose>
            </script>
            """)
    int deleteBuckets(@Param("targetTable") String targetTable, @Param("day") boolean day,
                      @Param("channelId") Long channelId,
                      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Insert("""
            INSERT INTO collector_aggregate_watermark (
                agg_type, channel_id, device_id, watermark_time, backfill_from, backfill_to,
                status, retry_count, last_error, locked_until, last_success_time, update_time
            ) VALUES (
                #{aggType}, #{channelId}, #{deviceId}, #{watermarkTime}, #{backfillFrom}, #{backfillTo},
                0, 0, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            )
            ON CONFLICT (agg_type, channel_id) DO UPDATE SET
                device_id = EXCLUDED.device_id,
                watermark_time = GREATEST(collector_aggregate_watermark.watermark_time, EXCLUDED.watermark_time),
                backfill_from = EXCLUDED.backfill_from,
                backfill_to = EXCLUDED.backfill_to,
                status = 0,
                retry_count = 0,
                last_error = NULL,
                locked_until = NULL,
                last_success_time = CURRENT_TIMESTAMP,
                update_time = CURRENT_TIMESTAMP
            """)
    int upsertWatermark(@Param("aggType") String aggType, @Param("channelId") Long channelId,
                        @Param("deviceId") Long deviceId, @Param("watermarkTime") LocalDateTime watermarkTime,
                        @Param("backfillFrom") LocalDateTime backfillFrom,
                        @Param("backfillTo") LocalDateTime backfillTo);

}
