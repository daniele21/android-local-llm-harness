# Public Consumer API target

Status: active
Document type: target-specification
Owner: shared-runtime-consumer-api
Canonical scope: shared-runtime.consumer-api.target
Read when: deciding what Local LLM Harness owns versus a calling Android application
Last reviewed: 2026-08-13

## Problem

A separately installed Android app should use Harness as a governed local inference capability without becoming responsible for model distribution, llama.cpp configuration or runtime lifecycle.

The consumer needs enough choice to express product intent, but not enough low-level control to bypass Harness policy or force every app to tune the runtime independently.

The accepted CA-0 boundary is recorded in [ADR 0013](../../adr/0013-public-consumer-capability-boundary.md) and preserves the host-owned model authority accepted by [ADR 0012](../../adr/0012-shared-runtime-same-signer-binder-boundary.md).

## Core rule

> The consumer chooses an authorized use case and approved product-level options; Harness resolves and executes the effective model and runtime configuration.

The consumer controls product intent. Harness controls model authority and runtime policy.

## Responsibility split

| Concern | Consumer app | Harness |
| --- | --- | --- |
| Identity | Installs as an authorized package. | Derives identity from verified Binder caller/signing policy. |
| Use case | Chooses an allowlisted `UseCaseId`. | Owns policy attached to that use case. |
| Input | Provides bounded text/messages. | Validates, templates and tokenizes. |
| Model | Does not choose an artifact or model selector in v1. | Resolves exact model, artifact, installation, readiness, loading and residency. |
| Preset | Chooses an advertised versioned preset or accepts default when alternatives exist. | Owns effective sampling/runtime configuration behind the preset. |
| Reasoning | Requests a supported surfaced mode when advertised. | Decides whether reasoning may be exposed and how it is separated/streamed. |
| Output | Requests a supported text/JSON/schema constraint. | Validates and enforces it. |
| Raw tuning | No normal control. | Owns sampling knobs, context, threads, batch, template and backend tuning. |
| Lifecycle | Prepares, opens/closes sessions and cancels own requests. | Owns scheduling, isolation, cleanup and process-death behavior. |
| Metrics | Consumes stable request metrics. | Measures authoritative runtime metrics. |
| Diagnostics | May show request-scoped safe details. | Owns logs, health, memory, thermal, cache and benchmarks. |

## Minimal consumer choices

V1 should require only:

- `UseCaseId`;
- bounded input;
- output constraint when text is insufficient.

Preset and reasoning preference are optional when Harness advertises defaults. Model selection is not a v1 consumer control.

This keeps the simplest integration small while allowing a reference/test app to exercise authorized product-level alternatives without acquiring model-management authority.

## Host-owned model resolution

A consumer never supplies a model selector as an authorization or load instruction.

Harness resolves:

```text
verified caller
  -> ApplicationId
  + UseCaseId
  -> ConsumerUseCasePolicy
  -> AppModelBinding / UseCaseProfile
  -> exact curated model profile
  -> exact artifact + runtime configuration
```

Capability discovery may expose a privacy-safe effective model label or identity when useful for transparency or compatibility, but it does not advertise alternative requestable models in v1.

The consumer never supplies a filesystem path, model URL, arbitrary digest, GGUF metadata, native/backend details or an implicit-download instruction.

If the bound model is unavailable, return a typed availability failure. Do not silently download, substitute or let the consumer escape the use-case binding.

Client-selectable logical models require a successor architectural decision because they change the authority boundary accepted by ADR 0012 and ADR 0013.

## Preset-first tuning

Presets are the normal optional tuning surface. Product-facing identities may be `fast`, `balanced`, `quality`, `deterministic` or use-case-specific names, but Harness owns their versioned semantics.

Internally a preset may control sampling, output budget, context policy, thinking defaults, generation guards and runtime tuning. The consumer selects the preset reference, not its individual raw values.

A use case may expose only one default preset and no alternatives. This is the initial OMBRA PII policy.

The existing core `GenerationOverrides` remains useful for Harness UI, benchmarks, deterministic tests and controlled experimentation. It does not automatically become the cross-app public API.

A raw option belongs in the public surface only if it expresses a stable product requirement, is host-bounded, has cross-model semantics and has deterministic validation/compatibility tests.

## Reasoning boundary

Reasoning is an optional content capability.

The API must distinguish:

- not supported;
- supported internally but not surfaced to this use case;
- surfaced reasoning supported but disabled;
- surfaced reasoning requested/enabled.

Only output intentionally exposed as a reasoning channel by the supported integration is returned as `REASONING`. The final response remains a separate `ANSWER` channel, and consumer code can ignore reasoning without changing answer handling.

