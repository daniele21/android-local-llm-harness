plugins {
    alias(libs.plugins.android.application)
}

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
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        create("internal") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".internal"
            versionNameSuffix = "-internal"
            matchingFallbacks += listOf("debug")
            isDebuggable = true
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
        resources {
            excludes += setOf("**/*.gguf", "**/*.ggml")
        }
    }
}

dependencies {
    implementation(project(":core:contracts"))
    implementation(project(":models:model-store"))
    implementation(project(":observability:contracts"))
    implementation(project(":observability:benchmark-engine"))
    implementation(project(":observability:health-engine"))
    implementation(project(":observability:in-memory-store"))

    testImplementation(libs.junit4)
}
