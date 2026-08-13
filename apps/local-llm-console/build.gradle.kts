plugins {
    alias(libs.plugins.android.application)
}

val sharedRuntimeReleaseHostPackage = "io.github.daniele21.localllm.phonetest"
val sharedRuntimeDebugHostPackage = "io.github.daniele21.localllm.phonetest.debug"
val sharedRuntimeHostService = "io.github.daniele21.localllm.phonetest.HarnessSharedRuntimeService"
val sharedRuntimeReleasePermission = "io.github.daniele21.localllm.permission.USE_LOCAL_LLM"
val sharedRuntimeDebugPermission = "io.github.daniele21.localllm.debug.permission.USE_LOCAL_LLM"

android {
    namespace = "io.github.daniele21.localllm.console"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        applicationId = "io.github.daniele21.localllm.console"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["sharedRuntimePermission"] = sharedRuntimeReleasePermission
        manifestPlaceholders["sharedRuntimeHostPackage"] = sharedRuntimeReleaseHostPackage
        buildConfigField("String", "SHARED_RUNTIME_HOST_PACKAGE", "\"$sharedRuntimeReleaseHostPackage\"")
        buildConfigField("String", "SHARED_RUNTIME_HOST_SERVICE", "\"$sharedRuntimeHostService\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["sharedRuntimePermission"] = sharedRuntimeDebugPermission
            manifestPlaceholders["sharedRuntimeHostPackage"] = sharedRuntimeDebugHostPackage
            buildConfigField("String", "SHARED_RUNTIME_HOST_PACKAGE", "\"$sharedRuntimeDebugHostPackage\"")
        }

        create("internal") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".internal"
            versionNameSuffix = "-internal"
            matchingFallbacks += listOf("debug")
            isDebuggable = true
            manifestPlaceholders["sharedRuntimePermission"] = sharedRuntimeDebugPermission
            manifestPlaceholders["sharedRuntimeHostPackage"] = sharedRuntimeDebugHostPackage
            buildConfigField("String", "SHARED_RUNTIME_HOST_PACKAGE", "\"$sharedRuntimeDebugHostPackage\"")
        }

        release {
            isDebuggable = false
            isMinifyEnabled = false
            manifestPlaceholders["sharedRuntimePermission"] = sharedRuntimeReleasePermission
            manifestPlaceholders["sharedRuntimeHostPackage"] = sharedRuntimeReleaseHostPackage
            buildConfigField("String", "SHARED_RUNTIME_HOST_PACKAGE", "\"$sharedRuntimeReleaseHostPackage\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        lintConfig = rootProject.file("lint.xml")
        abortOnError = true
        warningsAsErrors = false
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
    implementation(project(":core:contracts"))
    implementation(project(":models:model-store"))
    implementation(project(":observability:contracts"))
    implementation(project(":observability:health-engine"))
    implementation(project(":observability:in-memory-store"))
    implementation(project(":transports:android-binder-client"))

    testImplementation(libs.junit4)
}
