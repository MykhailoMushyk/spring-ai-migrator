package ai.migrator.generation;

import ai.migrator.model.*;
import ai.migrator.transform.DeterministicTransformService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FastApiGenerator {

    private final DeterministicTransformService typeMapper;

    public FastApiGenerator(DeterministicTransformService typeMapper) {
        this.typeMapper = typeMapper;
    }

    public void generateModule(Path outputDir, MigrationSpec migrationSpec, boolean multiModule) throws IOException {
        Path appDir = outputDir.resolve("app");
        Files.createDirectories(appDir);
        writeInit(appDir);

        Path baseDir = moduleBaseDir(appDir, migrationSpec.moduleName(), multiModule);
        Files.createDirectories(baseDir);
        writeInit(baseDir);

        Path modelsDir = baseDir.resolve("models");
        Path routersDir = baseDir.resolve("routers");
        Path servicesDir = baseDir.resolve("services");
        Path reposDir = baseDir.resolve("repositories");
        Path coreDir = baseDir.resolve("core");

        Files.createDirectories(modelsDir);
        Files.createDirectories(routersDir);
        Files.createDirectories(servicesDir);
        Files.createDirectories(reposDir);
        Files.createDirectories(coreDir);

        writeInit(modelsDir);
        writeInit(routersDir);
        writeInit(servicesDir);
        writeInit(reposDir);
        writeInit(coreDir);

        writeModels(modelsDir, migrationSpec);
        writeServices(servicesDir, migrationSpec);
        writeRepositories(reposDir, migrationSpec);
        writeCore(coreDir, migrationSpec, multiModule);
        writeRouters(routersDir, migrationSpec, multiModule);

        if (!multiModule) {
            writeMain(appDir, migrationSpec);
            writeRootArtifacts(outputDir, migrationSpec.projectName());
        }
    }

    public void generateRoot(Path outputDir, List<MigrationSpec> modules, boolean multiModule) throws IOException {
        if (!multiModule) {
            return;
        }
        Path appDir = outputDir.resolve("app");
        Files.createDirectories(appDir);
        writeInit(appDir);
        Path modulesDir = appDir.resolve("modules");
        Files.createDirectories(modulesDir);
        writeInit(modulesDir);

        Files.writeString(appDir.resolve("main.py"), renderRootMain(modules));
        writeRootArtifacts(outputDir, modules.isEmpty() ? "app" : modules.getFirst().projectName());
    }

    private Path moduleBaseDir(Path appDir, String moduleName, boolean multiModule) throws IOException {
        if (!multiModule || moduleName == null || moduleName.isBlank()) {
            return appDir;
        }
        Path modulesDir = appDir.resolve("modules");
        Files.createDirectories(modulesDir);
        writeInit(modulesDir);
        Path moduleDir = modulesDir.resolve(modulePackageName(moduleName));
        Files.createDirectories(moduleDir);
        writeInit(moduleDir);
        return moduleDir;
    }

    private void writeRootArtifacts(Path outputDir, String projectName) throws IOException {
        Files.writeString(outputDir.resolve("requirements.txt"), "fastapi\npydantic\npydantic-settings\nuvicorn\n");
        Files.writeString(outputDir.resolve("README.md"), renderReadme(projectName));
    }

    private void writeInit(Path dir) throws IOException {
        Files.writeString(dir.resolve("__init__.py"), "");
    }

    private void writeModels(Path modelsDir, MigrationSpec spec) throws IOException {
        List<DtoSpec> dtos = spec.dtos();
        List<String> exports = new ArrayList<>();

        for (DtoSpec dto : dtos) {
            String fileName = toSnake(dto.name()) + ".py";
            Files.writeString(modelsDir.resolve(fileName), renderModel(dto));
            exports.add("from ." + toSnake(dto.name()) + " import " + dto.name());
        }

        StringBuilder init = new StringBuilder();
        for (String line : exports) {
            init.append(line).append("\n");
        }
        if (!exports.isEmpty()) {
            init.append("\n__all__ = [\n");
            for (DtoSpec dto : dtos) {
                init.append("    \"").append(dto.name()).append("\",\n");
            }
            init.append("]\n");
        }

        Files.writeString(modelsDir.resolve("__init__.py"), init.toString());
    }

    private String renderModel(DtoSpec dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("from __future__ import annotations\n\n");
        sb.append("from pydantic import BaseModel, Field\n");

        boolean needsList = dto.fields().stream().anyMatch(f -> f.type().collection());
        boolean needsOptional = dto.fields().stream().anyMatch(FieldSpec::optional);
        boolean needsEmail = dto.fields().stream().anyMatch(f -> Boolean.TRUE.equals(f.validation().email()));
        boolean needsConfig = dto.fields().stream().anyMatch(f -> f.jsonAlias() != null && !f.jsonAlias().isBlank());

        List<String> typing = new ArrayList<>();
        if (needsList) typing.add("List");
        if (needsOptional) typing.add("Optional");
        if (!typing.isEmpty()) {
            sb.append("from typing import ").append(String.join(", ", typing)).append("\n");
        }
        if (needsEmail) {
            sb.append("from pydantic import EmailStr\n");
        }
        if (needsConfig) {
            sb.append("from pydantic import ConfigDict\n");
        }
        sb.append("\n");

        sb.append("class ").append(dto.name()).append("(BaseModel):\n");
        if (needsConfig) {
            sb.append("    model_config = ConfigDict(populate_by_name=True)\n\n");
        }

        if (dto.fields().isEmpty()) {
            sb.append("    pass\n");
            return sb.toString();
        }

        for (FieldSpec field : dto.fields()) {
            String type = resolveFieldType(field);
            String fieldLine = renderFieldLine(field, type);
            sb.append(fieldLine).append("\n");
        }

        return sb.toString();
    }

    private String resolveFieldType(FieldSpec field) {
        if (Boolean.TRUE.equals(field.validation().email())) {
            return "EmailStr";
        }
        return typeMapper.mapJavaType(field.type());
    }

    private String renderFieldLine(FieldSpec field, String type) {
        StringBuilder sb = new StringBuilder();
        String name = field.name();
        boolean optional = field.optional();

        String annotatedType = optional ? "Optional[" + type + "]" : type;
        String defaultValue = optional ? "None" : "...";

        List<String> args = new ArrayList<>();
        ValidationSpec v = field.validation();
        if (v != null) {
            Integer minLength = v.minLength();
            Integer maxLength = v.maxLength();
            if (minLength != null) args.add("min_length=" + minLength);
            if (maxLength != null) args.add("max_length=" + maxLength);

            Double gt = v.gt();
            Double ge = v.ge();
            Double lt = v.lt();
            Double le = v.le();
            Long min = v.min();
            Long max = v.max();

            if (gt != null) {
                args.add("gt=" + trimDouble(gt));
            } else if (ge != null) {
                args.add("ge=" + trimDouble(ge));
            } else if (min != null) {
                args.add("ge=" + min);
            }

            if (lt != null) {
                args.add("lt=" + trimDouble(lt));
            } else if (le != null) {
                args.add("le=" + trimDouble(le));
            } else if (max != null) {
                args.add("le=" + max);
            }

            if (v.pattern() != null && !v.pattern().isBlank()) {
                args.add("pattern=r\"" + v.pattern().replace("\"", "\\\"") + "\"");
            }
        }

        if (field.jsonAlias() != null && !field.jsonAlias().isBlank()) {
            args.add("alias=\"" + field.jsonAlias().replace("\"", "\\\"") + "\"");
        }

        if (!args.isEmpty()) {
            sb.append("    ").append(name).append(": ").append(annotatedType)
                .append(" = Field(").append(defaultValue).append(", ")
                .append(String.join(", ", args)).append(")");
        } else {
            sb.append("    ").append(name).append(": ").append(annotatedType);
            if (optional) {
                sb.append(" = None");
            }
        }

        return sb.toString();
    }

    private String trimDouble(Double value) {
        if (value == null) {
            return "0";
        }
        if (value == Math.floor(value)) {
            return String.valueOf(value.longValue());
        }
        return value.toString();
    }

    private void writeServices(Path servicesDir, MigrationSpec spec) throws IOException {
        for (ServiceSpec service : spec.services()) {
            String fileName = toSnake(service.name()) + ".py";
            Files.writeString(servicesDir.resolve(fileName), renderService(service));
        }
    }

    private String renderService(ServiceSpec service) {
        StringBuilder sb = new StringBuilder();
        sb.append("from __future__ import annotations\n\n");
        sb.append("from typing import Any\n\n");
        sb.append("class ").append(service.name()).append(":\n");
        sb.append("    def __init__(self, **deps: Any) -> None:\n");
        sb.append("        self._deps = deps\n\n");

        if (service.methods().isEmpty()) {
            sb.append("    pass\n");
            return sb.toString();
        }

        for (MethodSpec method : service.methods()) {
            sb.append("    def ").append(toSnake(method.name())).append("(self");
            for (MethodParamSpec param : method.params()) {
                String type = typeMapper.mapJavaType(param.type());
                sb.append(", ").append(toSnake(param.name())).append(": ").append(type);
            }
            sb.append(") -> Any:\n");
            sb.append("        raise NotImplementedError(\"Service method not implemented\")\n\n");
        }

        return sb.toString();
    }

    private void writeRepositories(Path reposDir, MigrationSpec spec) throws IOException {
        for (RepositorySpec repository : spec.repositories()) {
            String fileName = toSnake(repository.name()) + ".py";
            Files.writeString(reposDir.resolve(fileName), renderRepository(repository));
        }
    }

    private String renderRepository(RepositorySpec repository) {
        return "from __future__ import annotations\n\n" +
            "class " + repository.name() + ":\n" +
            "    pass\n";
    }

    private void writeCore(Path coreDir, MigrationSpec spec, boolean multiModule) throws IOException {
        Files.writeString(coreDir.resolve("settings.py"), renderSettings(spec));
        Files.writeString(coreDir.resolve("container.py"), renderContainer(spec, multiModule));
        Files.writeString(coreDir.resolve("dependencies.py"), renderDependencies(spec, multiModule));
        Files.writeString(coreDir.resolve("exceptions.py"), renderExceptions());
        Files.writeString(coreDir.resolve("handlers.py"), renderHandlers());
    }

    private String renderSettings(MigrationSpec spec) {
        return "from pydantic_settings import BaseSettings, SettingsConfigDict\n\n" +
            "class Settings(BaseSettings):\n" +
            "    app_name: str = \"" + spec.projectName() + "\"\n\n" +
            "    model_config = SettingsConfigDict(env_prefix=\"APP_\")\n";
    }

    private String renderContainer(MigrationSpec spec, boolean multiModule) {
        String prefix = modulePrefix(spec.moduleName(), multiModule);
        StringBuilder sb = new StringBuilder();
        sb.append("from __future__ import annotations\n\n");
        sb.append("from .settings import Settings\n");
        if (!spec.services().isEmpty()) {
            for (ServiceSpec service : spec.services()) {
                sb.append("from ").append(prefix).append(".services.")
                    .append(toSnake(service.name())).append(" import ")
                    .append(service.name()).append("\n");
            }
        }
        if (!spec.repositories().isEmpty()) {
            for (RepositorySpec repo : spec.repositories()) {
                sb.append("from ").append(prefix).append(".repositories.")
                    .append(toSnake(repo.name())).append(" import ")
                    .append(repo.name()).append("\n");
            }
        }
        sb.append("\n");
        sb.append("class Container:\n");
        sb.append("    def __init__(self) -> None:\n");
        sb.append("        self.settings = Settings()\n");
        for (RepositorySpec repo : spec.repositories()) {
            String var = toSnake(repo.name());
            sb.append("        self.").append(var).append(" = ").append(repo.name()).append("()\n");
        }
        for (ServiceSpec service : spec.services()) {
            String var = toSnake(service.name());
            sb.append("        self.").append(var).append(" = ").append(service.name()).append("()\n");
        }
        sb.append("\ncontainer = Container()\n");
        return sb.toString();
    }

    private String renderDependencies(MigrationSpec spec, boolean multiModule) {
        String prefix = modulePrefix(spec.moduleName(), multiModule);
        StringBuilder sb = new StringBuilder();
        sb.append("from __future__ import annotations\n\n");
        sb.append("from .container import container\n");
        for (ServiceSpec service : spec.services()) {
            sb.append("from ").append(prefix).append(".services.")
                .append(toSnake(service.name())).append(" import ")
                .append(service.name()).append("\n");
        }
        sb.append("\n");
        for (ServiceSpec service : spec.services()) {
            String var = toSnake(service.name());
            sb.append("def get_").append(var).append("() -> ").append(service.name()).append(":\n");
            sb.append("    return container.").append(var).append("\n\n");
        }
        if (spec.services().isEmpty()) {
            sb.append("# No services detected\n");
        }
        return sb.toString();
    }

    private String renderExceptions() {
        return "class BadRequestException(Exception):\n" +
            "    pass\n\n" +
            "class NotFoundException(Exception):\n" +
            "    pass\n\n" +
            "class AppException(Exception):\n" +
            "    pass\n";
    }

    private String renderHandlers() {
        return "from __future__ import annotations\n\n" +
            "from datetime import datetime\n" +
            "from fastapi import Request\n" +
            "from fastapi.responses import JSONResponse\n" +
            "from .exceptions import BadRequestException, NotFoundException\n\n" +
            "def _build_payload(status: int, message: str) -> dict:\n" +
            "    return {\"timestamp\": datetime.utcnow().isoformat(), \"status\": status, \"message\": message}\n\n" +
            "def add_exception_handlers(app):\n" +
            "    app.add_exception_handler(NotFoundException, _not_found)\n" +
            "    app.add_exception_handler(BadRequestException, _bad_request)\n\n" +
            "async def _not_found(request: Request, exc: NotFoundException):\n" +
            "    return JSONResponse(status_code=404, content=_build_payload(404, str(exc)))\n\n" +
            "async def _bad_request(request: Request, exc: BadRequestException):\n" +
            "    return JSONResponse(status_code=400, content=_build_payload(400, str(exc)))\n";
    }

    private void writeRouters(Path routersDir, MigrationSpec spec, boolean multiModule) throws IOException {
        Map<String, List<EndpointSpec>> grouped = spec.endpoints().stream()
            .collect(Collectors.groupingBy(EndpointSpec::controllerClass, LinkedHashMap::new, Collectors.toList()));

        Set<String> dtoNames = spec.dtos().stream().map(DtoSpec::name).collect(Collectors.toSet());

        for (Map.Entry<String, List<EndpointSpec>> entry : grouped.entrySet()) {
            String controllerClass = entry.getKey();
            List<EndpointSpec> endpoints = entry.getValue();
            String controllerSimple = simpleName(controllerClass);
            String fileName = toSnake(controllerSimple) + ".py";
            Files.writeString(routersDir.resolve(fileName), renderRouter(endpoints, dtoNames, spec.moduleName(), multiModule));
        }
    }

    private String renderRouter(List<EndpointSpec> endpoints, Set<String> dtoNames, String moduleName, boolean multiModule) {
        StringBuilder sb = new StringBuilder();
        sb.append("from __future__ import annotations\n\n");
        sb.append("from fastapi import APIRouter, Body, Depends, Header, HTTPException, Path, Query\n");
        boolean needsList = needsListTypes(endpoints);
        sb.append("from typing import Optional");
        if (needsList) {
            sb.append(", List");
        }
        sb.append("\n");

        String prefix = modulePrefix(moduleName, multiModule);

        Set<String> modelImports = new LinkedHashSet<>();
        Set<String> dependencyImports = new LinkedHashSet<>();
        Set<String> serviceImports = new LinkedHashSet<>();

        for (EndpointSpec endpoint : endpoints) {
            String reqModel = resolveModelName(endpoint.requestBody(), dtoNames);
            if (reqModel != null) {
                modelImports.add(reqModel);
            }
            String respModel = resolveModelName(endpoint.responseBody(), dtoNames);
            if (respModel != null) {
                modelImports.add(respModel);
            }
            for (String service : endpoint.controllerServices()) {
                String simple = simpleName(service);
                serviceImports.add("from " + prefix + ".services." + toSnake(simple) + " import " + simple);
                dependencyImports.add("from " + prefix + ".core.dependencies import get_" + toSnake(simple));
            }
        }

        if (!modelImports.isEmpty()) {
            sb.append("from ").append(prefix).append(".models import ").append(String.join(", ", modelImports)).append("\n");
        }
        if (!dependencyImports.isEmpty()) {
            for (String dep : dependencyImports) {
                sb.append(dep).append("\n");
            }
        }
        if (!serviceImports.isEmpty()) {
            for (String imp : serviceImports) {
                sb.append(imp).append("\n");
            }
        }
        sb.append("\n");

        String controllerPrefix = endpoints.getFirst().controllerPath();
        if (controllerPrefix == null) {
            controllerPrefix = "";
        }
        if (!controllerPrefix.isBlank()) {
            sb.append("router = APIRouter(prefix=\"").append(controllerPrefix).append("\")\n\n");
        } else {
            sb.append("router = APIRouter()\n\n");
        }

        for (EndpointSpec endpoint : endpoints) {
            String routePath = endpoint.methodPath();
            if (routePath == null || routePath.isBlank()) {
                routePath = "/";
            } else if (!routePath.startsWith("/")) {
                routePath = "/" + routePath;
            }

            String decorator = "@router." + endpoint.httpMethod().toLowerCase(Locale.ROOT) + "(\"" + routePath + "\"";
            String responseModel = resolveModelName(endpoint.responseBody(), dtoNames);
            if (responseModel != null) {
                decorator += ", response_model=" + responseModel;
            } else if (endpoint.responseBody() != null) {
                decorator += ", response_model=" + typeMapper.mapJavaType(endpoint.responseBody());
            }
            if (endpoint.statusCode() != null) {
                decorator += ", status_code=" + endpoint.statusCode();
            }
            decorator += ")\n";
            sb.append(decorator);

            List<String> params = new ArrayList<>();
            for (ParameterSpec param : endpoint.pathParams()) {
                String type = typeMapper.mapJavaType(param.type());
                String pyName = toPythonIdentifier(param.name());
                params.add(pyName + ": " + type + " = Path(...)");
            }
            for (ParameterSpec param : endpoint.queryParams()) {
                String type = typeMapper.mapJavaType(param.type());
                String pyName = toPythonIdentifier(param.name());
                String defaultExpr = param.requiredEffective() ? "..." : "None";
                String annotatedType = param.requiredEffective() ? type : ("Optional[" + type + "]");
                if (!pyName.equals(param.name())) {
                    params.add(pyName + ": " + annotatedType + " = Query(" + defaultExpr + ", alias=\"" + param.name() + "\")");
                } else {
                    params.add(pyName + ": " + annotatedType + " = Query(" + defaultExpr + ")");
                }
            }
            for (ParameterSpec param : endpoint.headerParams()) {
                String type = typeMapper.mapJavaType(param.type());
                String pyName = toPythonIdentifier(param.name());
                String defaultExpr = param.requiredEffective() ? "..." : "None";
                String annotatedType = param.requiredEffective() ? type : ("Optional[" + type + "]");
                if (!pyName.equals(param.name())) {
                    params.add(pyName + ": " + annotatedType + " = Header(" + defaultExpr + ", alias=\"" + param.name() + "\")");
                } else {
                    params.add(pyName + ": " + annotatedType + " = Header(" + defaultExpr + ")");
                }
            }

            String reqModel = resolveModelName(endpoint.requestBody(), dtoNames);
            if (reqModel != null) {
                params.add("payload: " + reqModel + " = Body(...)");
            } else if (endpoint.requestBody() != null) {
                params.add("payload: " + typeMapper.mapJavaType(endpoint.requestBody()) + " = Body(...)");
            }

            for (String service : endpoint.controllerServices()) {
                String simple = simpleName(service);
                String var = toSnake(simple);
                params.add(var + ": " + simple + " = Depends(get_" + var + ")");
            }

            String signature = String.join(", ", params);
            sb.append("def ").append(toSnake(endpoint.methodName())).append("(").append(signature).append("):\n");
            sb.append("    raise HTTPException(status_code=501, detail=\"Not implemented\")\n\n");
        }

        return sb.toString();
    }

    private void writeMain(Path appDir, MigrationSpec spec) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("from fastapi import FastAPI\n");
        sb.append("from app.core.handlers import add_exception_handlers\n");

        Set<String> routerImports = new LinkedHashSet<>();
        for (EndpointSpec endpoint : spec.endpoints()) {
            String controller = simpleName(endpoint.controllerClass());
            routerImports.add("from app.routers." + toSnake(controller) + " import router as " + toSnake(controller) + "_router");
        }
        for (String imp : routerImports) {
            sb.append(imp).append("\n");
        }
        sb.append("\napp = FastAPI(title=\"").append(spec.projectName()).append("\")\n");
        sb.append("add_exception_handlers(app)\n\n");

        for (String imp : routerImports) {
            String routerVar = imp.substring(imp.lastIndexOf(" as ") + 4);
            sb.append("app.include_router(").append(routerVar).append(")\n");
        }

        Files.writeString(appDir.resolve("main.py"), sb.toString());
    }

    private String renderRootMain(List<MigrationSpec> modules) {
        StringBuilder sb = new StringBuilder();
        sb.append("from fastapi import FastAPI\n\n");

        Set<String> routerImports = new LinkedHashSet<>();
        Set<String> routerVars = new LinkedHashSet<>();
        Set<String> handlerImports = new LinkedHashSet<>();
        Set<String> handlerVars = new LinkedHashSet<>();

        for (MigrationSpec spec : modules) {
            String moduleName = modulePackageName(spec.moduleName());
            String handlerVar = moduleName + "_add_handlers";
            handlerImports.add("from app.modules." + moduleName + ".core.handlers import add_exception_handlers as " + handlerVar);
            handlerVars.add(handlerVar);
            for (EndpointSpec endpoint : spec.endpoints()) {
                String controller = simpleName(endpoint.controllerClass());
                String routerVar = moduleName + "_" + toSnake(controller) + "_router";
                routerImports.add("from app.modules." + moduleName + ".routers." + toSnake(controller)
                    + " import router as " + routerVar);
                routerVars.add(routerVar);
            }
        }

        for (String imp : handlerImports) {
            sb.append(imp).append("\n");
        }
        for (String imp : routerImports) {
            sb.append(imp).append("\n");
        }

        sb.append("\napp = FastAPI(title=\"").append(modules.isEmpty() ? "app" : modules.getFirst().projectName()).append("\")\n\n");
        for (String handler : handlerVars) {
            sb.append(handler).append("(app)\n");
        }
        sb.append("\n");
        for (String router : routerVars) {
            sb.append("app.include_router(").append(router).append(")\n");
        }

        return sb.toString();
    }

    private String modulePackageName(String moduleName) {
        if (moduleName == null || moduleName.isBlank()) {
            return "module";
        }
        String snake = toSnake(moduleName);
        String py = toPythonIdentifier(snake);
        return py.toLowerCase(Locale.ROOT);
    }

    private boolean needsListTypes(List<EndpointSpec> endpoints) {
        for (EndpointSpec endpoint : endpoints) {
            if (endpoint.requestBody() != null && typeMapper.mapJavaType(endpoint.requestBody()).contains("List[")) {
                return true;
            }
            if (endpoint.responseBody() != null && typeMapper.mapJavaType(endpoint.responseBody()).contains("List[")) {
                return true;
            }
            for (ParameterSpec param : endpoint.queryParams()) {
                if (typeMapper.mapJavaType(param.type()).contains("List[")) {
                    return true;
                }
            }
            for (ParameterSpec param : endpoint.pathParams()) {
                if (typeMapper.mapJavaType(param.type()).contains("List[")) {
                    return true;
                }
            }
            for (ParameterSpec param : endpoint.headerParams()) {
                if (typeMapper.mapJavaType(param.type()).contains("List[")) {
                    return true;
                }
            }
        }
        return false;
    }

    private String renderReadme(String projectName) {
        return "# " + projectName + " FastAPI\n\n" +
            "Generated by spring-ai-migrator.\n\n" +
            "Run:\n\n" +
            "```bash\n" +
            "pip install -r requirements.txt\n" +
            "uvicorn app.main:app --reload\n" +
            "```\n";
    }

    private String resolveModelName(TypeRef typeRef, Set<String> dtoNames) {
        if (typeRef == null) {
            return null;
        }
        String name = simpleName(typeRef.name());
        return dtoNames.contains(name) ? name : null;
    }

    private String modulePrefix(String moduleName, boolean multiModule) {
        if (!multiModule) {
            return "app";
        }
        if (moduleName == null || moduleName.isBlank()) {
            return "app";
        }
        return "app.modules." + modulePackageName(moduleName);
    }

    private String simpleName(String typeName) {
        if (typeName == null) {
            return null;
        }
        int idx = typeName.lastIndexOf('.');
        return idx == -1 ? typeName : typeName.substring(idx + 1);
    }

    private String toSnake(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        char[] chars = name.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString().replaceAll("__", "_");
    }

    private String toPythonIdentifier(String name) {
        if (name == null || name.isBlank()) {
            return "param";
        }
        String cleaned = name.replace('-', '_');
        if (!Character.isJavaIdentifierStart(cleaned.charAt(0))) {
            cleaned = "param_" + cleaned;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (Character.isJavaIdentifierPart(c)) {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }
}
