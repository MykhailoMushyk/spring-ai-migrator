package ai.migrator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ParameterSpec(String name, TypeRef type, Boolean required, String source, Boolean optional) {
    public boolean requiredEffective() {
        if (required != null) {
            return required;
        }
        if (optional != null) {
            return !optional;
        }
        return false;
    }
}
