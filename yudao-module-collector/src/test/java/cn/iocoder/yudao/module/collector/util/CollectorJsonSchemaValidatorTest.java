package cn.iocoder.yudao.module.collector.util;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.COLLECTOR_SCHEMA_INVALID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CollectorJsonSchemaValidatorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CollectorJsonSchemaValidator validator = new CollectorJsonSchemaValidator();

    @Test
    @SuppressWarnings("unchecked")
    public void validate_objectRequiredTypeAndEnum() throws Exception {
        Map<String, Object> schema = OBJECT_MAPPER.readValue("""
                {"type":"object","properties":{"port":{"type":"string"},
                "mode":{"enum":["rtu","tcp"]},"retries":{"type":"integer","minimum":0,"maximum":10}},
                "required":["port","mode"]}
                """, Map.class);
        Map<String, Object> config = new HashMap<>();
        config.put("port", "/dev/ttyS4");
        config.put("mode", "rtu");
        config.put("retries", 2);
        validator.validate(schema, config);

        assertInvalid(schema, Map.of("mode", "rtu"), "port");
        assertInvalid(schema, Map.of("port", "/dev/ttyS4", "mode", "invalid"), "mode");
        assertInvalid(schema, Map.of("port", 123, "mode", "rtu"), "port");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void validate_nullableProperty() throws Exception {
        Map<String, Object> schema = OBJECT_MAPPER.readValue("""
                {"type":"object","properties":{"note":{"type":["string","null"]}},"required":["note"]}
                """, Map.class);
        Map<String, Object> config = new HashMap<>();
        config.put("note", null);
        validator.validate(schema, config);
        assertInvalid(schema, Map.of(), "note");
    }

    @Test
    public void validate_hsiPlaceholderAcceptsAnyObject() {
        validator.validate(Map.of("type", "object"), Map.of());
        validator.validate(Map.of("type", "object"), Map.of("custom", "value"));
    }

    private void assertInvalid(Map<String, Object> schema, Map<String, Object> config, String expectedPath) {
        try {
            validator.validate(schema, config);
            fail("应拒绝非法配置");
        } catch (ServiceException ex) {
            assertEquals(COLLECTOR_SCHEMA_INVALID.getCode(), ex.getCode());
            assertTrue(ex.getMessage(), ex.getMessage().contains(expectedPath));
        }
    }

}
