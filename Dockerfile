FROM johnsonlee/gradle-6.9:springboot-2.5.0 AS builder
WORKDIR /app
ADD . .
RUN ./gradlew bootJar --no-daemon

FROM johnsonlee/java15:latest
WORKDIR /app
COPY --from=builder /app/build/libs/app.jar app.jar
COPY --from=builder /app/envsetup.sh envsetup.sh
CMD ["/bin/bash", "-c", "source envsetup.sh && exec java $JAVA_OPTS -jar app.jar"]
