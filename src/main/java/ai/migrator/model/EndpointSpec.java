package ai.migrator.model;

import java.util.ArrayList;
import java.util.List;

public record EndpointSpec(
    String id,
    String controllerClass,
    String controllerPath,
    String methodName,
    String methodPath,
    String httpMethod,
    String path,
    Integer statusCode,
    TypeRef requestBody,
    TypeRef responseBody,
    List<ParameterSpec> queryParams,
    List<ParameterSpec> pathParams,
    List<ParameterSpec> headerParams,
    List<String> controllerServices
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String controllerClass;
        private String controllerPath;
        private String methodName;
        private String methodPath;
        private String httpMethod;
        private String path;
        private Integer statusCode;
        private TypeRef requestBody;
        private TypeRef responseBody;
        private List<ParameterSpec> queryParams = new ArrayList<>();
        private List<ParameterSpec> pathParams = new ArrayList<>();
        private List<ParameterSpec> headerParams = new ArrayList<>();
        private List<String> controllerServices = new ArrayList<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder controllerClass(String controllerClass) {
            this.controllerClass = controllerClass;
            return this;
        }

        public Builder controllerPath(String controllerPath) {
            this.controllerPath = controllerPath;
            return this;
        }

        public Builder methodName(String methodName) {
            this.methodName = methodName;
            return this;
        }

        public Builder methodPath(String methodPath) {
            this.methodPath = methodPath;
            return this;
        }

        public Builder httpMethod(String httpMethod) {
            this.httpMethod = httpMethod;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder statusCode(Integer statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder requestBody(TypeRef requestBody) {
            this.requestBody = requestBody;
            return this;
        }

        public Builder responseBody(TypeRef responseBody) {
            this.responseBody = responseBody;
            return this;
        }

        public Builder queryParams(List<ParameterSpec> queryParams) {
            this.queryParams = queryParams;
            return this;
        }

        public Builder pathParams(List<ParameterSpec> pathParams) {
            this.pathParams = pathParams;
            return this;
        }

        public Builder headerParams(List<ParameterSpec> headerParams) {
            this.headerParams = headerParams;
            return this;
        }

        public Builder controllerServices(List<String> controllerServices) {
            this.controllerServices = controllerServices;
            return this;
        }

        public EndpointSpec build() {
            return new EndpointSpec(id, controllerClass, controllerPath, methodName, methodPath,
                httpMethod, path, statusCode, requestBody, responseBody, queryParams, pathParams,
                headerParams, controllerServices);
        }
    }
}
