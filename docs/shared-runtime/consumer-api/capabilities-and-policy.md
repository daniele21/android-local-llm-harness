# Consumer capabilities and policy

Status: active
Document type: feature-specification
Owner: shared-runtime-consumer-api
Canonical scope: shared-runtime.consumer-api.capabilities-policy
Read when: defining model/preset discovery, defaults, allowlists, reasoning support or use-case policy
Last reviewed: 2026-08-13

## Goal

Define the host-owned policy and discovery surface that lets a consumer choose useful inference options without exposing global Harness state or unrestricted runtime tuning.

This document owns what the host advertises and how requests are authorized/resolved. The Kotlin lifecycle belongs to [`public-surface-v1.md`](public-surface-v1.md).

## Policy model

Capability discovery is always scoped by the authenticated caller and explicit use case:

```text
verified package/signing identity
  -> ApplicationId
  -> UseCaseId
  -> allowed model choices
  -> valid presets for each model
  -> reasoning/output/session capabilities
  -> limits and defaults
```

The host must not return a global list and expect the client to filter it.

## Capability response

The response should contain only information needed to construct a valid consumer request.

Recommended groups:

### Identity

- `UseCaseId`;
- capability schema/revision identifier;
- optional human-readable use-case label for reference/demo clients.

### Models

For each authorized logical model:

- stable consumer model ID;
- display name;
- concise size/quantization label when useful;
- readiness state relevant to the caller;
- supported consumer capabilities;
- whether it is the default/recommended choice.

Do not expose install paths, download URLs, host-global storage usage or unrelated installed models.

### Presets

For each selectable preset:

- `InferencePresetRef` ID/version;
- display label;
- concise semantic description such as latency/quality intent;
- compatible logical model IDs or model family constraint;
- whether it is the default;
- high-level capabilities affected when relevant.

Do not expose the entire raw sampling/runtime configuration as the normal discovery response.

### Reasoning

Advertise the consumer-visible capability, not an implementation guess.

A useful model is:

```text
NOT_SUPPORTED
SUPPORTED_NOT_SURFACED
SURFACED_OPTIONAL
SURFACED_REQUIRED_BY_POLICY   [only if a real use case needs it]
```

For normal use cases, `SURFACED_OPTIONAL` plus a default preference is sufficient.

### Output constraints

Advertise the supported subset of:

- text;
- JSON;
- JSON schema.

Schema length and transport bounds remain host-defined.

### Session capabilities

Advertise supported session kinds and bounded message/input limits. Context size remains an effective host configuration rather than a normal consumer control.

## Consumer model identity

A logical model ID is a stable product-level selector, for example:

```text
qwen35-0.8b-q4
qwen35-2b-q4
```

It is not required to equal a catalog filename or SHA-256 digest.

Harness owns the mapping:

```text
ConsumerModelId
  -> approved catalog/model profile
  -> exact artifact digest
  -> installed path
  -> runtime tuning/profile
```

This indirection allows artifact replacement or metadata evolution under explicit version/compatibility policy without exposing filesystem contracts to every client.

## Readiness semantics

The consumer needs enough state to understand whether a selectable option can be used now.

Candidate public states:

| State | Consumer meaning |
| --- | --- |
| `READY` | Can be prepared under current host state. |
| `AVAILABLE_REQUIRES_LOAD` | Installed/authorized and may require host preparation/load. |
| `UNAVAILABLE_NOT_INSTALLED` | Authorized choice exists but required artifact is absent. |
| `UNAVAILABLE_HOST_POLICY` | Temporarily not usable under host policy/device conditions. |

Avoid exposing internal lifecycle states that do not change a consumer action.

Whether `UNAVAILABLE_NOT_INSTALLED` should be listed or omitted is a product decision: listing it helps a reference client explain what is missing, but ordinary consumers may only need currently usable options.

## Model choice rules

1. Omitted model -> use the advertised default.
2. Advertised allowed model -> resolve exact host-owned artifact/profile.
3. Authorized but currently unavailable model -> typed `MODEL_UNAVAILABLE`.
4. Model not in the caller/use-case capability set -> typed `MODEL_NOT_ALLOWED`.
5. Never silently substitute another model after an explicit selection.
6. Never initiate model download merely because a consumer asked for inference.

## Preset semantics

A preset is a versioned behavior contract, not a bag of UI defaults.

A preset can internally own:

- sampling parameters;
- context policy;
- output-token budget;
- thinking/reasoning defaults;
- generation guards;
- runtime tuning profile selection.

The exact internal fields may differ by model while preserving the advertised product intent.

Example product semantics:

| Preset | Intent | Typical trade-off |
| --- | --- | --- |
| `fast` | Minimize latency/resources. | Shorter output or lower-cost generation behavior. |
| `balanced` | Default general-purpose behavior. | Middle ground. |
| `quality` | Favor answer quality where device/model support it. | Higher latency/resource budget. |
| `deterministic` | Stable evaluation/classification behavior. | Less sampling variability. |

