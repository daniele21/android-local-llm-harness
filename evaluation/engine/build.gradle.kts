plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.daniele21.localllm.evaluation.engine"
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
    api(project(":evaluation:contracts"))
    implementation(project(":evaluation:evaluators"))
    implementation(project(":models:model-profile"))
    implementation(project(":models:model-store"))
    implementation(project(":observability:contracts"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
}
