# Public Consumer API v1 surface

Status: active
Document type: feature-specification
Owner: shared-runtime-consumer-api
Canonical scope: shared-runtime.consumer-api.surface-v1
Read when: designing Kotlin client methods, Binder mappings or consumer request/session contracts
Last reviewed: 2026-08-13

## Goal

Define a small application-facing inference surface that preserves the existing `LocalLlmClient` lifecycle while adding capability discovery and constrained selection without exposing the internal generation/runtime superset.

This document owns public API shape and lifecycle semantics. Capability contents belong to [`capabilities-and-policy.md`](capabilities-and-policy.md); result semantics belong to [`results-and-metrics.md`](results-and-metrics.md).

## Design constraints

The public surface must:

- remain backend-neutral;
- keep generated AIDL/Parcelable types internal to the Binder transport;
- derive caller identity at the host boundary;
- preserve explicit `UseCaseId`;
- support default-only consumers with minimal setup;
- allow optional host-advertised model/preset choice;
- make session/request ownership deterministic;
- stream content without forcing the app to reconstruct transport details;
- keep low-level tuning out of ordinary consumer code;
- map failures to stable typed outcomes.

## Public lifecycle

Preferred v1 flow:

```text
connect / negotiate
  -> capabilities(useCase)
  -> prepare(useCase, optional selection)
  -> createSession(prepared selection, options)
  -> generate(input, output request, listener)
  -> cancel or terminal result
  -> closeSession
```

A minimal app may accept all defaults:

```text
capabilities(useCase)
  -> prepare(useCase)
  -> createSession(...)
  -> generate(prompt)
```

The SDK may provide convenience helpers later, but the explicit lifecycle remains testable and observable.

## Proposed public concepts

Names below are planning names; implementation may adapt them to existing core naming if semantics remain exact.

### UseCaseCapabilities

Describes only options the verified application is authorized to use for one `UseCaseId`.

Conceptual fields:

```kotlin
data class UseCaseCapabilities(
    val useCaseId: UseCaseId,
    val models: List<ConsumerModelOption>,
    val defaultModelId: ConsumerModelId?,
    val presets: List<ConsumerPresetOption>,
    val defaultPreset: InferencePresetRef?,
    val reasoning: ReasoningCapability,
    val outputConstraints: Set<ConsumerOutputConstraintKind>,
    val sessionKinds: Set<SessionKind>,
    val limits: ConsumerLimits,
    val capabilityRevision: String,
)
```

The capability object is not a global Harness inventory. It is already filtered by caller/use-case policy.

### ConsumerSelection

Represents consumer intent without raw runtime tuning.

```kotlin
data class ConsumerSelection(
    val modelId: ConsumerModelId? = null,
    val preset: InferencePresetRef? = null,
    val reasoning: ConsumerReasoningPreference = DEFAULT,
)
```

`null` means “use the advertised default”, not “choose any model”.

### ConsumerPrepareRequest

```kotlin
data class ConsumerPrepareRequest(
    val useCaseId: UseCaseId,
    val selection: ConsumerSelection = ConsumerSelection(),
)
```

The host resolves it to one effective allowed configuration.

### ConsumerPrepareResult

Should return enough identity for the consumer to understand what was accepted without leaking artifact paths or full runtime internals.

Conceptually:

```kotlin
data class ConsumerPrepareResult(
    val ready: Boolean,
    val effectiveModel: ConsumerModelIdentity?,
    val effectivePreset: InferencePresetRef?,
    val reasoningMode: EffectiveReasoningMode?,
    val capabilityRevision: String,
    val detailCode: ConsumerPrepareCode,
)
```

The host may retain exact model digest internally or expose a privacy-safe artifact identity only in request details if release/security review accepts it.

## Caller identity

The external app must not be trusted to provide its own `ApplicationId` as authorization input.

At the Binder host:

```text
calling UID/package/signing lineage
  -> authenticated host application identity
  -> application policy
```

The high-level client SDK may keep an application identity concept for embedded/in-process compatibility, but the shared-host implementation derives and validates it at the transport boundary.

## UseCaseId

`UseCaseId` remains explicit and required because it is the primary policy/routing key.

A single app may have multiple use cases with different allowed models, presets, output modes and reasoning policies.

Example:

```text
mail-summary      -> small model + balanced/fast + text
mail-reply        -> larger model + quality + text
mail-classify     -> small model + deterministic + JSON
```

The consumer cannot invent a new use case and receive generic inference automatically.

## Model selection semantics

A model choice is a logical `ConsumerModelId` advertised by capabilities.

The public API does not accept:

- `ModelDigest` as a load command;
- model path;
- URL;
- quantization override;
- arbitrary runtime profile ID.

The host maps the logical choice to its exact artifact/profile policy.

### Availability

Capability discovery may describe availability such as:

```text
READY
AVAILABLE_REQUIRES_LOAD
UNAVAILABLE_NOT_INSTALLED
UNAVAILABLE_POLICY
```

Only states that are useful and privacy-safe for the current caller should be exposed. The exact enum is owned by the capability-policy workstream.

An explicitly selected unavailable model fails predictably; Harness does not silently pick a different model.

## Preset selection semantics

A preset is a versioned host-defined contract represented by `InferencePresetRef` or a compatible consumer projection.

The consumer does not set the preset’s individual sampling/runtime fields. A selected preset must be valid for the selected/default model and use case.

