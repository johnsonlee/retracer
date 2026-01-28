import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("jvm") version embeddedKotlinVersion
    kotlin("plugin.spring") version embeddedKotlinVersion
    kotlin("plugin.jpa") version embeddedKotlinVersion

    id("org.springframework.boot") version "3.4.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("me.champeau.jmh") version "0.7.2"
}

group = "io.johnsonlee.springboot"
version = "0.2.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(fileTree("libs"))
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.cloud:spring-cloud-starter")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.4")
    implementation("io.johnsonlee:trace-parser:1.11.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2024.0.0")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

val jar by tasks.getting(Jar::class) {
    enabled = false
}

val bootJar by tasks.getting(BootJar::class) {
    enabled = true
    archiveFileName.set("app.jar")
    mainClass.set("io.johnsonlee.retracer.RetracerKt")
}
