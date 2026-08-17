# Consumer Android SDK publication

Status: active
Document type: feature-specification
Owner: shared-runtime-client
Canonical scope: shared-runtime.consumer-android-sdk
Read when: publishing, versioning, validating or consuming the external Android Consumer SDK artifact
Last reviewed: 2026-08-17

## Public dependency

External Android applications should consume one direct coordinate:

```kotlin
implementation("io.github.daniele21.localllm:consumer-android:<version>")
```

The publication carries `core-contracts` and the Binder contract transitively. Consumers must not use `project(...)`, composite builds, git submodules or a Harness source checkout.

## Published artifacts

- `io.github.daniele21.localllm:core-contracts`
- `io.github.daniele21.localllm:android-binder-contract`
- `io.github.daniele21.localllm:consumer-android`

`consumer-android` is the supported direct dependency. The other two artifacts exist to preserve ordinary Maven dependency metadata rather than producing a fat AAR.

## Local publication verification

Run:

```bash
bash scripts/verify-consumer-sdk-publication.sh
```

The verification has two stages: publish the release variants to the run-owned local Maven repository under `build/consumer-sdk-repository`, then invoke a separate Gradle build rooted at `samples/external-consumer-android`. That project is intentionally outside the Harness settings graph and may resolve only Maven coordinates.

A successful run also writes a source-aware build manifest and SHA-256 checksums for the generated AAR/POM/module metadata. The build directory remains ephemeral and must not be committed.

## Compatibility boundary

The SDK owns public Consumer API contracts, Binder transport composition and typed transport failures. It does not expose model-store/runtime/llama.cpp implementation types and does not grant authorization by itself; package/application/use-case/signing policy remains host-owned.

Before a stable SDK release, add a deterministic public API/ABI compatibility baseline and CI gate. That is still an HSDK-1 exit item.
