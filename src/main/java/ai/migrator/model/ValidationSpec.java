package ai.migrator.model;

public record ValidationSpec(
    Integer minLength,
    Integer maxLength,
    Long min,
    Long max,
    Double gt,
    Double ge,
    Double lt,
    Double le,
    Boolean notBlank,
    Boolean notNull,
    Boolean email,
    String pattern
) {
    public static ValidationSpec empty() {
        return new ValidationSpec(null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
