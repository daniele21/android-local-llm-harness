plugins {
    alias(libs.plugins.android.application)
}

val binderClientAar = files(
    project(":transports:android-binder-client").layout.buildDirectory.file(
        "outputs/aar/android-binder-client-debug.aar",
    ),
).builtBy(":transports:android-binder-client:assembleDebug")
val binderContractAar = files(
    project(":transports:android-binder-contract").layout.buildDirectory.file(
        "outputs/aar/android-binder-contract-debug.aar",
    ),
).builtBy(":transports:android-binder-contract:assembleDebug")

android {
    namespace = "io.github.daniele21.localllm.consumerfixture"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        applicationId = "io.github.daniele21.localllm.consumerfixture"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
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
        checkDependencies = true
    }

    packaging {
        resources {
            excludes += setOf("**/*.gguf", "**/*.ggml")
        }
    }
}

dependencies {
    implementation(binderClientAar)
    implementation(binderContractAar)
    implementation(project(":core:contracts"))
}
