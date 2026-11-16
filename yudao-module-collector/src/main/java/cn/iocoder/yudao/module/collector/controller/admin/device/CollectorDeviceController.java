package cn.iocoder.yudao.module.collector.controller.admin.device;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.collector.controller.admin.device.vo.CollectorDevicePageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.device.vo.CollectorDeviceRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.device.vo.CollectorDeviceSaveReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.device.vo.CollectorDeviceSimpleRespVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.device.CollectorDeviceDO;
import cn.iocoder.yudao.module.collector.service.device.CollectorDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 采集设备")
@RestController
@RequestMapping("/collector/device")
public class CollectorDeviceController {

    @Resource
    private CollectorDeviceService deviceService;

    @GetMapping("/page")
    @Operation(summary = "获得采集设备分页")
    @PreAuthorize("@ss.hasPermission('collector:device:query')")
    public CommonResult<PageResult<CollectorDeviceRespVO>> getDevicePage(@Valid CollectorDevicePageReqVO pageReqVO) {
        PageResult<CollectorDeviceDO> pageResult = deviceService.getDevicePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CollectorDeviceRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得采集设备详情")
    @Parameter(name = "id", description = "设备编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('collector:device:query')")
    public CommonResult<CollectorDeviceRespVO> getDevice(@RequestParam("id") Long id) {
        CollectorDeviceDO device = deviceService.getDevice(id);
        return success(BeanUtils.toBean(device, CollectorDeviceRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得采集设备精简列表")
    @Parameter(name = "enabled", description = "是否启用：0 否、1 是", example = "1")
    @PreAuthorize("@ss.hasPermission('collector:device:query')")
    public CommonResult<List<CollectorDeviceSimpleRespVO>> getDeviceSimpleList(
            @RequestParam(value = "enabled", required = false) Integer enabled) {
        List<CollectorDeviceDO> list = deviceService.getDeviceList(enabled);
        return success(BeanUtils.toBean(list, CollectorDeviceSimpleRespVO.class));
    }

    @PostMapping("/create")
    @Operation(summary = "创建采集设备")
    @PreAuthorize("@ss.hasPermission('collector:device:create')")
    public CommonResult<Long> createDevice(@Valid @RequestBody CollectorDeviceSaveReqVO createReqVO) {
        return success(deviceService.createDevice(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改采集设备")
    @PreAuthorize("@ss.hasPermission('collector:device:update')")
    public CommonResult<Boolean> updateDevice(@Valid @RequestBody CollectorDeviceSaveReqVO updateReqVO) {
        deviceService.updateDevice(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除采集设备")
    @Parameter(name = "id", description = "设备编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('collector:device:delete')")
    public CommonResult<Boolean> deleteDevice(@RequestParam("id") Long id) {
        deviceService.deleteDevice(id);
        return success(true);
    }

}
