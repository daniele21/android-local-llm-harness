plugins {
    id("com.android.library")
}

val consumerSdkVersion = providers.gradleProperty("consumerSdkVersion").orElse("0.1.0-SNAPSHOT")

android {
    namespace = "io.github.daniele21.localllm.externalfixture"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api("io.github.daniele21.localllm:consumer-android:${consumerSdkVersion.get()}")
}
