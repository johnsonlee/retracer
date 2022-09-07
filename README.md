# Introduction

Retracer is a high performance, and realtime retracing server which used for Java/Android stack trace retracing by [R8](https://r8.googlesource.com/r8)

## Getting Started

```bash
docker-compose up -d --build
```

or run from remote docker image directly

```bash
docker run -p 8080:8080 ghcr.io/johnsonlee/retracer
```

Then open http://localhost:8080/swagger-ui/ with browser to have a trial
