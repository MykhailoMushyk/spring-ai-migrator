package ai.migrator.model;

import ai.migrator.analysis.AnalysisResult;
import ai.migrator.analysis.ProjectLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record MigrationSpec(
    String projectName,
    String moduleName,
    List<EndpointSpec> endpoints,
    List<DtoSpec> dtos,
    List<ServiceSpec> services,
    List<RepositorySpec> repositories,
    Map<String, String> metadata
) {

    public static MigrationSpec from(ProjectLayout layout, AnalysisResult analysis) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("root", layout.root().toString());
        metadata.put("module", layout.name());
        return new MigrationSpec(
            layout.root().getFileName().toString(),
            layout.name(),
            analysis.getEndpoints(),
            analysis.getDtos(),
            analysis.getServices(),
            analysis.getRepositories(),
            metadata
        );
    }
}
