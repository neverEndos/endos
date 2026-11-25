package cn.iocoder.yudao.module.collector.convert.alarm;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.collector.controller.admin.alarm.vo.CollectorAlarmRespVO;
import cn.iocoder.yudao.module.collector.controller.admin.alarm.vo.CollectorAlarmStateRespVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.alarm.CollectorAlarmDO;
import cn.iocoder.yudao.module.collector.dal.dataobject.alarm.CollectorAlarmStateDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface CollectorAlarmConvert {

    CollectorAlarmConvert INSTANCE = Mappers.getMapper(CollectorAlarmConvert.class);

    PageResult<CollectorAlarmRespVO> convertPage(PageResult<CollectorAlarmDO> page);

    @Mapping(target = "deviceName", ignore = true)
    @Mapping(target = "sensorName", ignore = true)
    @Mapping(target = "channelName", ignore = true)
    CollectorAlarmRespVO convert(CollectorAlarmDO alarm);

    List<CollectorAlarmStateRespVO> convertStateList(List<CollectorAlarmStateDO> list);

    @Mapping(target = "deviceName", ignore = true)
    @Mapping(target = "sensorName", ignore = true)
    @Mapping(target = "channelName", ignore = true)
    @Mapping(target = "activeAlarmId", ignore = true)
    @Mapping(target = "acknowledged", ignore = true)
    @Mapping(target = "acknowledgedAt", ignore = true)
    CollectorAlarmStateRespVO convertState(CollectorAlarmStateDO state);

}
