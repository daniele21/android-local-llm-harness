# Shared runtime host service

Status: complete
Document type: feature-specification
Owner: shared-runtime-host
Canonical scope: shared-runtime.host-service
Read when: implementing or reviewing the exported service, caller authorization, runtime delegation or caller-owned cleanup
Last reviewed: 2026-08-12

## Goal

Expose one existing host-owned `LocalLlmClient` data plane through a signature-protected service while isolating callers, avoiding Binder-thread blocking and preserving exact model/use-case policy.

## Dependencies

- SR-0 accepted ADR.
- SR-1 frozen protocol fixtures and wire mappers.
- Existing runtime lifecycle, model binding and phone host composition.

## Owner and composition

`integrations/android-service-host` owns reusable Android service delegation, caller context, connection/resource ledgers, Binder composition and core/wire mapping. `SharedRuntimeHostComposition` wires caller authorization, protocol information, the delegate and Binder stub around a supplied `LocalLlmClient`. Closing the composition releases only service-owned Binder resources; it never closes the host runtime.

`apps/local-llm-phone-test` owns the concrete `HarnessSharedRuntimeService`, the process-scoped `HarnessRuntimeGraph`, the host-selected model and the proof client/use-case configuration. UI and service resolve through the same `HarnessPhoneBindingRegistry`; there is no second runtime or second model registry.

## Manifest and caller boundary

The proof host is exported only behind a variant-specific `signature` permission:

- release: `io.github.daniele21.localllm.permission.USE_LOCAL_LLM`;
- debug: `io.github.daniele21.localllm.debug.permission.USE_LOCAL_LLM`.

The service has no intent filter and clients bind with an explicit component. Authorization revalidates UID, exact package name, signing lineage and the use-case allowlist inside the Binder boundary. Caller-supplied package, application ID, certificate digest or UID never determines authorization.

The first external proof client is `apps/local-llm-console`. Exact package registration is variant-scoped:

- release host: `io.github.daniele21.localllm.console`;
- debug host: `io.github.daniele21.localllm.console.debug` and `io.github.daniele21.localllm.console.internal`.

No prefix/suffix stripping or wildcard package matching is used. Every registered console package must present the accepted host signing lineage.

## Host binding registry

The host registry resolves:

```text
AuthorizedCaller(ApplicationId, allowed UseCaseIds)
  + host user's explicit selected curated model
  + fixed reviewed use-case profile
    -> ResolvedUseCase with exact artifact digest
```

The external proof binding is host-owned:

```text
ApplicationId = local-llm-console
UseCaseId     = console-inference-playground
profile       = fixed shared-console Qwen3.5 profile
context       = 4096 tokens
max output    = 512 tokens
```

The client never supplies the model digest, artifact, profile or runtime tuning. `HarnessPhoneBindingRegistry` accepts only the internal phone-test bindings and the registered console binding. The selected model is validated against the exact curated Qwen3.5 catalog artifact and is restored into the same registry after process restart from the host's persisted selection.

If no host model is selected, external `prepare()` returns a not-ready result without creating a runtime, selecting a model or loading GGUF bytes. A valid explicit prepare may lazily create the process-scoped runtime and then lets the existing `RuntimeOrchestrator` resolve and load the exact host-selected artifact.

## Connection and resource ownership

Every authenticated connection owns its sessions, request mappings, generation handles, death link and callback dispatcher. The ledger is bounded by explicit connection/session/request quotas and maps external correlation IDs to host-generated internal IDs.

Connection cleanup order remains:

```text
mark client closing
  -> reject new work
  -> cancel mapped requests
  -> close mapped sessions
  -> unlink death recipient
  -> close callback dispatcher
  -> remove connection token
```

Registration is serialized against global service teardown so a new connection cannot be published after destruction has begun.

## Service destruction

`SharedRuntimeHostDelegate` and `SharedRuntimeHostComposition` are closeable and idempotently release only service-owned resources:

- reject/stop new control submissions;
- close the bounded control executor;
- cancel remaining service-mapped generation handles;
- close caller-owned sessions;
- unlink death recipients;
- close callback dispatchers;
- remove ledger ownership.

