# Shared runtime host service

Status: active
Document type: feature-specification
Owner: shared-runtime-host
Canonical scope: shared-runtime.host-service
Read when: implementing the exported service, caller authorization, runtime delegation or caller-owned cleanup
Last reviewed: 2026-08-12

## Goal

Expose one existing host-owned `LocalLlmClient` data plane through a signature-protected service while isolating callers, avoiding Binder-thread blocking and preserving exact model/use-case policy.

## Dependencies

- SR-0 accepted ADR.
- SR-1 frozen protocol fixtures and wire mappers.
- Existing runtime lifecycle, model binding and phone host composition.

## Inspect before editing

- [`../../architecture.md`](../../architecture.md) and ADR 0010/0011/0012
- `core/contracts` and `core/runtime-core` public lifecycle/tests
- `models/model-profile` binding contracts
- `apps/local-llm-phone-test/AGENTS.md`
- `apps/local-llm-phone-test/.../HarnessRuntimeGraph.kt`
- phone model selection/management controls and direct tests
- Android manifests and build variants for both proof apps

Read the model scoped guide only if selection/binding behavior changes. Read the backend guide only if service composition changes native packaging.

## Owner and composition

`integrations/android-service-host` owns reusable Android service delegation, caller context, connection/resource ledgers and core/wire mapping. `SharedRuntimeHostComposition` wires the caller authorizer, protocol information, delegate and Binder stub around a supplied `LocalLlmClient`; it does not instantiate `RuntimeOrchestrator`, select models or access Compose.

`apps/local-llm-phone-test` owns the concrete `HarnessSharedRuntimeService` entry point and supplies the process-scoped `HarnessRuntimeGraph`. `HarnessSharedRuntimeClient` is a stable facade over the graph's currently active in-process client. Binding, handshake, snapshot and obtaining the facade do not create a runtime, select a model or load a GGUF. The proof policy currently authorizes only the exact installed phone-test package and its real signing lineage. External authorized package/use-case registration is intentionally deferred to SR-HOST-08.

## Manifest boundary

The proof host declares an exported bound service behind a variant-specific signature permission. Release uses:

```text
io.github.daniele21.localllm.permission.USE_LOCAL_LLM
```

Debug uses:

```text
io.github.daniele21.localllm.debug.permission.USE_LOCAL_LLM
```

The application requests its own variant permission so the SR-HOST-07 self-bind proof passes the same manifest permission boundary used by external callers. The service has no intent filter and clients bind with an explicit component. Debug variants do not authorize release packages through suffix stripping.

## Caller authorization

For every Binder entry point:

1. capture calling UID before switching threads;
2. resolve packages for that UID;
3. verify the expected signing lineage and manifest permission;
4. map exactly one authorized package/configuration to an internal `ApplicationId`;
5. verify requested `UseCaseId` against that client's allowlist;
6. attach immutable caller context to queued work.

Ambiguous packages, missing registration, changed signing identity or an unauthorized use case fail before model/runtime access. Caller-supplied package, application ID, certificate digest or UID fields are ignored/rejected.

The service records only a safe client registration key in telemetry. Raw certificate data, Binder tokens and complete package/signature inspection results are not logged.

## Host binding registry

The SR-HOST-07 proof host resolves only its internal phone application identity. SR-HOST-08 extends one coherent host binding abstraction for authorized external clients rather than creating parallel registries that can disagree.

The target resolution is:

```text
AuthorizedCaller(ApplicationId, allowed UseCaseIds)
  + host user's explicit selected curated model
  + fixed reviewed use-case profile
    -> ResolvedUseCase with exact artifact digest
```

Registration must not:

- let a client choose an artifact/profile;
- synthesize a binding when no host model is selected;
- download/install/select during prepare;
- bypass catalog/Qwen3.5 product admission;
- alter the phone application's own playground/validation bindings.

If no exact binding is available, preparation returns a typed model/configuration result and leaves host state unchanged.

## Connection and resource ownership

Maintain a bounded ledger:

```text
ClientConnection
  authenticated caller context
  opaque client token
  lifecycle Binder/death recipient
  active sessions
  active/queued request mappings
  callback dispatchers
  closing flag
```

Every session and request is scoped to one connection. External client IDs map to host-generated runtime IDs so separate clients may safely reuse the same correlation value.

Ledger operations are serialized. Registration, close and death cleanup are idempotent. Limits for connections, sessions and outstanding requests are explicit constants with typed rejection; they are not inferred from memory exhaustion.

