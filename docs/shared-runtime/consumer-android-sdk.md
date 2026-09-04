# Consumer Android SDK publication

Status: active
Document type: feature-specification
Owner: shared-runtime-client
Canonical scope: shared-runtime.consumer-android-sdk
Read when: publishing, versioning, validating or consuming the external Android Consumer SDK artifact
Last reviewed: 2026-09-04

## Public dependency

External Android applications consume one direct coordinate:

```kotlin
implementation("io.github.daniele21.localllm:consumer-android:<version>")
```

The publication carries `core-contracts` and the Binder contract transitively. Consumers must not use `project(...)`, composite builds, git submodules or a Harness source checkout.

Current candidate: `0.1.0-alpha.10`.

## Published artifacts

- `io.github.daniele21.localllm:core-contracts`
- `io.github.daniele21.localllm:android-binder-contract`
- `io.github.daniele21.localllm:consumer-android`

`consumer-android` is the supported direct dependency. The other artifacts preserve ordinary Maven dependency metadata rather than producing a fat AAR.

## Supported boundary

The SDK owns public Consumer contracts, Binder composition, typed transport failures, passive control-plane/readiness inspection and the explicit durable logical-job API. It does not expose model-store/runtime/llama.cpp implementation types and does not grant authorization by itself; package/application/use-case/signing policy remains host-owned.

Ordinary `prepare/createSession/generate` remains connection-scoped for compatibility. Long-running work that must outlive transient Binder/UI observation opts into `ConsumerLogicalJobClient`:

```kotlin
val response = client.submitLogicalGeneration(
    ConsumerLogicalJobSubmitRequest(
        clientRequestId = ConsumerLogicalJobRequestId("analysis-42-chunk-0"),
        useCaseId = prepared.useCaseId,
        preparedId = prepared.preparedId,
        expectedExecution = prepared.toExecutionIdentity(),
        input = ConsumerGenerationInput.Text(input),
        outputConstraint = ConsumerOutputConstraint.JsonSchema(schema),
    ),
)
```

The accepted job returns a stable `ConsumerInferenceJobId`. After a transport reconnect, authenticated callers use `logicalJob(...)` and `logicalJobResult(...)` with that same ID/use-case instead of submitting duplicate inference. `cancelLogicalJob(...)` is explicit semantic cancellation.

The submit request pins the exact prepared `ConsumerExecutionIdentity`; the Host rejects mismatched scope/configuration rather than silently resolving a durable job against newer capability/preset state. Revisioned query/result snapshots are authoritative after reconnect. Binder callback/endpoint loss is transport loss, not implicit logical-job cancellation.

The logical-job contract is protocol minor 6 (`consumer-logical-jobs-v1`). Setup resolution remains protocol minor 5.

## Privacy and recovery boundary

Logical-job identifiers, revisions, attempts, runtime-session identity and safe error/state metadata are privacy-safe. The SDK contract does not authorize persistence of prompts, document text, findings, generated output, raw Binder payloads or native/KV state.

Host process death remains a truthful native interruption boundary. A later recovery attempt may restart only when the owning workflow still has safe input under its privacy policy; alpha.8 does not promise token-exact or sensitive-input-transparent resume.

## Publication verification

Run:

```bash
bash scripts/verify-consumer-sdk-publication.sh
```

The verification publishes release variants to a run-owned local Maven repository under `build/consumer-sdk-repository`, then builds the separate `samples/external-consumer-android` project using Maven coordinates only. A successful run also writes source-aware manifest/checksum evidence.

Public API/ABI compatibility is deterministic and already gated. The canonical baseline is:

```text
docs/shared-runtime/consumer-sdk-public-abi.txt
```

Validation generates the current ABI with `scripts/dump-consumer-sdk-abi.sh` and compares it using `scripts/verify-consumer-sdk-abi.sh`. Intentional public changes require inspection of the generated surface and an explicit baseline update; the gate must not be suppressed.

## GitHub Packages publication

`.github/workflows/publish-consumer-sdk.yml` owns real publication. It validates external consumption and ABI before publishing all three Maven artifacts to GitHub Packages. A push to `dev` that changes `docs/shared-runtime/consumer-sdk-version.txt` resolves the version from that file; workflow dispatch may provide an explicit version.

For the current alpha.10 candidate the correct sequence is:

1. exact-head PR documentation/validation/preflight is green;
2. merge the owning change to `dev`;
3. `Publish Consumer Android SDK` validates and publishes `0.1.0-alpha.10` from `dev`;
4. downstream apps update their Maven dependency only after publication succeeds.

Do not treat the pull-request `Consumer SDK validation` workflow as package publication: it uses a run-specific `0.1.0-ci.<run_id>` local repository solely to prove external consumption and ABI compatibility.
