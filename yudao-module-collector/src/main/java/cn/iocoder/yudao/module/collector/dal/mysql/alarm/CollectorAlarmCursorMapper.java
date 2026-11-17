package cn.iocoder.yudao.module.collector.dal.mysql.alarm;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.collector.dal.dataobject.alarm.CollectorAlarmCursorDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CollectorAlarmCursorMapper extends BaseMapperX<CollectorAlarmCursorDO> {

    @Select("SELECT id, last_sample_time, last_sample_id, updated_time FROM collector_alarm_cursor WHERE id = 1 FOR UPDATE")
    CollectorAlarmCursorDO selectSingletonForUpdate();

    /**
     * 初始化单例游标；并发初始化时后到者静默跳过，避免主键冲突。
     *
     * @return 实际插入行数，0 表示游标已由其他实例创建
     */
    @Insert("INSERT INTO collector_alarm_cursor (id, last_sample_time, last_sample_id, updated_time) "
            + "VALUES (#{id}, #{lastSampleTime}, #{lastSampleId}, #{updatedTime}) "
            + "ON CONFLICT (id) DO NOTHING")
    int insertIgnore(CollectorAlarmCursorDO cursor);

}
