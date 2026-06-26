import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.testing.Test
import java.util.*

plugins {
    java
    jacoco
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.freefair.lombok)
    alias(libs.plugins.google.jib)
}

group = "energy.eddie.s3"
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
    implementation(project(":api"))

    implementation(libs.bundles.spring.impl)
    implementation(libs.bundles.jackson.impl)

    implementation(libs.mapstruct)

    annotationProcessor(libs.mapstruct.processor)

    errorprone(libs.errorprone.core)

    runtimeOnly(libs.bundles.flyway.runtime)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.bundles.spring.test.impl)

    testRuntimeOnly(libs.junit.platform.launcher)
}

dependencyLocking {
    lockAllConfigurations()
}

plugins.withType<JavaPlugin> {
    dependencies {
        annotationProcessor(libs.nullaway)
        compileOnly(libs.jsr305)
    }
}

jib {
    from {
        image = "eclipse-temurin:21"
        platforms {
            platform { architecture = "amd64"; os = "linux" }
            platform { architecture = "arm64"; os = "linux" }
        }
    }
    to {
        image = System.getProperty("jib.to.image") ?: "ghcr.io/eddie-energy/ceeds-backend"
        auth {
            username = "oauth2accesstoken"
            password = System.getProperty("jib.to.auth.password")
        }
    }
    container {
        creationTime = "USE_CURRENT_TIMESTAMP"
    }
}

val generatedSrcDir = "${project.layout.buildDirectory.asFile.get()}/generated"

tasks.compileJava {
    options.compilerArgs.add("-parameters")

    if (!name.lowercase(Locale.getDefault()).contains("test")) {
        options.errorprone {
            check("NullAway", CheckSeverity.ERROR)
            option("NullAway:AnnotatedPackages", "energy.eddie.s3")
            excludedPaths = "${generatedSrcDir}/.*"
        }
    }
}

tasks.named<Test>("test") {
    description = "Runs all tests except integration tests."
    useJUnitPlatform {
        filter.excludeTestsMatching("*IntegrationTest")
    }

    testLogging.events("passed")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.register("prepareKotlinBuildScriptModel")

tasks.register<Copy>("buildFrontend") {
    group = "build"
    description = "Build the frontend into the Spring application for deployment"
    dependsOn(":frontend:build")
    from("${project.rootDir}/frontend/dist")
    into("${project.rootDir}/backend/src/main/resources/public")
}

tasks.named("processResources") {
    dependsOn("buildFrontend")
}
