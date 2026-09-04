import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import java.io.FileInputStream
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Exec

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

fun Project.readPropertiesFile(fileName: String): Properties? {
    val file = rootProject.file(fileName)
    if (!file.exists()) return null
    return Properties().apply { FileInputStream(file).use(::load) }
}

fun ProviderFactory.environmentOrNull(name: String): String? = environmentVariable(name).orNull?.trim()?.takeIf(String::isNotEmpty)

val versionProperties = requireNotNull(project.readPropertiesFile("apps/local-llm-phone-test/version.properties")) {
    "Missing apps/local-llm-phone-test/version.properties"
}
val configuredVersionName = requireNotNull(versionProperties.getProperty("VERSION_NAME")?.trim()?.takeIf(String::isNotEmpty)) {
    "Missing VERSION_NAME in apps/local-llm-phone-test/version.properties"
}
val configuredVersionCode = requireNotNull(versionProperties.getProperty("VERSION_CODE")?.trim()?.toIntOrNull()) {
    "Missing or invalid VERSION_CODE in apps/local-llm-phone-test/version.properties"
}
val phoneTestVersionCodeOverride = providers.gradleProperty("phoneTestVersionCode").orNull?.trim()?.takeIf(String::isNotEmpty)
val effectiveVersionCode = phoneTestVersionCodeOverride?.toIntOrNull()?.takeIf { it > 0 }
    ?: if (phoneTestVersionCodeOverride == null) {
        configuredVersionCode
    } else {
        throw GradleException("phoneTestVersionCode must be a positive integer")
    }
val phoneTestVersionNameOverride = providers.gradleProperty("phoneTestVersionName").orNull?.trim()?.takeIf(String::isNotEmpty)
val effectiveVersionName = phoneTestVersionNameOverride ?: configuredVersionName

val releaseProperties = project.readPropertiesFile("release.properties")
val phoneTestUploadSigningEnvironment = mapOf(
    "storeFile" to providers.environmentOrNull("PHONE_TEST_UPLOAD_STORE_FILE"),
    "storePassword" to providers.environmentOrNull("PHONE_TEST_UPLOAD_STORE_PASSWORD"),
    "keyAlias" to providers.environmentOrNull("PHONE_TEST_UPLOAD_KEY_ALIAS"),
    "keyPassword" to providers.environmentOrNull("PHONE_TEST_UPLOAD_KEY_PASSWORD"),
)
val phoneTestUploadSigningProperties = mapOf(
    "storeFile" to releaseProperties?.getProperty("phoneTest.upload.storeFile")?.trim()?.takeIf(String::isNotEmpty),
    "storePassword" to releaseProperties?.getProperty("phoneTest.upload.storePassword")?.trim()?.takeIf(String::isNotEmpty),
    "keyAlias" to releaseProperties?.getProperty("phoneTest.upload.keyAlias")?.trim()?.takeIf(String::isNotEmpty),
    "keyPassword" to releaseProperties?.getProperty("phoneTest.upload.keyPassword")?.trim()?.takeIf(String::isNotEmpty),
)
val phoneTestUploadSigning = phoneTestUploadSigningEnvironment.mapValues { (key, value) ->
    value ?: phoneTestUploadSigningProperties[key]
}
val phoneTestUploadSigningConfigured = phoneTestUploadSigning.values.all { it != null }
val allowUnsignedRelease = providers.environmentOrNull("LOCAL_LLM_PHONE_TEST_ALLOW_UNSIGNED_RELEASE") == "true"
if (!phoneTestUploadSigningConfigured && !allowUnsignedRelease) {
    logger.lifecycle(
        "Phone test upload signing is not configured. Release bundle tasks require signing unless " +
            "LOCAL_LLM_PHONE_TEST_ALLOW_UNSIGNED_RELEASE=true is set for local validation.",
    )
}

val sharedRuntimeReleasePermission = "io.github.daniele21.localllm.permission.BIND_SHARED_RUNTIME"
val sharedRuntimeDebugPermission = "io.github.daniele21.localllm.debug.permission.BIND_SHARED_RUNTIME"

android {
    namespace = "io.github.daniele21.localllm.phonetest"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        applicationId = "io.github.daniele21.localllm.phonetest"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = effectiveVersionCode
        versionName = effectiveVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["sharedRuntimePermission"] = sharedRuntimeReleasePermission
        buildConfigField("String", "SHARED_RUNTIME_PERMISSION", "\"$sharedRuntimeReleasePermission\"")
    }

    signingConfigs {
        create("upload") {
            if (phoneTestUploadSigningConfigured) {
                storeFile = file(phoneTestUploadSigning.getValue("storeFile")!!)
                storePassword = phoneTestUploadSigning.getValue("storePassword")
                keyAlias = phoneTestUploadSigning.getValue("keyAlias")
                keyPassword = phoneTestUploadSigning.getValue("keyPassword")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["sharedRuntimePermission"] = sharedRuntimeDebugPermission
            buildConfigField("String", "SHARED_RUNTIME_PERMISSION", "\"$sharedRuntimeDebugPermission\"")
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
        release {
            isDebuggable = false
            isMinifyEnabled = false
            manifestPlaceholders["sharedRuntimePermission"] = sharedRuntimeReleasePermission
            buildConfigField("String", "SHARED_RUNTIME_PERMISSION", "\"$sharedRuntimeReleasePermission\"")
            ndk {
                abiFilters += "arm64-v8a"
            }
            if (phoneTestUploadSigningConfigured) {
                signingConfig = signingConfigs.getByName("upload")
            }
        }
        create("emulatorE2e") {
            initWith(getByName("debug"))
            versionNameSuffix = "-emulator-e2e"
            matchingFallbacks += listOf("debug")
            ndk {
                abiFilters.clear()
                abiFilters += "x86_64"
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        lintConfig = rootProject.file("lint.xml")
        abortOnError = true
        warningsAsErrors = false
        htmlReport = true
        sarifReport = true
        checkDependencies = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf("**/*.gguf", "**/*.ggml")
        }
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(project(":core:contracts"))
    implementation(project(":core:runtime-core"))
    implementation(project(":evaluation:contracts"))
    implementation(project(":models:model-profile"))
    implementation(project(":models:model-store"))
    implementation(project(":models:control-plane-room-store"))
    implementation(project(":models:model-catalog"))
    implementation(project(":models:model-download"))
    implementation(project(":models:model-install"))
    implementation(project(":backends:llama-cpp"))
    implementation(project(":observability:contracts"))
    implementation(project(":observability:in-memory-store"))
    implementation(project(":observability:room-store"))
    implementation(project(":observability:health-engine"))
    implementation(project(":observability:android-resource-probe"))
    implementation(project(":observability:benchmark-engine"))
    implementation(project(":transports:in-process"))
    implementation(project(":integrations:android-service-host"))
    implementation(project(":ui:design-system"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)
}

val androidComponents = extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
val releaseVariantCounter = AtomicInteger(0)
androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    releaseVariantCounter.incrementAndGet()
}

tasks.withType<Exec>().configureEach {
    if (name == "processReleaseResources" && !phoneTestUploadSigningConfigured && !allowUnsignedRelease) {
        doFirst {
            throw GradleException(
                "Phone test upload signing is required for release bundles. Configure PHONE_TEST_UPLOAD_* " +
                    "or release.properties, or set LOCAL_LLM_PHONE_TEST_ALLOW_UNSIGNED_RELEASE=true for local validation.",
            )
        }
    }
}

tasks.register("verifyPhoneTestReleaseVariant") {
    doLast {
        check(releaseVariantCounter.get() == 1) { "Expected exactly one release variant" }
    }
}
