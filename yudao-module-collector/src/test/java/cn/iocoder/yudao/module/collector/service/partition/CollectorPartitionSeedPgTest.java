package cn.iocoder.yudao.module.collector.service.partition;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 种子第 6 节（raw 日分区预建定时任务）的幂等验证。
 *
 * <p>真实部署由 {@code dbctl.sh init/seed-system} 执行完整种子；本用例只建最小
 * {@code infra_job}/{@code infra_job_seq} 结构，把种子文件第 6 节原样执行两次，
 * 保证 CI 中也能回归「重复执行只有一条 deleted=0 记录」。</p>
 */
public class CollectorPartitionSeedPgTest extends CollectorPartitionPgTestSupport {

    private static final String JOB_HANDLER = "collectorPartitionMaintainJob";
    private static final String SECTION_MARKER = "-- ========== 6. raw 日分区预建定时任务";

    @Test
    public void sectionSixExecutedTwice_keepsSingleActiveJob() throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS public.infra_job");
            statement.execute("DROP SEQUENCE IF EXISTS public.infra_job_seq");
            statement.execute("""
                    CREATE SEQUENCE public.infra_job_seq START WITH 12909 INCREMENT BY 1
                    """);
            statement.execute("""
                    CREATE TABLE public.infra_job (
                        id bigint NOT NULL PRIMARY KEY,
                        name varchar(32) NOT NULL,
                        status smallint NOT NULL,
                        handler_name varchar(64) NOT NULL,
                        handler_param varchar(255) NOT NULL,
                        cron_expression varchar(255) NOT NULL,
                        retry_count integer NOT NULL,
                        retry_interval integer NOT NULL,
                        monitor_timeout integer NOT NULL,
                        creator varchar(64) NOT NULL,
                        updater varchar(64) NOT NULL,
                        deleted smallint NOT NULL
                    )
                    """);

            String section = readSeedSectionSix();
            statement.execute(section);
            statement.execute(section);

            try (ResultSet rs = statement.executeQuery("""
                    SELECT COUNT(*), COUNT(*) FILTER (WHERE deleted = 0)
                    FROM public.infra_job WHERE handler_name = '%s'
                    """.formatted(JOB_HANDLER))) {
                assertTrue(rs.next());
                assertEquals("重复执行后不应产生重复任务", 1, rs.getInt(1));
                assertEquals("deleted=0 的启用记录必须唯一", 1, rs.getInt(2));
                System.out.println("[证据] 场景11 种子执行两次后 handler=" + JOB_HANDLER
                        + " 总行数=" + rs.getInt(1) + "，deleted=0 行数=" + rs.getInt(2));
            }
            try (ResultSet rs = statement.executeQuery("""
                    SELECT name, status, cron_expression, deleted FROM public.infra_job
                    WHERE handler_name = '%s'
                    """.formatted(JOB_HANDLER))) {
                assertTrue(rs.next());
                assertEquals("采集 raw 日分区预建", rs.getString("name"));
                assertEquals(1, rs.getInt("status"));
                assertEquals("0 20 * * * ?", rs.getString("cron_expression"));
                assertEquals(0, rs.getInt("deleted"));
            }
        }
    }

    /** 从仓库种子文件抽取第 6 节（到 COMMIT 之前），按原样执行，避免复制一份 SQL 造成漂移。 */
    private static String readSeedSectionSix() throws IOException {
        Path file = locateSeedFile();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        StringBuilder section = new StringBuilder();
        boolean collecting = false;
        for (String line : lines) {
            if (!collecting) {
                if (line.startsWith(SECTION_MARKER)) {
                    collecting = true;
                    section.append(line).append('\n');
                }
                continue;
            }
            if (line.trim().equals("COMMIT;")) {
                break;
            }
            section.append(line).append('\n');
        }
        assertTrue("种子文件缺少第 6 节：" + file, collecting);
        assertTrue("第 6 节内容为空：" + file, section.toString().contains(JOB_HANDLER));
        return section.toString();
    }

    /** 兼容在仓库根或模块目录下执行 mvn test 两种工作目录。 */
    private static Path locateSeedFile() {
        String relative = "sql/postgresql/collector/02_system_seed.sql";
        for (Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
             current != null; current = current.getParent()) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("未找到种子文件 " + relative
                + "（user.dir=" + System.getProperty("user.dir") + "）");
    }

}
