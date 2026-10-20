import com.github.gradle.node.pnpm.task.PnpmTask

plugins {
    alias(libs.plugins.node.gradle)
}

node {
    version.set("22.19.0")
    pnpmVersion.set("10.15.0")
    download.set(true)
}

tasks.register<PnpmTask>("build") {
    group = "build"
    description = "Builds the frontend"
    dependsOn("pnpmInstall")
    pnpmCommand.set(listOf("run", "build"))
    environment = System.getenv()
}
