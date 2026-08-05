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
    ":models:model-catalog",
    ":models:model-download",
    ":models:model-install",
    ":backends:llama-cpp",
    ":observability:contracts",
    ":observability:in-memory-store",
    ":observability:room-store",
    ":observability:health-engine",
    ":observability:android-resource-probe",
    ":observability:benchmark-engine",
    ":transports:in-process",
    ":ui:design-system",
    ":apps:local-llm-console",
    ":apps:device-test-runner",
    ":apps:local-llm-phone-test",
)
