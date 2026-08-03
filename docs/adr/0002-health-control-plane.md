# ADR 0002 — Health and sanity control plane

- Status: Accepted
- Date: 2026-08-03

## Context

Phase 2 requires static model health, deterministic use-case sanity checks and remediation that can later be displayed by the developer console or exposed through a signature-protected diagnostics bridge.

The implementation must not couple health behavior to the console, Android Room, `llama.cpp` internals or one deployment transport. Sanity fixtures necessarily contain input text and inspect generated output, but normal telemetry must continue to exclude prompt and output content.

## Decision

Stable health DTOs and the `HealthControlPlane` interface live in `observability/contracts`.

Concrete orchestration lives in `observability/health-engine` and depends on:

- `ModelStore` for content-addressed artifact presence, size, SHA-256 and snapshot checks;
- `SanityExecutor` for deterministic fixture execution;
- `TelemetryRepository` for privacy-safe latest-result persistence.

`LocalLlmSanityExecutor` adapts `LocalLlmClient` to `SanityExecutor`. It owns the short-lived prepare, session, generation, timeout cancellation and session-close lifecycle for one fixture. The health engine never calls JNI or backend-specific APIs directly.

Fixture input and generated output remain in memory only for the duration of execution. Persisted health records contain check identifiers, status, duration, generic detail and remediation. They must not contain fixture prompts, generated output, arbitrary exception messages or model bytes.

The initial sanity rule set is deterministic and transport-friendly:

- non-empty output;
- required marker;
- forbidden marker;
- exact output for intentionally version-locked fixtures;
- regular-expression structure;
- maximum output-token guardrail.

Exact-output rules are available but are not the default because they are brittle across model or profile changes. Semantic LLM-as-judge evaluation is not part of this control plane.

Telemetry persistence remains best-effort. A telemetry failure may make a historical result unavailable, but it must not fail model verification, fixture execution or inference.

## Consequences

- The same health contracts can be called in-process now and transported through the future diagnostics bridge or Binder service later.
- Static integrity and deterministic sanity behavior can be unit-tested without Android UI, Room or a real GGUF.
- Product applications decide which fixture inputs are appropriate and keep them outside normal telemetry.
- Full GGUF metadata compatibility, native load health, memory recovery, cache health and physical-device stability remain separate checks built on the same report model.
- The current telemetry schema stores one latest result per check ID; historical health runs and richer structured remediation may require a later schema extension.

## Alternatives considered

### Implement health inside the console

Rejected because an embedded application owns the runtime and private model store. Console-owned checks would duplicate runtime behavior and could not access another application's sandbox without the diagnostics bridge.

### Put health orchestration in `core/runtime-core`

Rejected because model-store inspection and fixture policy are control-plane responsibilities, not generation lifecycle responsibilities.

### Persist fixture prompts and outputs for debugging

Rejected because it violates the metadata-only privacy default and is unnecessary for the initial deterministic assertions.

### Depend directly on `llama.cpp` for every health check

Rejected because it would leak backend ownership into the control plane and prevent fake or future backend implementations.
