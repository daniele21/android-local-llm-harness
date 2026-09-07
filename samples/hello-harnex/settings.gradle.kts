pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val consumerSdkRepositoryUrl = providers.gradleProperty("consumerSdkRepositoryUrl").orNull

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (!consumerSdkRepositoryUrl.isNullOrBlank()) {
            maven { url = uri(consumerSdkRepositoryUrl) }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "hello-harnex"
include(":app")
