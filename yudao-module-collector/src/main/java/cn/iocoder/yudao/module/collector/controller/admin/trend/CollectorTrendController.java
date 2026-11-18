package cn.iocoder.yudao.module.collector.controller.admin.trend;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendChannelPageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendChannelRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendDeviceRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendSeriesReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.trend.vo.CollectorTrendSeriesRespVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.device.CollectorDeviceDO;
import cn.iocoder.yudao.module.collector.service.trend.CollectorTrendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 采集趋势")
@RestController
@RequestMapping("/collector/trend")
public class CollectorTrendController {

    @Resource
    private CollectorTrendService trendService;

    @GetMapping("/series")
    @Operation(summary = "获得采集历史趋势序列",
            description = "按数据画像与时间范围自动选源（raw/秒/分钟/小时/天），超过点数上限时在层内放大桶宽")
    @PreAuthorize("@ss.hasPermission('collector:trend:query')")
    public CommonResult<CollectorTrendSeriesRespVO> getSeries(
            @Valid CollectorTrendSeriesReqVO reqVO) {
        return success(trendService.getSeries(reqVO));
    }

    @GetMapping("/devices")
    @Operation(summary = "获得趋势选择器设备列表",
            description = "只返回启用设备及趋势选择所需字段；供仅有 collector:trend:query 权限的角色加载设备下拉")
    @PreAuthorize("@ss.hasPermission('collector:trend:query')")
    public CommonResult<List<CollectorTrendDeviceRespVO>> getTrendDevices() {
        List<CollectorDeviceDO> list = trendService.getTrendDeviceList();
        return success(BeanUtils.toBean(list, CollectorTrendDeviceRespVO.class));
    }

    @GetMapping("/channels")
    @Operation(summary = "获得趋势选择器通道分页",
            description = "只返回指定设备下的启用通道，支持名称关键字模糊匹配；供仅有 collector:trend:query 权限的角色加载通道下拉")
    @PreAuthorize("@ss.hasPermission('collector:trend:query')")
    public CommonResult<PageResult<CollectorTrendChannelRespVO>> getTrendChannels(
            @Valid CollectorTrendChannelPageReqVO reqVO) {
        PageResult<CollectorChannelDO> pageResult = trendService.getTrendChannelPage(reqVO);
        return success(BeanUtils.toBean(pageResult, CollectorTrendChannelRespVO.class));
    }

}
