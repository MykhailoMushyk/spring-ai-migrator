package ai.migrator.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ProjectLayout(String name, Path root, Path sourceDir, Path testSourceDir, Path classesDir) {

    public static List<ProjectLayout> detectModules(Path root, int maxDepth) {
        Map<Path, ProjectLayout> modules = new LinkedHashMap<>();

        ProjectLayout rootLayout = detectModule(root);
        if (rootLayout != null) {
            modules.put(rootLayout.root(), rootLayout);
        }

        try (var paths = Files.walk(root, maxDepth)) {
            paths.filter(ProjectLayout::isBuildFile)
                .filter(p -> !isIgnoredPath(p))
                .forEach(p -> {
                    ProjectLayout layout = detectModule(p.getParent());
                    if (layout != null) {
                        modules.putIfAbsent(layout.root(), layout);
                    }
                });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to scan modules", ex);
        }

        return new ArrayList<>(modules.values());
    }

    private static ProjectLayout detectModule(Path root) {
        if (root == null) {
            return null;
        }

        Path srcMain = root.resolve("src/main/java");
        Path srcTest = root.resolve("src/test/java");
        Path mavenClasses = root.resolve("target/classes");
        Path gradleClasses = root.resolve("build/classes/java/main");

        Path sourceDir = Files.exists(srcMain) ? srcMain : null;
        Path testSourceDir = Files.exists(srcTest) ? srcTest : null;
        Path classesDir = Files.exists(mavenClasses) ? mavenClasses : (Files.exists(gradleClasses) ? gradleClasses : null);

        if (sourceDir == null && classesDir == null) {
            return null;
        }

        String name = root.getFileName() != null ? root.getFileName().toString() : "module";
        return new ProjectLayout(name, root, sourceDir, testSourceDir, classesDir);
    }

    private static boolean isBuildFile(Path path) {
        String name = path.getFileName().toString();
        return name.equals("pom.xml") || name.equals("build.gradle") || name.equals("build.gradle.kts");
    }

    private static boolean isIgnoredPath(Path path) {
        String normalized = path.toString().replace("\\", "/");
        return normalized.contains("/.git/")
            || normalized.contains("/target/")
            || normalized.contains("/build/")
            || normalized.contains("/.idea/")
            || normalized.contains("/node_modules/");
    }
}
