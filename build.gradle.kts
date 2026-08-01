import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.spotless)
}

group = "io.github.daniele21.localllm"
version = "0.1.0-SNAPSHOT"

val detektCli by configurations.creating

dependencies {
    detektCli(libs.detekt.cli)
}

allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}

configure<SpotlessExtension> {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "third_party/**")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "third_party/**")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("misc") {
        target(
            ".editorconfig",
            ".gitignore",
            "**/*.md",
            "**/*.toml",
            "**/*.yml",
            "**/*.yaml",
            "**/*.xml",
        )
        targetExclude("**/build/**", "third_party/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

val detektReportDirectory = layout.buildDirectory.dir("reports/detekt")
val detektInputs = providers.provider {
    fileTree(rootDir) {
        include("**/src/**/*.kt")
        exclude("**/build/**", "third_party/**")
    }.files.joinToString(",") { it.absolutePath }
}

tasks.register<JavaExec>("detekt") {
    group = "verification"
    description = "Runs Detekt CLI without coupling it to the Android Gradle Plugin."
    classpath = detektCli
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")

    doFirst {
        detektReportDirectory.get().asFile.mkdirs()
    }

    args(
        "--input",
        detektInputs.get(),
        "--config",
        rootProject.file("config/detekt/detekt.yml").absolutePath,
        "--build-upon-default-config",
        "--report",
        "html:${detektReportDirectory.get().file("detekt.html").asFile.absolutePath}",
        "--report",
        "sarif:${detektReportDirectory.get().file("detekt.sarif").asFile.absolutePath}",
    )
}

tasks.register("verifyNoModelArtifacts") {
    group = "verification"
    description = "Fails when GGUF/GGML model binaries are present in the repository source tree."

    doLast {
        val forbiddenFiles = fileTree(rootDir) {
            include("**/*.gguf", "**/*.ggml")
            exclude("**/build/**", ".gradle/**")
        }.files

        check(forbiddenFiles.isEmpty()) {
            "Model binaries must not be committed or bundled from the repository: " +
                forbiddenFiles.joinToString { it.relativeTo(rootDir).path }
        }
    }
}

tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs repository formatting, static analysis and model-artifact guards."
    dependsOn("spotlessCheck", "detekt", "verifyNoModelArtifacts")
}
