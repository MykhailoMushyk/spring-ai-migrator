# Spring AI Migrator

AI-driven agent that migrates a Spring Boot web API into an equivalent Python FastAPI project.

## Requirements
- Java 21+
- Maven 3.8+
- LM Studio running an OpenAI-compatible endpoint

## Runtime mode
This application runs as a CLI pipeline (no embedded web server).

## LM Studio configuration
This project uses Spring AI with an OpenAI-compatible client. For LM Studio:

- Base URL: `http://127.0.0.1:1234/v1`
- Model: `mistralai/devstral-small-2-2512`
- API key: any non-empty string (LM Studio ignores it)

Default values are in `src/main/resources/application.yml`. Override via env or CLI if needed.

## Run
Example (migrate visit-tracker):

```bash
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--migrator.input=/Users/mmushy/IdeaProjects/Private/visit-tracker --migrator.output=/Users/mmushy/IdeaProjects/Private/visit-tracker-fastapi"
```

Options:
- `--migrator.mode=AUTO|SOURCE|BYTECODE`
- `--migrator.maxChunkSize=12`
- `--migrator.useAi=true|false`
- `--migrator.includeTests=false`

## Build
```bash
mvn -q -DskipTests package
```

## Multi-module projects
The migrator detects modules by scanning for `pom.xml` / `build.gradle` files under the input root.
Each module is migrated into its own namespace under `app/modules/<module_name>/` and a root `app/main.py`
includes routers from all modules. You can tune discovery depth via:

- `--migrator.moduleSearchDepth=6`
