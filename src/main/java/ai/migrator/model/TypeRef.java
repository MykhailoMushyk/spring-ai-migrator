package ai.migrator.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = TypeRefDeserializer.class)
public record TypeRef(String name, boolean collection, String collectionType, String elementType) {
    public static TypeRef simple(String name) {
        return new TypeRef(name, false, null, null);
    }

    public static TypeRef collection(String collectionType, String elementType) {
        String display = collectionType + "<" + elementType + ">";
        return new TypeRef(display, true, collectionType, elementType);
    }
}
