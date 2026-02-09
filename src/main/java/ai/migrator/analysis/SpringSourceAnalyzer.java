package ai.migrator.analysis;

import ai.migrator.model.*;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SpringSourceAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(SpringSourceAnalyzer.class);

    private final JavaParser parser;

    public SpringSourceAnalyzer() {
        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        this.parser = new JavaParser(config);
    }

    public AnalysisResult analyze(ProjectLayout layout, boolean includeTests) throws IOException {
        AnalysisResult result = new AnalysisResult();
        if (layout.sourceDir() == null) {
            return result;
        }

        List<Path> roots = new ArrayList<>();
        roots.add(layout.sourceDir());
        if (includeTests && layout.testSourceDir() != null) {
            roots.add(layout.testSourceDir());
        }

        for (Path root : roots) {
            try (var paths = Files.walk(root)) {
                paths.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> parseFile(p, result));
            }
        }

        return result;
    }

    private void parseFile(Path path, AnalysisResult result) {
        try {
            Optional<CompilationUnit> maybeCu = parser.parse(path).getResult();
            if (maybeCu.isEmpty()) {
                return;
            }
            CompilationUnit cu = maybeCu.get();

            for (ClassOrInterfaceDeclaration decl : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                if (isController(decl)) {
                    parseController(decl, result);
                }
                if (isDto(decl)) {
                    parseDtoClass(decl, result);
                }
                if (isService(decl)) {
                    parseService(decl, result);
                }
                if (isRepository(decl)) {
                    parseRepository(decl, result);
                }
            }

            for (RecordDeclaration decl : cu.findAll(RecordDeclaration.class)) {
                if (isDto(decl)) {
                    parseDtoRecord(decl, result);
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to parse {}", path, ex);
        }
    }

    private boolean isController(NodeWithAnnotations<?> decl) {
        return hasAnnotation(decl, "RestController") || hasAnnotation(decl, "Controller");
    }

    private boolean isDto(NodeWithAnnotations<?> decl) {
        return decl.getAnnotations().stream()
            .map(a -> a.getName().getIdentifier())
            .anyMatch(name -> name.equals("Data") || name.equals("Value"))
            || decl.toString().contains("dto");
    }

    private boolean isService(ClassOrInterfaceDeclaration decl) {
        if (hasAnnotation(decl, "Service") || hasAnnotation(decl, "Component")) {
            return true;
        }
        if (decl.getNameAsString().endsWith("Service")) {
            return true;
        }
        return decl.getFullyQualifiedName().orElse("").contains(".service.");
    }

    private boolean isRepository(ClassOrInterfaceDeclaration decl) {
        if (hasAnnotation(decl, "Repository")) {
            return true;
        }
        if (decl.getNameAsString().endsWith("Repository")) {
            return true;
        }
        if (decl.getFullyQualifiedName().orElse("").contains(".repository.")) {
            return true;
        }
        if (decl.getExtendedTypes().stream().anyMatch(this::isJpaRepository)) {
            return true;
        }
        return decl.getImplementedTypes().stream().anyMatch(this::isJpaRepository);
    }

    private boolean isJpaRepository(ClassOrInterfaceType type) {
        String name = type.getNameAsString();
        return name.equals("JpaRepository") || name.equals("CrudRepository") || name.equals("PagingAndSortingRepository");
    }

    private void parseController(ClassOrInterfaceDeclaration decl, AnalysisResult result) {
        String basePath = extractRequestMappingPath(decl.getAnnotations());
        String controllerName = decl.getFullyQualifiedName().orElse(decl.getNameAsString());
        List<String> services = extractControllerServices(decl);

        for (MethodDeclaration method : decl.getMethods()) {
            Optional<MappingInfo> mapping = extractMapping(method);
            if (mapping.isEmpty()) {
                continue;
            }
            MappingInfo info = mapping.get();
            String path = normalizePath(basePath, info.path());

            EndpointSpec endpoint = EndpointSpec.builder()
                .id(controllerName + "#" + method.getNameAsString() + "::" + info.httpMethod())
                .controllerClass(controllerName)
                .controllerPath(basePath)
                .methodName(method.getNameAsString())
                .methodPath(info.path())
                .httpMethod(info.httpMethod())
                .path(path)
                .statusCode(detectStatus(method))
                .requestBody(extractRequestBody(method))
                .responseBody(extractResponseBody(method))
                .queryParams(extractParams(method, ParamSource.QUERY))
                .pathParams(extractParams(method, ParamSource.PATH))
                .headerParams(extractParams(method, ParamSource.HEADER))
                .controllerServices(services)
                .build();

            result.addEndpoint(endpoint);
        }
    }

    private void parseDtoRecord(RecordDeclaration decl, AnalysisResult result) {
        String packageName = decl.findCompilationUnit()
            .flatMap(CompilationUnit::getPackageDeclaration)
            .map(pd -> pd.getName().toString())
            .orElse("");

        List<FieldSpec> fields = decl.getParameters().stream()
            .map(p -> buildFieldSpec(p.getNameAsString(), p.getType(), p))
            .collect(Collectors.toList());

        DtoSpec dto = new DtoSpec(decl.getNameAsString(), packageName, fields, true);
        result.addDto(dto);
    }

    private void parseDtoClass(ClassOrInterfaceDeclaration decl, AnalysisResult result) {
        String packageName = decl.findCompilationUnit()
            .flatMap(CompilationUnit::getPackageDeclaration)
            .map(pd -> pd.getName().toString())
            .orElse("");

        List<FieldSpec> fields = new ArrayList<>();
        for (FieldDeclaration field : decl.getFields()) {
            field.getVariables().forEach(var ->
                fields.add(buildFieldSpec(var.getNameAsString(), field.getElementType(), field))
            );
        }

        if (!fields.isEmpty()) {
            DtoSpec dto = new DtoSpec(decl.getNameAsString(), packageName, fields, false);
            result.addDto(dto);
        }
    }

    private void parseService(ClassOrInterfaceDeclaration decl, AnalysisResult result) {
        String packageName = decl.findCompilationUnit()
            .flatMap(CompilationUnit::getPackageDeclaration)
            .map(pd -> pd.getName().toString())
            .orElse("");

        List<MethodSpec> methods = new ArrayList<>();
        for (MethodDeclaration method : decl.getMethods()) {
            List<MethodParamSpec> params = method.getParameters().stream()
                .map(p -> new MethodParamSpec(p.getNameAsString(), toTypeRef(p.getType()), isOptionalType(p.getType())))
                .collect(Collectors.toList());
            methods.add(new MethodSpec(method.getNameAsString(), params, toTypeRef(method.getType())));
        }

        ServiceSpec service = new ServiceSpec(decl.getNameAsString(), packageName, methods);
        result.addService(service);
    }

    private void parseRepository(ClassOrInterfaceDeclaration decl, AnalysisResult result) {
        String packageName = decl.findCompilationUnit()
            .flatMap(CompilationUnit::getPackageDeclaration)
            .map(pd -> pd.getName().toString())
            .orElse("");

        RepositorySpec repository = new RepositorySpec(decl.getNameAsString(), packageName);
        result.addRepository(repository);
    }

    private Optional<MappingInfo> extractMapping(MethodDeclaration method) {
        for (AnnotationExpr ann : method.getAnnotations()) {
            String name = ann.getName().getIdentifier();
            switch (name) {
                case "GetMapping" -> {
                    return Optional.of(new MappingInfo("GET", extractPath(ann)));
                }
                case "PostMapping" -> {
                    return Optional.of(new MappingInfo("POST", extractPath(ann)));
                }
                case "PutMapping" -> {
                    return Optional.of(new MappingInfo("PUT", extractPath(ann)));
                }
                case "DeleteMapping" -> {
                    return Optional.of(new MappingInfo("DELETE", extractPath(ann)));
                }
                case "PatchMapping" -> {
                    return Optional.of(new MappingInfo("PATCH", extractPath(ann)));
                }
                case "RequestMapping" -> {
                    String methodName = extractRequestMethod(ann).orElse("GET");
                    return Optional.of(new MappingInfo(methodName, extractPath(ann)));
                }
                default -> {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private String extractRequestMappingPath(List<AnnotationExpr> annotations) {
        for (AnnotationExpr ann : annotations) {
            if (ann.getName().getIdentifier().equals("RequestMapping")) {
                return extractPath(ann);
            }
        }
        return "";
    }

    private String extractPath(AnnotationExpr ann) {
        if (ann.isSingleMemberAnnotationExpr()) {
            Expression expr = ann.asSingleMemberAnnotationExpr().getMemberValue();
            return stringValue(expr);
        }
        if (ann.isNormalAnnotationExpr()) {
            for (MemberValuePair pair : ann.asNormalAnnotationExpr().getPairs()) {
                if (pair.getNameAsString().equals("value") || pair.getNameAsString().equals("path")) {
                    return stringValue(pair.getValue());
                }
            }
        }
        return "";
    }

    private Optional<String> extractRequestMethod(AnnotationExpr ann) {
        if (!ann.isNormalAnnotationExpr()) {
            return Optional.empty();
        }
        for (MemberValuePair pair : ann.asNormalAnnotationExpr().getPairs()) {
            if (!pair.getNameAsString().equals("method")) {
                continue;
            }
            String text = pair.getValue().toString();
            if (text.contains("RequestMethod.")) {
                return Optional.of(text.substring(text.indexOf("RequestMethod.") + "RequestMethod.".length()));
            }
        }
        return Optional.empty();
    }

    private Integer detectStatus(MethodDeclaration method) {
        if (hasAnnotation(method, "ResponseStatus")) {
            String raw = method.getAnnotationByName("ResponseStatus").get().toString();
            if (raw.contains("CREATED")) {
                return 201;
            }
            if (raw.contains("NO_CONTENT")) {
                return 204;
            }
            if (raw.contains("ACCEPTED")) {
                return 202;
            }
        }
        Optional<BlockStmt> body = method.getBody();
        if (body.isPresent()) {
            String bodyText = body.get().toString();
            if (bodyText.contains("HttpStatus.CREATED")) {
                return 201;
            }
            if (bodyText.contains("HttpStatus.NO_CONTENT")) {
                return 204;
            }
            if (bodyText.contains("HttpStatus.ACCEPTED")) {
                return 202;
            }
        }
        return 200;
    }

    private TypeRef extractRequestBody(MethodDeclaration method) {
        return method.getParameters().stream()
            .filter(p -> hasAnnotation(p, "RequestBody"))
            .findFirst()
            .map(p -> toTypeRef(p.getType()))
            .orElse(null);
    }

    private TypeRef extractResponseBody(MethodDeclaration method) {
        Type type = method.getType();
        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType ct = type.asClassOrInterfaceType();
            if (ct.getNameAsString().equals("ResponseEntity") && ct.getTypeArguments().isPresent()) {
                Type inner = ct.getTypeArguments().get().get(0);
                return toTypeRef(inner);
            }
        }
        return toTypeRef(type);
    }

    private List<ParameterSpec> extractParams(MethodDeclaration method, ParamSource source) {
        List<ParameterSpec> params = new ArrayList<>();
        method.getParameters().forEach(p -> {
            if (source == ParamSource.QUERY && hasAnnotation(p, "RequestParam")) {
                params.add(buildParam(p, source));
            }
            if (source == ParamSource.PATH && hasAnnotation(p, "PathVariable")) {
                params.add(buildParam(p, source));
            }
            if (source == ParamSource.HEADER && hasAnnotation(p, "RequestHeader")) {
                params.add(buildParam(p, source));
            }
        });
        return params;
    }

    private ParameterSpec buildParam(com.github.javaparser.ast.body.Parameter p, ParamSource source) {
        String name = p.getNameAsString();
        boolean required = source == ParamSource.PATH;

        for (AnnotationExpr ann : p.getAnnotations()) {
            if (ann.isNormalAnnotationExpr()) {
                for (MemberValuePair pair : ann.asNormalAnnotationExpr().getPairs()) {
                    if (pair.getNameAsString().equals("name") || pair.getNameAsString().equals("value")) {
                        name = stringValue(pair.getValue());
                    }
                    if (pair.getNameAsString().equals("required")) {
                        required = !pair.getValue().toString().equals("false");
                    }
                    if (pair.getNameAsString().equals("defaultValue")) {
                        required = false;
                    }
                }
            }
            if (ann.isSingleMemberAnnotationExpr()) {
                name = stringValue(ann.asSingleMemberAnnotationExpr().getMemberValue());
            }
        }

        if (isOptionalType(p.getType())) {
            required = false;
        }
        boolean optional = !required;
        return new ParameterSpec(name, toTypeRef(p.getType()), required, source.name().toLowerCase(), optional);
    }

    private FieldSpec buildFieldSpec(String name, Type type, NodeWithAnnotations<?> node) {
        ValidationSpec validation = extractValidation(node.getAnnotations());
        String alias = extractJsonAlias(node.getAnnotations());
        boolean optional = isOptionalType(type);
        if (Boolean.TRUE.equals(validation.notNull()) || Boolean.TRUE.equals(validation.notBlank())) {
            optional = false;
        }
        return new FieldSpec(name, toTypeRef(type), optional, validation, alias);
    }

    private ValidationSpec extractValidation(List<AnnotationExpr> annotations) {
        Integer minLength = null;
        Integer maxLength = null;
        Long min = null;
        Long max = null;
        Double gt = null;
        Double ge = null;
        Double lt = null;
        Double le = null;
        Boolean notBlank = null;
        Boolean notNull = null;
        Boolean email = null;
        String pattern = null;

        for (AnnotationExpr ann : annotations) {
            String name = ann.getName().getIdentifier();
            switch (name) {
                case "NotBlank" -> {
                    notBlank = true;
                    if (minLength == null || minLength < 1) {
                        minLength = 1;
                    }
                }
                case "NotNull" -> notNull = true;
                case "NotEmpty" -> {
                    if (minLength == null || minLength < 1) {
                        minLength = 1;
                    }
                }
                case "Email" -> email = true;
                case "Size" -> {
                    Integer minVal = parseInt(ann, "min");
                    Integer maxVal = parseInt(ann, "max");
                    if (minVal != null) minLength = minVal;
                    if (maxVal != null) maxLength = maxVal;
                }
                case "Min" -> {
                    Long v = parseLong(ann, "value");
                    if (v != null) min = v;
                }
                case "Max" -> {
                    Long v = parseLong(ann, "value");
                    if (v != null) max = v;
                }
                case "DecimalMin" -> {
                    Double v = parseDouble(ann, "value");
                    boolean inclusive = parseBoolean(ann, "inclusive", true);
                    if (v != null) {
                        if (inclusive) {
                            ge = v;
                        } else {
                            gt = v;
                        }
                    }
                }
                case "DecimalMax" -> {
                    Double v = parseDouble(ann, "value");
                    boolean inclusive = parseBoolean(ann, "inclusive", true);
                    if (v != null) {
                        if (inclusive) {
                            le = v;
                        } else {
                            lt = v;
                        }
                    }
                }
                case "Positive" -> gt = 0d;
                case "PositiveOrZero" -> ge = 0d;
                case "Negative" -> lt = 0d;
                case "NegativeOrZero" -> le = 0d;
                case "Pattern" -> {
                    String regex = parseString(ann, "regexp");
                    if (regex == null) {
                        regex = parseString(ann, "value");
                    }
                    pattern = regex;
                }
                default -> {
                }
            }
        }

        return new ValidationSpec(minLength, maxLength, min, max, gt, ge, lt, le, notBlank, notNull, email, pattern);
    }

    private String extractJsonAlias(List<AnnotationExpr> annotations) {
        for (AnnotationExpr ann : annotations) {
            String name = ann.getName().getIdentifier();
            if (name.equals("JsonProperty")) {
                String value = parseString(ann, "value");
                if (value != null) {
                    return value;
                }
            }
            if (name.equals("JsonAlias")) {
                String value = parseString(ann, "value");
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private Integer parseInt(AnnotationExpr ann, String key) {
        String value = parseString(ann, key);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long parseLong(AnnotationExpr ann, String key) {
        String value = parseString(ann, key);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Double parseDouble(AnnotationExpr ann, String key) {
        String value = parseString(ann, key);
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean parseBoolean(AnnotationExpr ann, String key, boolean defaultVal) {
        String value = parseString(ann, key);
        if (value == null) {
            return defaultVal;
        }
        return value.equalsIgnoreCase("true");
    }

    private String parseString(AnnotationExpr ann, String key) {
        if (ann.isSingleMemberAnnotationExpr()) {
            if (key.equals("value")) {
                return stringValue(ann.asSingleMemberAnnotationExpr().getMemberValue());
            }
        }
        if (ann.isNormalAnnotationExpr()) {
            for (MemberValuePair pair : ann.asNormalAnnotationExpr().getPairs()) {
                if (pair.getNameAsString().equals(key)) {
                    return stringValue(pair.getValue());
                }
            }
        }
        return null;
    }

    private List<String> extractControllerServices(ClassOrInterfaceDeclaration decl) {
        List<String> services = new ArrayList<>();
        for (FieldDeclaration field : decl.getFields()) {
            String typeName = field.getElementType().asString();
            if (typeName.endsWith("Service") || typeName.contains("service")) {
                services.add(typeName);
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

    private boolean hasAnnotation(NodeWithAnnotations<?> decl, String name) {
        return decl.getAnnotations().stream().anyMatch(a -> a.getName().getIdentifier().equals(name));
    }

    private boolean isOptionalType(Type type) {
        String text = type.asString();
        return text.startsWith("Optional<") || text.endsWith("?");
    }

    private TypeRef toTypeRef(Type type) {
        String name = type.asString();
        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType ct = type.asClassOrInterfaceType();
            if (ct.getNameAsString().equals("List") && ct.getTypeArguments().isPresent()) {
                Type inner = ct.getTypeArguments().get().get(0);
                return TypeRef.collection("List", inner.asString());
            }
        }
        return TypeRef.simple(name);
    }

    private String stringValue(Expression expr) {
        if (expr.isStringLiteralExpr()) {
            return expr.asStringLiteralExpr().asString();
        }
        if (expr.isNameExpr()) {
            return expr.asNameExpr().getNameAsString();
        }
        return expr.toString().replace("\"", "");
    }

    private record MappingInfo(String httpMethod, String path) {}

    private enum ParamSource { QUERY, PATH, HEADER }
}
