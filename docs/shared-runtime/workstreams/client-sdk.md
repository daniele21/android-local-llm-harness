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

- the Android library module exists and exposes an explicit `SharedRuntimeHostConfig` using exact package/service identity;
- no package scanning or implicit service discovery is present;
- `SharedRuntimeConnection` performs explicit bind, v1 protocol negotiation, typed connection-state transitions and idempotent close;
- Android binding details stay behind an internal environment boundary so connection behavior is deterministic under JVM tests;
- registration, lifecycle adaptation, streaming reconstruction and console consumption remain subsequent SR-3 tasks.

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

The adapter namespaces host correlation internally so equal request IDs from separate client instances cannot collide.

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

The console already adapts a `LocalLlmClient` through its inference control. Integrate at the composition boundary:

- add host connection state and an explicit connect/retry action;
- supply `BinderLocalLlmClient` and registered remote inference target only when connected;
- identify the source as remote Binder/shared host;
- keep prompt only in the request and generated output bounded in existing in-memory state;
- retain disconnected console-local observability/model-store behavior as separate capability state;
- close client/control in deterministic Activity teardown without saved prompt/output state.

Do not make opening the Playground bind, prepare or load implicitly unless the existing screen flow explicitly requests connection. Connection may be process-scoped, but model preparation remains a user operation.

## Packaging and compatibility

The client AAR must:

- contain the required contract/AIDL classes;
- expose only reviewed package names and high-level client types as supported API;
- include consumer keep rules if shrinkers need them;
- carry SDK version independently from protocol version;
- document min/target Android compatibility and host package configuration;
- contain no native library or model artifact;
- remain usable without importing host implementation modules.

Before publication, validate source/binary API and one consumer project using the packaged AAR rather than project dependencies.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| SR-CLIENT-01 | DONE | Create client module and explicit host-component configuration. |
| SR-CLIENT-02 | DONE | Implement typed bind/negotiate/disconnect state machine. |
| SR-CLIENT-03 | PLANNED | Implement registration and non-main blocking adapter for lifecycle calls. |
| SR-CLIENT-04 | PLANNED | Implement ordered callback mapper and bounded terminal reconstruction. |
| SR-CLIENT-05 | PLANNED | Implement idempotent generation handle, cancellation and close. |
| SR-CLIENT-06 | PLANNED | Implement timeout, dead-object and old-connection callback handling. |
| SR-CLIENT-07 | PLANNED | Connect the console composition and remote target states. |
| SR-CLIENT-08 | PLANNED | Add AAR consumer rules, API review and packaged-consumer test. |

## Deterministic coverage

Tests cover:

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
- aggregate output bound and prompt/output non-persistence;
- console disconnected/connecting/incompatible/ready/generating/failed UI state mapping.

## Acceptance criteria

- A consuming app binds with one explicit host configuration and no generated-AIDL knowledge.
- No blocking operation is allowed on the main thread.
- Supported core lifecycle and streaming semantics survive the transport mapping.
- Disconnect produces one deterministic terminal outcome and no automatic replay.
- Close/unbind/cancel/session cleanup are idempotent under races.
- The client artifact contains no runtime, backend, model store, GGUF or host-private API.
- Console integration does not blur remote inference with console-local diagnostics state.

## Focused validation

Run:

```bash
./gradlew :transports:android-binder-client:testDebugUnitTest \
  :transports:android-binder-client:lintDebug \
  :transports:android-binder-client:assembleDebug
```

The repository-wide Android gate also includes the client module. Run packaged-AAR consumer verification before publication. Multi-app and physical-device execution belongs to [`validation-rollout.md`](validation-rollout.md).
