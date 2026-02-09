package ai.migrator;

import ai.migrator.config.MigrationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MigrationProperties.class)
public class SpringAiMigratorApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringAiMigratorApplication.class, args);
    }
}
