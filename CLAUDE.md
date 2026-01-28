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
│       ├── service/
│       │   ├── RetraceService.kt       # Core retracing logic
│       │   ├── RetraceProvider.kt      # LRU cache management
│       │   └── ProguardService.kt      # Mapping file handling
│       └── partition/                  # Partitioned mapping for large files
│           ├── MappingIndex.kt             # Index: class name → file offset
│           ├── MappingIndexBuilder.kt      # Builds index from mapping file
│           ├── PartitionedClassNameMapper.kt # Lazy loading with LRU cache
│           ├── PartitionedRetracer.kt      # Retracer using partitioned mapper
│           └── PartitionedStringRetrace.kt # StringRetrace for partitioned mode
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

### Partitioned Mapping Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `retracer.usePartitionedMapping` | `true` | Enable partitioned mode for large files |
| `retracer.partitionedThresholdMb` | `50` | File size threshold (MB) to trigger partitioned mode |
| `retracer.maxCachedClassesPerMapping` | `1000` | Max class mappings cached per file in partitioned mode |

### Profiles

- `dev`: Trace-level logging for debugging
- `prod`: Info-level logging for production

## Key Implementation Details

1. **LRU Cache**: Uses Spring's `ConcurrentLruCache` for mapping file caching
2. **Lazy Loading**: Mapping files loaded on-demand
3. **Pre-loading**: Top N versions pre-loaded on startup
4. **Fingerprinting**: MD5 hash for crash grouping
5. **Root Cause Analysis**: Uses `trace-parser` library
6. **Partitioned Mapping**: Memory-efficient handling of large mapping files (see below)

## Partitioned Mapping Implementation

For large mapping files (100MB+), loading the entire file into memory is inefficient. The partitioned mapping system solves this by lazy-loading class mappings on demand.

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        RetraceProvider                          │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ File size < 50MB? → Standard StringRetrace (full load)    │  │
│  │ File size ≥ 50MB? → PartitionedStringRetrace (lazy load)  │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
            ┌─────────────────┴─────────────────┐
            ▼                                   ▼
┌─────────────────────┐             ┌─────────────────────────────┐
│  Standard Mode      │             │  Partitioned Mode           │
│  (R8 StringRetrace) │             │  ┌───────────────────────┐  │
│                     │             │  │ MappingIndex          │  │
│  - Full file load   │             │  │ (class → byte offset) │  │
│  - ~2x file size    │             │  └───────────┬───────────┘  │
│    in memory        │             │              │              │
└─────────────────────┘             │              ▼              │
                                    │  ┌───────────────────────┐  │
                                    │  │ PartitionedClassMap   │  │
                                    │  │ - RandomAccessFile    │  │
                                    │  │ - LRU cache (1000)    │  │
                                    │  └───────────┬───────────┘  │
                                    │              │              │
                                    │              ▼              │
                                    │  ┌───────────────────────┐  │
                                    │  │ On retrace request:   │  │
                                    │  │ 1. Parse stack trace  │  │
                                    │  │ 2. Extract class names│  │
                                    │  │ 3. Load only needed   │  │
                                    │  │    classes from file  │  │
                                    │  │ 4. Delegate to R8     │  │
                                    │  └───────────────────────┘  │
                                    └─────────────────────────────┘
```

### How It Works

1. **Index Building** (`MappingIndexBuilder`):
   - Scans the mapping file once to identify class boundaries
   - Class lines match pattern: `original.Class -> obfuscated.Class:`
   - Records byte offset and length for each class
   - Saves index to `mapping.idx` for fast subsequent loads

2. **Index Structure** (`MappingIndex`):
   - Maps obfuscated class name → `(originalName, byteOffset, byteLength)`
   - Binary serialization for fast loading (~50 bytes per class)
   - Validates against mapping file timestamp/size

3. **Lazy Loading** (`PartitionedClassNameMapper`):
   - Uses `RandomAccessFile` to seek directly to class positions
   - LRU cache holds recently used `ClassNamingForNameMapper` instances
   - Loads class mapping by reading bytes at recorded offset

4. **Retrace Flow** (`PartitionedStringRetrace`):
   - Parses incoming stack trace to extract class names
   - Loads only the referenced classes from the file
   - Creates minimal `ClassNameMapper` with just those classes
   - Delegates to R8's `StringRetrace` for actual retracing

### Performance Characteristics

| Metric | Standard Mode | Partitioned Mode |
|--------|---------------|------------------|
| Memory per 200MB mapping | ~400MB | ~5MB index + ~50MB LRU |
| Initial load time | ~10s | <1s (index only) |
| First retrace | Fast | Slightly slower |
| Subsequent retrace | Fast | Fast (cached) |

### File Structure

```
/data/{appId}/{version}/{code}/
├── mapping.txt    # Original R8/ProGuard mapping file
└── mapping.idx    # Binary index (auto-generated)
```

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
