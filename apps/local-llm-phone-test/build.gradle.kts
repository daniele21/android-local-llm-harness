plugins {
    alias(libs.plugins.android.application)
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

    defaultConfig {
        applicationId = "io.github.daniele21.localllm.phonetest"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
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
    implementation(project(":core:contracts"))
    implementation(project(":core:runtime-core"))
    implementation(project(":models:model-profile"))
    implementation(project(":models:model-store"))
    implementation(project(":backends:llama-cpp"))
    testImplementation(libs.junit4)
}
