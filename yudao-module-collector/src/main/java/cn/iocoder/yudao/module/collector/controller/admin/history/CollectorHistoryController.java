package cn.iocoder.yudao.module.collector.controller.admin.history;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.collector.controller.admin.history.vo.CollectorHistoryExportReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.history.vo.CollectorHistoryPageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.history.vo.CollectorHistoryRowVO;
import cn.iocoder.yudao.module.collector.service.history.CollectorHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Slf4j
@Tag(name = "管理后台 - 采集通道历史数据")
@RestController
@RequestMapping("/collector/history")
public class CollectorHistoryController {

    @Resource
    private CollectorHistoryService historyService;

    @GetMapping("/page")
    @Operation(summary = "获得通道历史数据分页",
            description = "按粒度 raw/second/minute/hour/day 查询单通道历史数据，"
                    + "结果固定按时间升序（ASC）；时间跨度不得超过 365 天；无数据返回空页（total=0）")
    @PreAuthorize("@ss.hasPermission('collector:trend:query')")
    public CommonResult<PageResult<CollectorHistoryRowVO>> getHistoryPage(
            @Valid CollectorHistoryPageReqVO reqVO) {
        return success(historyService.getHistoryPage(reqVO));
    }

    @GetMapping("/export")
    @Operation(summary = "导出通道历史数据 CSV",
            description = "直接写 HttpServletResponse（text/csv;charset=UTF-8，带 UTF-8 BOM），"
                    + "按时间升序流式分批写出，单次导出上限 50 万行；"
                    + "时间范围、粒度、通道与行数校验均发生在写出响应体之前")
    @PreAuthorize("@ss.hasPermission('collector:history:export')")
    public void exportHistoryCsv(@Valid CollectorHistoryExportReqVO reqVO,
                                 HttpServletResponse response) throws IOException {
        try {
            historyService.exportHistoryCsv(reqVO, response);
        } catch (IOException | RuntimeException ex) {
            // 响应已开始写出时无法再返回 CommonResult JSON，记录日志并向上抛出由容器中断连接。
            if (response.isCommitted()) {
                log.error("[exportHistoryCsv][响应已提交，中止连接，避免输出半截 JSON] channelId={}, "
                        + "granularity={}, from={}, to={}",
                        reqVO.getChannelId(), reqVO.getGranularity(), reqVO.getFrom(), reqVO.getTo(), ex);
            }
            throw ex;
        }
    }

}
