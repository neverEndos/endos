package cn.iocoder.yudao.module.collector.service.protocol;

import cn.iocoder.yudao.module.collector.dal.dataobject.protocol.CollectorProtocolDO;

import java.util.List;

/**
 * 采集协议 Service 接口（只读）
 */
public interface CollectorProtocolService {

    /**
     * 查询协议列表；enabled 为空时返回全部未删除协议。
     */
    List<CollectorProtocolDO> getProtocolList(Integer enabled);

    CollectorProtocolDO getProtocol(Long id);

    /**
     * 校验协议编码存在，并返回协议。
     */
    CollectorProtocolDO validateProtocolExists(String protocolCode);

    /**
     * 校验协议编码存在且启用，并返回协议。
     */
    CollectorProtocolDO validateProtocolEnabled(String protocolCode);

}
