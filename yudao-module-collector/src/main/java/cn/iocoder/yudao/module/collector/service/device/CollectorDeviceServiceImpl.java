package cn.iocoder.yudao.module.collector.service.device;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.collector.controller.admin.device.vo.CollectorDevicePageReqVO;
import cn.iocoder.yudao.module.collector.controller.admin.device.vo.CollectorDeviceSaveReqVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.device.CollectorDeviceDO;
import cn.iocoder.yudao.module.collector.dal.mysql.channel.CollectorChannelMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.device.CollectorDeviceMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.reference.CollectorReferenceMapper;
import cn.iocoder.yudao.module.collector.dal.mysql.sensor.CollectorSensorMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.DEVICE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.DEVICE_HAS_CHILDREN;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.DEVICE_NOT_EXISTS;

@Service
public class CollectorDeviceServiceImpl implements CollectorDeviceService {

    @Resource
    private CollectorDeviceMapper deviceMapper;

    @Resource
    private CollectorSensorMapper sensorMapper;

    @Resource
    private CollectorChannelMapper channelMapper;

    @Resource
    private CollectorReferenceMapper referenceMapper;

    @Override
    public PageResult<CollectorDeviceDO> getDevicePage(CollectorDevicePageReqVO pageReqVO) {
        return deviceMapper.selectPage(pageReqVO);
    }

    @Override
    public List<CollectorDeviceDO> getDeviceList(Integer enabled) {
        return deviceMapper.selectListByEnabled(enabled);
    }

    @Override
    public CollectorDeviceDO getDevice(Long id) {
        return deviceMapper.selectById(id);
    }

    @Override
    public Long createDevice(CollectorDeviceSaveReqVO createReqVO) {
        validateDeviceCodeUnique(null, createReqVO.getDeviceCode());
        CollectorDeviceDO device = BeanUtils.toBean(createReqVO, CollectorDeviceDO.class);
        fillCreateDefaults(device);
        deviceMapper.insert(device);
        return device.getId();
    }

    @Override
    public void updateDevice(CollectorDeviceSaveReqVO updateReqVO) {
        validateDeviceExists(updateReqVO.getId());
        validateDeviceCodeUnique(updateReqVO.getId(), updateReqVO.getDeviceCode());
        CollectorDeviceDO updateObj = BeanUtils.toBean(updateReqVO, CollectorDeviceDO.class);
        deviceMapper.updateById(updateObj);
    }

    @Override
    public void deleteDevice(Long id) {
        validateDeviceExists(id);
        if (sensorMapper.selectCountByDeviceId(id) > 0
                || channelMapper.selectCountByDeviceId(id) > 0
                || referenceMapper.selectPollScheduleCountByDeviceId(id) > 0) {
            throw exception(DEVICE_HAS_CHILDREN);
        }
        deviceMapper.deleteById(id);
    }

    @Override
    public CollectorDeviceDO validateDeviceExists(Long id) {
        CollectorDeviceDO device = deviceMapper.selectById(id);
        if (device == null) {
            throw exception(DEVICE_NOT_EXISTS);
        }
        return device;
    }

    private void validateDeviceCodeUnique(Long id, String deviceCode) {
        CollectorDeviceDO device = deviceMapper.selectByDeviceCode(deviceCode);
        if (device == null) {
            return;
        }
        if (id == null || !device.getId().equals(id)) {
            throw exception(DEVICE_CODE_DUPLICATE, deviceCode);
        }
    }

    private void fillCreateDefaults(CollectorDeviceDO device) {
        if (device.getEnabled() == null) {
            device.setEnabled(1);
        }
        if (device.getPollIntervalMs() == null) {
            device.setPollIntervalMs(5000);
        }
        if (device.getTimeoutMs() == null) {
            device.setTimeoutMs(1000);
        }
        if (device.getRetries() == null) {
            device.setRetries(2);
        }
        if (device.getReconnectIntervalMs() == null) {
            device.setReconnectIntervalMs(5000);
        }
        if (device.getSort() == null) {
            device.setSort(0);
        }
    }

}
