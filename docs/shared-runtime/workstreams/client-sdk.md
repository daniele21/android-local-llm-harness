# Shared runtime Android client SDK

Status: active
Document type: feature-specification
Owner: shared-runtime-client
Canonical scope: shared-runtime.client-sdk
Read when: implementing service binding, the Binder-backed client, callback mapping or console consumer integration
Last reviewed: 2026-08-12

## Goal

Provide a small Android client artifact that binds explicitly to the host, negotiates protocol compatibility and exposes supported local-LLM lifecycle behavior without leaking AIDL plumbing into consumer code.

## Dependencies

- SR-0 accepted ADR.
- SR-1 AIDL contract and compatibility fixtures.
- Existing `LocalLlmClient` consumers and console inference control.

## Inspect before editing

- `core/contracts` public client/generation types
- `transports/in-process` adapter
- `apps/local-llm-console/AGENTS.md` if one is added; otherwise root guide plus console docs
- `apps/local-llm-console/.../LocalLlmConsoleInferenceControl.kt` and tests
- console Activity/composition, manifest and build variants
- existing API lifecycle and versioning sources

Do not import host runtime, backend, model-store or phone UI code into the client module.

## Owner

`transports/android-binder-client` owns:

- explicit service discovery/configuration;
- bind/unbind and connection state;
- protocol/feature negotiation;
- AIDL proxy invocation and callback stubs;
- event sequencing and terminal reconstruction;
- Binder-backed generation handles;
- mapping to the supported `LocalLlmClient` surface.

The first consumer is `apps/local-llm-console`. A published consumer SDK is a later SR-6 output, not implied by creating the internal module.

Current implementation status:

- the Android library module exposes an explicit `SharedRuntimeHostConfig` using exact package/service identity and a high-level `BinderLocalLlmClient`; the concrete `SharedRuntimeConnection` remains internal;
- no package scanning or implicit service discovery is present;
- the internal connection performs explicit bind, v1 protocol negotiation, registered-client lifecycle, typed connection-state transitions, monotonic connection epochs and idempotent close;
- Android binding details stay behind an internal environment boundary so connection behavior is deterministic under JVM tests;
- `BinderLifecycleAdapter` maps prepare/session lifecycle with main-thread rejection, bounded callback waits, opaque session IDs, epoch-owned session cleanup and deterministic timeout/dead-object/disconnect handling;
- `BinderGenerationAdapter` reconstructs ordered SR-1 events on a bounded serial executor, coalesces small deltas, enforces an aggregate-output bound, isolates consumer listener failures from Binder threads and serializes generation submission against client teardown;
- Binder generation handles preserve caller request identity, cancel at most once, remain safe after terminal/disconnect and convert cancellation transport death into one asynchronous `SERVICE_DISCONNECTED` outcome;
- callbacks and resources from superseded connection epochs are rejected without replay against a replacement registration;
- `apps/local-llm-console` exposes explicit connect/retry states and supplies its remote inference target only after a successful shared-runtime registration; opening or refreshing the Playground never binds, prepares or loads implicitly;
- console-local diagnostics/model-store state remains separate from remote inference state because Binder protocol v1 does not expose host runtime snapshots;
- the client AAR carries consumer shrinker rules, contains no native/model payload, and is validated both structurally and by `apps/shared-runtime-client-consumer-fixture`, which compiles against packaged client and contract AARs rather than a project dependency.

## Connection API

Use an explicit host component supplied by trusted application configuration. Do not scan installed packages or accept the first service responding to an intent.

Connection state is typed:

```text
DISCONNECTED
  -> BINDING
  -> NEGOTIATING
  -> CONNECTED

terminal/side states:
  HOST_NOT_INSTALLED
  PERMISSION_DENIED
  INCOMPATIBLE
  CONNECTION_LOST
  CLOSED
```

Only `CONNECTED` exposes a usable client. Reconnect after `CONNECTION_LOST` creates a new host registration and invalidates all old sessions/handles.

Connection and close are idempotent. Closing rejects new operations, best-effort cancels active handles, unregisters, unbinds exactly once and shuts down only executors owned by the client instance.

