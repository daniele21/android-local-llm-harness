# Harnex samples

Samples demonstrate public consumption boundaries without making repository-internal modules part of the integration contract.

## Available

### `hello-harnex`

The **runnable onboarding sample** for external Android developers. It is a standalone app that consumes the published Consumer SDK, shows its exact package/signer identity, guides the user through Harnex authorization and runs one real local inference through Binder.

Start here if you want to answer: **“How does another Android app actually use Harnex?”**

```bash
./gradlew -p samples/hello-harnex :app:installDebug
```

See [`hello-harnex/README.md`](hello-harnex/README.md) for the five-minute flow.

### `external-consumer-android`

A standalone Gradle project used to prove that the published Consumer Android SDK can be consumed through normal Maven coordinates without a Harnex source checkout, composite build or project dependency.

It validates the external API/ABI and dependency surface. It is intentionally a **compatibility fixture**, not a polished product demo application.

Public dependency:

```kotlin
implementation("io.github.daniele21.localllm:consumer-android:0.1.0-alpha.11")
```

For the complete Consumer contract, lifecycle and authorization model, see [`../docs/shared-runtime/consumer-android-sdk.md`](../docs/shared-runtime/consumer-android-sdk.md).

## Sample design rules

A Harnex sample should:

- depend only on supported public artifacts/contracts;
- keep Harnex runtime/model policy out of consumer product code;
- demonstrate explicit connect/disconnect/close lifecycle where applicable;
- avoid persisting prompts or outputs as diagnostics/evidence;
- fail closed when authorization or negotiated capability is unavailable;
- remain small enough that the Harnex integration boundary is obvious.

`hello-harnex` owns developer onboarding; `external-consumer-android` owns Maven/API compatibility proof. Do not merge those responsibilities into one hidden test harness.
