package cn.iocoder.yudao.module.collector.controller.admin.channel;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelDeviceRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelPageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelThresholdBatchUpdateReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelThresholdPreviewReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelThresholdPreviewRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelThresholdUpdateReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelUpdateReqVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;
import cn.iocoder.yudao.module.collector.service.channel.CollectorChannelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 采集通道")
@RestController
@RequestMapping("/collector/channel")
public class CollectorChannelController {

    @Resource
    private CollectorChannelService channelService;

    @GetMapping("/page")
    @Operation(summary = "获得采集通道分页")
    @PreAuthorize("@ss.hasPermission('collector:channel:query')")
    public CommonResult<PageResult<CollectorChannelRespVO>> getChannelPage(@Valid CollectorChannelPageReqVO pageReqVO) {
        PageResult<CollectorChannelDO> pageResult = channelService.getChannelPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CollectorChannelRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得采集通道详情")
    @Parameter(name = "id", description = "通道编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('collector:channel:query')")
    public CommonResult<CollectorChannelRespVO> getChannel(@RequestParam("id") Long id) {
        CollectorChannelDO channel = channelService.getChannel(id);
        return success(BeanUtils.toBean(channel, CollectorChannelRespVO.class));
    }

    @GetMapping("/list-by-device")
    @Operation(summary = "按设备获得通道阈值矩阵")
    @Parameter(name = "deviceId", description = "设备编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('collector:channel:query')")
    public CommonResult<CollectorChannelDeviceRespVO> getChannelListByDevice(@RequestParam("deviceId") Long deviceId) {
        return success(channelService.getChannelListByDevice(deviceId));
    }

    @PutMapping("/update")
    @Operation(summary = "修改通道通用信息")
    @PreAuthorize("@ss.hasPermission('collector:channel:update')")
    public CommonResult<Boolean> updateChannel(@Valid @RequestBody CollectorChannelUpdateReqVO updateReqVO) {
        channelService.updateChannel(updateReqVO);
        return success(true);
    }

    @PutMapping("/update-threshold")
    @Operation(summary = "修改通道阈值与报警配置")
    @PreAuthorize("@ss.hasPermission('collector:channel:update')")
    public CommonResult<Boolean> updateChannelThreshold(
            @Valid @RequestBody CollectorChannelThresholdUpdateReqVO updateReqVO) {
        channelService.updateChannelThreshold(updateReqVO);
        return success(true);
    }

    @PutMapping("/update-threshold-batch")
    @Operation(summary = "批量修改设备下通道的阈值与报警配置")
    @PreAuthorize("@ss.hasPermission('collector:channel:update')")
    public CommonResult<Integer> updateChannelThresholdBatch(
            @Valid @RequestBody CollectorChannelThresholdBatchUpdateReqVO updateReqVO) {
        return success(channelService.updateChannelThresholdBatch(updateReqVO));
    }

    @GetMapping("/preview-threshold")
    @Operation(summary = "按量程与比例预览四档阈值；通道查询或参数模板查询权限均可调用")
    @PreAuthorize("@ss.hasPermission('collector:channel:query') or @ss.hasPermission('collector:sensor-template:query')")
    public CommonResult<CollectorChannelThresholdPreviewRespVO> previewThreshold(
            CollectorChannelThresholdPreviewReqVO previewReqVO) {
        return success(channelService.previewThreshold(previewReqVO));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除采集通道")
    @Parameter(name = "id", description = "通道编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('collector:channel:delete')")
    public CommonResult<Boolean> deleteChannel(@RequestParam("id") Long id) {
        channelService.deleteChannel(id);
        return success(true);
    }

}