These names are illustrative; capability discovery is authoritative.

## Preset compatibility

Preset validity is evaluated after model resolution.

```text
requested/default model
  -> allowed preset set for model + use case
  -> requested/default preset
  -> effective host configuration
```

If a requested preset is not valid for the selected model/use case, return a typed incompatibility. Do not switch the model or preset silently.

## Defaults and recommendation

Different concepts should remain separate:

- **default** — what Harness uses when the consumer omits a choice;
- **recommended** — what the host suggests in a UI, potentially based on reviewed device/model evidence;
- **available** — what policy permits and current host state can serve.

A default must always be valid if the use case is advertised as ready. If no valid default is available, prepare fails explicitly.

## Device-aware policy

Harness may use device capability/tier internally to decide which model/preset combinations are safe or recommended.

The consumer should not reproduce RAM/thermal heuristics. Instead the capability set already reflects host policy.

If device conditions change materially, Harness may return a new capability revision or typed runtime availability change. Existing sessions must follow deterministic lifecycle rules rather than silently changing model/preset mid-generation.

## Reasoning policy

Reasoning support is the intersection of:

```text
model capability
AND backend parsing/support
AND use-case policy
AND selected preset/effective mode
AND negotiated protocol feature
```

The consumer must not infer reasoning support only from a model name.

If reasoning is requested but unsupported by any layer, fail or downgrade only according to an explicit request/default rule. Preferred rule for explicit requests: fail typed rather than silently dropping requested surfaced reasoning.

## Output policy

Output constraints are also use-case scoped.

Examples:

```text
assistant-chat       -> TEXT
transaction-classify -> JSON / JSON_SCHEMA
summary              -> TEXT
```

A generic use case should not automatically gain arbitrary JSON-schema execution merely because the core backend can parse a schema.

## Limits

Capability discovery should expose stable consumer-relevant bounds such as:

- maximum input characters/messages;
- maximum schema size where supported;
- supported session kinds;
- perhaps high-level output budget choices if v1 introduces them.

Do not expose raw queue size, RAM thresholds, native context allocation or backend batch limits as normal consumer limits.

## Capability revision and caching

The client may cache capabilities only if it can detect staleness.

Provide a stable revision/version token covering policy-visible changes. A capability response can become stale when:

- host version/policy changes;
- model availability changes;
- app/use-case authorization changes;
- optional protocol feature negotiation changes.

The client must tolerate `NOT_ALLOWED`, `UNAVAILABLE` or incompatibility at prepare time even after a previous capability response.

## Host configuration source

The implementation should centralize consumer policy in a host-owned registry rather than scattering allowlists across Activities, Binder delegates and UI code.

Conceptually:

```kotlin
ConsumerUseCasePolicy(
    applicationId,
    useCaseId,
    models,
    defaultModel,
    presets,
    defaultPreset,
    reasoningPolicy,
    outputPolicy,
    sessionPolicy,
    limits,
)
```

Existing `AppModelBinding` / resolved use-case configuration should be reused where semantics fit. Do not create a parallel model-resolution engine.

## Reference-consumer policy

The first product-shaped reference consumer is the OMBRA PDF/PII application defined in [`pii-redactor/`](pii-redactor/). Its initial host policy should remain deliberately narrow:

```text
use case: document-pii-detection
model: one reviewed default logical model for the device/host policy
preset: one deterministic host-owned default
reasoning: disabled / not surfaced
output: JSON_SCHEMA required
session: STATELESS
```

Model alternatives and additional presets are not useful primary product choices for this workflow. They may be advertised later only after the generic policy tests and PII quality evidence justify them. Exact artifact support follows the reviewed curated catalog/runtime evidence, not the application plan.

## Authorization and information minimization

Capability discovery is itself an information surface. Therefore:

- authenticate before returning use-case capabilities;
- reject unauthorized use cases without revealing their model configuration;
- return only caller-scoped model labels/status;
- scrub backend/file/path details;
- keep signing/package diagnostics out of ordinary success responses;
- rate/bound discovery requests if necessary to preserve service robustness.

## Deterministic tests

Required test classes include:

- authorized caller receives only its use-case capability set;
- different callers cannot enumerate each other's use cases/models;
- omitted model/preset resolves defaults;
- allowed model + allowed preset resolves exact expected policy;
- explicit unavailable model fails without fallback/download;
- unauthorized model fails before model load;
- incompatible preset fails without substitution;
- unsupported reasoning/output request fails typed;
- stale capability selection is revalidated during prepare;
- capability response contains no path/URL/native/private diagnostic data.

## Acceptance criteria

This workstream is ready to implement when:

- logical model identity and readiness semantics are accepted;
- default versus recommended semantics are explicit;
- preset versioning/compatibility rules are accepted;
- reasoning/output support is use-case scoped;
- capability caching/staleness behavior is defined;
- host policy has one implementation owner;
- unauthorized discovery cannot leak global inventory;
- every optional consumer choice can be validated before native generation starts.
