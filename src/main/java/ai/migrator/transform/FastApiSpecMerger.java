package ai.migrator.transform;

import ai.migrator.model.FastApiRoute;
import ai.migrator.model.FastApiSpec;
import ai.migrator.model.PydanticModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FastApiSpecMerger {
    public static FastApiSpec merge(List<FastApiSpec> parts) {
        Map<String, PydanticModel> models = new LinkedHashMap<>();
        List<FastApiRoute> routes = new ArrayList<>();

        for (FastApiSpec part : parts) {
            for (PydanticModel model : part.models()) {
                models.putIfAbsent(model.name(), model);
            }
            routes.addAll(part.routes());
        }

        return new FastApiSpec(new ArrayList<>(models.values()), routes);
    }
}
