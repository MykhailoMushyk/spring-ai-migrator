package ai.migrator.analysis;

import ai.migrator.model.*;
import io.github.classgraph.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SpringBytecodeAnalyzer {

    public AnalysisResult analyze(ProjectLayout layout) {
        AnalysisResult result = new AnalysisResult();
        if (layout.classesDir() == null) {
            return result;
        }

        try (ScanResult scan = new ClassGraph()
            .enableAllInfo()
            .overrideClasspath(layout.classesDir().toString())
            .scan()) {

            for (ClassInfo controller : scan.getClassesWithAnnotation("org.springframework.web.bind.annotation.RestController")) {
                parseController(controller, result);
            }

            for (ClassInfo dto : scan.getAllClasses()) {
                if (dto.getName().contains(".dto.")) {
                    parseDto(dto, result);
                }
            }

            for (ClassInfo service : scan.getClassesWithAnnotation("org.springframework.stereotype.Service")) {
                parseService(service, result);
            }

            for (ClassInfo repo : scan.getClassesWithAnnotation("org.springframework.stereotype.Repository")) {
                parseRepository(repo, result);
            }
        }

        return result;
    }

    private void parseController(ClassInfo controller, AnalysisResult result) {
        String basePath = extractRequestMappingPath(controller.getAnnotationInfo());
        List<String> services = extractControllerServices(controller);

        for (MethodInfo method : controller.getMethodInfo()) {
            MappingInfo mapping = extractMapping(method);
            if (mapping == null) {
                continue;
            }

            String path = normalizePath(basePath, mapping.path());

            EndpointSpec endpoint = EndpointSpec.builder()
                .id(controller.getName() + "#" + method.getName() + "::" + mapping.httpMethod())
                .controllerClass(controller.getName())
                .controllerPath(basePath)
                .methodName(method.getName())
                .methodPath(mapping.path())
                .httpMethod(mapping.httpMethod())
                .path(path)
                .statusCode(200)
                .requestBody(extractRequestBody(method))
                .responseBody(extractResponseBody(method))
                .queryParams(extractParams(method, "org.springframework.web.bind.annotation.RequestParam", "query"))
                .pathParams(extractParams(method, "org.springframework.web.bind.annotation.PathVariable", "path"))
                .headerParams(extractParams(method, "org.springframework.web.bind.annotation.RequestHeader", "header"))
                .controllerServices(services)
                .build();

            result.addEndpoint(endpoint);
        }
    }

    private void parseDto(ClassInfo dto, AnalysisResult result) {
        List<FieldSpec> fields = new ArrayList<>();
        for (FieldInfo field : dto.getFieldInfo()) {
            fields.add(new FieldSpec(field.getName(), TypeRef.simple(field.getTypeDescriptor().toString()), false, ValidationSpec.empty(), null));
        }
        if (!fields.isEmpty()) {
            DtoSpec spec = new DtoSpec(dto.getSimpleName(), packageOf(dto.getName()), fields, dto.isRecord());
            result.addDto(spec);
        }
    }

    private void parseService(ClassInfo service, AnalysisResult result) {
        List<MethodSpec> methods = new ArrayList<>();
        for (MethodInfo method : service.getMethodInfo()) {
            List<MethodParamSpec> params = Arrays.stream(method.getParameterInfo())
                .map(p -> new MethodParamSpec(p.getName(), TypeRef.simple(p.getTypeDescriptor().toString()), false))
                .collect(Collectors.toList());
            methods.add(new MethodSpec(method.getName(), params, TypeRef.simple(method.getTypeDescriptor().toString())));
        }
        ServiceSpec spec = new ServiceSpec(service.getSimpleName(), packageOf(service.getName()), methods);
        result.addService(spec);
    }

    private void parseRepository(ClassInfo repo, AnalysisResult result) {
        RepositorySpec spec = new RepositorySpec(repo.getSimpleName(), packageOf(repo.getName()));
        result.addRepository(spec);
    }

    private String extractRequestMappingPath(AnnotationInfoList annotations) {
        for (AnnotationInfo ann : annotations) {
            if (ann.getName().equals("org.springframework.web.bind.annotation.RequestMapping")) {
                Object value = ann.getParameterValues().getValue("value");
                if (value == null) {
                    value = ann.getParameterValues().getValue("path");
                }
                if (value != null) {
                    return stringify(value);
                }
            }
        }
        return "";
    }

    private MappingInfo extractMapping(MethodInfo method) {
        AnnotationInfoList annotations = method.getAnnotationInfo();
        for (AnnotationInfo ann : annotations) {
            String name = ann.getName();
            if (name.endsWith("GetMapping")) {
                return new MappingInfo("GET", extractPath(ann));
            }
            if (name.endsWith("PostMapping")) {
                return new MappingInfo("POST", extractPath(ann));
            }
            if (name.endsWith("PutMapping")) {
                return new MappingInfo("PUT", extractPath(ann));
            }
            if (name.endsWith("DeleteMapping")) {
                return new MappingInfo("DELETE", extractPath(ann));
            }
            if (name.endsWith("PatchMapping")) {
                return new MappingInfo("PATCH", extractPath(ann));
            }
            if (name.endsWith("RequestMapping")) {
                String methodName = extractRequestMethod(ann);
                return new MappingInfo(methodName, extractPath(ann));
            }
        }
        return null;
    }

    private String extractRequestMethod(AnnotationInfo ann) {
        Object value = ann.getParameterValues().getValue("method");
        if (value != null) {
            String text = stringify(value);
            if (text.contains("RequestMethod.")) {
                return text.substring(text.indexOf("RequestMethod.") + "RequestMethod.".length());
            }
        }
        return "GET";
    }

    private String extractPath(AnnotationInfo ann) {
        Object value = ann.getParameterValues().getValue("value");
        if (value == null) {
            value = ann.getParameterValues().getValue("path");
        }
        return value != null ? stringify(value) : "";
    }

    private TypeRef extractRequestBody(MethodInfo method) {
        for (MethodParameterInfo param : method.getParameterInfo()) {
            if (param.hasAnnotation("org.springframework.web.bind.annotation.RequestBody")) {
                return TypeRef.simple(param.getTypeDescriptor().toString());
            }
        }
        return null;
    }

    private TypeRef extractResponseBody(MethodInfo method) {
        return TypeRef.simple(method.getTypeDescriptor().toString());
    }

    private List<ParameterSpec> extractParams(MethodInfo method, String annotation, String source) {
        List<ParameterSpec> params = new ArrayList<>();
        for (MethodParameterInfo param : method.getParameterInfo()) {
            if (param.hasAnnotation(annotation)) {
                params.add(new ParameterSpec(param.getName(), TypeRef.simple(param.getTypeDescriptor().toString()), false, source, true));
            }
        }
        return params;
    }

    private List<String> extractControllerServices(ClassInfo controller) {
        List<String> services = new ArrayList<>();
        for (FieldInfo field : controller.getFieldInfo()) {
            String name = field.getTypeDescriptor().toString();
            if (name.endsWith("Service")) {
                services.add(field.getTypeSignatureOrTypeDescriptor().toString());
            }
        }
        return services;
    }

    private String normalizePath(String base, String method) {
        String combined = (base + "/" + method).replace("//", "/");
        if (!combined.startsWith("/")) {
            combined = "/" + combined;
        }
        if (combined.endsWith("/") && combined.length() > 1) {
            combined = combined.substring(0, combined.length() - 1);
        }
        return combined;
    }

    private String stringify(Object value) {
        if (value instanceof Object[] arr) {
            return Arrays.stream(arr).map(Object::toString).collect(Collectors.joining(","));
        }
        return value.toString().replace("\"", "");
    }

    private String packageOf(String name) {
        int idx = name.lastIndexOf('.');
        return idx == -1 ? "" : name.substring(0, idx);
    }

    private record MappingInfo(String httpMethod, String path) {}
}
