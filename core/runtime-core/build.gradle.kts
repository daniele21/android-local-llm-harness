plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.daniele21.localllm.runtime"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:contracts"))
    implementation(project(":models:model-profile"))
    implementation(project(":models:model-store"))
    implementation(project(":backends:llama-cpp"))
    implementation(project(":observability:contracts"))
}
