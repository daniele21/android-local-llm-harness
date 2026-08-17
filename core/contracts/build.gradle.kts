import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

val consumerSdkVersion = providers.gradleProperty("consumerSdkVersion").orElse("0.1.0-SNAPSHOT")
val githubActor = providers.environmentVariable("GITHUB_ACTOR")
val githubToken = providers.environmentVariable("GITHUB_TOKEN")

group = "io.github.daniele21.localllm"
version = consumerSdkVersion.get()

android {
    namespace = "io.github.daniele21.localllm.contracts"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        lintConfig = rootProject.file("lint.xml")
        abortOnError = true
        htmlReport = true
        sarifReport = true
    }
}

publishing {
    repositories {
        maven {
            name = "consumerSdk"
            url = uri(rootProject.layout.buildDirectory.dir("consumer-sdk-repository"))
        }
        if (githubActor.isPresent && githubToken.isPresent) {
            maven {
                name = "githubPackages"
                url = uri("https://maven.pkg.github.com/daniele21/android-local-llm-harness")
                credentials {
                    username = githubActor.get()
                    password = githubToken.get()
                }
            }
        }
    }
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "core-contracts"
            version = project.version.toString()
            afterEvaluate { from(components["release"]) }
        }
    }
}

dependencies {
    testImplementation(libs.junit4)
}
