package cn.iocoder.yudao.module.collector.service.history;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.collector.controller.admin.history.vo.CollectorHistoryExportReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.history.vo.CollectorHistoryPageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.history.vo.CollectorHistoryRowVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.history.CollectorHistoryAggregateRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.history.CollectorHistoryRawRow;
import cn.iocoder.yudao.module.collector.dal.mysql.channel.CollectorChannelMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.history.CollectorHistoryMapper;
import cn.iocoder.yudao.module.collector.util.CollectorDashboardUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.HISTORY_CHANNEL_NOT_EXISTS;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.HISTORY_EXPORT_ROW_LIMIT_EXCEEDED;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.HISTORY_GRANULARITY_INVALID;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.HISTORY_QUERY_INVALID;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.HISTORY_RANGE_EXCEEDED;

/**
 * 通道历史数据只读实现。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>时间区间为 <b>[from, to)</b>，显式按 Asia/Shanghai 墙钟换算，不依赖 JVM 默认时区；</li>
 *   <li>raw 为分区表，所有 raw SQL 均携带 {@code collect_time} 区间以触发分区裁剪；</li>
 *   <li>聚合表名只来自 {@link Granularity} 枚举常量，杜绝 SQL 注入；</li>
 *   <li>排序固定时间升序，分页与导出共用同一套筛选条件。</li>
 * </ul>
 */
@Slf4j
@Service
public class CollectorHistoryServiceImpl implements CollectorHistoryService {

    /** 分页与导出的时间范围上限：超过 365 天直接报错，不静默截断。 */
    static final long MAX_RANGE_MILLIS = Duration.ofDays(365).toMillis();
    /** 导出行数上限；超过时提示缩小时间范围或提高聚合分辨率。 */
    static final long MAX_EXPORT_ROWS = 500_000L;
    /** 导出每批查询行数，配合键集游标流式写出，避免一次性加载全部数据。 */
    static final int EXPORT_BATCH_SIZE = 5_000;
    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_PAGE_SIZE = 20;

    private static final DateTimeFormatter CSV_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 文件名时间格式：yyyyMMddHHmmss（紧凑、无特殊字符，前端与 Excel 均友好）。 */
    private static final DateTimeFormatter FILE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final String CSV_CONTENT_TYPE = "text/csv; charset=UTF-8";
    private static final String CSV_LINE_SEPARATOR = "\r\n";
    private static final String RAW_CSV_HEADER = "时间,值,原始值,质量码";
    private static final String AGGREGATE_CSV_HEADER = "时间,采样数,正常数,坏值数,平均值,最小值,最大值,首值,末值";

    private final CollectorHistoryMapper historyMapper;
    private final CollectorChannelMapper channelMapper;

