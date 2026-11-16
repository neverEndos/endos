package cn.iocoder.yudao.module.collector.controller.admin.sensortype;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.collector.controller.admin.sensortype.vo.CollectorSensorTypePageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.sensortype.vo.CollectorSensorTypeRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.sensortype.vo.CollectorSensorTypeSaveReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.sensortype.vo.CollectorSensorTypeSimpleRespVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.sensortype.CollectorSensorTypeDO;
import cn.iocoder.yudao.module.collector.service.sensortype.CollectorSensorTypeService;
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

@Tag(name = "管理后台 - 传感器类型")
@RestController
@RequestMapping("/collector/sensor-type")
public class CollectorSensorTypeController {

    @Resource
    private CollectorSensorTypeService sensorTypeService;

    @GetMapping("/page")
    @Operation(summary = "获得传感器类型分页")
    @PreAuthorize("@ss.hasPermission('collector:sensor-type:query')")
    public CommonResult<PageResult<CollectorSensorTypeRespVO>> getSensorTypePage(
            @Valid CollectorSensorTypePageReqVO pageReqVO) {
        PageResult<CollectorSensorTypeDO> pageResult = sensorTypeService.getSensorTypePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CollectorSensorTypeRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得传感器类型详情")
    @Parameter(name = "id", description = "类型编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('collector:sensor-type:query')")
    public CommonResult<CollectorSensorTypeRespVO> getSensorType(@RequestParam("id") Long id) {
        CollectorSensorTypeDO sensorType = sensorTypeService.getSensorType(id);
        return success(BeanUtils.toBean(sensorType, CollectorSensorTypeRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得传感器类型精简列表")
    @Parameter(name = "enabled", description = "是否启用：0 否、1 是", example = "1")
    @PreAuthorize("@ss.hasPermission('collector:sensor-type:query')")
    public CommonResult<List<CollectorSensorTypeSimpleRespVO>> getSensorTypeSimpleList(
            @RequestParam(value = "enabled", required = false) Integer enabled) {
        List<CollectorSensorTypeDO> list = sensorTypeService.getSensorTypeList(enabled);
        return success(BeanUtils.toBean(list, CollectorSensorTypeSimpleRespVO.class));
    }

    @PostMapping("/create")
    @Operation(summary = "创建传感器类型")
    @PreAuthorize("@ss.hasPermission('collector:sensor-type:create')")
    public CommonResult<Long> createSensorType(@Valid @RequestBody CollectorSensorTypeSaveReqVO createReqVO) {
        return success(sensorTypeService.createSensorType(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改传感器类型")
    @PreAuthorize("@ss.hasPermission('collector:sensor-type:update')")
    public CommonResult<Boolean> updateSensorType(@Valid @RequestBody CollectorSensorTypeSaveReqVO updateReqVO) {
        sensorTypeService.updateSensorType(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除传感器类型")
    @Parameter(name = "id", description = "类型编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('collector:sensor-type:delete')")
    public CommonResult<Boolean> deleteSensorType(@RequestParam("id") Long id) {
        sensorTypeService.deleteSensorType(id);
        return success(true);
    }

}