## Threading model

The AIDL stub performs only bounded parsing, authorization lookup and task submission. It never calls model load or blocking runtime work inline.

Use one bounded control executor owned by the service delegate for:

- registration/unregistration;
- preparation;
- session open/close;
- request mapping and terminal cleanup;
- client death cleanup.

Generation remains owned by the existing single-decode scheduler. Core events enter a bounded serial callback dispatcher so the native generation callback is not indefinitely blocked by remote IPC.

Close order:

```text
mark client closing
  -> reject new work
  -> cancel mapped requests
  -> await/observe terminal cleanup within bound
  -> close mapped sessions
  -> unlink death recipient
  -> remove callbacks and token
```

SR-HOST-09 wires service destruction and Android memory-pressure callbacks. Until then the proof service deliberately does not take ownership of runtime destruction: runtime graph lifetime remains owned by the host application composition.

## Lifecycle behavior

- A pure bind creates the service as required and does not prepare a model.
- Registration and handshake have no model side effects.
- Client unbind or Binder death cancels that client's work and closes its sessions.
- Host UI recreation does not recreate the process graph or service-owned ledgers.
- Host process death may lose all sessions; the client reconnects from a clean state.
- No request is replayed because the client cannot know whether a terminal output was partially delivered.
- Last-client disconnect does not remove installed bytes or change selected model.
- Idle model unload follows runtime policy, not Binder ref-count alone.

V1 does not start a foreground service. If product requirements demand generation after the client leaves the foreground or unbinds, stop and design an Android background/notification policy under a new ADR.

## Callback delivery and backpressure

For each request:

- map the internal runtime ID back to the caller's correlation ID;
- assign monotonic wire sequence numbers;
- chunk/coalesce adjacent deltas within protocol limits;
- preserve reasoning/answer content type;
- deliver exactly one terminal event;
- terminate and cancel when the bounded callback queue overflows or Binder dies;
- remove request ownership only after terminal processing is committed.

One slow client must not block another client's callbacks or the scheduler control path. Callback exceptions are treated as connection failure and never propagated into runtime generation.

## Error and privacy behavior

The host maps errors at the integration boundary. It must not expose:

- exception class, stack or backend-owned text;
- native/library/file paths;
- model-store root or verified download location;
- packages/signing metadata of any caller;
- other clients' state, IDs or queue contents;
- prompt, reasoning, answer, schema text or system prompt.

Safe telemetry may include internal application/use-case identity, host/protocol version, request lifecycle, queue/load/timing/token metrics, exact model digest and typed outcome.

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
| SR-HOST-08 | PLANNED | Extend one host binding registry for authorized external use cases. |
| SR-HOST-09 | PLANNED | Add memory-pressure/service-destroy integration without duplicate runtime ownership. |

## Deterministic coverage

Tests cover:

- authorized caller, unknown package, invalid signature and ambiguous UID mapping;
- unregistered, expired and cross-UID client tokens;
- allowed/denied use cases and no selected/installed model;
- no model load during bind/registration/snapshot operations;
- prepare success/failure and executor rejection;
- session/request ownership and external ID collision across clients;
- queued/running cancellation and idempotent close;
- client death during prepare, queued generation, decode and terminal callback;
- callback exception, queue overflow and service destruction;
- two clients sharing the runtime scheduler without cross-control;
- error redaction and prompt/output sentinel exclusion;
- exact cleanup order and no leaked death recipients/executors/sessions;
- SR-HOST-07 protocol composition uses the frozen V1 contract deterministically;
- the phone-test manifest exposes the proof service only behind the variant signature permission;
- explicit proof self-bind leaves the process-scoped runtime uncreated.

## Acceptance criteria

- Unauthorized callers cannot obtain a service interface or runtime information.
- Authorized callers can access only registered use cases.
- The service invokes the same `LocalLlmClient` data plane used in-process.
- Binder threads perform no model load or generation.
- Every resource is owned by one authenticated client and cleaned after death/disconnect.
- Client input cannot mutate host model acquisition, installation or selection.
- Callback and control queues are bounded and failure leaves the runtime reusable.
- Host UI and service share one runtime graph without introducing screen-owned policy.

## Focused validation

Run `spotlessCheck`, `detekt`, host integration unit/lint/assembly, phone-test compile/unit/lint/assembly and Android packaging verification. The instrumentation suite includes the manifest boundary and pure-bind/no-runtime-side-effect proof; compiling/assembling that suite is not equivalent to executing it on an emulator or physical device.
