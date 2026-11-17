package cn.iocoder.yudao.module.collector.dal.mysql.channel;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.collector.controller.admin.channel.vo.CollectorChannelPageReqVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.channel.CollectorChannelDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CollectorChannelMapper extends BaseMapperX<CollectorChannelDO> {

    default PageResult<CollectorChannelDO> selectPage(CollectorChannelPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CollectorChannelDO>()
                .eqIfPresent(CollectorChannelDO::getDeviceId, reqVO.getDeviceId())
                .eqIfPresent(CollectorChannelDO::getSensorId, reqVO.getSensorId())
                .likeIfPresent(CollectorChannelDO::getChannelCode, reqVO.getChannelCode())
                .likeIfPresent(CollectorChannelDO::getChannelName, reqVO.getChannelName())
                .eqIfPresent(CollectorChannelDO::getValueKind, reqVO.getValueKind())
                .eqIfPresent(CollectorChannelDO::getEnabled, reqVO.getEnabled())
                .orderByAsc(CollectorChannelDO::getSort)
                .orderByAsc(CollectorChannelDO::getId));
    }

    default CollectorChannelDO selectBySensorIdAndChannelCode(Long sensorId, String channelCode) {
        return selectOne(new LambdaQueryWrapperX<CollectorChannelDO>()
                .eq(CollectorChannelDO::getSensorId, sensorId)
                .eq(CollectorChannelDO::getChannelCode, channelCode)
                .last("LIMIT 1"));
    }

    /**
     * 批量加载通道并显式包含逻辑删除行，供抑制对账区分“已删除”和“物理不存在”。
     * 只读取判定所需字段，不经过 MyBatis-Plus 的 deleted=0 自动过滤。
     */
    @Select("""
            <script>
            SELECT id, device_id, sensor_id, value_kind, enabled, alarm_enabled, deleted
            FROM collector_channel
            WHERE id IN
            <foreach collection="channelIds" item="channelId" open="(" separator="," close=")">
                #{channelId}
            </foreach>
            </script>
            """)
    List<CollectorChannelDO> selectListIncludingDeletedByIds(@Param("channelIds") Collection<Long> channelIds);

    default List<CollectorChannelDO> selectListByDeviceId(Long deviceId) {
        return selectList(new LambdaQueryWrapperX<CollectorChannelDO>()
                .eq(CollectorChannelDO::getDeviceId, deviceId)
                .orderByAsc(CollectorChannelDO::getSensorId)
                .orderByAsc(CollectorChannelDO::getSort)
                .orderByAsc(CollectorChannelDO::getId));
    }

    default List<CollectorChannelDO> selectListBySensorId(Long sensorId) {
        return selectList(new LambdaQueryWrapperX<CollectorChannelDO>()
                .eq(CollectorChannelDO::getSensorId, sensorId)
                .orderByAsc(CollectorChannelDO::getSort)
                .orderByAsc(CollectorChannelDO::getId));
    }

    default List<CollectorChannelDO> selectListBySensorIds(Collection<Long> sensorIds) {
        if (sensorIds == null || sensorIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CollectorChannelDO>()
                .in(CollectorChannelDO::getSensorId, sensorIds));
    }

    default Long selectCountByDeviceId(Long deviceId) {
        return selectCount(CollectorChannelDO::getDeviceId, deviceId);
    }

    default Long selectCountBySensorId(Long sensorId) {
        return selectCount(CollectorChannelDO::getSensorId, sensorId);
    }

    default Long selectCountBySensorTemplateId(Long sensorTemplateId) {
        return selectCount(CollectorChannelDO::getSensorTemplateId, sensorTemplateId);
    }

}
