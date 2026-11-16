package cn.iocoder.yudao.module.collector.dal.mysql.sensor;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.collector.controller.admin.sensor.vo.CollectorSensorPageReqVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.sensor.CollectorSensorDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CollectorSensorMapper extends BaseMapperX<CollectorSensorDO> {

    default PageResult<CollectorSensorDO> selectPage(CollectorSensorPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CollectorSensorDO>()
                .eqIfPresent(CollectorSensorDO::getDeviceId, reqVO.getDeviceId())
                .eqIfPresent(CollectorSensorDO::getSensorTypeId, reqVO.getSensorTypeId())
                .likeIfPresent(CollectorSensorDO::getSensorCode, reqVO.getSensorCode())
                .likeIfPresent(CollectorSensorDO::getSensorName, reqVO.getSensorName())
                .eqIfPresent(CollectorSensorDO::getProtocolCode, reqVO.getProtocolCode())
                .eqIfPresent(CollectorSensorDO::getEnabled, reqVO.getEnabled())
                .orderByAsc(CollectorSensorDO::getSort)
                .orderByAsc(CollectorSensorDO::getId));
    }

    default CollectorSensorDO selectByDeviceIdAndSensorCode(Long deviceId, String sensorCode) {
        return selectOne(new LambdaQueryWrapperX<CollectorSensorDO>()
                .eq(CollectorSensorDO::getDeviceId, deviceId)
                .eq(CollectorSensorDO::getSensorCode, sensorCode)
                .last("LIMIT 1"));
    }

    default List<CollectorSensorDO> selectListByDeviceIdAndEnabled(Long deviceId, Integer enabled) {
        return selectList(new LambdaQueryWrapperX<CollectorSensorDO>()
                .eqIfPresent(CollectorSensorDO::getDeviceId, deviceId)
                .eqIfPresent(CollectorSensorDO::getEnabled, enabled)
                .orderByAsc(CollectorSensorDO::getSort)
                .orderByAsc(CollectorSensorDO::getId));
    }

    default Long selectCountByDeviceId(Long deviceId) {
        return selectCount(CollectorSensorDO::getDeviceId, deviceId);
    }

    default Long selectCountBySensorTypeId(Long sensorTypeId) {
        return selectCount(CollectorSensorDO::getSensorTypeId, sensorTypeId);
    }

}
