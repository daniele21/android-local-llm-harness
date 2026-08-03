plugins {
    alias(libs.plugins.android.application)
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

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isDebuggable = false
            isMinifyEnabled = false
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
