package ai.migrator.model;

import java.util.List;

public record PydanticModel(String name, List<PydanticField> fields) {}
