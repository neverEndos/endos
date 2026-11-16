package cn.iocoder.yudao.module.collector.controller.admin.protocol;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.collector.controller.admin.protocol.vo.CollectorProtocolRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.protocol.vo.CollectorProtocolSimpleRespVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.protocol.CollectorProtocolDO;
import cn.iocoder.yudao.module.collector.service.protocol.CollectorProtocolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 采集协议")
@RestController
@RequestMapping("/collector/protocol")
public class CollectorProtocolController {

    @Resource
    private CollectorProtocolService protocolService;

    @GetMapping("/list")
    @Operation(summary = "获得采集协议列表")
    @PreAuthorize("@ss.hasPermission('collector:protocol:query')")
    public CommonResult<List<CollectorProtocolRespVO>> getProtocolList(
            @Parameter(description = "是否启用：0 否、1 是") @RequestParam(value = "enabled", required = false) Integer enabled) {
        List<CollectorProtocolDO> list = protocolService.getProtocolList(enabled);
        return success(BeanUtils.toBean(list, CollectorProtocolRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得采集协议精简列表")
    @PreAuthorize("@ss.hasPermission('collector:protocol:query')")
    public CommonResult<List<CollectorProtocolSimpleRespVO>> getProtocolSimpleList(
            @Parameter(description = "是否启用：0 否、1 是") @RequestParam(value = "enabled", required = false) Integer enabled) {
        List<CollectorProtocolDO> list = protocolService.getProtocolList(enabled);
        return success(BeanUtils.toBean(list, CollectorProtocolSimpleRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得采集协议详情")
    @Parameter(name = "id", description = "协议编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('collector:protocol:query')")
    public CommonResult<CollectorProtocolRespVO> getProtocol(@RequestParam("id") Long id) {
        CollectorProtocolDO protocol = protocolService.getProtocol(id);
        return success(BeanUtils.toBean(protocol, CollectorProtocolRespVO.class));
    }

}
