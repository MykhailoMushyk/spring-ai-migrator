package ai.migrator.model;

import java.util.List;

public record ServiceSpec(String name, String packageName, List<MethodSpec> methods) {
    public String id() {
        return packageName + "." + name;
    }
}
