package ai.migrator.transform;

import ai.migrator.model.FastApiSpec;
import ai.migrator.model.MigrationSpec;

import java.nio.file.Path;

public interface AiTransformService {
    FastApiSpec transform(MigrationSpec spec, int maxChunkSize, Path cacheDir);
}
