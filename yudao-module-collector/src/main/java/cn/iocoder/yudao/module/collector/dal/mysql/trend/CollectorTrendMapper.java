package cn.iocoder.yudao.module.collector.dal.mysql.trend;

import cn.iocoder.yudao.module.collector.dal.dataobject.trend.CollectorTrendBucketRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.trend.CollectorTrendRawPointRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 趋势查询 SQL。
 *
 * <p>动态表名只接受 Service 中的 {@code CollectorTrendSource} 枚举常量；桶宽由 Service 计算后
 * 以整数传入，SQL 中只做 interval 乘法。按层分组后一次查询多个通道，避免每通道一条 SQL。</p>
 */
@Mapper
public interface CollectorTrendMapper {

    /**
     * 聚合层按目标桶宽再次分桶。avg 以 valid_count 为权重，count 为下层有效样本数之和，
     * 与聚合服务的 valid_count 口径一致。GROUP BY 使用位置而非别名，避免与表内同名列冲突。
     * HAVING 过滤整桶无有效统计值的组合（如全部坏质量或空值回填的桶），
     * 避免空白点被当作有效数据返回。date_bin 固定以 2000-01-01 为原点，
     * 桶宽选择需按最坏桶数核算（首尾各多覆盖半桶）。
     */
    @Select("""
            <script>
            SELECT channel_id,
                date_bin(INTERVAL '1 second' * #{bucketSeconds}, bucket_time,
                    TIMESTAMP '2000-01-01 00:00:00') AS bucket_time,
                SUM(value_sum) / NULLIF(SUM(valid_count), 0) AS avg,
                MIN(value_min) AS min,
                MAX(value_max) AS max,
                SUM(valid_count)::integer AS count
            FROM ${sourceTable}
            WHERE channel_id IN
            <foreach collection="channelIds" item="channelId" open="(" separator="," close=")">
                #{channelId}
            </foreach>
              AND bucket_time &gt;= #{from}
              AND bucket_time &lt; #{to}
            GROUP BY 1, 2
            HAVING SUM(valid_count) &gt; 0
            ORDER BY 1, 2
            </script>
            """)
    List<CollectorTrendBucketRow> selectAggregateBuckets(@Param("sourceTable") String sourceTable,
                                                         @Param("bucketSeconds") int bucketSeconds,
                                                         @Param("channelIds") Collection<Long> channelIds,
                                                         @Param("from") LocalDateTime from,
                                                         @Param("to") LocalDateTime to);

    /**
     * day 表按 bucket_date 存储；条件仍与 from/to 时间戳比较，正确处理非零点结束时间。
     * 只返回 valid_count &gt; 0 的日期，整桶无有效统计值的行不参与趋势展示。
     */
    @Select("""
            <script>
            SELECT channel_id, bucket_date::timestamp AS bucket_time,
                value_avg AS avg, value_min AS min, value_max AS max, valid_count AS count
            FROM collector_aggregate_day
            WHERE channel_id IN
            <foreach collection="channelIds" item="channelId" open="(" separator="," close=")">
                #{channelId}
            </foreach>
              AND bucket_date &gt;= CAST(#{from} AS date)
              AND bucket_date::timestamp &lt; #{to}
              AND valid_count &gt; 0
            ORDER BY channel_id, bucket_date
            </script>
            """)
    List<CollectorTrendBucketRow> selectDayBuckets(@Param("channelIds") Collection<Long> channelIds,
                                                   @Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to);

    /**
     * raw 层短范围查询：直接命中 (channel_id, collect_time) 主键并触发分区裁剪，
     * 只统计 quality=0 且 value 非 NULL 的有效样本。
     */
    @Select("""
            <script>
            SELECT channel_id,
                date_bin(INTERVAL '1 second' * #{bucketSeconds}, collect_time,
                    TIMESTAMP '2000-01-01 00:00:00') AS bucket_time,
                AVG(value) AS avg, MIN(value) AS min, MAX(value) AS max, COUNT(*)::integer AS count
            FROM collector_sample_raw
            WHERE channel_id IN
            <foreach collection="channelIds" item="channelId" open="(" separator="," close=")">
                #{channelId}
            </foreach>
              AND collect_time &gt;= #{from}
              AND collect_time &lt; #{to}
              AND quality = 0
              AND value IS NOT NULL
            GROUP BY 1, 2
            ORDER BY 1, 2
            </script>
            """)
    List<CollectorTrendBucketRow> selectRawBuckets(@Param("bucketSeconds") int bucketSeconds,
                                                   @Param("channelIds") Collection<Long> channelIds,
                                                   @Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to);

    /**
     * 叠加原始点：按步长桶抽样，每个通道每个步长桶只保留最早的有效采样。
     * 步长由 Service 按最坏桶数（ceil(范围 / 步长) + 1）核算，任意时间对齐下单通道点数
     * 不超过 1000。
     */
    @Select("""
            <script>
            WITH sampled AS (
                SELECT channel_id, collect_time, value,
                    date_bin(INTERVAL '1 second' * #{stepSeconds}, collect_time,
                        TIMESTAMP '2000-01-01 00:00:00') AS bucket_time
                FROM collector_sample_raw
                WHERE channel_id IN
                <foreach collection="channelIds" item="channelId" open="(" separator="," close=")">
                    #{channelId}
                </foreach>
                  AND collect_time &gt;= #{from}
                  AND collect_time &lt; #{to}
                  AND quality = 0
                  AND value IS NOT NULL
            )
            SELECT DISTINCT ON (channel_id, bucket_time) channel_id, bucket_time, value
            FROM sampled
            ORDER BY channel_id, bucket_time, collect_time
            </script>
            """)
    List<CollectorTrendRawPointRow> selectRawPoints(@Param("stepSeconds") int stepSeconds,
                                                    @Param("channelIds") Collection<Long> channelIds,
                                                    @Param("from") LocalDateTime from,
                                                    @Param("to") LocalDateTime to);

    /**
     * 为原始点叠加查询设置明确且较短的 statement_timeout。
     * 使用 {@code SET LOCAL}：只影响当前事务，事务结束（提交或回滚）自动还原，
     * 不会把超时设置泄漏给连接池上的后续请求。timeoutMillis 为 Service 传入的 int
     * 常量，直接内联拼接，不接受外部字符串，无注入面。
     * 注意：必须在事务内执行（Service 的 getSeries 带 {@code @Transactional}）。
     */
    @Update("SET LOCAL statement_timeout = ${timeoutMillis}")
    void setRawOverlayTimeout(@Param("timeoutMillis") int timeoutMillis);

}
