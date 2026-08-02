# Phase 1 embedded API and lifecycle

This document describes the implemented Phase 1 embedded runtime API. It is based on the current public contracts and production runtime classes; it does not describe the future Binder service or Capacitor plugin.

The current integration is intentionally explicit. An application owns:

- the application and use-case identifiers;
- the exact GGUF artifact identity;
- the model and generation profiles;
- the model store location;
- the `RuntimeOrchestrator` lifetime;
- the mapping from runtime events to its own UI or domain state.

## Module responsibilities

Use these modules from an embedded Android application:

| Module | Purpose |
| --- | --- |
| `core/contracts` | Stable identifiers, client interface, requests, events, metrics and typed public errors |
| `models/model-profile` | Exact GGUF, model-load and use-case configuration |
| `models/model-store` | SHA-256 content-addressed import, lookup, verification and removal |
| `core/runtime-core` | Lifecycle orchestration, single-decode scheduling and memory policy |
| `backends/llama-cpp` | Pinned `llama.cpp` JNI implementation |
| `transports/in-process` | Thin embedded transport exposing `LocalLlmClient` |

Do not call JNI classes directly from product code. Product code should depend on `LocalLlmClient` and profile/store contracts; runtime assembly belongs in an Android integration layer.

## Resolution model

Every operation resolves an exact model through:

```text
ApplicationId + UseCaseId
        -> AppModelBinding
        -> UseCaseProfile
        -> GgufModelProfile
        -> GgufArtifact.digest
```

The runtime does not discover, substitute or silently downgrade models. A missing or invalid binding is a configuration failure.

## Minimal runtime assembly

The following example wires one application/use-case pair to one imported GGUF. The digest must be the real lowercase SHA-256 of the file, and `sizeBytes` must match the file exactly.

```kotlin
import android.content.Context
import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.contracts.UseCaseId
import io.github.daniele21.localllm.models.AppModelBinding
import io.github.daniele21.localllm.models.ArtifactSource
import io.github.daniele21.localllm.models.GenerationDefaults
import io.github.daniele21.localllm.models.GgufArtifact
import io.github.daniele21.localllm.models.GgufModelProfile
import io.github.daniele21.localllm.models.ModelProfileRegistry
import io.github.daniele21.localllm.models.OutputMode
import io.github.daniele21.localllm.models.ResolvedUseCase
import io.github.daniele21.localllm.models.UseCaseCachePolicy
import io.github.daniele21.localllm.models.UseCaseProfile
import io.github.daniele21.localllm.runtime.LlamaCppInferenceBackend
import io.github.daniele21.localllm.runtime.RuntimeOrchestrator
import io.github.daniele21.localllm.store.FileSystemModelStore
import io.github.daniele21.localllm.transport.InProcessLocalLlmClient
import java.io.File

class EmbeddedLocalLlm(
    context: Context,
    sourceGguf: File,
    sha256: String,
) : AutoCloseable {
    val applicationId = ApplicationId("example-app")
    val useCaseId = UseCaseId("assistant")

    private val artifact = GgufArtifact(
        digest = ModelDigest(sha256.lowercase()),
        fileName = sourceGguf.name,
        sizeBytes = sourceGguf.length(),
        architecture = "qwen2",
        quantization = "Q4_K_M",
        source = ArtifactSource.Imported(sourceGguf.name),
    )

    private val model = GgufModelProfile(
        id = "assistant-model-v1",
        artifact = artifact,
        contextSize = 2_048,
        batchSize = 256,
        microBatchSize = 64,
        cpuThreads = 4,
        batchThreads = 4,
        gpuLayers = 0,
    )

    private val useCase = UseCaseProfile(
        id = "assistant-use-case-v1",
        modelProfileId = model.id,
        systemPromptVersion = "assistant-system-v1",
        generationDefaults = GenerationDefaults(
            maxOutputTokens = 256,
            temperature = 0.2f,
            topP = 0.95f,
            topK = 40,
            seed = 42,
        ),
        outputMode = OutputMode.TEXT,
        cachePolicy = UseCaseCachePolicy(
            retainModelWarmMs = 0,
            reuseStatelessContext = false,
            enablePrefixSnapshot = false,
            enableDeterministicResultCache = false,
        ),
        healthSuiteId = "assistant-health-v1",
    )

    private val resolved = ResolvedUseCase(
        binding = AppModelBinding(applicationId, useCaseId, useCase.id),
        useCase = useCase,
        model = model,
    )

    private val registry = object : ModelProfileRegistry {
        override fun resolve(
            applicationId: ApplicationId,
            useCaseId: UseCaseId,
        ): ResolvedUseCase {
            require(applicationId == resolved.binding.applicationId)
            require(useCaseId == resolved.binding.useCaseId)
            require(resolved.binding.enabled)
            return resolved
        }
    }

    private val modelStore = FileSystemModelStore(
        File(context.noBackupFilesDir, "local-llm"),
    )

    private val runtime: RuntimeOrchestrator

    val client: InProcessLocalLlmClient

    init {
        modelStore.import(sourceGguf, artifact)

        val nativeLibraryDirectory = File(context.applicationInfo.nativeLibraryDir)
        runtime = RuntimeOrchestrator(
            registry = registry,
            modelStore = modelStore,
            backend = LlamaCppInferenceBackend(nativeLibraryDirectory),
        )
        client = InProcessLocalLlmClient(runtime)
    }

    override fun close() {
        runtime.close()
    }
}
```

