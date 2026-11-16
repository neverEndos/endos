package cn.iocoder.yudao.module.collector.dal.mysql.sensortemplate;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.collector.controller.admin.sensortemplate.vo.CollectorSensorTemplatePageReqVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.sensortemplate.CollectorSensorTemplateDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CollectorSensorTemplateMapper extends BaseMapperX<CollectorSensorTemplateDO> {

    default PageResult<CollectorSensorTemplateDO> selectPage(CollectorSensorTemplatePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CollectorSensorTemplateDO>()
                .eqIfPresent(CollectorSensorTemplateDO::getSensorTypeId, reqVO.getSensorTypeId())
                .likeIfPresent(CollectorSensorTemplateDO::getChannelCode, reqVO.getChannelCode())
                .likeIfPresent(CollectorSensorTemplateDO::getChannelName, reqVO.getChannelName())
                .eqIfPresent(CollectorSensorTemplateDO::getValueKind, reqVO.getValueKind())
                .eqIfPresent(CollectorSensorTemplateDO::getEnabled, reqVO.getEnabled())
                .orderByAsc(CollectorSensorTemplateDO::getSort)
                .orderByAsc(CollectorSensorTemplateDO::getId));
    }

    default CollectorSensorTemplateDO selectBySensorTypeIdAndChannelCode(Long sensorTypeId, String channelCode) {
        return selectOne(new LambdaQueryWrapperX<CollectorSensorTemplateDO>()
                .eq(CollectorSensorTemplateDO::getSensorTypeId, sensorTypeId)
                .eq(CollectorSensorTemplateDO::getChannelCode, channelCode)
                .last("LIMIT 1"));
    }

    default List<CollectorSensorTemplateDO> selectListBySensorTypeId(Long sensorTypeId, Integer enabled) {
        return selectList(new LambdaQueryWrapperX<CollectorSensorTemplateDO>()
                .eq(CollectorSensorTemplateDO::getSensorTypeId, sensorTypeId)
                .eqIfPresent(CollectorSensorTemplateDO::getEnabled, enabled)
                .orderByAsc(CollectorSensorTemplateDO::getSort)
                .orderByAsc(CollectorSensorTemplateDO::getId));
    }

    default List<CollectorSensorTemplateDO> selectListBySensorTypeIds(Collection<Long> sensorTypeIds) {
        if (sensorTypeIds == null || sensorTypeIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CollectorSensorTemplateDO>()
                .in(CollectorSensorTemplateDO::getSensorTypeId, sensorTypeIds));
    }

    default Long selectCountBySensorTypeId(Long sensorTypeId) {
        return selectCount(CollectorSensorTemplateDO::getSensorTypeId, sensorTypeId);
    }

}
