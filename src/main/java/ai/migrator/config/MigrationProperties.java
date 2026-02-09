package ai.migrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "migrator")
public class MigrationProperties {

    public enum Mode {
        AUTO, SOURCE, BYTECODE
    }

    private Path input;
    private Path output;
    private Mode mode = Mode.AUTO;
    private int maxChunkSize = 10;
    private boolean includeTests = false;
    private boolean useAi = true;
    private Path cacheDir = Path.of(".migrator-cache");
    private int moduleSearchDepth = 6;

    public Path getInput() {
        return input;
    }

    public void setInput(Path input) {
        this.input = input;
    }

    public Path getOutput() {
        return output;
    }

    public void setOutput(Path output) {
        this.output = output;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    public void setMaxChunkSize(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
    }

    public boolean isIncludeTests() {
        return includeTests;
    }

    public void setIncludeTests(boolean includeTests) {
        this.includeTests = includeTests;
    }

    public boolean isUseAi() {
        return useAi;
    }

    public void setUseAi(boolean useAi) {
        this.useAi = useAi;
    }

    public Path getCacheDir() {
        return cacheDir;
    }

    public void setCacheDir(Path cacheDir) {
        this.cacheDir = cacheDir;
    }


    public int getModuleSearchDepth() {
        return moduleSearchDepth;
    }

    public void setModuleSearchDepth(int moduleSearchDepth) {
        this.moduleSearchDepth = moduleSearchDepth;
    }
}
