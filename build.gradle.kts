plugins {
    java
    // Every plugin is pinned to an exact version. Library versions come from the
    // Spring Boot BOM applied by the dependency-management plugin, so every
    // dependency below resolves to one fixed, reproducible version.
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.1.0"
description = "Task management REST API"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Lock state for every resolvable configuration. gradle.lockfile is generated,
// not committed (it is in .gitignore). Regenerate it with:
//   ./gradlew dependencies --write-locks
// The TigerGate workflow does the same inside the scan job so SCA sees exact
// transitive versions.
dependencyLocking {
    lockAllConfigurations()
}

// No version ranges, "+", "latest.*" or SNAPSHOTs are used anywhere. (Gradle's
// failOnDynamicVersions()/failOnChangingVersions() cannot be combined with
// dependency locking, which already pins the full graph once generated.)

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")

    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:unchecked", "-Xlint:deprecation"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Only the executable Spring Boot jar is needed (see Dockerfile).
tasks.jar {
    enabled = false
}
