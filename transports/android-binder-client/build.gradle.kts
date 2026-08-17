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
    namespace = "io.github.daniele21.localllm.transport.binder.client"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
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
            artifactId = "consumer-android"
            version = project.version.toString()
            pom {
                name.set("Local AI Harness Consumer Android SDK")
                description.set("Public Android client for the Local AI Harness shared-runtime Consumer API.")
            }
            afterEvaluate { from(components["release"]) }
        }
    }
}

dependencies {
    api(project(":core:contracts"))
    implementation(project(":transports:android-binder-contract"))

    testImplementation(libs.junit4)
}
