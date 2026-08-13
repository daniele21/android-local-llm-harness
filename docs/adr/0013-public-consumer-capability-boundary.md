# ADR 0013: Public consumer capability boundary

- Status: Accepted
- Date: 2026-08-13

## Context

ADR 0012 established the first shared-runtime trust and ownership boundary: a same-signer client requests an authorized `UseCaseId`, while Harness derives application identity and owns the exact model binding, artifact, runtime profile and lifecycle.

The public-consumer API plan adds capability discovery, presets, surfaced reasoning, output constraints and stable request metrics so a product application can express useful inference intent without importing the Harness control plane or low-level runtime configuration.

The initial consumer-api target also proposed allowing a client to choose among allowlisted logical model IDs. That conflicts with the accepted ADR 0012 invariant that model authority is host-owned. CA-0 must resolve that conflict before capability-policy code is introduced.

The first product-shaped consumer is OMBRA PII. Its `document-pii-detection` workflow benefits from deterministic host-owned model selection, deterministic generation behavior, no surfaced reasoning and a required JSON-schema output contract. It does not need model-shopping or runtime-tuning controls.

## Decision

### 1. Model authority remains host-owned in v1

A public consumer does **not** select a model ID in v1.

The authoritative resolution remains:

```text
verified caller
  -> ApplicationId
  + requested UseCaseId
  -> ConsumerUseCasePolicy
  -> AppModelBinding / UseCaseProfile
  -> exact curated model profile + artifact
```

Capability discovery may expose a privacy-safe effective model label or identity when useful for transparency, compatibility or a reference UI, but it does not advertise alternative model choices as requestable options.

A missing or unusable bound model is reported as typed use-case/model availability. The client cannot repair that state by naming another model, passing a digest/path/URL or triggering a download.

Adding client-selectable logical models later requires an explicit successor ADR because it changes the accepted model-authority boundary and protocol semantics.

### 2. Presets are the primary optional tuning surface

A consumer may select only host-advertised, versioned presets that are valid for the authenticated application and use case.

A preset expresses stable product intent; it does not expose temperature, top-p, top-k, context size, thread count, batch size, templates or backend-specific configuration.

The host owns the mapping from a preset to effective model/runtime configuration. If a requested preset is no longer valid at prepare time, preparation fails with a typed error; Harness does not silently substitute another preset.

A use case may expose exactly one default preset and no selectable alternatives. OMBRA PII starts this way with a deterministic host-owned default.

### 3. Surfaced reasoning is optional, typed and policy-scoped

Reasoning is a consumer-visible capability only when the model integration, protocol and use-case policy intentionally support it.

The public content model distinguishes `REASONING` from `ANSWER`. A consumer can ignore reasoning without changing final-answer handling.

An explicit request for surfaced reasoning that is not permitted fails typed rather than silently exposing hidden model internals. OMBRA PII does not surface reasoning in v1.

### 4. Public metrics are a stable projection, not diagnostics

Harness remains authoritative for execution metrics. The public terminal projection may include stable, backend-neutral fields whose semantics are frozen by the consumer API specification, including:

- input tokens when known;
- output tokens;
- optional answer/reasoning token counts when measured reliably;
- time to first token;
- defined host execution/request duration;
- decode tokens per second;
- stop/finish reason.

Memory, thermal state, cache internals, logs, benchmark history, native timings and host-global diagnostics remain outside the inference API.

### 5. Capability discovery is caller- and use-case-scoped

Discovery authenticates the caller before returning capabilities and returns only the requested authorized `UseCaseId` view.

The response may include:

- use-case readiness;
- capability revision;
- effective/default preset and optional allowed preset alternatives;
- surfaced reasoning support/default;
- output constraints;
- supported session kinds;
- stable consumer limits;
- optional privacy-safe effective model identity.

It must not expose global model inventory, install paths, URLs, artifact-management controls, unrelated use cases or diagnostics.

### 6. Preparation binds a deterministic execution selection

`prepare(useCaseId, consumer options)` revalidates authorization and capability revision, resolves the host-owned model plus allowed preset/reasoning/output/session policy and creates an immutable prepared selection for subsequent session creation.

A session created from that prepared selection does not silently change model, preset or reasoning semantics mid-session. Capability changes affect future preparation; they do not mutate an already accepted session.

Stale discovery is therefore safe: every advertised choice is revalidated during preparation.

### 7. Readiness is use-case oriented

Because v1 does not expose model selection, ordinary consumers do not need a list of model-level readiness states.

The capability surface reports whether the authorized use case can be prepared now and, when useful, a typed reason such as model unavailable, host policy unavailable or incompatible capability. Internal model lifecycle states remain private.

Reference/diagnostic UIs may show an approved effective model label, but availability semantics stay tied to the consumer action: prepare the use case.

### 8. Compatibility and evolution

These decisions refine the high-level consumer SDK while preserving ADR 0012's trust, identity and model-ownership invariants.

Capability discovery, preset selection, surfaced reasoning and public metrics must be classified as SDK-only or additive feature-negotiated protocol changes before Binder mapping. Older hosts must fail closed when a required feature is missing.

Client-selectable model authority, third-party publisher access, implicit downloads or raw runtime tuning are not additive v1 details and require explicit architectural review.

## Consequences

- The public API remains small and policy-driven.
- OMBRA can integrate without owning model installation, selection or llama.cpp configuration.
- `ApplicationId + UseCaseId` continues to be the authorization and routing anchor.
- Capability discovery becomes useful without turning into a model-store API.
- Presets allow product-level tuning without exposing backend knobs.
- Prepared sessions have stable execution identity and are straightforward to test.
- The accepted ADR 0012 boundary remains intact.
- Future model choice is possible, but it is intentionally a separately reviewed product/security decision.

## Alternatives considered

### Allow selection among host-advertised logical model IDs in v1

Rejected for v1 because it contradicts ADR 0012, broadens compatibility and policy behavior before a real consumer needs it, and creates more stale-capability/error cases. It can be revisited with a successor ADR once there is a concrete product requirement.

### Expose raw generation overrides instead of presets

Rejected because it makes every consumer responsible for model-dependent tuning and leaks Harness runtime policy into the application contract.

### Hide all capability information and expose only `generate(useCaseId)`

Rejected because consumers need typed readiness, output/session support and optional product-level capabilities to build deterministic UX without probing failures.

### Re-resolve model/preset independently for every request

Rejected because repeated turns could silently change execution identity and make metrics, reproducibility and lifecycle reasoning harder.

## Implementation gate

With this ADR accepted, consumer-api CA-0 is complete.

CA-1 may implement the caller/use-case capability domain and one host-owned policy registry, reusing existing `AppModelBinding` / use-case resolution where semantics fit. CA-1 must not add client-controlled model selection, duplicate model resolution or expose host-global inventory.

Any implementation that changes model authority, raw tuning exposure, reasoning privacy, diagnostics separation or prepared-session determinism must stop and review this ADR (or a successor) first.
