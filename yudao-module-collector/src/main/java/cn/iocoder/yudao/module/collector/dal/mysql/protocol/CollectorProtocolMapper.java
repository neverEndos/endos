package cn.iocoder.yudao.module.collector.dal.mysql.protocol;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.collector.dal.dataobject.protocol.CollectorProtocolDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CollectorProtocolMapper extends BaseMapperX<CollectorProtocolDO> {

    default CollectorProtocolDO selectByProtocolCode(String protocolCode) {
        return selectOne(CollectorProtocolDO::getProtocolCode, protocolCode);
    }

    default List<CollectorProtocolDO> selectListByEnabled(Integer enabled) {
        return selectList(new LambdaQueryWrapperX<CollectorProtocolDO>()
                .eqIfPresent(CollectorProtocolDO::getEnabled, enabled)
                .orderByAsc(CollectorProtocolDO::getSort)
                .orderByAsc(CollectorProtocolDO::getId));
    }

}
