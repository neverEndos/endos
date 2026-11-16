package cn.iocoder.yudao.module.collector.controller.admin.sensor;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.collector.controller.admin.sensor.vo.CollectorGenerateChannelPreviewRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.sensor.vo.CollectorGenerateChannelsRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.sensor.vo.CollectorSensorPageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.sensor.vo.CollectorSensorRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.sensor.vo.CollectorSensorSaveReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.sensor.vo.CollectorSensorSimpleRespVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.sensor.CollectorSensorDO;
import cn.iocoder.yudao.module.collector.service.sensor.CollectorSensorService;
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

@Tag(name = "管理后台 - 采集传感器")
@RestController
@RequestMapping("/collector/sensor")
public class CollectorSensorController {

    @Resource
    private CollectorSensorService sensorService;

    @GetMapping("/page")
    @Operation(summary = "获得采集传感器分页")
    @PreAuthorize("@ss.hasPermission('collector:sensor:query')")
    public CommonResult<PageResult<CollectorSensorRespVO>> getSensorPage(@Valid CollectorSensorPageReqVO pageReqVO) {
        PageResult<CollectorSensorDO> pageResult = sensorService.getSensorPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CollectorSensorRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得采集传感器详情")
    @Parameter(name = "id", description = "传感器编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('collector:sensor:query')")
    public CommonResult<CollectorSensorRespVO> getSensor(@RequestParam("id") Long id) {
        CollectorSensorDO sensor = sensorService.getSensor(id);
        return success(BeanUtils.toBean(sensor, CollectorSensorRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得采集传感器精简列表")
    @Parameter(name = "deviceId", description = "设备编号", example = "1")
    @Parameter(name = "enabled", description = "是否启用：0 否、1 是", example = "1")
    @PreAuthorize("@ss.hasPermission('collector:sensor:query')")
    public CommonResult<List<CollectorSensorSimpleRespVO>> getSensorSimpleList(
            @RequestParam(value = "deviceId", required = false) Long deviceId,
            @RequestParam(value = "enabled", required = false) Integer enabled) {
        List<CollectorSensorDO> list = sensorService.getSensorList(deviceId, enabled);
        return success(BeanUtils.toBean(list, CollectorSensorSimpleRespVO.class));
    }

    @PostMapping("/create")
    @Operation(summary = "创建采集传感器")
    @PreAuthorize("@ss.hasPermission('collector:sensor:create')")
    public CommonResult<Long> createSensor(@Valid @RequestBody CollectorSensorSaveReqVO createReqVO) {
        return success(sensorService.createSensor(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改采集传感器")
    @PreAuthorize("@ss.hasPermission('collector:sensor:update')")
    public CommonResult<Boolean> updateSensor(@Valid @RequestBody CollectorSensorSaveReqVO updateReqVO) {
        sensorService.updateSensor(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除采集传感器")
    @Parameter(name = "id", description = "传感器编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('collector:sensor:delete')")
    public CommonResult<Boolean> deleteSensor(@RequestParam("id") Long id) {
        sensorService.deleteSensor(id);
        return success(true);
    }

    @GetMapping("/generate-channel-preview")
    @Operation(summary = "预览按传感器类型模板生成的通道")
    @Parameter(name = "sensorTypeId", description = "传感器类型编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('collector:sensor:query')")
    public CommonResult<List<CollectorGenerateChannelPreviewRespVO>> generateChannelPreview(
            @RequestParam("sensorTypeId") Long sensorTypeId) {
        return success(sensorService.generateChannelsPreview(sensorTypeId));
    }

    @PostMapping("/generate-channels")
    @Operation(summary = "为传感器生成通道，已存在的通道跳过")
    @Parameter(name = "sensorId", description = "传感器编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('collector:sensor:create')")
    public CommonResult<CollectorGenerateChannelsRespVO> generateChannels(@RequestParam("sensorId") Long sensorId) {
        return success(sensorService.generateChannels(sensorId));
    }

}
