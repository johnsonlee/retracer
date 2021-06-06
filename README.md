# Introduction

Retracer is a high performance, and near realtime REST API which used for Java/Android stack trace retracing by [R8](https://r8.googlesource.com/r8)

## Getting Started

```bash
docker-compose up -d --build
```

Then open http://localhost:8080/swagger-ui/ with browser to have a trial

## Build R8

```bash
git submodule init
docker-compose -f docker-compose.r8.yml up
```
