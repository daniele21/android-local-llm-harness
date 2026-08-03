plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.daniele21.localllm.observability.health"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        lintConfig = rootProject.file("lint.xml")
        abortOnError = true
    }
}

dependencies {
    api(project(":observability:contracts"))
    implementation(project(":core:contracts"))
    implementation(project(":models:model-store"))

    testImplementation(project(":observability:in-memory-store"))
    testImplementation(libs.junit4)
}
