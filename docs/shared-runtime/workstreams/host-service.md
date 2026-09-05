# Shared runtime host service

Status: active
Document type: feature-specification
Owner: shared-runtime-host
Canonical scope: shared-runtime.host-service
Read when: implementing or reviewing the exported service, caller authorization, runtime delegation or caller-owned cleanup
Last reviewed: 2026-09-05

## Goal

Expose one existing host-owned `LocalLlmClient` data plane through an explicitly bindable service while isolating callers, avoiding Binder-thread blocking and preserving exact application/use-case/model policy.

## Dependencies

- ADR 0018 accepted trust boundary; ADR 0012 remains authoritative for the non-superseded shared-runtime rules.
- SR-1 frozen protocol fixtures and wire mappers.
- Existing runtime lifecycle, model binding and phone host composition.

## Owner and composition

`integrations/android-service-host` owns reusable Android service delegation, caller context, connection/resource ledgers, Binder composition and core/wire mapping. `SharedRuntimeHostComposition` wires caller authorization, protocol information, the delegate and Binder stub around a supplied `LocalLlmClient`. Closing the composition releases only service-owned Binder resources; it never closes the host runtime.

`apps/local-llm-phone-test` owns the concrete `HarnessSharedRuntimeService`, the process-scoped `HarnessRuntimeGraph`, the host-selected model and the proof client/use-case configuration. UI and service resolve through the same `HarnessPhoneBindingRegistry`; there is no second runtime or second model registry.

## Manifest and caller boundary

The proof Host is exported behind a variant-specific normal capability permission:

- release: `io.github.daniele21.localllm.permission.BIND_LOCAL_LLM`;
- debug: `io.github.daniele21.localllm.debug.permission.BIND_LOCAL_LLM`.

This permission is only an explicit opt-in to the bind surface. It is not an authorization grant. The service has no intent filter and clients bind with an explicit component.

Every privileged Binder operation revalidates the Android-derived caller boundary before Host policy or runtime work:

```text
Binder calling UID
  -> exact installed package
  -> exact signing certificate
  -> Harnex Control Plane authorization state
  -> enabled Host-owned use-case binding
```

Caller-supplied package, application ID, certificate digest or UID never determines authorization. Ambiguous UID/package resolution, unknown package, signer mismatch, pending/disabled state, signer replacement and unauthorized use case fail closed before model resolution.

Same-publisher Console registrations retain the reviewed Host signing lineage. Independently signed consumers such as RedactGuard are observed from `PackageManager`, persisted as `PENDING` on first observation and require explicit user authorization in Harnex. A later observed signer replacement becomes `SIGNATURE_CHANGED` and requires explicit reauthorization before the new signer enters live policy.

Emulator-only fault controls are separate from inference binding and remain variant-scoped behind their own signature-level control permission.

## Host binding registry

The host registry resolves:

```text
AuthorizedCaller(ApplicationId, allowed UseCaseIds)
  + host user's explicit selected curated model
  + fixed reviewed use-case profile
    -> ResolvedUseCase with exact artifact digest
```

The external proof binding is host-owned. Clients never supply model digest, artifact, profile or runtime tuning. `HarnessPhoneBindingRegistry` accepts only registered application/use-case bindings. The selected model is validated against the exact curated Qwen3.5 catalog artifact and is restored into the same registry after process restart from the host's persisted selection.

If no host model is selected, external `prepare()` returns a not-ready result without creating a runtime, selecting a model or loading GGUF bytes. A valid explicit prepare may lazily create the process-scoped runtime and then lets the existing `RuntimeOrchestrator` resolve and load the exact host-selected artifact.

### Control-plane preset identity at runtime

For control-plane consumers, the persisted public preset identity and the host-owned inference profile are deliberately separate. Activation resolves the exact persisted preset, model profile and canonical `InferencePresetRef` first. The host then creates an ephemeral runtime alias whose `InferencePreset.ref` is the public activated preset while generation settings, context preference, system prompt and allowed output modes remain copied from the canonical host-owned inference profile.

The activation-bound `ResolvedUseCase` exposes only that public alias and makes it the runtime default. The Consumer API capability policy follows the active public preset for the same application/use case and falls back to the reviewed built-in policy after deactivation or connection cleanup. This keeps one identity across control-plane activation, Consumer API prepare/session/generation and telemetry without allowing the consumer to manufacture model or tuning state.

`PresetExecutionPolicy.contextTokens` remains a host execution requirement used by `HostExecutionResolver` when checking model compatibility. It is not converted into a second consumer-side session override. Model loading also remains unchanged: activation does not import, verify or load GGUF bytes; a subsequent valid `prepare()` lets the existing `RuntimeOrchestrator` load or reuse the already installed and verified resolved model.

## Connection and resource ownership

Every authenticated connection owns its sessions, request mappings, generation handles, death link and callback dispatcher. The ledger is bounded by explicit connection/session/request quotas and maps external correlation IDs to host-generated internal IDs.

Connection cleanup order remains:

```text
mark client closing
  -> reject new work
  -> cancel mapped connection-scoped requests
  -> close mapped connection-scoped sessions
  -> unlink death recipient
  -> close callback dispatcher
  -> remove connection token
```

Registration is serialized against global service teardown so a new connection cannot be published after destruction has begun. Consumer SDK `disconnect()` explicitly unregisters/unbinds while keeping the client reusable; a later `connect()` creates a fresh connection epoch and re-runs negotiation and authorization. ADR 0016 separately owns durable logical-job lifetime across transport loss.

