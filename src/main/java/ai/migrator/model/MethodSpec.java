package ai.migrator.model;

import java.util.List;

public record MethodSpec(String name, List<MethodParamSpec> params, TypeRef returnType) {}
