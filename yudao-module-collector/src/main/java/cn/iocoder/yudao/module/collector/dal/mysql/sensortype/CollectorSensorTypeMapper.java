package cn.iocoder.yudao.module.collector.dal.mysql.sensortype;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.collector.controller.admin.sensortype.vo.CollectorSensorTypePageReqVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.sensortype.CollectorSensorTypeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CollectorSensorTypeMapper extends BaseMapperX<CollectorSensorTypeDO> {

    default PageResult<CollectorSensorTypeDO> selectPage(CollectorSensorTypePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CollectorSensorTypeDO>()
                .likeIfPresent(CollectorSensorTypeDO::getTypeCode, reqVO.getTypeCode())
                .likeIfPresent(CollectorSensorTypeDO::getTypeName, reqVO.getTypeName())
                .eqIfPresent(CollectorSensorTypeDO::getProtocolCode, reqVO.getProtocolCode())
                .eqIfPresent(CollectorSensorTypeDO::getEnabled, reqVO.getEnabled())
                .orderByAsc(CollectorSensorTypeDO::getSort)
                .orderByAsc(CollectorSensorTypeDO::getId));
    }

    default CollectorSensorTypeDO selectByTypeCode(String typeCode) {
        return selectOne(CollectorSensorTypeDO::getTypeCode, typeCode);
    }

    default List<CollectorSensorTypeDO> selectListByEnabled(Integer enabled) {
        return selectList(new LambdaQueryWrapperX<CollectorSensorTypeDO>()
                .eqIfPresent(CollectorSensorTypeDO::getEnabled, enabled)
                .orderByAsc(CollectorSensorTypeDO::getSort)
                .orderByAsc(CollectorSensorTypeDO::getId));
    }

}
