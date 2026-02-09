package ai.migrator.transform;

import ai.migrator.model.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DeterministicTransformService {

    public FastApiSpec transform(MigrationSpec spec) {
        List<PydanticModel> models = new ArrayList<>();
        for (DtoSpec dto : spec.dtos()) {
            List<PydanticField> fields = new ArrayList<>();
            for (FieldSpec field : dto.fields()) {
                String type = mapJavaType(field.type());
                fields.add(new PydanticField(field.name(), type, field.optional(), field.type().collection()));
            }
            models.add(new PydanticModel(dto.name(), fields));
        }

        List<FastApiRoute> routes = new ArrayList<>();
        for (EndpointSpec endpoint : spec.endpoints()) {
            String functionName = toSnake(endpoint.methodName());
            String requestModel = endpoint.requestBody() != null ? simpleName(endpoint.requestBody().name()) : null;
            String responseModel = endpoint.responseBody() != null ? simpleName(endpoint.responseBody().name()) : null;
            routes.add(new FastApiRoute(
                endpoint.path(),
                endpoint.httpMethod(),
                functionName,
                requestModel,
                responseModel,
                endpoint.statusCode(),
                endpoint.queryParams(),
                endpoint.pathParams(),
                endpoint.headerParams()
            ));
        }

        return new FastApiSpec(models, routes);
    }

    public String mapJavaType(TypeRef type) {
        if (type == null) {
            return "Any";
        }
        if (type.collection()) {
            String inner = mapJavaName(type.elementType());
            return "List[" + inner + "]";
        }
        return mapJavaName(type.name());
    }

    private String mapJavaName(String javaName) {
        String name = simpleName(javaName);
        return switch (name) {
            case "String" -> "str";
            case "Integer", "int" -> "int";
            case "Long", "long" -> "int";
            case "Double", "double", "Float", "float" -> "float";
            case "Boolean", "boolean" -> "bool";
            case "BigDecimal" -> "float";
            case "Instant" -> "datetime";
            case "LocalDate" -> "date";
            default -> name;
        };
    }

    private String simpleName(String typeName) {
        int idx = typeName.lastIndexOf('.');
        return idx == -1 ? typeName : typeName.substring(idx + 1);
    }

    private String toSnake(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('_').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString().replaceAll("__", "_");
    }
}
