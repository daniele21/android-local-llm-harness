plugins {
    alias(libs.plugins.android.library)
}

val experimentalOpenCl = providers.gradleProperty("localLlm.experimentalOpenCl")
    .orNull
    ?.toBooleanStrictOrNull()
    ?: false

android {
    namespace = "io.github.daniele21.localllm.llamacpp"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                arguments += "-DLOCAL_LLM_EXPERIMENTAL_OPENCL=${if (experimentalOpenCl) "ON" else "OFF"}"
                cppFlags += listOf("-std=c++17", "-Wall", "-Wextra")
            }
        }

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
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

dependencies {
    api(project(":core:contracts"))
    api(project(":core:backend-spi"))
    api(project(":models:model-profile"))
    api(project(":models:model-install"))
    testImplementation(libs.junit4)
}