If the preset is omitted, the host resolves the advertised default.

If a preset is requested but incompatible with the selected model, fail with a typed configuration error rather than silently changing either choice.

## Reasoning preference

The public request needs a small preference, for example:

```kotlin
enum class ConsumerReasoningPreference {
    DEFAULT,
    DISABLED,
    SURFACED_IF_SUPPORTED,
}
```

A stronger `REQUIRED` mode may be added only if product use cases need fail-closed reasoning support.

The preference controls whether an intentionally surfaced reasoning channel may be returned; it does not expose arbitrary hidden model internals.

## Output request

Reuse the existing text/JSON/JSON-schema concepts where wire and security limits permit.

Conceptually:

```kotlin
sealed interface ConsumerOutputConstraint {
    data object Text
    data object Json
    data class JsonSchema(val schema: String)
}
```

Schema size and syntax remain bounded by Harness policy. A use case may allow only a subset.

## Generation request

The ordinary consumer generation request should be narrower than core `GenerationRequest`.

Conceptually:

```kotlin
data class ConsumerGenerationRequest(
    val requestId: RequestId,
    val sessionId: SessionId,
    val input: GenerationInput,
    val outputConstraint: ConsumerOutputConstraint = Text,
)
```

Model/preset/reasoning selection is preferably resolved during prepare/session creation rather than repeated as mutable raw request options.

This makes a conversational session’s execution identity stable and simplifies policy enforcement.

## Session options

Preserve `STATELESS` and `CONVERSATIONAL` where advertised.

Public session options should expose only stable product semantics such as session kind. Manual context sizing should remain host-owned for ordinary cross-app consumers unless a separate requirement proves it belongs in public v1.

Preferred shape:

```kotlin
data class ConsumerSessionOptions(
    val kind: SessionKind = STATELESS,
)
```

Harness owns effective context size and reuse policy through model/preset/use-case configuration.

## Public client methods

A likely evolved high-level surface is:

```kotlin
interface SharedLocalLlmClient {
    fun connectionState(): SharedRuntimeConnectionState

    fun capabilities(useCaseId: UseCaseId): UseCaseCapabilities

    fun prepare(request: ConsumerPrepareRequest): ConsumerPrepareResult

    fun createSession(
        useCaseId: UseCaseId,
        options: ConsumerSessionOptions = ConsumerSessionOptions(),
    ): SessionId

    fun generate(
        request: ConsumerGenerationRequest,
        listener: ConsumerGenerationListener,
    ): GenerationHandle

    fun closeSession(sessionId: SessionId)
}
```

Implementation must evaluate whether this becomes an additive interface, an evolution of `LocalLlmClient`, or a consumer facade layered over it. Avoid breaking embedded consumers merely to make Binder naming cleaner.

## Event surface

The public listener should preserve ordered lifecycle events but may project the internal superset.

Required semantic events:

```text
Queued (optional if queueing is exposed)
Prepared / Started identity
ReasoningDelta (when surfaced)
AnswerDelta
Completed(result + metrics)
Failed(error)
```

Transport-only details such as callback sequence bookkeeping remain internal.

## Effective configuration disclosure

The consumer needs to know which logical choices actually ran:

- effective logical model ID/name;
- effective preset ID/version;
- effective reasoning mode;
- output constraint;
- request/session identifiers as needed.

It does not need raw temperature/top-p/top-k/context/thread/batch values by default. Those remain request-detail or Harness diagnostic information only if explicitly approved.

## Cancellation

`GenerationHandle.cancel()` remains first-class.

Cancellation must:

- apply only to the caller-owned request;
- converge to exactly one terminal outcome;
- preserve the same request ID;
- release request resources;
- leave the host reusable for subsequent requests.

## Compatibility strategy

The Binder protocol already negotiates major/minor/features. Implementation must classify each API addition as:

- SDK-only facade change;
- backward-compatible optional protocol feature;
- incompatible protocol semantic change.

Capability discovery and optional selection should prefer feature-negotiated additive wire fields where semantics remain unambiguous. If enabling client model choice changes an accepted invariant that older hosts cannot interpret safely, increment the appropriate compatibility boundary instead of pretending it is a minor feature.

## Migration from current Console proof

The current proof client can be migrated into the OMBRA reference consumer in stages:

1. keep existing fixed `console-inference-playground` use case;
2. expose one capability response with one default model/preset;
3. switch Console UI to capability-driven display;
4. add the authorized `document-pii-detection` use case with deterministic defaults and `JSON_SCHEMA` capability;
5. remove Console-local model/health/runtime ownership and raw generation controls unrelated to consumer behavior;
6. implement the product flow owned by [`pii-redactor/`](pii-redactor/) without adding PII-specific public SDK types;
7. use the packaged OMBRA application as the reference consumer for client SDK validation.

## Acceptance criteria

The public surface is ready for implementation when:

- required versus optional consumer choices are agreed;
- session selection semantics are deterministic;
- application identity is not client-trusted;
- model/preset selection cannot bypass allowlists;
- no raw llama.cpp/runtime tuning leaks into the ordinary consumer contract;
- reasoning and answer are independently representable;
- output constraints are use-case bounded;
- cancellation and typed terminal behavior remain intact;
- compatibility impact is classified before wire changes begin;
- the surface can be demonstrated by a consumer app with no model/runtime implementation.
