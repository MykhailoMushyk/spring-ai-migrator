package ai.migrator.transform;

import ai.migrator.model.MigrationSpec;
import ai.migrator.util.JsonUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class PromptBuilder {

    public String systemPrompt() {
        return load("prompts/fastapi-transform-system.txt");
    }

    public String userPrompt(MigrationSpec spec) {
        String template = load("prompts/fastapi-transform-user.txt");
        return template.replace("{{spec}}", JsonUtils.toJson(spec));
    }

    private String load(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            try (var in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load prompt: " + path, ex);
        }
    }
}
