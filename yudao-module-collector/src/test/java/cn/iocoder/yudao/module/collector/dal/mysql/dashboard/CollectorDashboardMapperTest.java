package cn.iocoder.yudao.module.collector.dal.mysql.dashboard;

import cn.iocoder.yudao.module.collector.dal.dataobject.dashboard.CollectorDashboardChannelRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.dashboard.CollectorDashboardDeviceRow;
import cn.iocoder.yudao.module.collector.dal.dataobject.dashboard.CollectorDashboardSensorRow;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 真实 PostgreSQL 上的 Mapper SQL 验证；未提供临时库连接信息时跳过。
 * 运行示例：COLLECTOR_TEST_PG_URL=jdbc:postgresql://127.0.0.1:5432/collector_test ...
 */
public class CollectorDashboardMapperTest {

    private static final String URL = System.getenv("COLLECTOR_TEST_PG_URL");
    private static final String USER = System.getenv("COLLECTOR_TEST_PG_USER");
    private static final String PASSWORD = System.getenv("COLLECTOR_TEST_PG_PASSWORD");

    private SqlSession session;
    private CollectorDashboardMapper mapper;

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("未设置 COLLECTOR_TEST_PG_URL，跳过临时 PostgreSQL Mapper 测试",
                URL != null && !URL.isBlank());
        Class.forName("org.postgresql.Driver");
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    DROP TABLE IF EXISTS collector_channel_latest, collector_channel,
                        collector_sensor, collector_sensor_type, collector_device_status,
                        collector_heartbeat, collector_device CASCADE
                    """);
            statement.execute("""
                    CREATE TABLE collector_device (
                        id bigint PRIMARY KEY, device_name varchar(64), poll_interval_ms integer,
                        update_time timestamp, deleted smallint DEFAULT 0
                    )
                    """);
            statement.execute("""
                    CREATE TABLE collector_device_status (
                        device_id bigint PRIMARY KEY, online smallint, consecutive_failures integer,
                        update_time timestamp
                    )
                    """);
            statement.execute("""
                    CREATE TABLE collector_heartbeat (
                        id bigint PRIMARY KEY, heartbeat_time timestamp, write_lag_ms integer
                    )
                    """);
            statement.execute("""
                    CREATE TABLE collector_sensor_type (
                        id bigint PRIMARY KEY, type_name varchar(64), deleted smallint DEFAULT 0
                    )
                    """);
            statement.execute("""
                    CREATE TABLE collector_sensor (
                        id bigint PRIMARY KEY, device_id bigint, sensor_type_id bigint,
                        sensor_name varchar(64), sort integer, deleted smallint DEFAULT 0
                    )
                    """);
            statement.execute("""
                    CREATE TABLE collector_channel (
                        id bigint PRIMARY KEY, device_id bigint, sensor_id bigint,
                        channel_name varchar(64), unit varchar(32), value_kind varchar(16),
                        poll_interval_ms integer, sort integer, enabled smallint, deleted smallint DEFAULT 0
                    )
                    """);
            statement.execute("""
                    CREATE TABLE collector_channel_latest (
                        channel_id bigint PRIMARY KEY, device_id bigint, sensor_id bigint,
                        value_num numeric, value_text varchar(64), quality smallint,
                        sample_time timestamp(3), updated_time timestamp
                    )
                    """);
            statement.execute("""
                    INSERT INTO collector_device VALUES
                        (7, 'CS1000-HSI', 60000, TIMESTAMP '2026-11-17 12:00:05', 0)
                    """);
            statement.execute("""
                    INSERT INTO collector_device_status VALUES
                        (7, 1, 0, TIMESTAMP '2026-11-17 12:00:05')
                    """);
            statement.execute("""
                    INSERT INTO collector_heartbeat VALUES
                        (1, TIMESTAMP '2026-11-17 11:59:00', 5),
                        (2, TIMESTAMP '2026-11-17 12:00:00', 9)
                    """);
            statement.execute("INSERT INTO collector_sensor_type VALUES (1, 'CS1000', 0)");
            statement.execute("""
                    INSERT INTO collector_sensor VALUES
                        (10, 7, 1, 'CS1000-01', 1, 0),
                        (11, 7, 1, '已删除传感器', 2, 1)
                    """);
            statement.execute("""
                    INSERT INTO collector_channel VALUES
                        (101, 7, 10, 'iso_4', 'ISO', 'number', NULL, 1, 1, 0),
                        (102, 7, 10, 'flow_state', NULL, 'enum', 5000, 2, 1, 0),
                        (103, 7, 10, 'disabled', NULL, 'number', NULL, 3, 0, 0)
                    """);
            statement.execute("""
                    INSERT INTO collector_channel_latest VALUES
                        (101, 7, 10, 12.5, NULL, 0, TIMESTAMP '2026-11-17 12:00:00', CURRENT_TIMESTAMP),
                        (102, 7, 10, NULL, '11', 2, TIMESTAMP '2026-11-17 12:00:00', CURRENT_TIMESTAMP),
                        (103, 7, 10, NULL, NULL, 0, TIMESTAMP '2026-11-17 12:00:02', CURRENT_TIMESTAMP)
                    """);
        }

        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.postgresql.Driver", URL, USER, PASSWORD);
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(CollectorDashboardMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        session = factory.openSession(true);
        mapper = session.getMapper(CollectorDashboardMapper.class);
    }

    @After
    public void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    public void selectDeviceState_readsHeartbeatAsProcessLevelLag() {
        CollectorDashboardDeviceRow row = mapper.selectDeviceState(7L);
        assertNotNull(row);
        assertEquals("CS1000-HSI", row.getName());
        assertEquals(Integer.valueOf(60000), row.getPollIntervalMs());
        assertEquals(Integer.valueOf(1), row.getOnline());
        assertEquals(Integer.valueOf(9), row.getLastWriteLagMs());
        assertEquals(java.time.LocalDateTime.of(2026, 11, 17, 12, 0, 5), row.getLastPollAt());
        // 禁用通道不出现在看板列表，但其采样仍用于设备活性，避免禁用操作洗掉离线状态。
        assertEquals(java.time.LocalDateTime.of(2026, 11, 17, 12, 0, 2), row.getLastSampleTime());
        assertNull(mapper.selectDeviceState(99L));
    }

    @Test
    public void selectSensors_joinsTypeNameAndSkipsDeletedRows() {
        List<CollectorDashboardSensorRow> rows = mapper.selectSensors(7L);
        assertEquals(1, rows.size());
        assertEquals(Long.valueOf(10), rows.get(0).getId());
        assertEquals("CS1000", rows.get(0).getModel());
    }

    @Test
    public void selectChannelLatest_excludesDisabledChannelsAndKeepsLatestValues() {
        List<CollectorDashboardChannelRow> rows = mapper.selectChannelLatest(7L);
        assertEquals(2, rows.size());

        CollectorDashboardChannelRow number = rows.get(0);
        assertEquals(Long.valueOf(101), number.getId());
        assertEquals(new java.math.BigDecimal("12.5"), number.getValueNum());
        assertEquals(Integer.valueOf(0), number.getQuality());
        assertNull(number.getPollIntervalMs());

        CollectorDashboardChannelRow enumRow = rows.get(1);
        assertEquals("enum", enumRow.getValueKind());
        assertEquals("11", enumRow.getValueText());
        assertEquals(Integer.valueOf(2), enumRow.getQuality());
        // 通道级轮询周期已预留读取，但 v1 不参与陈旧判定。
        assertEquals(Integer.valueOf(5000), enumRow.getPollIntervalMs());
        assertTrue(rows.stream().noneMatch(row -> Long.valueOf(103).equals(row.getId())));
    }

}
