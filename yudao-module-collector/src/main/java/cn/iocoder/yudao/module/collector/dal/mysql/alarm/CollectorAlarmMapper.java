package cn.iocoder.yudao.module.collector.dal.mysql.alarm;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.collector.controller.admin.alarm.vo.CollectorAlarmPageReqVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.alarm.CollectorAlarmDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CollectorAlarmMapper extends BaseMapperX<CollectorAlarmDO> {

    default PageResult<CollectorAlarmDO> selectPage(CollectorAlarmPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CollectorAlarmDO>()
                .eqIfPresent(CollectorAlarmDO::getDeviceId, reqVO.getDeviceId())
                .eqIfPresent(CollectorAlarmDO::getSensorId, reqVO.getSensorId())
                .eqIfPresent(CollectorAlarmDO::getChannelId, reqVO.getChannelId())
                .eqIfPresent(CollectorAlarmDO::getLevel, reqVO.getLevel())
                .eqIfPresent(CollectorAlarmDO::getEventType, reqVO.getEventType())
                .betweenIfPresent(CollectorAlarmDO::getTriggeredAt, reqVO.getTriggeredAt())
                .orderByDesc(CollectorAlarmDO::getTriggeredAt)
                .orderByDesc(CollectorAlarmDO::getId));
    }

    default List<CollectorAlarmDO> selectActiveListByChannelIds(Collection<Long> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CollectorAlarmDO>()
                .in(CollectorAlarmDO::getChannelId, channelIds)
                .isNull(CollectorAlarmDO::getClearedAt));
    }

}
