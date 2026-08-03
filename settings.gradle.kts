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
        google()
        mavenCentral()
    }
}

rootProject.name = "android-local-llm-harness"

include(
    ":core:contracts",
    ":core:runtime-core",
    ":models:model-profile",
    ":models:model-store",
    ":backends:llama-cpp",
    ":observability:contracts",
    ":observability:in-memory-store",
    ":observability:room-store",
    ":transports:in-process",
    ":apps:local-llm-console",
    ":apps:device-test-runner",
)
