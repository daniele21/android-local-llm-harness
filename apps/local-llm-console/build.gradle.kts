import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val consoleUploadSigningEnvironment =
    mapOf(
        "storeFile" to System.getenv("LOCAL_LLM_CONSOLE_ANDROID_UPLOAD_STORE_FILE"),
        "storePassword" to System.getenv("LOCAL_LLM_CONSOLE_ANDROID_UPLOAD_STORE_PASSWORD"),
        "keyAlias" to System.getenv("LOCAL_LLM_CONSOLE_ANDROID_UPLOAD_KEY_ALIAS"),
        "keyPassword" to System.getenv("LOCAL_LLM_CONSOLE_ANDROID_UPLOAD_KEY_PASSWORD"),
    )
val consoleUploadSigningConfigured =
    consoleUploadSigningEnvironment.values.all { !it.isNullOrBlank() }
val consoleUploadSigningPartiallyConfigured =
    consoleUploadSigningEnvironment.values.any { !it.isNullOrBlank() } && !consoleUploadSigningConfigured
val allowUnsignedRelease =
    System.getenv("LOCAL_LLM_CONSOLE_ALLOW_UNSIGNED_RELEASE").equals("true", ignoreCase = true)

val sharedRuntimeReleaseHostPackage = "io.github.daniele21.localllm.phonetest"
val sharedRuntimeDebugHostPackage = "io.github.daniele21.localllm.phonetest.debug"
val sharedRuntimeHostService = "io.github.daniele21.localllm.phonetest.HarnessSharedRuntimeService"
val sharedRuntimeReleasePermission = "io.github.daniele21.localllm.permission.USE_LOCAL_LLM"
val sharedRuntimeDebugPermission = "io.github.daniele21.localllm.debug.permission.USE_LOCAL_LLM"

gradle.taskGraph.whenReady {
    val packagesConsoleRelease =
        allTasks.any { task ->
            task.path == ":apps:local-llm-console:bundleRelease" ||
                task.path == ":apps:local-llm-console:assembleRelease"
        }
    if (consoleUploadSigningPartiallyConfigured) {
        throw GradleException(
            "Console release signing is incomplete. Set all " +
                "LOCAL_LLM_CONSOLE_ANDROID_UPLOAD_* variables; never commit upload-key material.",
        )
    }
    if (packagesConsoleRelease && !consoleUploadSigningConfigured && !allowUnsignedRelease) {
        throw GradleException(
            "Console release signing is not configured. Use scripts/build-console-release.sh, " +
                "or set LOCAL_LLM_CONSOLE_ALLOW_UNSIGNED_RELEASE=true only for an intentional unsigned CI artifact.",
        )
    }
}

val versionPropertiesFile = file("version.properties")
val versionProperties = Properties().apply {
    if (versionPropertiesFile.exists()) {
        FileInputStream(versionPropertiesFile).use { load(it) }
    }
}
val currentVersionCode = (versionProperties.getProperty("versionCode") ?: "1").toInt()
val currentVersionName = versionProperties.getProperty("versionName") ?: "0.1.0"

android {
    namespace = "io.github.daniele21.localllm.console"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        applicationId = "io.github.daniele21.localllm.console"
        // OMBRA deliberately raises only the consumer APK floor to Android 9 so it can use the
        // sandboxed AndroidX PDF document APIs. Harness host/library compatibility remains at 26.
        minSdk = 28
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = currentVersionCode
        versionName = currentVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["sharedRuntimePermission"] = sharedRuntimeReleasePermission
        manifestPlaceholders["sharedRuntimeHostPackage"] = sharedRuntimeReleaseHostPackage
        buildConfigField("String", "SHARED_RUNTIME_HOST_PACKAGE", "\"$sharedRuntimeReleaseHostPackage\"")
        buildConfigField("String", "SHARED_RUNTIME_HOST_SERVICE", "\"$sharedRuntimeHostService\"")
    }

    signingConfigs {
        create("upload") {
            if (consoleUploadSigningConfigured) {
                storeFile = file(consoleUploadSigningEnvironment.getValue("storeFile")!!)
                storePassword = consoleUploadSigningEnvironment.getValue("storePassword")
                keyAlias = consoleUploadSigningEnvironment.getValue("keyAlias")
                keyPassword = consoleUploadSigningEnvironment.getValue("keyPassword")
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
            if (consoleUploadSigningConfigured) {
                signingConfig = signingConfigs.getByName("upload")
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

    // OMB-0A parser spike: first-party, sandboxed PDF loading/content extraction. These remain
    // experimental until representative fixtures and packaged-size evidence close OMB-0.
    implementation(libs.androidx.pdf.core)
    implementation(libs.androidx.pdf.document.service)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    testImplementation(libs.junit4)
}
