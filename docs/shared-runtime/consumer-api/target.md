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

## Core rule

> The consumer chooses from approved capabilities; Harness resolves and executes the effective inference configuration.

The consumer controls product intent. Harness controls runtime policy.

## Responsibility split

| Concern | Consumer app | Harness |
| --- | --- | --- |
| Identity | Installs as an authorized package. | Derives identity from verified Binder caller/signing policy. |
| Use case | Chooses an allowlisted `UseCaseId`. | Owns policy attached to that use case. |
| Input | Provides bounded text/messages. | Validates, templates and tokenizes. |
| Model | Chooses an advertised logical model or accepts default. | Owns exact artifact, installation, readiness, loading and residency. |
| Preset | Chooses an advertised versioned preset or accepts default. | Owns effective sampling/runtime configuration behind the preset. |
| Reasoning | Requests a supported surfaced mode. | Decides whether reasoning may be exposed and how it is separated/streamed. |
| Output | Requests a supported text/JSON/schema constraint. | Validates and enforces it. |
| Raw tuning | No normal control. | Owns sampling knobs, context, threads, batch, template and backend tuning. |
| Lifecycle | Opens/closes sessions and cancels own requests. | Owns scheduling, isolation, cleanup and process-death behavior. |
| Metrics | Consumes stable request metrics. | Measures authoritative runtime metrics. |
| Diagnostics | May show request-scoped safe details. | Owns logs, health, memory, thermal, cache and benchmarks. |

## Minimal consumer choices

V1 should require only:

- `UseCaseId`;
- bounded input;
- output constraint when text is insufficient.

Model, preset and reasoning preference should be optional when Harness advertises defaults. This keeps the simplest integration small while allowing a reference/test app to exercise authorized alternatives.

## Constrained model choice

A consumer model choice is a logical host-advertised ID, never an artifact location.

The consumer may select only models returned for its verified application/use-case policy. Harness maps the logical choice to an exact installed artifact and runtime profile.

The consumer never supplies a filesystem path, model URL, arbitrary digest as a load instruction, GGUF metadata used to bypass the catalog, or native/backend details.

Choosing a logical model does not grant model-management authority. If an explicitly selected model is unavailable, return a typed failure; do not silently download or substitute an unadvertised model.

## Preset-first tuning

Presets are the normal tuning surface. Product-facing identities may be `fast`, `balanced`, `quality` or use-case-specific names, but Harness owns their versioned semantics.

Internally a preset may control sampling, output budget, context policy, thinking defaults, generation guards and runtime tuning. The consumer selects the preset reference, not its individual raw values.

The existing core `GenerationOverrides` remains useful for Harness UI, benchmarks, deterministic tests and controlled experimentation. It should not automatically become the cross-app public API.

A raw option belongs in the public surface only if it expresses a stable product requirement, is host-bounded, has cross-model semantics and has deterministic validation/compatibility tests.

## Reasoning boundary

Reasoning is an optional content capability.

The API must distinguish:

- not supported;
- supported internally but not surfaced to this use case;
- surfaced reasoning supported but disabled;
- surfaced reasoning requested/enabled.

Only output intentionally exposed as a reasoning channel by the supported integration is returned as `REASONING`. The final response remains a separate `ANSWER` channel, and consumer code can ignore reasoning without changing answer handling.

## Metrics boundary

Harness is authoritative for inference metrics because it observes runtime execution internally.

The stable consumer projection should cover:

- input tokens when known;
- output tokens;
- optional reasoning and answer token counts;
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
  -> allowed logical models
  -> model-compatible presets
  -> reasoning/output capabilities
  -> effective runtime configuration
  -> exact artifact + backend execution
```

A request violating a layer fails explicitly. Harness never widens permissions to make it succeed.

## Defaults

A use case may declare a default model, compatible default preset, default reasoning behavior and default output mode.

Defaults apply when the client omits a choice. They are not fallback substitutions for an explicitly requested option that fails.

Example:

```text
model omitted -> authorized default model
requested model unavailable -> MODEL_UNAVAILABLE
requested model not allowed -> MODEL_NOT_ALLOWED
```

## Capability discovery

Before presenting optional choices, a consumer should query an application/use-case-scoped capability view that describes only what it may request:

- logical models and readiness;
- valid presets per model/use case;
- defaults;
- surfaced reasoning support;
- output constraints;
- bounded input/session limits;
- optional protocol/features required.

Discovery must not expose host-private storage, unrelated model inventory, other applications/use cases or sensitive operational state.

## Session semantics

Session ownership stays caller-scoped. Effective model/preset/reasoning selection must be deterministic for a session or request.

Preferred v1 direction: bind the resolved selection to a prepared session so repeated turns do not silently change execution identity. The public-surface workstream must validate whether this can be added compatibly to the existing lifecycle.

## Consumer-facing error classes

The public surface should let the app act on categories such as:

- host unavailable/disconnected;
- permission denied;
- protocol/capability incompatible;
- use case not allowed;
- model not allowed or unavailable;
- preset not allowed/incompatible;
- reasoning/output capability unsupported;
- invalid/bounded input;
- queue/resource limit;
- cancelled;
- runtime generation failure.

Backend paths, native exception details and host-private diagnostics remain internal.

## Privacy boundary

Normal telemetry/evidence continues to exclude prompt, reasoning and answer text. The requesting app receives its requested content in memory, but the public API must not require content persistence.

## Non-goals

- consumer model download/install/remove;
- arbitrary GGUF/native access;
- unrestricted low-level generation tuning;
- host-wide diagnostics through the inference protocol;
- remote/cloud fallback;
- arbitrary third-party publisher access;
- assuming every model exposes reasoning;
- changing the same-signer trust decision without explicit security/ADR revision.

## Decisions requiring acceptance

1. External clients may choose an allowlisted logical model instead of being permanently fixed to one host-selected model.
2. Presets are the primary consumer tuning mechanism.
3. Surfaced reasoning is optional, typed and separately streamable when allowed.
4. A small stable public metric projection is distinct from internal diagnostics.
5. Capability discovery is application/use-case scoped.
6. Model/preset selection has deterministic session/request semantics.

If decision 1 is rejected, the rest of the plan still works with exactly one host-owned model advertised per use case.

## Success

A reference consumer can connect, discover authorized capabilities, accept defaults or choose an allowed model/preset, request supported reasoning/output behavior, stream typed content, receive final answer plus stable metrics, cancel and close cleanly—without embedding a model store, llama.cpp/JNI, runtime tuning policy or global Harness diagnostics.