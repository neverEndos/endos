package cn.iocoder.yudao.module.collector.dal.mysql.alarm;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.collector.dal.dataobject.alarm.CollectorAlarmStateDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CollectorAlarmStateMapper extends BaseMapperX<CollectorAlarmStateDO> {

    default List<CollectorAlarmStateDO> selectListByDeviceId(Long deviceId) {
        return selectList(new LambdaQueryWrapperX<CollectorAlarmStateDO>()
                .eqIfPresent(CollectorAlarmStateDO::getDeviceId, deviceId)
                .orderByDesc(CollectorAlarmStateDO::getLevel)
                .orderByAsc(CollectorAlarmStateDO::getChannelId));
    }

    /**
     * 查询仍处于报警级别的通道状态，用于不依赖新采样的抑制对账。
     */
    default List<CollectorAlarmStateDO> selectListByPositiveLevel() {
        return selectList(new LambdaQueryWrapperX<CollectorAlarmStateDO>()
                .gt(CollectorAlarmStateDO::getLevel, 0)
                .orderByAsc(CollectorAlarmStateDO::getChannelId));
    }

}
