package cn.iocoder.yudao.module.collector.service.history;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.collector.controller.admin.history.vo.CollectorHistoryExportReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.history.vo.CollectorHistoryPageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.history.vo.CollectorHistoryRowVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.history.CollectorHistoryAggregateRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.history.CollectorHistoryRawRow;
import cn.iocoder.yudao.module.collector.dal.mysql.channel.CollectorChannelMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.history.CollectorHistoryMapper;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.HISTORY_CHANNEL_NOT_EXISTS;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.HISTORY_EXPORT_ROW_LIMIT_EXCEEDED;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.HISTORY_GRANULARITY_INVALID;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.HISTORY_QUERY_INVALID;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.HISTORY_RANGE_EXCEEDED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 历史分页与导出的边界校验。Mapper 由 Mockito 替代；真实 PostgreSQL SQL 由
 * {@code CollectorHistoryMapperPgTest} 验证。
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class CollectorHistoryServiceImplTest {

    /** 2026-01-01 00:00:00 Asia/Shanghai。 */
    private static final long BASE_MILLIS = 1767196800000L;
    private static final long ONE_DAY_MILLIS = Duration.ofDays(1).toMillis();
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);

    private static final long CHANNEL_ID = 1L;

    @Mock
    private CollectorHistoryMapper historyMapper;
    @Mock
    private CollectorChannelMapper channelMapper;

    private CollectorHistoryServiceImpl historyService;
    private CapturingResponse response;

    @Before
    public void setUp() {
        historyService = new CollectorHistoryServiceImpl(historyMapper, channelMapper);
        CollectorChannelDO channel = new CollectorChannelDO();
        channel.setId(CHANNEL_ID);
        channel.setChannelName("油温");
        channel.setUnit("℃");
        when(channelMapper.selectById(CHANNEL_ID)).thenReturn(channel);
        response = new CapturingResponse();
    }

    // ==================== 参数与范围 ====================

    @Test
    public void page_rejectsRangeLongerThan365Days() {
        long from = BASE_MILLIS;
        long to = from + Duration.ofDays(365).toMillis() + 1;

        ServiceException ex = assertThrows(ServiceException.class,
                () -> historyService.getHistoryPage(pageReq(from, to, "minute")));

        assertEquals(HISTORY_RANGE_EXCEEDED.getCode(), ex.getCode());
        verify(historyMapper, never()).countAggregate(anyString(), anyLong(), any(), any());
    }

    @Test
    public void page_acceptsRangeExactly365Days() {
        long from = BASE_MILLIS;
        long to = from + Duration.ofDays(365).toMillis();
        when(historyMapper.countAggregate(eq("collector_aggregate_minute"), eq(CHANNEL_ID), any(), any()))
                .thenReturn(0L);

        PageResult<CollectorHistoryRowVO> result =
                historyService.getHistoryPage(pageReq(from, to, "minute"));

        assertEquals(0L, result.getTotal().longValue());
        assertTrue(result.getList().isEmpty());
    }

    @Test
    public void page_rejectsInvalidGranularity() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> historyService.getHistoryPage(pageReq(BASE_MILLIS, BASE_MILLIS + ONE_DAY_MILLIS, "week")));

        assertEquals(HISTORY_GRANULARITY_INVALID.getCode(), ex.getCode());
        verify(historyMapper, never()).countRaw(anyLong(), any(), any());
    }

    @Test
    public void page_rejectsReversedRange() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> historyService.getHistoryPage(
                        pageReq(BASE_MILLIS + ONE_DAY_MILLIS, BASE_MILLIS, "minute")));

        assertEquals(HISTORY_QUERY_INVALID.getCode(), ex.getCode());
    }

    @Test
    public void page_rejectsUnknownChannel() {
        long from = BASE_MILLIS;
        long to = from + ONE_DAY_MILLIS;
        when(channelMapper.selectById(999L)).thenReturn(null);

        CollectorHistoryPageReqVO reqVO = pageReq(from, to, "minute");
        reqVO.setChannelId(999L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> historyService.getHistoryPage(reqVO));

        assertEquals(HISTORY_CHANNEL_NOT_EXISTS.getCode(), ex.getCode());
    }

    // ==================== 空数据与分页上限 ====================

    @Test
    public void page_returnsEmptyPageWithoutFakeRows() {
        long from = BASE_MILLIS;
        long to = from + ONE_DAY_MILLIS;
        when(historyMapper.countRaw(eq(CHANNEL_ID), any(), any())).thenReturn(0L);

        PageResult<CollectorHistoryRowVO> result =
                historyService.getHistoryPage(pageReq(from, to, "raw"));

        assertEquals(0L, result.getTotal().longValue());
        assertTrue(result.getList().isEmpty());
        verify(historyMapper, never()).selectRawPage(anyLong(), any(), any(), anyLong(), anyInt());
    }

    @Test
    public void page_capsPageSizeAt100() {
        long from = BASE_MILLIS;
        long to = from + ONE_DAY_MILLIS;
        when(historyMapper.countRaw(eq(CHANNEL_ID), any(), any())).thenReturn(500L);
        when(historyMapper.selectRawPage(eq(CHANNEL_ID), any(), any(), eq(0L), eq(100)))
                .thenReturn(List.of());

        CollectorHistoryPageReqVO reqVO = pageReq(from, to, "raw");
        reqVO.setPageSize(1000);
        historyService.getHistoryPage(reqVO);

        verify(historyMapper).selectRawPage(eq(CHANNEL_ID), any(), any(), eq(0L), eq(100));
    }

    @Test
    public void page_defaultsTo20WhenPageSizeMissing() {
        long from = BASE_MILLIS;
        long to = from + ONE_DAY_MILLIS;
        when(historyMapper.countRaw(eq(CHANNEL_ID), any(), any())).thenReturn(500L);
        when(historyMapper.selectRawPage(eq(CHANNEL_ID), any(), any(), eq(0L), eq(20)))
                .thenReturn(List.of());

        CollectorHistoryPageReqVO reqVO = pageReq(from, to, "raw");
        reqVO.setPageSize(null);
        historyService.getHistoryPage(reqVO);

        verify(historyMapper).selectRawPage(eq(CHANNEL_ID), any(), any(), eq(0L), eq(20));
    }

    @Test
    public void page_mapsRawRowToEpochMillisWithChannelMeta() {
        long from = BASE_MILLIS;
        long to = from + ONE_DAY_MILLIS;
        CollectorHistoryRawRow row = new CollectorHistoryRawRow();
        row.setChannelId(CHANNEL_ID);
        row.setCollectTime(BASE_TIME.plusMinutes(1));
        row.setValue(23.5D);
        row.setRawValue(235L);
        row.setQuality(0);
        when(historyMapper.countRaw(eq(CHANNEL_ID), any(), any())).thenReturn(1L);
        when(historyMapper.selectRawPage(eq(CHANNEL_ID), any(), any(), eq(0L), eq(20)))
                .thenReturn(List.of(row));

        PageResult<CollectorHistoryRowVO> result =
                historyService.getHistoryPage(pageReq(from, to, "raw"));

        assertEquals(1L, result.getTotal().longValue());
        CollectorHistoryRowVO vo = result.getList().get(0);
        assertEquals(Long.valueOf(BASE_MILLIS + 60_000L), vo.getBucketTime());
        assertEquals("℃", vo.getUnit());
        assertEquals("油温", vo.getChannelName());
        assertEquals(Double.valueOf(23.5D), vo.getValue());
        assertEquals(Long.valueOf(235L), vo.getRawValue());
        assertEquals(Integer.valueOf(0), vo.getQuality());
        // 聚合列在 raw 行不应被填充
        assertNull(vo.getSampleCount());
        assertNull(vo.getValueAvg());
    }

    @Test
    public void page_mapsDayBucketDateToMidnightEpochMillis() {
        long from = BASE_MILLIS;
        long to = from + ONE_DAY_MILLIS;
        CollectorHistoryAggregateRow row = new CollectorHistoryAggregateRow();
        row.setChannelId(CHANNEL_ID);
        row.setBucketTime(BASE_TIME); // bucket_date::timestamp → 当天 00:00
        row.setSampleCount(144);
        row.setGoodCount(140);
        row.setBadCount(2);
        row.setValueAvg(20D);
        when(historyMapper.countDay(eq(CHANNEL_ID), any(), any())).thenReturn(1L);
        when(historyMapper.selectDayPage(eq(CHANNEL_ID), any(), any(), eq(0L), eq(20)))
                .thenReturn(List.of(row));

        PageResult<CollectorHistoryRowVO> result =
                historyService.getHistoryPage(pageReq(from, to, "day"));

        assertEquals(Long.valueOf(BASE_MILLIS), result.getList().get(0).getBucketTime());
        assertEquals(Integer.valueOf(144), result.getList().get(0).getSampleCount());
        // raw 列在聚合行不应被填充
        assertNull(result.getList().get(0).getValue());
    }

    // ==================== 导出 ====================

    @Test
    public void export_rejectsTotalExceedingRowLimitBeforeWritingAnything() {
        long from = BASE_MILLIS;
        long to = from + ONE_DAY_MILLIS;
        when(historyMapper.countRaw(eq(CHANNEL_ID), any(), any()))
                .thenReturn(CollectorHistoryServiceImpl.MAX_EXPORT_ROWS + 1);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> historyService.exportHistoryCsv(exportReq(from, to, "raw"), response));

        assertEquals(HISTORY_EXPORT_ROW_LIMIT_EXCEEDED.getCode(), ex.getCode());
        // 校验前置：未写出任何字节，也未进入分批查询
        assertEquals(0, response.bytes().length);
        assertNull(response.getHeader("Content-Disposition"));
        verify(historyMapper, never()).selectRawBatch(anyLong(), any(), any(), any(), anyInt());
    }

    @Test
    public void export_writesBomHeaderAndAllRowsInFixedSizeBatches() throws IOException {
        long from = BASE_MILLIS;
        long to = from + ONE_DAY_MILLIS;
        int totalRows = 12_001;
        when(historyMapper.countAggregate(eq("collector_aggregate_minute"), eq(CHANNEL_ID), any(), any()))
                .thenReturn((long) totalRows);
        when(historyMapper.selectAggregateBatch(eq("collector_aggregate_minute"), eq(CHANNEL_ID),
                any(), any(), any(), eq(CollectorHistoryServiceImpl.EXPORT_BATCH_SIZE)))
                .thenAnswer(invocation -> {
                    LocalDateTime cursor = invocation.getArgument(4);
                    int limit = invocation.getArgument(5);
                    int start = cursor == null ? 0 : (int) Duration.between(BASE_TIME, cursor).toMinutes() + 1;
                    return aggregateRows(start, Math.min(limit, totalRows - start));
                });

        historyService.exportHistoryCsv(exportReq(from, to, "minute"), response);

        List<String> lines = csvLines(response.bytes());
        // BOM+表头 + 全部数据行 + 末尾空元素
        assertEquals(totalRows + 2, lines.size());
        assertTrue(lines.get(0).startsWith("\uFEFF"));
        assertEquals("\uFEFF时间,采样数,正常数,坏值数,平均值,最小值,最大值,首值,末值", lines.get(0));
        assertEquals("2026-01-01 00:00:00", lines.get(1).substring(0, 19));

        // 分批固定：三批（5000 / 5000 / 2001），单批上限恒为 5000，SQL 条数随批次而非行数增长
        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(historyMapper, times(3)).selectAggregateBatch(anyString(), anyLong(), any(), any(),
                any(), limitCaptor.capture());
        assertEquals(List.of(5000, 5000, 5000), limitCaptor.getAllValues());
        assertEquals("text/csv; charset=UTF-8", response.getContentType());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertTrue(response.getHeader("Content-Disposition")
                .startsWith("attachment; filename*=UTF-8''"));
    }

    @Test
    public void export_usesRawBatchForRawGranularity() throws IOException {
        long from = BASE_MILLIS;
        long to = from + ONE_DAY_MILLIS;
        when(historyMapper.countRaw(eq(CHANNEL_ID), any(), any())).thenReturn(2L);
        when(historyMapper.selectRawBatch(eq(CHANNEL_ID), any(), any(), isNull(), eq(5000)))
                .thenReturn(List.of(rawRow(0), rawRow(1)));

        historyService.exportHistoryCsv(exportReq(from, to, "raw"), response);

        List<String> lines = csvLines(response.bytes());
        assertEquals(4, lines.size());
        assertEquals("\uFEFF时间,值,原始值,质量码", lines.get(0));
        assertTrue(lines.get(1).endsWith(",23.5,235,0"));
    }

    // ==================== 辅助 ====================

    private CollectorHistoryPageReqVO pageReq(long from, long to, String granularity) {
        CollectorHistoryPageReqVO reqVO = new CollectorHistoryPageReqVO();
        reqVO.setChannelId(CHANNEL_ID);
        reqVO.setFrom(from);
        reqVO.setTo(to);
        reqVO.setGranularity(granularity);
        return reqVO;
    }

    private CollectorHistoryExportReqVO exportReq(long from, long to, String granularity) {
        CollectorHistoryExportReqVO reqVO = new CollectorHistoryExportReqVO();
        reqVO.setChannelId(CHANNEL_ID);
        reqVO.setFrom(from);
        reqVO.setTo(to);
        reqVO.setGranularity(granularity);
        return reqVO;
    }

    /** 从第 start 分钟开始生成 count 行分钟聚合数据，值随序号递增。 */
    private static List<CollectorHistoryAggregateRow> aggregateRows(int start, int count) {
        List<CollectorHistoryAggregateRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            CollectorHistoryAggregateRow row = new CollectorHistoryAggregateRow();
            row.setChannelId(CHANNEL_ID);
            row.setBucketTime(BASE_TIME.plusMinutes(start + i));
            row.setSampleCount(1);
            row.setGoodCount(1);
            row.setBadCount(0);
            row.setValueAvg(20D + start + i);
            rows.add(row);
        }
        return rows;
    }

    private static CollectorHistoryRawRow rawRow(int offsetSeconds) {
        CollectorHistoryRawRow row = new CollectorHistoryRawRow();
        row.setChannelId(CHANNEL_ID);
        row.setCollectTime(BASE_TIME.plusSeconds(offsetSeconds));
        row.setValue(23.5D);
        row.setRawValue(235L);
        row.setQuality(0);
        return row;
    }

    /** 按 CRLF 切分 CSV；末尾的换行会留下一个空元素，便于校验总行数。 */
    private static List<String> csvLines(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        return List.of(content.split("\r\n", -1));
    }

    /** 捕获写出的字节流，避免依赖容器。 */
    private static final class CapturingResponse implements HttpServletResponse {

        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final java.util.Map<String, String> headers = new java.util.HashMap<>();
        private String contentType;

        byte[] bytes() {
            return buffer.toByteArray();
        }

        private final ServletOutputStream outputStream = new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                // 测试环境无需异步写
            }

            @Override
            public void write(int b) {
                buffer.write(b);
            }
        };

        @Override
        public ServletOutputStream getOutputStream() {
            return outputStream;
        }

        @Override
        public void setContentType(String type) {
            this.contentType = type;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public void setCharacterEncoding(String charset) {
            // 忽略：写入固定 UTF-8
        }

        @Override
        public String getCharacterEncoding() {
            return StandardCharsets.UTF_8.name();
        }

        @Override
        public void setHeader(String name, String value) {
            headers.put(name, value);
        }

        @Override
        public String getHeader(String name) {
            return headers.get(name);
        }

        @Override
        public boolean isCommitted() {
            return buffer.size() > 0;
        }

        // ===== 其余接口方法在测试中不使用 =====

        @Override
        public void addCookie(jakarta.servlet.http.Cookie cookie) {
        }

        @Override
        public boolean containsHeader(String name) {
            return headers.containsKey(name);
        }

        @Override
        public String encodeURL(String url) {
            return url;
        }

        @Override
        public String encodeRedirectURL(String url) {
            return url;
        }

        @Override
        public void sendError(int sc, String msg) {
        }

        @Override
        public void sendError(int sc) {
        }

        @Override
        public void sendRedirect(String location) {
        }

        @Override
        public void sendRedirect(String location, int sc, boolean clearBuffer) {
        }

        @Override
        public void setDateHeader(String name, long date) {
        }

        @Override
        public void addDateHeader(String name, long date) {
        }

        @Override
        public void addHeader(String name, String value) {
            headers.put(name, value);
        }

        @Override
        public void setIntHeader(String name, int value) {
            headers.put(name, String.valueOf(value));
        }

        @Override
        public void addIntHeader(String name, int value) {
            headers.put(name, String.valueOf(value));
        }

        @Override
        public void setStatus(int sc) {
        }

        @Override
        public int getStatus() {
            return 200;
        }

        @Override
        public java.util.Collection<String> getHeaderNames() {
            return headers.keySet();
        }

        @Override
        public java.util.Collection<String> getHeaders(String name) {
            String value = headers.get(name);
            return value == null ? List.of() : List.of(value);
        }

        @Override
        public java.io.PrintWriter getWriter() {
            return new java.io.PrintWriter(new java.io.OutputStreamWriter(buffer, StandardCharsets.UTF_8));
        }

        @Override
        public void setContentLength(int len) {
        }

        @Override
        public void setContentLengthLong(long len) {
        }

        @Override
        public void setBufferSize(int size) {
        }

        @Override
        public int getBufferSize() {
            return 0;
        }

        @Override
        public void flushBuffer() {
        }

        @Override
        public void resetBuffer() {
        }

        @Override
        public void reset() {
        }

        @Override
        public void setLocale(java.util.Locale loc) {
        }

        @Override
        public java.util.Locale getLocale() {
            return java.util.Locale.SIMPLIFIED_CHINESE;
        }

    }

}
