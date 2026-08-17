pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("../../build/consumer-sdk-repository") }
        google()
        mavenCentral()
    }
}

rootProject.name = "external-consumer-android"
include(":consumer")
