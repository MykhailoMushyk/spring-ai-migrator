package ai.migrator.model;

import java.util.List;

public record FastApiSpec(List<PydanticModel> models, List<FastApiRoute> routes) {}
