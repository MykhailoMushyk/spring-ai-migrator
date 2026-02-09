package ai.migrator.analysis;

import ai.migrator.model.DtoSpec;
import ai.migrator.model.EndpointSpec;
import ai.migrator.model.RepositorySpec;
import ai.migrator.model.ServiceSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AnalysisResult {

    private final Map<String, EndpointSpec> endpoints = new LinkedHashMap<>();
    private final Map<String, DtoSpec> dtos = new LinkedHashMap<>();
    private final Map<String, ServiceSpec> services = new LinkedHashMap<>();
    private final Map<String, RepositorySpec> repositories = new LinkedHashMap<>();

    public List<EndpointSpec> getEndpoints() {
        return new ArrayList<>(endpoints.values());
    }

    public List<DtoSpec> getDtos() {
        return new ArrayList<>(dtos.values());
    }

    public List<ServiceSpec> getServices() {
        return new ArrayList<>(services.values());
    }

    public List<RepositorySpec> getRepositories() {
        return new ArrayList<>(repositories.values());
    }

    public void addEndpoint(EndpointSpec endpoint) {
        endpoints.put(endpoint.id(), endpoint);
    }

    public void addDto(DtoSpec dto) {
        dtos.put(dto.id(), dto);
    }

    public void addService(ServiceSpec service) {
        services.put(service.id(), service);
    }

    public void addRepository(RepositorySpec repository) {
        repositories.put(repository.id(), repository);
    }

    public void merge(AnalysisResult other) {
        other.getEndpoints().forEach(e -> endpoints.putIfAbsent(e.id(), e));
        other.getDtos().forEach(d -> dtos.putIfAbsent(d.id(), d));
        other.getServices().forEach(s -> services.putIfAbsent(s.id(), s));
        other.getRepositories().forEach(r -> repositories.putIfAbsent(r.id(), r));
    }
}
