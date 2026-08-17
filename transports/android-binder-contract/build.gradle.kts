import org.gradle.api.provider.Provider
import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class BuiltInKotlinParcelizePlugin : KotlinCompilerPluginSupportPlugin {
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean =
        kotlinCompilation.platformType == KotlinPlatformType.androidJvm

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> =
        kotlinCompilation.target.project.provider { emptyList() }

    override fun getCompilerPluginId(): String = "org.jetbrains.kotlin.parcelize"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "org.jetbrains.kotlin",
        artifactId = "kotlin-parcelize-compiler",
    )
}

plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

apply<BuiltInKotlinParcelizePlugin>()

val consumerSdkVersion = providers.gradleProperty("consumerSdkVersion").orElse("0.1.0-SNAPSHOT")

group = "io.github.daniele21.localllm"
version = consumerSdkVersion.get()

android {
    namespace = "io.github.daniele21.localllm.transport.binder.contract"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        aidl = true
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
    }
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "android-binder-contract"
            version = project.version.toString()
            afterEvaluate { from(components["release"]) }
        }
    }
}

dependencies {
    api(project(":core:contracts"))
    implementation(libs.kotlin.parcelize.runtime)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
