package ai.migrator.model;

public record FieldSpec(String name, TypeRef type, boolean optional, ValidationSpec validation, String jsonAlias) {
    public FieldSpec {
        if (validation == null) {
            validation = ValidationSpec.empty();
        }
    }
}
