import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.openapi.generator)
}

group = "energy.eddie.s3.api"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // `api` scope: types appear in generated public signatures (ResponseEntity, models),
    // so consumers (backend) get them transitively.
    api(libs.spring.boot.starter.web)
    api(libs.spring.boot.starter.validation)
}

val apiGenerateDir = layout.buildDirectory.dir("generated").get().asFile.path

tasks.register<GenerateTask>("generateServerApi") {
    generatorName = "spring"
    inputSpec = "${rootDir}/api-specs/backend-api.yml"
    outputDir = apiGenerateDir
    apiPackage = "energy.eddie.s3.generated.api"
    modelPackage = "energy.eddie.s3.generated.model"
    configOptions = mapOf(
        "interfaceOnly" to "true",
        "requestMappingMode" to "api_interface",
        "useTags" to "true",
        "useSpringBoot3" to "true",
        "openApiNullable" to "false",
        "documentationProvider" to "none",
        "annotationLibrary" to "none",
    )
    // Map OpenAPI date-time to Instant instead of OffsetDateTime.
    typeMappings = mapOf("OffsetDateTime" to "java.time.Instant")
    importMappings = mapOf("OffsetDateTime" to "java.time.Instant")
}

tasks.named("compileJava") {
    dependsOn(tasks.named("generateServerApi"))
}

sourceSets {
    main {
        java {
            srcDir("${apiGenerateDir}/src/main/java")
        }
    }
}