`FileSystemModelStore.import` copies and hashes the source through a staging file, validates size and digest, and publishes it under the content-addressed store. Reimporting an identical verified artifact is deduplicated. The source GGUF should remain outside the APK unless a separate bundled-asset installation flow is designed.

## Prepare and create a session

`prepare` verifies and loads the resolved model. It reports failure as a `PrepareResult`; it does not throw for normal preparation errors.

```kotlin
val prepared = localLlm.client.prepare(
    localLlm.applicationId,
    localLlm.useCaseId,
)

check(prepared.ready) {
    "Local model preparation failed: ${prepared.detail}"
}

val sessionId = localLlm.client.createSession(
    localLlm.applicationId,
    localLlm.useCaseId,
)
```

`createSession` creates a native context for the exact resolved profile. It requires a valid installed model and may throw when configuration, integrity or native context creation fails.

A session is bound to one `ApplicationId` and `UseCaseId`. Requests with a different binding are rejected.

## Generate and stream

`generate` returns immediately with a `GenerationHandle`. Generation is serialized by the single-decode scheduler and emits lifecycle events through the supplied listener.

```kotlin
import io.github.daniele21.localllm.contracts.GenerationEvent
import io.github.daniele21.localllm.contracts.GenerationListener
import io.github.daniele21.localllm.contracts.GenerationOverrides
import io.github.daniele21.localllm.contracts.GenerationRequest
import io.github.daniele21.localllm.contracts.RequestId
import java.util.UUID

val request = GenerationRequest(
    requestId = RequestId(UUID.randomUUID().toString()),
    sessionId = sessionId,
    applicationId = localLlm.applicationId,
    useCaseId = localLlm.useCaseId,
    input = "Summarize this note in one sentence.",
    overrides = GenerationOverrides(
        maxOutputTokens = 96,
        temperature = 0.1f,
        seed = 42,
    ),
)

val handle = localLlm.client.generate(
    request,
    GenerationListener { event ->
        when (event) {
            is GenerationEvent.Queued -> {
                // event.position is the initial queue position.
            }

            is GenerationEvent.Started -> {
                // event.modelDigest identifies the exact loaded artifact.
            }

            is GenerationEvent.TextDelta -> {
                // Append event.text to the visible response.
            }

            is GenerationEvent.Completed -> {
                val output = event.output
                val metrics = event.metrics
                // Persist only the data allowed by the application's privacy policy.
            }

            is GenerationEvent.Failed -> {
                when (event.error.code) {
                    "CANCELLED" -> Unit
                    else -> reportLocalInferenceFailure(event.error)
                }
            }
        }
    },
)
```

