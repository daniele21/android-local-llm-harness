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
    ":evaluation:contracts",
    ":evaluation:comparison",
    ":evaluation:dataset-adapter",
    ":evaluation:datasets",
    ":evaluation:evaluators",
    ":evaluation:engine",
    ":evaluation:in-memory-store",
    ":evaluation:persistence",
    ":evaluation:room-store",
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
    ":transports:android-binder-contract",
    ":transports:android-binder-client",
    ":integrations:android-service-host",
    ":ui:design-system",
    ":apps:local-llm-console",
    ":apps:shared-runtime-client-consumer-fixture",
    ":apps:device-test-runner",
    ":apps:local-llm-phone-test",
)