## Service destruction

`SharedRuntimeHostDelegate` and `SharedRuntimeHostComposition` are closeable and idempotently release only service-owned resources:

- reject/stop new control submissions;
- close the bounded control executor;
- cancel remaining connection-scoped service-mapped generation handles;
- close caller-owned connection-scoped sessions;
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
- Control-plane activation resolves and binds execution identity but does not import, verify or load model bytes.
- A public custom preset remains the same preset identity across activation, Consumer API capability validation and runtime generation while its tuning stays host-owned.
- Deactivation and connection cleanup remove the activation-bound alias; later capability discovery falls back to the reviewed built-in policy.
- Explicit consumer disconnect releases the connection but keeps the Consumer SDK client reusable.
- Client death/disconnect releases only that client's connection-scoped work; durable jobs follow ADR 0016.
- Host UI recreation does not recreate the process graph.
- Service destruction drains service adapter resources without destroying the graph.
- Host process death loses connection-scoped sessions and clients reconnect from a clean state; requests are never replayed implicitly.
- Last-client disconnect does not remove installed bytes or mutate model selection.
- Memory-pressure behavior remains owned by `runtime-core` policy.

## Error and privacy behavior

The host never exposes exception classes/stacks, native or model-store paths, signing metadata, another client's state, prompt/reasoning/answer text through normal telemetry, or client control over model acquisition/selection. Safe transport outcomes use the frozen protocol error mapping and privacy-safe runtime metadata.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| SR-HOST-01 | DONE | Create host integration module with fake-client/fake-runtime composition. |
| SR-HOST-02 | DONE | Implement Binder-derived caller context and exact package/signing authorization. |
| SR-HOST-03 | DONE | Implement bounded client/session/request ownership ledger and quotas. |
| SR-HOST-04 | DONE | Implement asynchronous prepare/session/generate/cancel/close delegation. |
| SR-HOST-05 | DONE | Implement lifecycle token death monitoring and idempotent cleanup. |
| SR-HOST-06 | DONE | Implement serial chunked callback delivery and backpressure failure. |
| SR-HOST-07 | DONE | Implement proof Host service manifest and app composition. |
| SR-HOST-08 | DONE | Extend one host binding registry for authorized external use cases. |
| SR-HOST-09 | DONE | Add memory-pressure/service-destroy integration without duplicate runtime ownership. |
| SR-HOST-10 | DONE | Separate bind capability from independent-consumer authorization and require explicit approval of observed signer identity. |

## Deterministic coverage

Coverage includes:

- authorized caller, unknown package, invalid signature and ambiguous UID mapping;
- same-publisher Console package registration;
- independently signed RedactGuard observation as pending and explicit authorization of its exact signer;
- signer replacement becoming fail-closed until explicit reauthorization;
- allowed/denied use cases and cross-UID token isolation;
- external binding failure without a selected model;
- exact external resolution to the host-selected curated model digest;
- activation-time public preset alias preserving canonical host-owned generation configuration;
- Consumer API capability exposure following the active public preset and reverting after release;
- control-plane activation installing the public alias without importing, re-verifying or removing model bytes;
- no runtime creation during bind/registration/snapshot or prepare-without-selection;
- prepare/session/generation delegation and external/internal ID mapping;
- reusable explicit disconnect/reconnect with a fresh authorization epoch;
- cancellation, client death and idempotent connection cleanup;
- callback backpressure/failure and privacy-safe error mapping;
- service close cancelling handles, closing sessions, unlinking lifecycle resources and closing service-owned executors;
- registration rejection after service close;
- manifest normal bind-capability boundary plus Binder-owned signer trust;
- emulator-only fault controls remaining separately signature-protected;
- runtime-core memory-pressure policy and integration coverage.

Cross-APK integration evidence for an independent consumer must sign Host and consumer with distinct keys, prove the identities differ, prove the consumer is denied before explicit Harnex authorization, then prove authorized connect/disconnect/reconnect. Same-key emulator evidence is insufficient for this trust claim.

## Acceptance criteria

- Declaring `BIND_LOCAL_LLM` alone never grants runtime access.
- Unauthorized, pending, disabled or signer-changed callers cannot obtain runtime access.
- Authorized callers can access only exact registered use cases.
- An independently signed consumer can be explicitly authorized by exact source-observed package/signer identity without sharing Harnex signing credentials.
- Client input cannot select or mutate model acquisition, installation or artifact identity.
- A published control-plane preset accepted by activation is the same public preset accepted by Consumer API prepare/session/generation.
- Public custom preset identity never duplicates or transfers ownership of canonical model/generation policy to the consumer.
- The service invokes the same process-scoped `LocalLlmClient` data plane used in-process.
- Binder threads perform no model load or generation.
- Every caller-owned resource is isolated and cleanable after death/disconnect/destruction according to its connection/durable-job lifetime.
- Service destruction releases adapter resources without taking runtime ownership.
- Android memory pressure delegates to the existing runtime policy.
- Host UI and service share one runtime graph and one host-owned binding registry.

## Focused validation

Run the repository selector with `profile=auto`; trust/Manifest/Binder/public-SDK changes require at least STRONG deterministic coverage. Applicable gates include Spotless/detekt, Host integration tests, Consumer SDK ABI/external-consumer validation, phone-test compile/unit/lint/assembly, manifest/packaging checks and cross-APK independent-signer E2E. Physical Play Internal confirmation is REAL_ENVIRONMENT evidence for the distribution signing topology and remains required before stable promotion.