package ai.migrator.transform;

import ai.migrator.model.FastApiSpec;
import ai.migrator.model.MigrationSpec;
import ai.migrator.util.Hashing;
import ai.migrator.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class SpringAiTransformService implements AiTransformService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiTransformService.class);

    private final ChatClient chatClient;
    private final PromptBuilder promptBuilder;
    private final DeterministicTransformService fallback;

    public SpringAiTransformService(ChatClient chatClient,
                                   PromptBuilder promptBuilder,
                                   DeterministicTransformService fallback) {
        this.chatClient = chatClient;
        this.promptBuilder = promptBuilder;
        this.fallback = fallback;
    }

    @Override
    public FastApiSpec transform(MigrationSpec spec, int maxChunkSize, Path cacheDir) {
        try {
            Files.createDirectories(cacheDir);
        } catch (Exception ex) {
            log.warn("Could not create cache dir: {}", cacheDir, ex);
        }

        List<FastApiSpec> parts = new ArrayList<>();
        List<MigrationSpec> chunks = MigrationChunker.chunk(spec, maxChunkSize);

        for (MigrationSpec chunk : chunks) {
            try {
                String payload = JsonUtils.toPrettyJson(chunk);
                String hash = Hashing.sha256(payload);
                Path cacheFile = cacheDir.resolve(hash + ".json");

                if (Files.exists(cacheFile)) {
                    parts.add(JsonUtils.readJson(cacheFile, FastApiSpec.class));
                    continue;
                }

                String content = chatClient.prompt()
                    .system(promptBuilder.systemPrompt())
                    .user(promptBuilder.userPrompt(chunk))
                    .call()
                    .content();

                if (content == null || content.isBlank()) {
                    throw new IllegalStateException("LLM returned empty content");
                }

                FastApiSpec specPart = JsonUtils.readJson(content, FastApiSpec.class);
                JsonUtils.writeJson(cacheFile, specPart);
                parts.add(specPart);
            } catch (Exception ex) {
                if (isContextLimitError(ex) && chunk.endpoints().size() > 1) {
                    log.warn("Chunk too large for model context; splitting and retrying", ex);
                    int smaller = Math.max(1, chunk.endpoints().size() / 2);
                    for (MigrationSpec sub : MigrationChunker.chunk(chunk, smaller)) {
                        parts.add(transform(sub, Math.max(1, smaller), cacheDir));
                    }
                } else {
                    log.warn("LLM transform failed for chunk, falling back to deterministic", ex);
                    parts.add(fallback.transform(chunk));
                }
            }
        }

        return FastApiSpecMerger.merge(parts);
    }
    private boolean isContextLimitError(Exception ex) {
        String message = ex.getMessage();
        if (message != null && (message.contains("context length") || message.contains("tokens to keep"))) {
            return true;
        }
        Throwable cause = ex.getCause();
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && (msg.contains("context length") || msg.contains("tokens to keep"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

}