## `LocalLlmClient` adaptation

The wire protocol is asynchronous, while current `prepare` and `createSession` methods return synchronously. The adapter may wait for asynchronous callbacks only on a non-main caller executor with explicit timeout and cancellation.

Requirements:

- fail immediately when synchronous lifecycle methods are invoked on the Android main thread;
- never hold a UI/Compose thread while binding, loading a model or waiting for a callback;
- keep generate callback-based and return a cancellable handle immediately after accepted submission;
- use host-returned opaque session tokens behind core `SessionId` values without exposing token structure;
- convert disconnect/timeouts to fixed transport-safe failures;
- close a session at most once even when terminal delivery and consumer close race.

`BinderLocalLlmClient.runtimeSnapshot()` is intentionally unsupported in protocol v1 because the host does not expose a runtime-snapshot RPC. Consumers must not synthesize remote diagnostics from local state.

An optional coroutine facade may be added after the base adapter stabilizes. It must be a thin adapter over the same connection/client behavior, not a second transport implementation.

## Callback model

AIDL callbacks arrive on Binder-managed threads. The client routes them through one bounded serial executor per client/request before invoking consumer listeners.

For each request it tracks:

- expected next sequence number;
- accumulated bounded reasoning and answer text;
- latest generated-token count;
- terminal-delivered flag;
- connection generation to reject callbacks from an old host registration.

`TEXT_DELTA` maps to the existing core event. `COMPLETED` combines accumulated outputs with terminal metrics to create one core completion. Unknown tags, duplicates, gaps, post-terminal events or aggregate-limit overflow produce one local protocol failure followed by cleanup.

Consumer listener exceptions cannot escape into Binder threads. They cancel/close only the affected client request according to documented policy.

## Generation handle and cancellation

The Binder-backed `GenerationHandle`:

- exposes the caller's `RequestId`;
- sends an idempotent cancel once;
- remains safe before host acceptance, after terminal delivery and after disconnect;
- never blocks waiting for cancellation completion;
- treats terminal cancellation as a normal `Failed(CANCELLED)` event from the host;
- does not replay cancellation after reconnect against a new registration.

The adapter namespaces host correlation internally so equal request IDs from separate client instances cannot collide. Generation submission and client teardown share one lifecycle boundary so a close cannot leave a newly submitted request unowned; concurrent callback cleanup iterates the active map without materializing a size-sensitive snapshot.

## Failure behavior

Connection failures are distinct from generation failures:

| Condition | Client behavior |
| --- | --- |
| Host missing | `HOST_NOT_INSTALLED`; no bind retry loop |
| Permission denial | `PERMISSION_DENIED`; no fallback host |
| Major/version mismatch | `INCOMPATIBLE`; no runtime operation |
| Bind lost before request | typed connection failure |
| Bind lost during request | exactly one local `SERVICE_DISCONNECTED` terminal event |
| Timeout before accepted operation | cancel best effort, fail locally, never assume host did not run |
| Unknown/gapped event | protocol failure and affected-request cleanup |
| Consumer closes | cancel/close/unregister/unbind idempotently |

Automatic retry is limited to connection establishment under an explicit caller policy. Generation is never automatically resubmitted because partial execution/output may already have occurred.

## Console vertical slice

The console adapts the high-level Binder client through its existing inference control at the composition boundary:

- host connection state is explicit and the user chooses connect/retry;
- the configured host package follows the console build variant and the service class remains an exact fully-qualified component;
- `BinderLocalLlmClient` and the registered `local-llm-console / console-inference-playground` remote target become usable only when connected;
- source is identified as `Shared Android runtime (Binder)`;
- prompt remains only in the request and generated output remains bounded in existing in-memory state;
- console-local observability/model-store behavior remains separate capability state and is never presented as proof-host telemetry;
- client/control teardown is deterministic and does not persist prompt/output state.

Opening or refreshing the Playground does not bind, prepare or load. Connection is explicit, and model preparation remains a user-triggered inference operation.

