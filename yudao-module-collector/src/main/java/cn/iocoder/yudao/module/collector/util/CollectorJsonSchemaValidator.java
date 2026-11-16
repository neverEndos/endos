package cn.iocoder.yudao.module.collector.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.collector.enums.CollectorErrorCodeConstants.COLLECTOR_SCHEMA_INVALID;

/**
 * 采集协议 JSON Schema 校验器，统一使用 Draft 2020-12。
 */
@Component
public class CollectorJsonSchemaValidator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final JsonSchemaFactory SCHEMA_FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    /**
     * 按协议 Schema 校验配置对象；失败时返回包含实例字段路径的业务异常。
     */
    public void validate(Map<String, Object> schema, Map<String, Object> config) {
        Map<String, Object> effectiveSchema = schema != null ? schema : Map.of("type", "object");
        Map<String, Object> effectiveConfig = config != null ? config : Map.of();
        JsonSchema jsonSchema;
        JsonNode configNode;
        try {
            JsonNode schemaNode = OBJECT_MAPPER.valueToTree(effectiveSchema);
            configNode = OBJECT_MAPPER.valueToTree(effectiveConfig);
            jsonSchema = SCHEMA_FACTORY.getSchema(schemaNode);
        } catch (RuntimeException ex) {
            throw exception(COLLECTOR_SCHEMA_INVALID, "Schema 定义或配置无法解析：" + ex.getMessage());
        }
        Set<ValidationMessage> messages = jsonSchema.validate(configNode);
        if (messages.isEmpty()) {
            return;
        }
        String detail = messages.stream()
                .sorted(Comparator.comparing(message -> message.getInstanceLocation().toString()))
                .map(message -> {
                    String path = message.getInstanceLocation().toString();
                    return (path.isEmpty() ? "$" : path) + " " + message.getMessage();
                })
                .collect(Collectors.joining("；"));
        throw exception(COLLECTOR_SCHEMA_INVALID, detail);
    }

}
