package ai.migrator.transform;

import ai.migrator.model.*;

import java.util.*;

public class MigrationChunker {
    public static List<MigrationSpec> chunk(MigrationSpec spec, int maxChunkSize) {
        if (spec.endpoints().size() <= maxChunkSize) {
            return List.of(spec);
        }

        List<MigrationSpec> chunks = new ArrayList<>();
        List<EndpointSpec> endpoints = spec.endpoints();

        for (int i = 0; i < endpoints.size(); i += maxChunkSize) {
            int end = Math.min(i + maxChunkSize, endpoints.size());
            List<EndpointSpec> endpointChunk = endpoints.subList(i, end);
            List<DtoSpec> dtoChunk = selectDtos(endpointChunk, spec.dtos());

            chunks.add(new MigrationSpec(
                spec.projectName(),
                spec.moduleName(),
                endpointChunk,
                dtoChunk,
                List.of(),
                List.of(),
                spec.metadata()
            ));
        }

        return chunks;
    }

    private static List<DtoSpec> selectDtos(List<EndpointSpec> endpoints, List<DtoSpec> allDtos) {
        Map<String, DtoSpec> bySimple = new LinkedHashMap<>();
        for (DtoSpec dto : allDtos) {
            bySimple.put(simpleName(dto.name()), dto);
        }

        Set<String> required = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();

        for (EndpointSpec endpoint : endpoints) {
            addType(endpoint.requestBody(), bySimple, required, queue);
            addType(endpoint.responseBody(), bySimple, required, queue);
            for (ParameterSpec param : endpoint.queryParams()) {
                addType(param.type(), bySimple, required, queue);
            }
            for (ParameterSpec param : endpoint.pathParams()) {
                addType(param.type(), bySimple, required, queue);
            }
            for (ParameterSpec param : endpoint.headerParams()) {
                addType(param.type(), bySimple, required, queue);
            }
        }

        while (!queue.isEmpty()) {
            String name = queue.removeFirst();
            DtoSpec dto = bySimple.get(name);
            if (dto == null) {
                continue;
            }
            for (FieldSpec field : dto.fields()) {
                addType(field.type(), bySimple, required, queue);
            }
        }

        List<DtoSpec> result = new ArrayList<>();
        for (String name : required) {
            DtoSpec dto = bySimple.get(name);
            if (dto != null) {
                result.add(dto);
            }
        }
        return result;
    }

    private static void addType(TypeRef typeRef, Map<String, DtoSpec> bySimple, Set<String> required, Deque<String> queue) {
        if (typeRef == null) {
            return;
        }

        if (typeRef.collection()) {
            addName(typeRef.elementType(), bySimple, required, queue);
        }

        addName(typeRef.name(), bySimple, required, queue);
    }

    private static void addName(String rawName, Map<String, DtoSpec> bySimple, Set<String> required, Deque<String> queue) {
        if (rawName == null || rawName.isBlank()) {
            return;
        }

        String simple = simpleName(rawName);
        if (simple != null && bySimple.containsKey(simple) && required.add(simple)) {
            queue.add(simple);
            return;
        }

        String generic = extractGenericElement(rawName);
        if (generic != null) {
            String genericSimple = simpleName(generic);
            if (genericSimple != null && bySimple.containsKey(genericSimple) && required.add(genericSimple)) {
                queue.add(genericSimple);
            }
        }
    }

    private static String extractGenericElement(String rawName) {
        int start = rawName.indexOf('<');
        int end = rawName.lastIndexOf('>');
        if (start >= 0 && end > start) {
            return rawName.substring(start + 1, end).trim();
        }
        return null;
    }

    private static String simpleName(String typeName) {
        if (typeName == null) {
            return null;
        }
        int idx = typeName.lastIndexOf('.');
        return idx == -1 ? typeName : typeName.substring(idx + 1);
    }
}
