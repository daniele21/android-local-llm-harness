# Android Local LLM Harness — target overview

Status: active
Document type: target-specification
Owner: repository
Canonical scope: target.repository
Read when: a change affects repository-wide product behavior, phase boundaries or acceptance criteria
Last reviewed: 2026-08-06

This document defines the repository-level target and routes work to the focused specification that owns each behavior. It intentionally avoids repeating module contracts, implementation status or release evidence.

## Product target

The harness provides a local-first Android runtime for explicitly selected GGUF models through `llama.cpp`. Applications embed the runtime today through backend-neutral public contracts; future Binder or Capacitor adapters must preserve the same domain boundary.

The product must:

- resolve models explicitly from `applicationId + useCaseId` without silent substitution;
- keep catalog selection, verified transfer, installation, binding, runtime loading and inference as separate operations;
- keep public contracts independent from Android UI, persistence and backend-owned native types;
- treat cancellation, shutdown, partial failure and cleanup as normal lifecycle paths;
- exclude prompts and generated content from normal telemetry and shared evidence;
- use measurements from representative hardware before changing residency, concurrency or cache policy;
- remain merge-ready through deterministic automation while reserving production-readiness claims for physical-device evidence.

## Canonical lifecycle

```text
applicationId + useCaseId
  -> reviewed model/use-case binding
  -> immutable GGUF digest
  -> installed artifact verification
  -> explicit runtime preparation
  -> one session and one active decode by default
  -> generation events and privacy-safe telemetry
  -> cancellation or completion
  -> session close and eventual model release
```

Remote distribution precedes this lifecycle but never bypasses it:

```text
catalog release
  -> compatibility decision
  -> allowlisted verified download
  -> opaque verified handle
  -> GGUF inspection and ModelStore installation
  -> explicit selection or binding
```

## Focused specifications

| Concern | Canonical source |
| --- | --- |
| Dependency direction and current module ownership | [`architecture.md`](architecture.md) and accepted [`adr/`](adr/) records |
| Public embedded assembly and lifecycle | [`api-usage.md`](api-usage.md) |
| Model distribution and installation routing | [`model-catalog-download-plan.md`](model-catalog-download-plan.md) |
| Curated catalog behavior | [`curated-model-catalog.md`](curated-model-catalog.md) |
| Secure verified transfer | [`secure-model-download.md`](secure-model-download.md) |
| Verified GGUF installation | [`model-installation.md`](model-installation.md) |
| Phone model lifecycle | [`phone-model-distribution.md`](phone-model-distribution.md) and [`model-management-phone.md`](model-management-phone.md) |
| Generation planning and prompting | [`generation-configuration-and-prompting-plan.md`](generation-configuration-and-prompting-plan.md) |
| Runtime telemetry and console surfaces | [`console-observability.md`](console-observability.md) |
| Health behavior | [`health-engine.md`](health-engine.md) |
| Resource observation | [`resource-observability.md`](resource-observability.md) |
| Benchmark and regression behavior | [`benchmark-engine.md`](benchmark-engine.md) |
| Connected phone application boundary | [`features/phone-app-architecture.md`](features/phone-app-architecture.md) |
| Phone UX/UI acceptance criteria | [`harness-ux-ui-implementation-plan.md`](harness-ux-ui-implementation-plan.md) |
| Shared visual system | [`design-system.md`](design-system.md) and [`harness-brand-guidelines.md`](harness-brand-guidelines.md) |
| Merge and production completion | [`definition-of-done.md`](definition-of-done.md) |

## Cross-cutting acceptance criteria

Every coherent implementation slice must satisfy the applicable criteria below.

### Contracts and ownership

- New behavior starts in the owning domain module and is exposed through the narrowest stable contract.
- Runtime orchestration does not depend on UI, transport or concrete persistence implementations.
- Native pointers, backend structures and filesystem details remain inside their owning adapter or store.
- Fakes support deterministic success, invalid-input, failure, cancellation and cleanup tests.

### Model integrity and security

- GGUF identity is the immutable SHA-256 digest, never a URL or display name.
- Model binaries, credentials, signing material and private paths remain outside source control and shared reports.
- Remote sources are validated before transfer and downloaded bytes are verified before installation.
- Installation rollback does not publish partial state or silently activate a model.

### Runtime lifecycle

- Load, session, generation, cancellation, close and unload ownership are explicit and idempotent where required.
- One model residency and one decode remain the default until representative measurements justify broader concurrency.
- Memory pressure, background transitions and partial native failures leave the runtime recoverable.

### Observability and privacy

- Runs, safe metrics, logs, health, resources and benchmark outcomes use bounded schemas and retention.
- Prompts, generated text, signed URLs, document URIs and private paths are not persisted in normal telemetry or evidence.
- Observability failures do not fail inference.

### Validation

- The narrowest deterministic unit, integration and packaging checks cover the changed owner and direct consumers.
- Shared contracts or multi-domain changes pass the repository-wide gate.
- Emulator evidence is labelled as emulator evidence.
- Production-readiness, compatibility and performance claims require representative physical-device GGUF evidence.

## Delivery direction

The current embedded Android boundary is the only active runtime path. Native Android SDK and Capacitor adapters may build on it after the embedded API and release gates are stable. A shared Android service remains deferred until measurements demonstrate that cross-application artifact or RAM deduplication justifies Binder lifecycle complexity.

Current integration status belongs only in [`current-state.md`](current-state.md). Capability sequencing belongs in [`roadmap.md`](roadmap.md). Harness 0.5.0 release evidence belongs in [`releases/harness-0.5.md`](releases/harness-0.5.md).

The original phase-oriented implementation plan is preserved as historical context in [`archive/plans/initial-implementation-plan.md`](archive/plans/initial-implementation-plan.md).
