plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.daniele21.localllm.console"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.daniele21.localllm.console"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:contracts"))
    implementation(project(":observability:contracts"))
    implementation(project(":observability:in-memory-store"))
}
