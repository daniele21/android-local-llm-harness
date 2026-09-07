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
            maven {
                url = uri(consumerSdkRepositoryUrl)
                content { includeGroup("io.github.daniele21.localllm") }
            }
        }
        google()
        mavenCentral()
        maven {
            name = "harnexConsumerSdk"
            url = uri("https://raw.githubusercontent.com/daniele21/harnex/consumer-sdk-maven/maven")
            content { includeGroup("io.github.daniele21.localllm") }
        }
    }
}

rootProject.name = "hello-harnex"
include(":app")
