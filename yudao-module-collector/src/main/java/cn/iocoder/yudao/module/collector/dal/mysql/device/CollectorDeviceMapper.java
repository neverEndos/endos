package cn.iocoder.yudao.module.collector.dal.mysql.device;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.collector.controller.admin.device.vo.CollectorDevicePageReqVO;
import cn.iocoder.yudao.module.collector.dal.dataobject.device.CollectorDeviceDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CollectorDeviceMapper extends BaseMapperX<CollectorDeviceDO> {

    default PageResult<CollectorDeviceDO> selectPage(CollectorDevicePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CollectorDeviceDO>()
                .likeIfPresent(CollectorDeviceDO::getDeviceCode, reqVO.getDeviceCode())
                .likeIfPresent(CollectorDeviceDO::getDeviceName, reqVO.getDeviceName())
                .eqIfPresent(CollectorDeviceDO::getEnabled, reqVO.getEnabled())
                .orderByAsc(CollectorDeviceDO::getSort)
                .orderByAsc(CollectorDeviceDO::getId));
    }

    default CollectorDeviceDO selectByDeviceCode(String deviceCode) {
        return selectOne(CollectorDeviceDO::getDeviceCode, deviceCode);
    }

    default List<CollectorDeviceDO> selectListByEnabled(Integer enabled) {
        return selectList(new LambdaQueryWrapperX<CollectorDeviceDO>()
                .eqIfPresent(CollectorDeviceDO::getEnabled, enabled)
                .orderByAsc(CollectorDeviceDO::getSort)
                .orderByAsc(CollectorDeviceDO::getId));
    }

}
