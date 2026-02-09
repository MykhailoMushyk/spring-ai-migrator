package ai.migrator.model;

public record RepositorySpec(String name, String packageName) {
    public String id() {
        return packageName + "." + name;
    }
}
