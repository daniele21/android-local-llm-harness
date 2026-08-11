# Shared Android runtime architecture

Status: active
Document type: architecture
Owner: shared-runtime
Canonical scope: shared-runtime.architecture
Read when: changing shared-runtime deployment, module ownership, dependency direction, identity, threading or lifecycle
Last reviewed: 2026-08-11

## Deployment shape

```text
Client APK process
  application UI/domain
    -> BinderLocalLlmClient
      -> explicit bind + AIDL proxy
        == Binder boundary ==
          -> signature-protected Service
            -> caller authorization and wire mapping
              -> LocalLlmClient / RuntimeOrchestrator
                -> explicit model binding
                -> content-addressed host ModelStore
                -> llama.cpp backend / GGUF
                -> host-owned telemetry
```

The client receives lifecycle events through an AIDL callback. It never imports runtime, model-store or backend modules. The embedded transport and Binder transport execute the same runtime data plane; only transport, application identity and storage ownership differ.

The proof service runs in the host application's existing process so it can reuse one process-scoped runtime graph. V1 does not create a second `:llm` process or migrate the host UI through Binder. A dedicated service process remains a later deployment decision because it would change runtime ownership, process-memory evidence and host UI integration.

## Planned modules

| Module | Owns | Must not own |
| --- | --- | --- |
| `transports/android-binder-contract` | AIDL interfaces, parcel DTOs, protocol constants and wire validation | Runtime, app authorization, model policy or UI |
| `transports/android-binder-client` | Binding state machine, proxy, event ordering and `LocalLlmClient` adapter | Host model state, service implementation or generated content persistence |
| `integrations/android-service-host` | Service delegate, caller identity, client resource ownership and core/wire mapping | Product model selection, Compose or backend implementation |
| `apps/local-llm-phone-test` | Proof service entry point, host configuration and process graph composition | Parallel runtime, transport policy or client SDK logic |
| `apps/local-llm-console` | First client composition and presentation | AIDL parsing, host storage or model management |

Create a module only when its first vertical slice has implementation and tests. `settings.gradle.kts` remains the authoritative module list.

## Dependency direction

```text
apps/local-llm-console
  -> transports/android-binder-client
     -> transports/android-binder-contract
     -> core/contracts

apps/local-llm-phone-test
  -> integrations/android-service-host
     -> transports/android-binder-contract
     -> core/contracts
  -> existing runtime/model/backend graph
```

The contract module is Android-specific but data-plane-neutral. `core/contracts` must not depend on it. Host integration depends on `LocalLlmClient`, not `RuntimeOrchestrator`, except at the app composition root where the existing runtime is supplied.

## Contract translation

Core data classes and sealed interfaces are semantic contracts, not wire ABI. Dedicated parcel DTOs represent their supported v1 projection.

```text
wire request
  -> validate protocol envelope and bounded fields
  -> authorize caller/use case
  -> map to core request with host-owned identity
  -> execute LocalLlmClient
  -> map core event to bounded wire event
  -> serialize per-request callback delivery
  -> client maps event back to core-facing API
```

Unknown wire tags and unsupported feature combinations fail explicitly. They are never mapped to a plausible default.

## Identity model

Identity exists at three levels:

1. **Android caller identity:** calling UID, resolved package and signing lineage.
2. **Product binding identity:** host-owned `ApplicationId + UseCaseId` mapping.
3. **Runtime correlation identity:** host-generated internal session/request identifiers.

Client-provided IDs are correlation values only. The host keys ownership by an authenticated client connection and generates collision-free internal IDs before invoking the runtime. A client cannot enumerate, cancel or close another client's resources even if it guesses an external identifier.

The host captures `Binder.getCallingUid()` before dispatching work away from the Binder thread and preserves the verified caller context with the submitted operation.

## Authorization model

V1 requires both layers:

- manifest-level signature permission on an exported service;
- service-level package/signing verification and an allowlist mapping to an internal `ApplicationId` and allowed use cases.

Binding uses an explicit component. No service intent filter, implicit discovery or fallback host selection is part of v1. Build variants must declare exact host and client package mappings; debug convenience cannot weaken release authorization.

Authorization is checked before model resolution or expensive work. Denial returns no runtime snapshot, model identity or other client information.

## Model and use-case resolution

The client does not send `ApplicationId` or model identity. The host performs:

```text
verified caller
  -> authorized client registration
  -> internal ApplicationId
  + requested allowlisted UseCaseId
  -> AppModelBinding
  -> UseCaseProfile
  -> exact curated GgufModelProfile and artifact digest
```

Missing installation, selection or binding is a typed preparation failure. The host never substitutes another model and never starts a download or installation as a side effect of binding or generation.

## Threading and scheduling

AIDL entry points validate small inputs, capture caller context and enqueue work. They do not load models or block on generation inside a Binder thread.

The host integration uses:

- one bounded control executor for prepare, open/close and ownership mutations;
- the existing runtime scheduler for generation serialization and cancellation;
- one bounded serial callback dispatcher per active request or client;
- no hidden unbounded executor or callback queue.

Per-request event order is stable even when core events originate on different threads. Callback delivery cannot block the native decode callback indefinitely. Slow or dead clients are cancelled with a typed transport failure and cleaned up.

## Service and client lifecycle

V1 is a bound service:

- the first client bind creates the service as needed;
- the service may coexist with the host UI in the same process;
- each client registers a Binder lifecycle token or callback used with `linkToDeath`;
- client death cancels its active/queued requests and closes its sessions;
- explicit unbind performs the same idempotent cleanup after pending calls terminate;
- last-client unbind allows normal service destruction;
- host process death is surfaced to clients as disconnection, with no automatic request replay;
- reconnect creates a new client ownership scope and never resurrects old sessions.

The runtime's configured residency policy decides whether an idle model stays loaded while the host process remains alive. Binder connection lifetime must not silently override model lifecycle policy.

## Protocol and payload boundaries

Every request carries a protocol envelope or is issued only after a successful compatibility handshake. V1 uses append-only compatible minor evolution and rejects unsupported major versions.

Binder transactions remain deliberately small:

- no model files, bitmaps, telemetry histories or aggregate reports;
- current core input/schema limits remain hard upper bounds, not transfer targets;
- delta text is chunked below a protocol constant;
- terminal events carry metrics and stop reason, not a second full output copy;
- the client assembles bounded answer/reasoning output from ordered deltas;
- large future inputs require a separate file-descriptor/shared-memory design and protocol version.

## Error boundary

Wire failures have stable codes grouped by:

- connection/protocol;
- authorization/use-case policy;
- model readiness;
- invalid generation input/configuration;
- cancellation/client death;
- service/runtime availability;
- bounded transport/backpressure.

Backend exception text, file paths, Binder implementation details and signing metadata do not cross the boundary. A transport mapper may preserve an approved safe core error code while replacing its message with a fixed consumer-safe description.

## Privacy and observability

The host necessarily processes client prompt and output in memory. Neither side persists them as part of the transport.

Privacy-safe telemetry may include authenticated application/use-case identity, protocol/SDK/host version, request lifecycle, queue/load/timing/token metrics, model digest and typed terminal outcome. It excludes prompt, reasoning, answer, JSON schema text, private paths, Binder tokens and certificate material.

Cross-application diagnostics remains a separate read/control protocol with its own permission and redaction policy. Adding inference must not implicitly expose Room, logs, health controls, model inventory or cache maintenance.

## Architectural gates

An implementation change requires an ADR update when it changes the trust model, exported component, background lifetime, process deployment, public SDK compatibility, model ownership or protocol versioning rule. Public wire changes require compatibility tests and version-policy updates in the same slice.
