plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val phoneTestUploadSigningEnvironment =
    mapOf(
        "storeFile" to System.getenv("LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_FILE"),
        "storePassword" to System.getenv("LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_PASSWORD"),
        "keyAlias" to System.getenv("LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_ALIAS"),
        "keyPassword" to System.getenv("LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_PASSWORD"),
    )
val phoneTestUploadSigningConfigured =
    phoneTestUploadSigningEnvironment.values.all { !it.isNullOrBlank() }
val phoneTestUploadSigningPartiallyConfigured =
    phoneTestUploadSigningEnvironment.values.any { !it.isNullOrBlank() } && !phoneTestUploadSigningConfigured
val allowUnsignedRelease =
    System.getenv("LOCAL_LLM_PHONE_TEST_ALLOW_UNSIGNED_RELEASE").equals("true", ignoreCase = true)

gradle.taskGraph.whenReady {
    val packagesPhoneTestRelease =
        allTasks.any { task ->
            task.path == ":apps:local-llm-phone-test:bundleRelease" ||
                task.path == ":apps:local-llm-phone-test:assembleRelease"
        }
    if (phoneTestUploadSigningPartiallyConfigured) {
        throw GradleException(
            "Phone-test release signing is incomplete. Set all " +
                "LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_* variables; never commit upload-key material.",
        )
    }
    if (packagesPhoneTestRelease && !phoneTestUploadSigningConfigured && !allowUnsignedRelease) {
        throw GradleException(
            "Phone-test release signing is not configured. Use scripts/build-phone-test-release.sh, " +
                "or set LOCAL_LLM_PHONE_TEST_ALLOW_UNSIGNED_RELEASE=true only for an intentional unsigned CI artifact.",
        )
    }
}

android {
    namespace = "io.github.daniele21.localllm.phonetest"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        applicationId = "io.github.daniele21.localllm.phonetest"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 4
        versionName = "0.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        create("upload") {
            if (phoneTestUploadSigningConfigured) {
                storeFile = file(phoneTestUploadSigningEnvironment.getValue("storeFile")!!)
                storePassword = phoneTestUploadSigningEnvironment.getValue("storePassword")
                keyAlias = phoneTestUploadSigningEnvironment.getValue("keyAlias")
                keyPassword = phoneTestUploadSigningEnvironment.getValue("keyPassword")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isDebuggable = false
            isMinifyEnabled = false
            if (phoneTestUploadSigningConfigured) {
                signingConfig = signingConfigs.getByName("upload")
            }
        }
    }

    buildFeatures {
        compose = true
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
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf("**/*.gguf", "**/*.ggml")
        }
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(project(":core:contracts"))
    implementation(project(":core:runtime-core"))
    implementation(project(":models:model-profile"))
    implementation(project(":models:model-store"))
    implementation(project(":models:model-catalog"))
    implementation(project(":models:model-download"))
    implementation(project(":models:model-install"))
    implementation(project(":backends:llama-cpp"))
    implementation(project(":observability:contracts"))
    implementation(project(":observability:in-memory-store"))
    implementation(project(":observability:health-engine"))
    implementation(project(":observability:android-resource-probe"))
    implementation(project(":observability:benchmark-engine"))
    implementation(project(":transports:in-process"))
    implementation(project(":ui:design-system"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)
}
