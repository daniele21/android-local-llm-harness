plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.daniele21.localllm.runtime"
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
        htmlReport = true
        sarifReport = true
    }
}

dependencies {
    api(project(":core:contracts"))
    api(project(":core:backend-spi"))
    implementation(project(":models:model-profile"))
    implementation(project(":models:model-store"))
    implementation(project(":observability:contracts"))

    testImplementation(project(":observability:in-memory-store"))
    testImplementation(libs.junit4)
}
