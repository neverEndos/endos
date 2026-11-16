package cn.iocoder.yudao.module.collector.dal.mysql.handler;

import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Collector JSONB 字段类型处理器。
 *
 * 复用 MyBatis-Plus 的 Jackson 序列化/反序列化能力，但写入时按 {@link Types#OTHER}
 * 绑定参数，让 PostgreSQL 驱动以“未指定类型”发送 JSON 文本，从而由数据库列推导为 jsonb。
 * 直接使用 {@link Jackson3TypeHandler} 会按 varchar 绑定，写入 jsonb 列时报类型不匹配。
 */
public class CollectorJsonbTypeHandler extends Jackson3TypeHandler {

    public CollectorJsonbTypeHandler(Class<?> type) {
        super(type);
    }

    public CollectorJsonbTypeHandler(Class<?> type, Field field) {
        super(type, field);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, toJson(parameter), Types.OTHER);
    }

}