Listener callbacks are not an Android main-thread API. Dispatch UI updates to the application's main-thread mechanism.

A request receives one terminal event: `Completed` or `Failed`. Applications should treat `Queued`, `Started` and `TextDelta` as intermediate events and finalize UI/resource state only on a terminal event.

## Cancellation

Cancellation is cooperative and idempotent from the caller's perspective:

```kotlin
handle.cancel()
```

A queued request is removed before execution. A running request signals the native backend and finishes with `GenerationEvent.Failed(LocalLlmError.Cancelled)` when cancellation is observed.

Do not assume cancellation is instantaneous. Keep the session and runtime alive until the terminal event arrives.

## Session and runtime shutdown

Close a session when its conversational/native context is no longer needed:

```kotlin
localLlm.client.closeSession(sessionId)
```

When a request is active, session release is deferred until that request reaches its terminal path. Repeated `closeSession` calls are safe for an already absent session.

Close the runtime at the owning Android component or application scope:

```kotlin
localLlm.close()
```

`RuntimeOrchestrator.close` cancels active work, closes the scheduler, releases contexts when safe, unloads the model, shuts down the backend and clears integrity-cache state. It is idempotent.

## Runtime inspection

The stable client snapshot exposes high-level state without native handles:

```kotlin
val snapshot = localLlm.client.runtimeSnapshot()

snapshot.state
snapshot.loadedModel
snapshot.activeSessions
snapshot.queuedRequests
```

Possible states are `IDLE`, `PREPARING`, `READY`, `GENERATING`, `DEGRADED` and `FAILED`.

Backend pointers and `llama.cpp` structures must never cross the public contract boundary.

## Model switching

Phase 1 defaults to one loaded model and one active decode. A different resolved model cannot replace the loaded model while sessions, queued requests or active generation own runtime resources.

To switch models safely:

1. stop submitting new requests;
2. cancel or complete active requests;
3. close all sessions;
4. wait until the runtime snapshot reports no active sessions or queued work;
5. resolve the new binding and call `prepare` or `createSession`.

Model selection must remain explicit in the registry. Do not implement fallback by silently changing a digest or profile.

## Memory-pressure integration

Android integrations may register `AndroidMemoryPressureCallbacks` against the application or service lifecycle. The runtime policy can unload an idle model or cancel and release resources under critical pressure.

Resource release under memory pressure can be deferred while active work is reaching a safe terminal state. Product code should surface recoverable degradation instead of assuming every trim callback immediately unloads native memory.

## Error handling

Public generation failures use these stable categories:

| Code | Meaning |
| --- | --- |
| `CONFIGURATION` | Invalid runtime, session or request binding/state |
| `MODEL_UNAVAILABLE` | Required model is not installed or available |
| `NATIVE_RUNTIME` | JNI, `llama.cpp`, model-load, context or generation failure |
| `CANCELLED` | Queued or active generation was cancelled |

Model import failures are reported separately through `ModelImportException` and `ModelImportErrorCode`, including invalid source, invalid digest, size mismatch, digest mismatch, destination conflict and I/O failure.

Do not parse free-form error messages to drive product behavior. Use the typed error/category first and retain messages for diagnostics.

## Current Phase 1 limits

- embedded in-process runtime only;
- Android `arm64-v8a` CPU backend;
- one loaded model and one active decode by default;
- no production Binder/AIDL service;
- no Capacitor plugin yet;
- no automatic model download manager;
- no automatic model selection or fallback;
- no guarantee of broad device/model compatibility without matrix evidence.

Validate the exact target device, model and quantization through [`device-e2e-testing.md`](device-e2e-testing.md) and capture acceptance evidence with [`device-e2e-evidence.md`](device-e2e-evidence.md).
