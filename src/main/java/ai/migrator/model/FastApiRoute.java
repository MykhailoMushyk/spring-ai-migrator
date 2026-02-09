package ai.migrator.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FastApiRoute(
    String path,
    String method,
    String functionName,
    @JsonAlias({"requestBody"}) String requestModel,
    @JsonAlias({"responseBody"}) String responseModel,
    Integer statusCode,
    List<ParameterSpec> queryParams,
    List<ParameterSpec> pathParams,
    List<ParameterSpec> headerParams
) {}
