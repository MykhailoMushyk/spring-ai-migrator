package ai.migrator.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JsonUtils {
    private static final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new Jdk8Module())
        .registerModule(new JavaTimeModule());

    public static String toPrettyJson(Object obj) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> T readJson(String content, Class<T> type) {
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Empty content; LLM response was null or blank");
        }
        try {
            return mapper.readValue(extractJson(content), type);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> T readJson(Path path, Class<T> type) throws IOException {
        return mapper.readValue(Files.readString(path), type);
    }

    public static void writeJson(Path path, Object obj) throws IOException {
        Files.writeString(path, toPrettyJson(obj));
    }

    private static String extractJson(String content) {
        if (content == null) {
            return null;
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }
}
