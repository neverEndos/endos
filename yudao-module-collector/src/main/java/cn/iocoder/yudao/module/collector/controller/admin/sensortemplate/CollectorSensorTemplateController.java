package cn.iocoder.yudao.module.collector.controller.admin.sensortemplate;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.collector.controller.admin.sensortemplate.vo.CollectorSensorTemplatePageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.sensortemplate.vo.CollectorSensorTemplateRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.sensortemplate.vo.CollectorSensorTemplateSaveReqVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.sensortemplate.CollectorSensorTemplateDO;
import cn.iocoder.yudao.module.collector.service.sensortemplate.CollectorSensorTemplateService;
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

@Tag(name = "管理后台 - 传感器参数模板")
@RestController
@RequestMapping("/collector/sensor-template")
public class CollectorSensorTemplateController {

    @Resource
    private CollectorSensorTemplateService sensorTemplateService;

    @GetMapping("/page")
    @Operation(summary = "获得传感器参数模板分页")
    @PreAuthorize("@ss.hasPermission('collector:sensor-template:query')")
    public CommonResult<PageResult<CollectorSensorTemplateRespVO>> getTemplatePage(
            @Valid CollectorSensorTemplatePageReqVO pageReqVO) {
        PageResult<CollectorSensorTemplateDO> pageResult = sensorTemplateService.getTemplatePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, CollectorSensorTemplateRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得传感器参数模板详情")
    @Parameter(name = "id", description = "模板编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('collector:sensor-template:query')")
    public CommonResult<CollectorSensorTemplateRespVO> getTemplate(@RequestParam("id") Long id) {
        CollectorSensorTemplateDO template = sensorTemplateService.getTemplate(id);
        return success(BeanUtils.toBean(template, CollectorSensorTemplateRespVO.class));
    }

    @GetMapping("/list-by-type")
    @Operation(summary = "获得指定传感器类型下的参数模板列表")
    @Parameter(name = "sensorTypeId", description = "传感器类型编号", required = true, example = "1")
    @Parameter(name = "enabled", description = "是否启用：0 否、1 是", example = "1")
    @PreAuthorize("@ss.hasPermission('collector:sensor-template:query')")
    public CommonResult<List<CollectorSensorTemplateRespVO>> getTemplateListByType(
            @RequestParam("sensorTypeId") Long sensorTypeId,
            @RequestParam(value = "enabled", required = false) Integer enabled) {
        List<CollectorSensorTemplateDO> list = sensorTemplateService.getTemplateListByType(sensorTypeId, enabled);
        return success(BeanUtils.toBean(list, CollectorSensorTemplateRespVO.class));
    }

    @PostMapping("/create")
    @Operation(summary = "创建传感器参数模板")
    @PreAuthorize("@ss.hasPermission('collector:sensor-template:create')")
    public CommonResult<Long> createTemplate(@Valid @RequestBody CollectorSensorTemplateSaveReqVO createReqVO) {
        return success(sensorTemplateService.createTemplate(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改传感器参数模板")
    @PreAuthorize("@ss.hasPermission('collector:sensor-template:update')")
    public CommonResult<Boolean> updateTemplate(@Valid @RequestBody CollectorSensorTemplateSaveReqVO updateReqVO) {
        sensorTemplateService.updateTemplate(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除传感器参数模板")
    @Parameter(name = "id", description = "模板编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('collector:sensor-template:delete')")
    public CommonResult<Boolean> deleteTemplate(@RequestParam("id") Long id) {
        sensorTemplateService.deleteTemplate(id);
        return success(true);
    }

}
