plugins {
    id("com.android.application")
}

val consumerSdkVersion = providers.gradleProperty("consumerSdkVersion").orElse("0.1.0-SNAPSHOT")

android {
    namespace = "io.github.daniele21.localllm.externalfixture.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.daniele21.localllm.externalfixture.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("io.github.daniele21.localllm:consumer-android:${consumerSdkVersion.get()}")
}
