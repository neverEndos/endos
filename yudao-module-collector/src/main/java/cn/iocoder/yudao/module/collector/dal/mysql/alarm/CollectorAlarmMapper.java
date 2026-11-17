package cn.iocoder.yudao.module.collector.dal.mysql.alarm;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.collector.dal.dataobject.alarm.CollectorAlarmDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CollectorAlarmMapper extends BaseMapperX<CollectorAlarmDO> {

    default List<CollectorAlarmDO> selectActiveListByChannelIds(Collection<Long> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CollectorAlarmDO>()
                .in(CollectorAlarmDO::getChannelId, channelIds)
                .isNull(CollectorAlarmDO::getClearedAt));
    }

}
