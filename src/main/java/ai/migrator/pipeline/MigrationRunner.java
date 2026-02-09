package ai.migrator.pipeline;

import ai.migrator.config.MigrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class MigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final MigrationProperties properties;
    private final MigrationPipeline pipeline;

    public MigrationRunner(MigrationProperties properties, MigrationPipeline pipeline) {
        this.properties = properties;
        this.pipeline = pipeline;
    }

    @Override
    public void run(String... args) {
        if (properties.getInput() == null || properties.getOutput() == null) {
            log.info("Missing migrator.input or migrator.output. Example:");
            log.info("--migrator.input=/path/to/spring --migrator.output=/path/to/out");
            return;
        }
        pipeline.run(properties);
    }
}