An explicit surfaced-reasoning request that policy cannot honor fails typed. OMBRA PII keeps reasoning disabled/not surfaced in v1.

## Metrics boundary

Harness is authoritative for inference metrics because it observes runtime execution internally.

The stable consumer projection should cover:

- input tokens when known;
- output tokens;
- optional reasoning and answer token counts when reliably measured;
- TTFT;
- precisely defined total host execution/request time;
- decode tokens per second;
- stop reason.

Deeper timing and device diagnostics remain Harness-owned. Exact public metric semantics belong to [`results-and-metrics.md`](results-and-metrics.md).

## Host-owned policy hierarchy

```text
verified caller
  -> application policy
  -> use-case policy
  -> host-owned model binding
  -> allowed/default preset
  -> reasoning/output/session capabilities
  -> effective runtime configuration
  -> exact artifact + backend execution
```

A request violating a layer fails explicitly. Harness never widens permissions to make it succeed.

## Defaults

A use case declares a valid host-owned model binding and may declare a default preset, default reasoning behavior and default output mode.

Defaults apply when the client omits an optional consumer choice. They are not fallback substitutions for an explicitly requested option that fails.

Example:

```text
preset omitted -> authorized default preset
requested preset incompatible -> PRESET_INCOMPATIBLE
bound model unavailable -> MODEL_UNAVAILABLE
```

## Capability discovery

Before presenting optional choices, a consumer queries an application/use-case-scoped capability view that describes only what it may request or needs to understand:

- use-case readiness and capability revision;
- optional privacy-safe effective model identity;
- valid presets and defaults;
- surfaced reasoning support;
- output constraints;
- bounded input/session limits;
- optional protocol/features required.

Discovery must not expose host-private storage, global model inventory, unrelated applications/use cases or sensitive operational state.

Because v1 has no consumer model selection, readiness is primarily use-case oriented: can this authorized use case be prepared now, and if not, what typed consumer-actionable reason applies?

## Preparation and session semantics

Preparation revalidates the caller, `UseCaseId`, capability revision and all requested optional choices against current host policy.

Preferred v1 direction is now accepted: `prepare` resolves one immutable effective selection containing the host-owned model binding plus allowed preset/reasoning/output/session policy. Session creation binds to that prepared selection.

Repeated turns do not silently change model, preset or reasoning semantics. Capability/policy changes apply to future preparation rather than mutating an active session.

A stale capability response is therefore not trusted as authorization; every choice is revalidated before preparation.

## Consumer-facing error classes

The public surface should let the app act on categories such as:

- host unavailable/disconnected;
- permission denied;
- protocol/capability incompatible;
- use case not allowed;
- bound model unavailable;
- preset not allowed/incompatible;
- reasoning/output capability unsupported;
- invalid/bounded input;
- queue/resource limit;
- cancelled;
- runtime generation failure.

Backend paths, native exception details and host-private diagnostics remain internal.

## Privacy boundary

Normal telemetry/evidence continues to exclude prompt, reasoning and answer text. The requesting app receives its requested content in memory, but the public API must not require content persistence.

Capability discovery is also an information surface: authenticate first and return only caller/use-case-scoped data.

## Non-goals

- consumer model download/install/remove;
- consumer-selectable model identity in v1;
- arbitrary GGUF/native access;
- unrestricted low-level generation tuning;
- host-wide diagnostics through the inference protocol;
- remote/cloud fallback;
- arbitrary third-party publisher access;
- assuming every model exposes reasoning;
- changing the same-signer trust decision without explicit security/ADR revision.

## Accepted CA-0 decisions

ADR 0013 accepts the following v1 decisions:

1. **Model authority:** the consumer chooses `UseCaseId`; Harness owns exact model binding. No public model selector in v1.
2. **Preset-first tuning:** optional tuning uses host-advertised versioned presets, never raw runtime knobs.
3. **Reasoning:** surfaced reasoning is optional, typed and separately streamable only when use-case policy allows it.
4. **Metrics:** a small stable public metric projection is distinct from internal diagnostics.
5. **Discovery:** capability discovery is authenticated and application/use-case scoped.
6. **Determinism:** preparation resolves an immutable effective selection that is bound to the session/request lifecycle and revalidated after stale discovery.

These decisions close consumer-api CA-0. Changing model authority or another security/ownership invariant requires ADR review before dependent implementation.

## Success

A reference consumer can connect, discover authorized use-case capabilities, accept defaults or choose an allowed preset, request supported reasoning/output behavior, prepare a deterministic host-owned execution selection, stream typed content, receive final answer plus stable metrics, cancel and close cleanly—without embedding a model store, llama.cpp/JNI, runtime tuning policy or global Harness diagnostics.
