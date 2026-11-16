package cn.iocoder.yudao.module.collector.service.protocol;

import cn.iocoder.yudao.module.collector.dal.dataobject.protocol.CollectorProtocolDO;
import cn.iocoder.yudao.module.collector.dal.mysql.protocol.CollectorProtocolMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.PROTOCOL_NOT_ENABLED;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.PROTOCOL_NOT_EXISTS;

@Service
public class CollectorProtocolServiceImpl implements CollectorProtocolService {

    @Resource
    private CollectorProtocolMapper protocolMapper;

    @Override
    public List<CollectorProtocolDO> getProtocolList(Integer enabled) {
        return protocolMapper.selectListByEnabled(enabled);
    }

    @Override
    public CollectorProtocolDO getProtocol(Long id) {
        return protocolMapper.selectById(id);
    }

    @Override
    public CollectorProtocolDO validateProtocolExists(String protocolCode) {
        CollectorProtocolDO protocol = protocolMapper.selectByProtocolCode(protocolCode);
        if (protocol == null) {
            throw exception(PROTOCOL_NOT_EXISTS);
        }
        return protocol;
    }

    @Override
    public CollectorProtocolDO validateProtocolEnabled(String protocolCode) {
        CollectorProtocolDO protocol = validateProtocolExists(protocolCode);
        if (protocol.getEnabled() == null || protocol.getEnabled() != 1) {
            throw exception(PROTOCOL_NOT_ENABLED, protocolCode);
        }
        return protocol;
    }

}