    public CollectorHistoryServiceImpl(CollectorHistoryMapper historyMapper,
                                      CollectorChannelMapper channelMapper) {
        this.historyMapper = historyMapper;
        this.channelMapper = channelMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CollectorHistoryRowVO> getHistoryPage(CollectorHistoryPageReqVO reqVO) {
        QueryPlan plan = resolvePlan(reqVO.getChannelId(), reqVO.getFrom(), reqVO.getTo(),
                reqVO.getGranularity());
        int pageNo = reqVO.getPageNo() == null || reqVO.getPageNo() < 1 ? 1 : reqVO.getPageNo();
        int pageSize = reqVO.getPageSize() == null || reqVO.getPageSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(reqVO.getPageSize(), MAX_PAGE_SIZE);
        long total = countRows(plan);
        if (total == 0) {
            // 无数据返回空页，不伪造 0 行占位。
            return PageResult.empty(0L);
        }
        long offset = (long) (pageNo - 1) * pageSize;
        if (offset >= total) {
            return new PageResult<>(new ArrayList<>(), total);
        }
        List<CollectorHistoryRowVO> rows = selectPage(plan, offset, pageSize);
        return new PageResult<>(rows, total);
    }

    @Override
    public void exportHistoryCsv(CollectorHistoryExportReqVO reqVO,
                                 HttpServletResponse response) throws IOException {
        // 1. 全部校验前置：范围、粒度、通道存在性、行数上限，任一不满足都在写出任何字节之前失败。
        QueryPlan plan = resolvePlan(reqVO.getChannelId(), reqVO.getFrom(), reqVO.getTo(),
                reqVO.getGranularity());
        long total = countRows(plan);
        if (total > MAX_EXPORT_ROWS) {
            throw exception(HISTORY_EXPORT_ROW_LIMIT_EXCEEDED, total, MAX_EXPORT_ROWS);
        }

        // 2. 写响应头；此后进入流式写出阶段，不再抛业务异常。
        response.setContentType(CSV_CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + encodeRfc5987(buildFileName(plan)));
        response.setHeader("Cache-Control", "no-store");

        long written = 0;
        Writer writer = new BufferedWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
        try {
            // UTF-8 BOM：缺少时 Excel 打开会中文乱码。
            writer.write('\uFEFF');
            writer.write(plan.granularity().isRaw() ? RAW_CSV_HEADER : AGGREGATE_CSV_HEADER);
            writer.write(CSV_LINE_SEPARATOR);
            written = writeBatches(plan, writer);
            writer.flush();
            log.info("[exportHistoryCsv][通道({}) 粒度({}) 范围 {}-{} 导出完成，共 {} 行]",
                    plan.channel().getId(), plan.granularity().getCode(), plan.from(), plan.to(),
                    written);
        } catch (IOException | RuntimeException ex) {
            // 响应体可能已部分写出：此处只记录日志并向上抛出，由容器中断连接。
            // 绝不在已提交的 CSV 流里再写 JSON 错误体。
            log.error("[exportHistoryCsv][通道({}) 粒度({}) 范围 {}-{} 导出中断，已写 {} 行]",
                    plan.channel().getId(), plan.granularity().getCode(), plan.from(), plan.to(),
                    written, ex);
            throw ex;
        }
    }

    // ==================== 分批写出 ====================

    /** 按 {@link #EXPORT_BATCH_SIZE} 分批取数并逐行写出，返回实际写出行数。 */
    private long writeBatches(QueryPlan plan, Writer writer) throws IOException {
        if (plan.granularity().isRaw()) {
            return writeBatched(writer, new RawRowAdapter(),
                    (cursor, limit) -> historyMapper.selectRawBatch(plan.channel().getId(),
                            plan.from(), plan.to(), cursor, limit));
        }
        if (plan.granularity().isDay()) {
            return writeBatched(writer, new AggregateRowAdapter(),
                    (cursor, limit) -> historyMapper.selectDayBatch(plan.channel().getId(),
                            plan.from(), plan.to(), cursor, limit));
        }
        String table = plan.granularity().getTable();
        return writeBatched(writer, new AggregateRowAdapter(),
                (cursor, limit) -> historyMapper.selectAggregateBatch(table,
                        plan.channel().getId(), plan.from(), plan.to(), cursor, limit));
    }

    /**
     * 键集分页的通用写出循环：每批最多 {@link #EXPORT_BATCH_SIZE} 行，
     * 以上一批最后一行的时间作为下一批游标，SQL 语句数只随批次数增长，不随行数线性增长。
     */
    private <T> long writeBatched(Writer writer, RowAdapter<T> adapter, BatchFetcher<T> fetcher)
            throws IOException {
        long written = 0;
        LocalDateTime cursor = null;
        while (true) {
            List<T> batch = fetcher.fetch(cursor, EXPORT_BATCH_SIZE);
            if (batch.isEmpty()) {
                return written;
            }
            for (T row : batch) {
                writer.write(adapter.toCsvLine(row));
                writer.write(CSV_LINE_SEPARATOR);
            }
            written += batch.size();
            if (batch.size() < EXPORT_BATCH_SIZE) {
                return written;
            }
            cursor = adapter.bucketTimeOf(batch.get(batch.size() - 1));
        }
    }

    @FunctionalInterface
    private interface BatchFetcher<T> {
        List<T> fetch(LocalDateTime cursor, int limit);
    }

    private interface RowAdapter<T> {
        String toCsvLine(T row);

        LocalDateTime bucketTimeOf(T row);
    }

    private static final class RawRowAdapter implements RowAdapter<CollectorHistoryRawRow> {

        @Override
        public String toCsvLine(CollectorHistoryRawRow row) {
            return csvTime(CollectorDashboardUtils.toEpochMilli(row.getCollectTime()))
                    + ',' + csvNumber(row.getValue())
                    + ',' + csvLong(row.getRawValue())
                    + ',' + csvInteger(row.getQuality());
        }

        @Override
        public LocalDateTime bucketTimeOf(CollectorHistoryRawRow row) {
            return row.getCollectTime();
        }
    }

    private static final class AggregateRowAdapter implements RowAdapter<CollectorHistoryAggregateRow> {

        @Override
        public String toCsvLine(CollectorHistoryAggregateRow row) {
            return csvTime(CollectorDashboardUtils.toEpochMilli(row.getBucketTime()))
                    + ',' + csvInteger(row.getSampleCount())
                    + ',' + csvInteger(row.getGoodCount())
                    + ',' + csvInteger(row.getBadCount())
                    + ',' + csvNumber(row.getValueAvg())
                    + ',' + csvNumber(row.getValueMin())
                    + ',' + csvNumber(row.getValueMax())
                    + ',' + csvNumber(row.getValueFirst())
                    + ',' + csvNumber(row.getValueLast());
        }

        @Override
        public LocalDateTime bucketTimeOf(CollectorHistoryAggregateRow row) {
            return row.getBucketTime();
        }
    }

    // ==================== 查询与校验 ====================

    private long countRows(QueryPlan plan) {
        Granularity granularity = plan.granularity();
        if (granularity.isRaw()) {
            return historyMapper.countRaw(plan.channel().getId(), plan.from(), plan.to());
        }
        if (granularity.isDay()) {
            return historyMapper.countDay(plan.channel().getId(), plan.from(), plan.to());
        }
        return historyMapper.countAggregate(granularity.getTable(), plan.channel().getId(),
                plan.from(), plan.to());
    }

    private List<CollectorHistoryRowVO> selectPage(QueryPlan plan, long offset, int limit) {
        Granularity granularity = plan.granularity();
        if (granularity.isRaw()) {
            List<CollectorHistoryRawRow> rows = historyMapper.selectRawPage(plan.channel().getId(),
                    plan.from(), plan.to(), offset, limit);
            List<CollectorHistoryRowVO> result = new ArrayList<>(rows.size());
            for (CollectorHistoryRawRow row : rows) {
                CollectorHistoryRowVO vo = baseRow(plan, row.getCollectTime());
                vo.setValue(row.getValue());
                vo.setRawValue(row.getRawValue());
                vo.setQuality(row.getQuality());
                result.add(vo);
            }
            return result;
        }
        List<CollectorHistoryAggregateRow> rows = granularity.isDay()
                ? historyMapper.selectDayPage(plan.channel().getId(), plan.from(), plan.to(), offset, limit)
                : historyMapper.selectAggregatePage(granularity.getTable(), plan.channel().getId(),
                        plan.from(), plan.to(), offset, limit);
        List<CollectorHistoryRowVO> result = new ArrayList<>(rows.size());
        for (CollectorHistoryAggregateRow row : rows) {
            CollectorHistoryRowVO vo = baseRow(plan, row.getBucketTime());
            vo.setSampleCount(row.getSampleCount());
            vo.setGoodCount(row.getGoodCount());
            vo.setBadCount(row.getBadCount());
            vo.setValueAvg(row.getValueAvg());
            vo.setValueMin(row.getValueMin());
            vo.setValueMax(row.getValueMax());
            vo.setValueFirst(row.getValueFirst());
            vo.setValueLast(row.getValueLast());
            result.add(vo);
        }
        return result;
    }

    private CollectorHistoryRowVO baseRow(QueryPlan plan, LocalDateTime bucketTime) {
        CollectorHistoryRowVO vo = new CollectorHistoryRowVO();
        vo.setBucketTime(CollectorDashboardUtils.toEpochMilli(bucketTime));
        vo.setUnit(plan.channel().getUnit());
        vo.setChannelName(plan.channel().getChannelName());
        return vo;
    }

    private QueryPlan resolvePlan(Long channelId, Long from, Long to, String granularityCode) {
        if (channelId == null) {
            throw exception(HISTORY_QUERY_INVALID, "通道编号不能为空");
        }
        if (from == null || to == null) {
            throw exception(HISTORY_QUERY_INVALID, "开始时间与结束时间不能为空");
        }
        long rangeMillis;
        try {
            rangeMillis = Math.subtractExact(to, from);
        } catch (ArithmeticException ex) {
            throw exception(HISTORY_QUERY_INVALID, "时间范围溢出");
        }
        if (rangeMillis <= 0) {
            throw exception(HISTORY_QUERY_INVALID, "结束时间必须晚于开始时间");
        }
        if (rangeMillis > MAX_RANGE_MILLIS) {
            throw exception(HISTORY_RANGE_EXCEEDED);
        }
        Granularity granularity = parseGranularity(granularityCode);
        // 通道信息一次查出，分页/导出复用，避免逐行回查。
        CollectorChannelDO channel = channelMapper.selectById(channelId);
        if (channel == null) {
            throw exception(HISTORY_CHANNEL_NOT_EXISTS, channelId);
        }
        return new QueryPlan(channel, granularity, toLocalDateTime(from), toLocalDateTime(to));
    }

    private Granularity parseGranularity(String code) {
        try {
            return Granularity.fromCode(code);
        } catch (IllegalArgumentException ex) {
            throw exception(HISTORY_GRANULARITY_INVALID, code);
        }
    }

    private static LocalDateTime toLocalDateTime(Long epochMillis) {
        return Instant.ofEpochMilli(epochMillis)
                .atZone(CollectorDashboardUtils.SHANGHAI_ZONE)
                .toLocalDateTime();
    }

    // ==================== CSV 辅助 ====================

    /** 文件名：通道名_开始时间_结束时间.csv，时间格式 yyyyMMddHHmmss（Asia/Shanghai）。 */
    private String buildFileName(QueryPlan plan) {
        String channelName = plan.channel().getChannelName();
        if (channelName == null || channelName.isBlank()) {
            channelName = "channel-" + plan.channel().getId();
        }
        return sanitizeFileName(channelName)
                + '_' + plan.from().format(FILE_TIME_FORMATTER)
                + '_' + plan.to().format(FILE_TIME_FORMATTER)
                + ".csv";
    }

    /** 去掉文件名中在多数系统上非法的字符，避免 Content-Disposition 解析异常。 */
    private static String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
    }

    /** RFC 5987 percent-encoding：URLEncoder 的 {@code +} 表示空格在 filename* 中不合法，需转成 %20。 */
    private static String encodeRfc5987(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A");
    }

    private static String csvTime(Long epochMillis) {
        if (epochMillis == null) {
            return "";
        }
        return Instant.ofEpochMilli(epochMillis)
                .atZone(CollectorDashboardUtils.SHANGHAI_ZONE)
                .toLocalDateTime()
                .format(CSV_TIME_FORMATTER);
    }

    private static String csvNumber(Double value) {
        return value == null ? "" : value.toString();
    }

    private static String csvLong(Long value) {
        return value == null ? "" : value.toString();
    }

    private static String csvInteger(Integer value) {
        return value == null ? "" : value.toString();
    }

    private record QueryPlan(CollectorChannelDO channel, Granularity granularity,
                             LocalDateTime from, LocalDateTime to) {
    }

}