`HarnessSharedRuntimeService.onDestroy()` closes the host composition only. It does **not** call `HarnessRuntimeGraph.close()` and therefore does not take ownership of the process-scoped runtime, installed model bytes or host selection.

## Memory pressure

The service reuses the existing `runtime-core` Android adapter and policy instead of duplicating Android thresholds:

```text
Android onTrimMemory/onLowMemory
  -> AndroidMemoryPressureCallbacks
  -> RuntimeMemoryPressure
  -> HarnessRuntimeGraph.handleMemoryPressure
  -> RuntimeOrchestrator.handleMemoryPressure
  -> RuntimeMemoryPolicy
```

`UI_HIDDEN` and `BACKGROUND` may unload only an idle resident model. `LOW_MEMORY` may unload an idle model or cancel/release active runtime resources according to `RuntimeMemoryPolicy`. Binder reference counts do not independently decide model residency.

## Lifecycle behavior

- Pure bind, registration, handshake and snapshot do not create a runtime or load a model.
- Explicit prepare remains inert while no host model is selected.
- Client death/disconnect releases only that client's work.
- Host UI recreation does not recreate the process graph.
- Service destruction drains service adapter resources without destroying the graph.
- Host process death loses sessions and clients reconnect from a clean state; requests are never replayed.
- Last-client disconnect does not remove installed bytes or mutate model selection.
- Memory-pressure behavior remains owned by `runtime-core` policy.

V1 remains bound-only and does not start a foreground service.

## Error and privacy behavior

The host never exposes exception classes/stacks, native or model-store paths, signing metadata, another client's state, prompt/reasoning/answer text through normal telemetry, or client control over model acquisition/selection. Safe transport outcomes use the frozen protocol error mapping and privacy-safe runtime metadata.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| SR-HOST-01 | DONE | Create host integration module with fake-client/fake-runtime composition. |
| SR-HOST-02 | DONE | Implement immutable caller context and same-signer/package authorization. |
| SR-HOST-03 | DONE | Implement bounded client/session/request ownership ledger and quotas. |
| SR-HOST-04 | DONE | Implement asynchronous prepare/session/generate/cancel/close delegation. |
| SR-HOST-05 | DONE | Implement lifecycle token death monitoring and idempotent cleanup. |
| SR-HOST-06 | DONE | Implement serial chunked callback delivery and backpressure failure. |
| SR-HOST-07 | DONE | Add proof host service manifest and app composition. |
| SR-HOST-08 | DONE | Extend one host binding registry for authorized external use cases. |
| SR-HOST-09 | DONE | Add memory-pressure/service-destroy integration without duplicate runtime ownership. |

## Deterministic coverage

Coverage includes:

- authorized caller, unknown package, invalid signature and ambiguous UID mapping;
- exact debug/internal/release console package registration;
- allowed/denied use cases and cross-UID token isolation;
- external binding failure without a selected model;
- exact external resolution to the host-selected curated model digest;
- no runtime creation during bind/registration/snapshot or prepare-without-selection;
- prepare/session/generation delegation and external/internal ID mapping;
- cancellation, client death and idempotent connection cleanup;
- callback backpressure/failure and privacy-safe error mapping;
- service close cancelling handles, closing sessions, unlinking lifecycle resources and closing service-owned executors;
- registration rejection after service close;
- manifest signature-permission boundary and proof self-bind with no runtime side effect;
- runtime-core memory-pressure policy and integration coverage.

## Acceptance criteria

- Unauthorized callers cannot obtain runtime access.
- Authorized callers can access only exact registered use cases.
- Client input cannot select or mutate model acquisition, installation or artifact identity.
- The service invokes the same process-scoped `LocalLlmClient` data plane used in-process.
- Binder threads perform no model load or generation.
- Every caller-owned resource is isolated and cleanable after death/disconnect/destruction.
- Service destruction releases adapter resources without taking runtime ownership.
- Android memory pressure delegates to the existing runtime policy.
- Host UI and service share one runtime graph and one host-owned binding registry.

## Focused validation

Run `spotlessCheck`, `detekt`, host integration unit/lint/assembly, phone-test compile/unit/lint/assembly and Android packaging verification. The instrumentation suite covers the manifest boundary and pure-bind/no-runtime-side-effect proof; execution on an emulator or physical device remains a later evidence gate and is not inferred from assembly alone.
