package ai.migrator.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

public class TypeRefDeserializer extends JsonDeserializer<TypeRef> {
    @Override
    public TypeRef deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_STRING) {
            return TypeRef.simple(p.getValueAsString());
        }
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token == JsonToken.START_OBJECT) {
            JsonNode node = p.getCodec().readTree(p);
            String name = node.path("name").isMissingNode() ? null : node.path("name").asText(null);
            boolean collection = node.path("collection").asBoolean(false);
            String collectionType = node.path("collectionType").asText(null);
            String elementType = node.path("elementType").asText(null);

            if (collection || (collectionType != null && !collectionType.isBlank())) {
                if (collectionType == null || collectionType.isBlank()) {
                    collectionType = "List";
                }
                if (elementType == null || elementType.isBlank()) {
                    elementType = name != null ? name : "Object";
                }
                String display = name != null ? name : (collectionType + "<" + elementType + ">"
                );
                return new TypeRef(display, true, collectionType, elementType);
            }

            if (name == null || name.isBlank()) {
                return TypeRef.simple(node.toString());
            }
            return TypeRef.simple(name);
        }
        return (TypeRef) ctxt.handleUnexpectedToken(TypeRef.class, p);
    }
}