## Packaging and compatibility

The packaged client boundary is intentionally split into the client AAR plus the Binder contract AAR; the client AAR does not duplicate generated AIDL/parcel classes from the contract artifact.

The client AAR must:

- expose only reviewed high-level client/configuration/connection-state types as supported source API;
- keep the concrete connection implementation and registration/AIDL plumbing internal;
- include consumer keep rules for the generated Binder contract when shrinkers are enabled;
- carry SDK version independently from protocol version before publication;
- document min/target Android compatibility and exact host package/service configuration before publication;
- contain no native library, GGUF/GGML model artifact, host runtime, backend or model-store implementation;
- remain usable without importing host implementation modules.

`apps/shared-runtime-client-consumer-fixture` is a compile-time consumer that depends on the assembled client and contract AAR files rather than the client project. Repository packaging validation also opens the client AAR and verifies reviewed public classes, consumer rules and absence of native/model payloads.

Source/binary compatibility policy and publication metadata remain SR-6 release work; SR-3 proves the internal packaged boundary and one real repository consumer.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| SR-CLIENT-01 | DONE | Create client module and explicit host-component configuration. |
| SR-CLIENT-02 | DONE | Implement typed bind/negotiate/disconnect state machine. |
| SR-CLIENT-03 | DONE | Implement registration and non-main blocking adapter for lifecycle calls. |
| SR-CLIENT-04 | DONE | Implement ordered callback mapper and bounded terminal reconstruction. |
| SR-CLIENT-05 | DONE | Implement idempotent generation handle, cancellation and close. |
| SR-CLIENT-06 | DONE | Implement timeout, dead-object and old-connection callback handling. |
| SR-CLIENT-07 | DONE | Connect the console composition and remote target states. |
| SR-CLIENT-08 | DONE | Add AAR consumer rules, API review and packaged-consumer test. |

## Deterministic coverage

Tests and repository gates cover:

- host missing, bind rejected, delayed bind, incompatible version and missing feature;
- connect/close idempotency and bind/unbind count;
- main-thread rejection for blocking adapter methods;
- prepare/session success, safe failure and timeout races;
- queued/prepared/started/reasoning/answer/completed mapping;
- chunk reassembly, Unicode boundaries, duplicate/gap/post-terminal events;
- cancellation before acceptance, queued, running, terminal and disconnected;
- service death during each lifecycle stage and clean reconnect;
- old callbacks ignored after connection generation changes;
- consumer listener failure isolation;
- aggregate output bound, close-vs-terminal teardown and prompt/output non-persistence;
- console disconnected/connecting/host-missing/permission-denied/incompatible/connection-lost/ready/generating/failed state mapping;
- packaged client/contract AAR consumption from a separate fixture module;
- packaged client API classes, consumer shrinker rules and absence of native/model artifacts.

## Acceptance criteria

- A consuming app binds with one explicit host configuration and no generated-AIDL knowledge in application code.
- No blocking operation is allowed on the main thread.
- Supported core lifecycle and streaming semantics survive the transport mapping.
- Disconnect produces one deterministic terminal outcome and no automatic replay.
- Close/unbind/cancel/session cleanup are idempotent under races.
- The client artifact contains no runtime, backend, model store, GGUF or host-private API.
- Console integration does not blur remote inference with console-local diagnostics state.
- Packaged client and contract AARs compile in the repository consumer fixture without a binder-client project dependency.

## Focused validation

Run:

```bash
./gradlew :transports:android-binder-client:testDebugUnitTest \
  :transports:android-binder-client:lintDebug \
  :transports:android-binder-client:assembleDebug \
  :transports:android-binder-contract:assembleDebug \
  :apps:local-llm-console:testDebugUnitTest \
  :apps:local-llm-console:lintInternal \
  :apps:shared-runtime-client-consumer-fixture:assembleDebug
```

The repository-wide Android gate includes the Console and packaged consumer fixture. Packaging validation verifies the Binder client AAR in addition to native packages. Multi-APK execution and physical-device evidence belong to [`validation-rollout.md`](validation-rollout.md).
