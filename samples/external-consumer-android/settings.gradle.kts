pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val consumerSdkRepositoryUrl =
    providers.gradleProperty("consumerSdkRepositoryUrl")
        .orElse("../../build/consumer-sdk-repository")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri(consumerSdkRepositoryUrl.get()) }
        google()
        mavenCentral()
    }
}

rootProject.name = "external-consumer-android"
include(":consumer")
