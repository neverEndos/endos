package cn.iocoder.yudao.module.collector.service.history;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.collector.controller.admin.history.vo.CollectorHistoryExportReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.history.vo.CollectorHistoryPageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.history.vo.CollectorHistoryRowVO;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Locale;

/**
 * 通道历史数据只读服务：分页查询与 CSV 流式导出。
 */
public interface CollectorHistoryService {

    /**
     * 历史数据粒度。
     *
     * <p><b>SQL 注入红线</b>：聚合表名只允许取自本枚举常量（{@link #getTable()}），
     * 请求参数只用于匹配 {@link #getCode()}，绝不参与 SQL 拼接。</p>
     */
    enum Granularity {

        RAW("raw", null),
        SECOND("second", "collector_aggregate_second"),
        MINUTE("minute", "collector_aggregate_minute"),
        HOUR("hour", "collector_aggregate_hour"),
        DAY("day", "collector_aggregate_day");

        private final String code;
        private final String table;

        Granularity(String code, String table) {
            this.code = code;
            this.table = table;
        }

        public String getCode() {
            return code;
        }

        /** 聚合表名常量；{@link #RAW} 直接查分区表 {@code collector_sample_raw}，无表名。 */
        public String getTable() {
            return table;
        }

        public boolean isRaw() {
            return this == RAW;
        }

        public boolean isDay() {
            return this == DAY;
        }

        /** 不区分大小写与首尾空白，非法值抛 {@link IllegalArgumentException} 由 Service 转业务异常。 */
        public static Granularity fromCode(String code) {
            if (code == null) {
                throw new IllegalArgumentException("granularity 不能为空");
            }
            String normalized = code.trim().toLowerCase(Locale.ROOT);
            for (Granularity granularity : values()) {
                if (granularity.code.equals(normalized)) {
                    return granularity;
                }
            }
            throw new IllegalArgumentException(code);
        }

    }

    /**
     * 分页查询通道历史数据，结果固定按时间升序。
     *
     * @param reqVO 查询条件（通道、时间范围、粒度、页码）
     * @return 分页结果；无数据时 total=0 且列表为空，不伪造数据行
     */
    PageResult<CollectorHistoryRowVO> getHistoryPage(CollectorHistoryPageReqVO reqVO);

    /**
     * 将通道历史数据以 CSV 形式流式写入响应。
     *
     * <p>时间范围、粒度、通道存在性与导出行数上限均在写出响应体之前校验；
     * 一旦开始写出后发生异常，只记录日志并向上抛出以中断连接，不会再返回 JSON。</p>
     *
     * @param reqVO    导出条件（无分页参数）
     * @param response HTTP 响应，由本方法直接写入 CSV 字节流
     * @throws IOException 写出失败
     */
    void exportHistoryCsv(CollectorHistoryExportReqVO reqVO, HttpServletResponse response) throws IOException;

}
