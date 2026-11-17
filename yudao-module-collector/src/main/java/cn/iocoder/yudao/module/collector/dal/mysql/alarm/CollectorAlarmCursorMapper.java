package cn.iocoder.yudao.module.collector.dal.mysql.alarm;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.collector.dal.dataobject.alarm.CollectorAlarmCursorDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CollectorAlarmCursorMapper extends BaseMapperX<CollectorAlarmCursorDO> {

    @Select("SELECT id, last_sample_time, last_sample_id, updated_time FROM collector_alarm_cursor WHERE id = 1 FOR UPDATE")
    CollectorAlarmCursorDO selectSingletonForUpdate();

}
