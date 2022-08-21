import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("jvm") version embeddedKotlinVersion
    kotlin("plugin.spring") version embeddedKotlinVersion
    kotlin("plugin.jpa") version embeddedKotlinVersion

    id("org.springframework.boot") version "2.5.0"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    id("me.champeau.gradle.jmh") version "0.5.0"
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.3")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.cloud:spring-cloud-starter")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.springfox:springfox-boot-starter:3.0.0")
    implementation("io.johnsonlee:trace-parser:1.11.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2020.0.3")
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "1.8"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val jar by tasks.getting(Jar::class) {
    enabled = false
}

val bootJar by tasks.getting(BootJar::class) {
    enabled = true
    archiveFileName.set("app.jar")
    mainClass.set("io.johnsonlee.retracer.RetracerKt")
}
