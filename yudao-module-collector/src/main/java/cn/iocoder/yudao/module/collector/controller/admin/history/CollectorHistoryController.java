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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

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
        // 校验阶段（权限、范围、粒度、行数）失败时响应尚未写出，由全局异常处理器返回 CommonResult JSON；
        // 一旦进入流式写出阶段，失败只由 Service 记录日志后抛出，由容器中断连接，绝不在此再写 JSON。
        // 日志统一在 Service 内记录（含已写行数），此处不重复打点。
        historyService.exportHistoryCsv(reqVO, response);
    }

}
