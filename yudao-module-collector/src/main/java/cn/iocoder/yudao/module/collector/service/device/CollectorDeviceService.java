package cn.iocoder.yudao.module.collector.service.device;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.collector.controller.admin.device.vo.CollectorDevicePageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.device.vo.CollectorDeviceSaveReqVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.device.CollectorDeviceDO;

import java.util.List;

/**
 * 采集设备 Service 接口
 */
public interface CollectorDeviceService {

    PageResult<CollectorDeviceDO> getDevicePage(CollectorDevicePageReqVO pageReqVO);

    List<CollectorDeviceDO> getDeviceList(Integer enabled);

    CollectorDeviceDO getDevice(Long id);

    Long createDevice(CollectorDeviceSaveReqVO createReqVO);

    void updateDevice(CollectorDeviceSaveReqVO updateReqVO);

    void deleteDevice(Long id);

    /**
     * 校验设备存在，并返回设备。
     */
    CollectorDeviceDO validateDeviceExists(Long id);

}
