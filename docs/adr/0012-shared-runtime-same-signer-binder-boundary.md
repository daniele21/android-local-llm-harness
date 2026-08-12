# ADR 0012: Same-signer bound-service shared runtime

- Status: Accepted
- Date: 2026-08-11

## Context

The repository currently exposes local inference through the backend-neutral `LocalLlmClient` contract inside the application process. The shared-runtime target adds a second deployment shape in which a separately installed Android application hosts the existing runtime, curated model store and native backend while authorized client applications consume the same lifecycle semantics across Binder.

The shared deployment must preserve the existing data-plane rules rather than create a parallel runtime: explicit application/use-case binding, host-owned exact model selection, single-decode scheduling, bounded generation configuration, streaming/cancellation semantics, privacy-safe telemetry and model-family-neutral core contracts. ADR 0010 and ADR 0011 remain authoritative for the embedded-first architecture and the Qwen3.5 product support envelope.

Android IPC also introduces a security boundary that does not exist in the embedded transport. The protocol therefore cannot trust client-supplied application identity, model identity, package claims or signing claims, and generated AIDL must not become the public domain API.

## Decision

### Trust boundary

V1 supports only host and client APKs controlled by the same publisher and signed by an accepted signing lineage.

The exported host service is protected by a signature-level permission and also revalidates the calling UID, resolved package and accepted signing lineage inside the service before authorizing any operation. A Binder token is scoped to the authenticated connection but never replaces per-call caller verification.

Arbitrary third-party publishers, user-granted runtime permission and implicit trust based on package name are out of scope for v1. Supporting them requires a new security and product ADR.

### Host identity and proof application

`apps/local-llm-phone-test` is the first proof host because it already owns a real runtime composition root and device validation flow. This does not make it the final distributed host product.

The shared-runtime modules must therefore keep host integration independent from that application. A later dedicated host application can reuse the same service integration without moving protocol, authorization or runtime policy into the proof app.

### Lifecycle

V1 uses an explicitly bound service. It is not a generic background-generation or foreground-service product.

Client death, lifecycle Binder death, explicit unbind or unregister cancels caller-owned queued/running work and closes caller-owned sessions through idempotent cleanup. Host process death is surfaced as disconnection; requests and sessions are not automatically replayed after reconnect.

The existing runtime residency policy remains authoritative while the host process is alive. Binder connection lifetime must not silently redefine model residency.

### Public API ownership

`core/contracts` remains the semantic, backend-neutral contract and must not depend on Android Binder or `Parcelable` types.

Generated AIDL and parcel DTOs belong to the Android transport contract and are internal transport ABI. Client applications consume a high-level client artifact implementing `LocalLlmClient`; they do not parse AIDL, parcels, Binder tokens or host storage/runtime types directly.

Wire-to-core translation is explicit and one-directional. Unsupported core semantics fail with a typed feature/protocol error instead of receiving guessed transport defaults.

### Protocol and compatibility

The Binder protocol has an identity independent from SDK, host application, runtime/backend and model versions.

V1 uses:

```text
protocolMajor = 1
protocolMinor = additive capability level
minSupportedMinor = oldest compatible minor
featureFlags = explicit optional behavior
```

Major incompatibility and missing required features fail closed during a side-effect-free handshake before preparation, session creation or model access. Minor evolution is append-only: existing transaction meaning, stable codes, field meaning, ordering and terminal behavior do not change in place. Unknown tags or features are rejected explicitly unless a documented `UNKNOWN` representation is safe for that field.

### Model ownership and application identity

The client chooses only an authorized `UseCaseId` and supported request/session options. It does not send trusted `ApplicationId`, model ID, model path, artifact URL, native handle, system prompt or backend configuration.

The host derives application identity from the verified Android caller and owns the mapping:

```text
verified caller
  -> internal ApplicationId
  + requested allowlisted UseCaseId
  -> AppModelBinding
  -> UseCaseProfile
  -> exact curated GgufModelProfile/artifact digest
```

