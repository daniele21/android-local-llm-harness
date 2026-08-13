plugins {
    alias(libs.plugins.android.application)
}

val sharedRuntimeReleaseHostPackage = "io.github.daniele21.localllm.phonetest"
val sharedRuntimeDebugHostPackage = "io.github.daniele21.localllm.phonetest.debug"
val sharedRuntimeHostService = "io.github.daniele21.localllm.phonetest.HarnessSharedRuntimeService"
val sharedRuntimeReleasePermission = "io.github.daniele21.localllm.permission.USE_LOCAL_LLM"
val sharedRuntimeDebugPermission = "io.github.daniele21.localllm.debug.permission.USE_LOCAL_LLM"
val sharedRuntimeSigningEnvironment =
    mapOf(
        "storeFile" to System.getenv("LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_FILE"),
        "storePassword" to System.getenv("LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_PASSWORD"),
        "keyAlias" to System.getenv("LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_ALIAS"),
        "keyPassword" to System.getenv("LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_PASSWORD"),
    )
val sharedRuntimeSigningConfigured = sharedRuntimeSigningEnvironment.values.all { !it.isNullOrBlank() }
val sharedRuntimeSigningPartiallyConfigured =
    sharedRuntimeSigningEnvironment.values.any { !it.isNullOrBlank() } && !sharedRuntimeSigningConfigured

if (sharedRuntimeSigningPartiallyConfigured) {
    throw GradleException(
        "Shared-runtime consumer-fixture signing is incomplete. Set all " +
            "LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_* variables or none of them.",
    )
}

val binderClientAar = files(
    project(":transports:android-binder-client").layout.buildDirectory.file(
        "outputs/aar/android-binder-client-release.aar",
    ),
).builtBy(":transports:android-binder-client:assembleRelease")
val binderContractAar = files(
    project(":transports:android-binder-contract").layout.buildDirectory.file(
        "outputs/aar/android-binder-contract-release.aar",
    ),
).builtBy(":transports:android-binder-contract:assembleRelease")

android {
    namespace = "io.github.daniele21.localllm.consumerfixture"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()
    testBuildType = "release"

    defaultConfig {
        applicationId = "io.github.daniele21.localllm.consumerfixture"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["sharedRuntimePermission"] = sharedRuntimeReleasePermission
        manifestPlaceholders["sharedRuntimeHostPackage"] = sharedRuntimeReleaseHostPackage
        buildConfigField("String", "SHARED_RUNTIME_HOST_PACKAGE", "\"$sharedRuntimeReleaseHostPackage\"")
        buildConfigField("String", "SHARED_RUNTIME_HOST_SERVICE", "\"$sharedRuntimeHostService\"")
    }

    signingConfigs {
        create("sharedRuntimeRelease") {
            if (sharedRuntimeSigningConfigured) {
                storeFile = file(sharedRuntimeSigningEnvironment.getValue("storeFile")!!)
                storePassword = sharedRuntimeSigningEnvironment.getValue("storePassword")
                keyAlias = sharedRuntimeSigningEnvironment.getValue("keyAlias")
                keyPassword = sharedRuntimeSigningEnvironment.getValue("keyPassword")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
            if (sharedRuntimeSigningConfigured) {
                signingConfig = signingConfigs.getByName("sharedRuntimeRelease")
            }
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

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
