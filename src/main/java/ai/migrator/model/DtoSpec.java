package ai.migrator.model;

import java.util.List;

public record DtoSpec(String name, String packageName, List<FieldSpec> fields, boolean record) {
    public String id() {
        return packageName + "." + name;
    }
}