Missing installation, readiness, selection or authorization is a typed outcome. Binding or generation never triggers implicit download, model substitution or arbitrary GGUF import.

Client-provided session/request IDs are external correlation values only. The host owns collision-free internal identifiers and resource ownership per authenticated connection, so one client cannot enumerate, cancel or close another client's work.

### Diagnostics boundary

Inference IPC and cross-application diagnostics/control remain separate protocols and permissions.

The inference service exposes only the bounded lifecycle information required to prepare, open sessions, generate, cancel, close and report protocol-safe status/errors. It does not expose Room databases, telemetry histories, logs, health controls, cache maintenance, model inventory administration or native diagnostics.

### Privacy and payload boundary

Prompts, message content, JSON schemas, reasoning and generated answers are processed in memory only as required for inference and are not persisted by the transport or included in normal telemetry/evidence.

Binder payloads are deliberately bounded. No model bytes, files, file paths, bitmaps, aggregate reports, arbitrary `Bundle`/`Map` payloads or serialization blobs cross the inference boundary. Large future payload mechanisms such as file descriptors or shared memory require a separate protocol decision.

## Module ownership

The accepted v1 module boundary is the one defined in `docs/shared-runtime/architecture.md`:

- `transports/android-binder-contract` owns AIDL, parcel DTOs, protocol constants and wire validation;
- `transports/android-binder-client` owns connection state, proxy/event ordering and the `LocalLlmClient` adapter;
- `integrations/android-service-host` owns service delegation, caller authorization, ownership scopes and core/wire mapping;
- `apps/local-llm-phone-test` owns proof-host composition only;
- `apps/local-llm-console` owns the first client composition and presentation only.

Modules are created only with their first implementation and tests. `settings.gradle.kts` remains the authoritative integrated module list.

## Consequences

- The embedded and shared deployments reuse the same runtime semantics instead of maintaining two inference engines.
- Same-signer distribution makes the first security model intentionally narrow and reviewable.
- Host-owned identity and model selection prevent clients from escalating model/storage authority through IPC.
- Core contracts remain portable and testable without Android transport dependencies.
- AIDL can evolve under an explicit protocol compatibility policy without forcing core domain objects to become wire ABI.
- Bound-only lifecycle gives deterministic cleanup and avoids an accidental background-compute product.
- The proof host can change later without invalidating the protocol/client architecture.
- Cross-app diagnostics can evolve independently with a stricter read/control permission model.

## Alternatives considered

### Trust client-provided `ApplicationId` and model selection

Rejected because both values are authorization-sensitive host policy. Accepting them as authority would allow a client to escape its caller registration or select an unintended artifact/profile.

### Expose generated AIDL as the supported SDK

Rejected because it would leak Android transport mechanics into applications, make compatibility harder to evolve and duplicate the role already owned by `LocalLlmClient`.

### Run generation as a persistent foreground/background service in v1

Rejected because it changes lifecycle, user-visible execution policy and process-death semantics beyond the current product goal. V1 is bound-only.

### Put the service in a dedicated `:llm` process immediately

Rejected for the first slice because the proof host can reuse its existing process-scoped runtime graph. A dedicated process would change memory ownership and host UI integration before the Binder boundary itself is validated.

### Fold diagnostics into the inference protocol

Rejected because inference callers do not automatically need control-plane visibility. Combining the two would broaden permissions, payloads and privacy exposure without serving the v1 inference path.

## Implementation gate

With this ADR accepted, SR-0 is complete and SR-1 may create `transports/android-binder-contract` with the first AIDL/DTO implementation and tests. Any change to trust, exported-component protection, background lifetime, process deployment, public SDK ownership, model authority, diagnostics separation or protocol versioning requires this ADR (or a successor ADR) to be reviewed before dependent implementation changes.
