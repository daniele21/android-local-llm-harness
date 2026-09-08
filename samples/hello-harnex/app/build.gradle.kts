plugins {
    id("com.android.application")
}

val consumerSdkVersion = providers.gradleProperty("consumerSdkVersion").orElse("0.1.0-alpha.11")
val harnexHostPackage = providers.gradleProperty("harnexHostPackage").orElse("io.github.daniele21.localllm.phonetest.debug")

android {
    namespace = "io.github.daniele21.harnex.hello"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.daniele21.harnex.hello"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        manifestPlaceholders["harnexHostPackage"] = harnexHostPackage.get()
        buildConfigField("String", "HARNEX_HOST_PACKAGE", "\"${harnexHostPackage.get()}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("io.github.daniele21.localllm:consumer-android:${consumerSdkVersion.get()}")

    implementation("androidx.activity:activity:1.12.1")
    implementation("androidx.lifecycle:lifecycle-livedata-core:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.9.4")

    testImplementation("junit:junit:4.13.2")
}
