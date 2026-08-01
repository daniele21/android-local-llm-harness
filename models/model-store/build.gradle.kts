plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.daniele21.localllm.store"
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
    api(project(":models:model-profile"))
}
