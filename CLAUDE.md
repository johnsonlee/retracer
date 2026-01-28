# CLAUDE.md

This file provides guidance for Claude Code when working with this repository.

## Project Overview

**Retracer** is a high-performance REST API server for Java/Android stack trace de-obfuscation using R8/ProGuard mapping files. It takes obfuscated stack traces and converts them back to human-readable form for debugging.

- **Version**: 0.2.0
- **Author**: johnsonlee (g.johnsonlee@gmail.com)
- **License**: See repository

## Tech Stack

- **Language**: Kotlin (JVM 17+)
- **Framework**: Spring Boot 3.4.2, Spring Cloud 2024.0.0
- **Build System**: Gradle 8.13 (Kotlin DSL)
- **Containerization**: Docker, Docker Compose
- **CI/CD**: GitHub Actions
- **Dependencies**: R8 3.0.78 (de-obfuscation), trace-parser, Micrometer Prometheus, springdoc-openapi

## Project Structure

```
retracer/
├── src/main/kotlin/io/johnsonlee/retracer/
│   ├── Retracer.kt              # Main Spring Boot entry point
│   ├── Constants.kt             # Regex patterns and constants
│   ├── config/
│   │   ├── RetraceConfig.kt     # Configuration beans
│   │   └── SwaggerConfig.kt     # OpenAPI/Swagger setup
│   └── r8/
│       ├── controller/
│       │   ├── RetraceController.kt    # POST /api/retrace/r8/{appId}/{version}/{code}
│       │   ├── SymbolController.kt     # POST /api/symbol/r8/{appId}/{version}/{code}
│       │   └── PrometheusController.kt # GET /metrics
│       └── service/
│           ├── RetraceService.kt       # Core retracing logic
│           ├── RetraceProvider.kt      # LRU cache management
│           └── ProguardService.kt      # Mapping file handling
├── r8/                          # Git submodule (custom R8 fork)
├── libs/r8.jar                  # Pre-built R8 library (3.0.78)
├── data/                        # ProGuard mapping files storage
├── Dockerfile                   # Multi-stage Docker build
├── docker-compose.yml           # Production setup
└── build.gradle.kts             # Gradle configuration
```

## Build Commands

```bash
# Build the application
./gradlew bootJar --no-daemon

# Run the application
java -jar build/libs/app.jar

# Run with Docker Compose
docker-compose up -d --build

# Run directly with Docker
docker run -p 8080:8080 ghcr.io/johnsonlee/retracer

# Build R8 from submodule (requires Java 15)
JAVA_HOME=/path/to/java15 ./gradlew -p r8 r8WithDeps --no-daemon
cp r8/build/libs/r8_with_deps.jar libs/r8.jar
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/retrace/r8/` | Get R8 version and cache info |
| POST | `/api/retrace/r8/{appId}/{version}/{code}` | Retrace a stack trace |
| POST | `/api/symbol/r8/{appId}/{version}/{code}` | Upload mapping file |
| GET | `/metrics` | Prometheus metrics |
| GET | `/swagger-ui/index.html` | API documentation (Swagger UI) |
| GET | `/actuator/health` | Health check |

## Configuration

- **Data directory**: `/data` (configurable via `retracer.dataDir`)
- **Cache size**: min 5, max 20 app versions (configurable)
- **Port**: 8080 (default)

### Profiles

- `dev`: Trace-level logging for debugging
- `prod`: Info-level logging for production

## Key Implementation Details

1. **LRU Cache**: Uses Spring's `ConcurrentLruCache` for mapping file caching
2. **Lazy Loading**: Mapping files loaded on-demand
3. **Pre-loading**: Top N versions pre-loaded on startup
4. **Fingerprinting**: MD5 hash for crash grouping
5. **Root Cause Analysis**: Uses `trace-parser` library

## Git Submodule

The `r8/` directory is a git submodule pointing to a custom R8 fork. To sync:

```bash
git submodule update --init --recursive
```

## Development Notes

- Main entry point: `io.johnsonlee.retracer.RetracerKt`
- Mapping file path pattern: `/data/{appId}/{appVersionName}/{appVersionCode}/mapping.txt`
- Docker images published to: `ghcr.io/johnsonlee/retracer`
- Requires Java 17+ to run (Spring Boot 3.x requirement)
- Uses Jakarta EE (jakarta.*) instead of Java EE (javax.*)
