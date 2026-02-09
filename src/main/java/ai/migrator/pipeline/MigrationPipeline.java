package ai.migrator.pipeline;

import ai.migrator.analysis.AnalysisResult;
import ai.migrator.analysis.ProjectLayout;
import ai.migrator.analysis.SpringBytecodeAnalyzer;
import ai.migrator.analysis.SpringSourceAnalyzer;
import ai.migrator.config.MigrationProperties;
import ai.migrator.generation.FastApiGenerator;
import ai.migrator.model.FastApiSpec;
import ai.migrator.model.MigrationSpec;
import ai.migrator.transform.AiTransformService;
import ai.migrator.transform.DeterministicTransformService;
import ai.migrator.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class MigrationPipeline {

    private static final Logger log = LoggerFactory.getLogger(MigrationPipeline.class);

    private final SpringSourceAnalyzer sourceAnalyzer;
    private final SpringBytecodeAnalyzer bytecodeAnalyzer;
    private final AiTransformService aiTransformService;
    private final DeterministicTransformService deterministicTransformService;
    private final FastApiGenerator generator;

    public MigrationPipeline(SpringSourceAnalyzer sourceAnalyzer,
                             SpringBytecodeAnalyzer bytecodeAnalyzer,
                             AiTransformService aiTransformService,
                             DeterministicTransformService deterministicTransformService,
                             FastApiGenerator generator) {
        this.sourceAnalyzer = sourceAnalyzer;
        this.bytecodeAnalyzer = bytecodeAnalyzer;
        this.aiTransformService = aiTransformService;
        this.deterministicTransformService = deterministicTransformService;
        this.generator = generator;
    }

    public void run(MigrationProperties properties) {
        try {
            boolean useSource = properties.getMode() == MigrationProperties.Mode.SOURCE
                || properties.getMode() == MigrationProperties.Mode.AUTO;
            boolean useBytecode = properties.getMode() == MigrationProperties.Mode.BYTECODE
                || properties.getMode() == MigrationProperties.Mode.AUTO;

            var modules = ProjectLayout.detectModules(properties.getInput(), properties.getModuleSearchDepth());
            if (modules.isEmpty()) {
                log.warn("No modules detected under {}", properties.getInput());
                return;
            }

            boolean multiModule = modules.size() > 1;
            log.info("Detected {} module(s)", modules.size());

            List<MigrationSpec> moduleSpecs = new ArrayList<>();

            for (ProjectLayout layout : modules) {
                AnalysisResult analysis = new AnalysisResult();

                if (useSource && layout.sourceDir() != null) {
                    log.info("Analyzing sources from {}", layout.sourceDir());
                    analysis.merge(sourceAnalyzer.analyze(layout, properties.isIncludeTests()));
                }

                if (useBytecode && layout.classesDir() != null) {
                    log.info("Analyzing bytecode from {}", layout.classesDir());
                    analysis.merge(bytecodeAnalyzer.analyze(layout));
                }

                MigrationSpec spec = MigrationSpec.from(layout, analysis);

                FastApiSpec fastApiSpec = properties.isUseAi()
                    ? aiTransformService.transform(spec, properties.getMaxChunkSize(), properties.getCacheDir())
                    : deterministicTransformService.transform(spec);

                generator.generateModule(properties.getOutput(), spec, multiModule);
                moduleSpecs.add(spec);

                Path metaDir = properties.getOutput().resolve(".migrator").resolve(spec.moduleName());
                Files.createDirectories(metaDir);
                JsonUtils.writeJson(metaDir.resolve("analysis.json"), analysis);
                JsonUtils.writeJson(metaDir.resolve("fastapi-spec.json"), fastApiSpec);
            }

            generator.generateRoot(properties.getOutput(), moduleSpecs, multiModule);

            log.info("Migration completed. Output at {}", properties.getOutput());
        } catch (Exception ex) {
            log.error("Migration failed", ex);
        }
    }
}
