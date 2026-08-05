plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.daniele21.localllm.install"
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
    api(project(":models:model-profile"))
    api(project(":models:model-store"))
    api(project(":models:model-catalog"))
    api(project(":models:model-download"))
    testImplementation(libs.junit4)
}
