plugins {
    alias(libs.plugins.android.library)
}

val experimentalOpenCl = providers.gradleProperty("localLlm.experimentalOpenCl")
    .orNull
    ?.toBooleanStrictOrNull()
    ?: false
val openClIncludeDirProperty = providers.gradleProperty("localLlm.openClIncludeDir").orNull
val openClLibraryProperty = providers.gradleProperty("localLlm.openClLibrary").orNull
val llamaCppQualificationCommit = providers.gradleProperty("localLlm.llamaCppQualificationCommit").orNull

val openClIncludeDir = openClIncludeDirProperty?.let { rootProject.file(it) }
val openClLibrary = openClLibraryProperty?.let { rootProject.file(it) }

llamaCppQualificationCommit?.let { commit ->
    require(Regex("^[0-9a-f]{40}$").matches(commit)) {
        "-PlocalLlm.llamaCppQualificationCommit must be an exact lowercase 40-character SHA"
    }
}

if (experimentalOpenCl) {
    val includeDir = requireNotNull(openClIncludeDir) {
        "-PlocalLlm.openClIncludeDir is required when experimental OpenCL is enabled"
    }
    val library = requireNotNull(openClLibrary) {
        "-PlocalLlm.openClLibrary is required when experimental OpenCL is enabled"
    }
    require(includeDir.isDirectory && includeDir.canRead()) {
        "-PlocalLlm.openClIncludeDir must point to readable OpenCL headers when experimental OpenCL is enabled"
    }
    require(file("${includeDir.absolutePath}/CL/cl.h").isFile) {
        "OpenCL include directory must contain CL/cl.h"
    }
    require(library.isFile && library.canRead()) {
        "-PlocalLlm.openClLibrary must point to a readable arm64-v8a libOpenCL.so when experimental OpenCL is enabled"
    }
} else {
    require(openClIncludeDirProperty == null && openClLibraryProperty == null) {
        "OpenCL provisioning properties require -PlocalLlm.experimentalOpenCl=true"
    }
}

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
                llamaCppQualificationCommit?.let { commit ->
                    arguments += "-DLOCAL_LLM_LLAMA_CPP_QUALIFICATION_COMMIT=$commit"
                }
                if (experimentalOpenCl) {
                    arguments += "-DOpenCL_INCLUDE_DIR=${openClIncludeDir!!.absolutePath}"
                    arguments += "-DOpenCL_LIBRARY=${openClLibrary!!.absolutePath}"
                }
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
