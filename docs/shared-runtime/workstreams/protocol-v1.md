# Shared runtime Binder protocol v1

Status: active
Document type: feature-specification
Owner: shared-runtime-protocol
Canonical scope: shared-runtime.protocol-v1
Read when: defining or changing AIDL methods, wire DTOs, event mapping, payload limits or protocol compatibility
Last reviewed: 2026-08-11

## Goal

Define a small, versioned Android IPC protocol that preserves the supported semantics of `LocalLlmClient` without turning core contracts into Android ABI.

This workstream owns wire syntax and compatibility only. Host caller policy belongs in [`host-service.md`](host-service.md); client binding behavior belongs in [`client-sdk.md`](client-sdk.md).

## Dependencies

- SR-0 accepted deployment ADR.
- Existing `core/contracts` request, session, event, metric and error semantics.
- Existing protocol identity rule in [`../../versioning.md`](../../versioning.md).

## Inspect before editing

- `core/contracts/src/main/kotlin/**`
- `transports/in-process/src/main/kotlin/**`
- `core/runtime-core` direct implementations and tests of `LocalLlmClient`
- `docs/api-usage.md` for current embedded lifecycle
- direct console and health-engine consumers of `LocalLlmClient`

Do not inspect JNI/backend sources unless a supported core event cannot be represented without understanding its public semantics.

## Planned owner

Create `transports/android-binder-contract` only when the first AIDL interface, DTO implementation and tests land together. It is an Android library with AIDL enabled and no dependency on runtime, model store, observability implementation or application UI.

The consumer artifact must contain the generated interface required by both sides while keeping generated class names out of the supported high-level client API.

## Protocol envelope

The protocol has identities independent from the SDK release:

```text
protocolMajor = 1
protocolMinor = additive capability level
minSupportedMinor = oldest compatible minor
featureFlags = explicit optional behavior
```

Handshake is small and side-effect free. An incompatible major or missing required feature fails before preparation, session creation or model access.

Minor evolution is append-only. Existing transaction codes, field meaning, stable string codes and terminal behavior never change in place. New enum-like values include an explicit `UNKNOWN` handling path. Removed behavior requires a new major version.

## Proposed AIDL surface

Names are provisional until SR-0, but responsibilities are fixed:

```aidl
interface ILocalLlmService {
    ProtocolInfoParcel getProtocolInfo();
    void registerClient(in ClientHelloParcel hello, in IClientLifecycle lifecycle,
        in IRegistrationCallback callback);
    void prepare(in PrepareRequestParcel request, in IOperationCallback callback);
    void openSession(in OpenSessionRequestParcel request, in ISessionCallback callback);
    void generate(in GenerationRequestParcel request, in IGenerationCallback callback);
    oneway void cancel(in CancelRequestParcel request);
    oneway void closeSession(in CloseSessionRequestParcel request);
    oneway void unregisterClient(in ClientTokenParcel client);
}

interface IGenerationCallback {
    oneway void onEvent(in GenerationEventParcel event);
}
```

Registration returns an opaque host token scoped to the authenticated calling UID and lifecycle Binder. Every later request carries that token plus an external operation/session correlation ID. Possession of a token never replaces per-call caller verification.

Heavy operations are callback-based. No call waits for model loading or generation on a Binder thread. Cancellation and close are idempotent `oneway` requests.

## Wire DTO inventory

| DTO | Required contents | Excluded contents |
| --- | --- | --- |
| `ProtocolInfoParcel` | major/minor range, features, host API identity | runtime snapshot, model list |
| `ClientHelloParcel` | client SDK version, required features | trusted app identity or signing claims |
| `ClientTokenParcel` | opaque random token | UID/package/certificate material |
| `PrepareRequestParcel` | token, operation ID, use-case ID | application ID, model ID/path |
| `OpenSessionRequestParcel` | token, operation ID, use case, context/session options | native handles |
| `GenerationRequestParcel` | token, request/session correlations, bounded input, overrides, constraint | system prompt, template or model settings outside public policy |
| `GenerationEventParcel` | request correlation, sequence, event tag and bounded event payload | duplicate full output in terminal event |
| `WireErrorParcel` | stable code, fixed safe message, retry classification | exception class/stack/backend message/path |

Parcelable implementations use explicit fields and stable tags. Do not send Java/Kotlin serialization blobs, arbitrary `Bundle`, `Map`, polymorphic class names or core data objects directly.

## Request mapping

Supported v1 input variants:

- `TEXT` with one bounded string;
- `MESSAGES` with bounded role/content entries and aggregate length;
- `RAW_COMPLETION` only when the resolved use case permits it.

Supported overrides mirror approved core fields exactly: preset ID/version, output-token limit, sampling scalars, seed policy, repeat controls and thinking mode. Null means unspecified. A missing field never invents a transport default; core profile resolution remains authoritative.

Supported output constraints are `TEXT`, `JSON` and bounded `JSON_SCHEMA`. The protocol validates structure and size before host/core mapping; the runtime still performs semantic policy validation.

## Event mapping

Wire events use stable tags:

```text
QUEUED
PREPARED
STARTED
TEXT_DELTA
COMPLETED
FAILED
```

Each event contains the external request correlation ID and a monotonically increasing sequence number assigned by the host transport. Exactly one `COMPLETED` or `FAILED` event terminates a request.

`TEXT_DELTA` carries content type, bounded text and generated-token count. The client independently assembles reasoning and answer output. `COMPLETED` carries terminal metrics and stop reason but not a second copy of aggregate output; the client adapter constructs the core terminal event from accumulated bounded deltas.

Empty deltas may be omitted. Reordering, duplicate terminal delivery or an unexplained sequence gap becomes a typed protocol failure and triggers local cleanup; the client never guesses missing text.

## Payload and backpressure policy

Define constants in one wire owner and test them on both sides:

- core input/message/schema character limits remain absolute maxima;
- each emitted delta is split to at most `MAX_DELTA_CHARACTERS`, initially proposed as 4,096 characters;
- each parcel is rejected when its estimated encoded size exceeds a conservative protocol ceiling, initially proposed as 128 KiB;
- no aggregate telemetry, model bytes, bitmaps, file paths or file contents cross Binder;
- a bounded per-request event queue coalesces adjacent compatible deltas;
- queue overflow cancels the request with `CLIENT_BACKPRESSURE` rather than allocating unbounded memory.

The first implementation records actual parcel sizes in tests. Raising limits requires evidence and a protocol review; it is not a workaround for batching mistakes.

## Stable failure groups

V1 reserves stable codes for:

- `PROTOCOL_INCOMPATIBLE`, `FEATURE_UNAVAILABLE`, `INVALID_WIRE_REQUEST`;
- `CLIENT_NOT_REGISTERED`, `CLIENT_TOKEN_INVALID`, `UNAUTHORIZED_USE_CASE`;
- `MODEL_UNAVAILABLE`, `PREPARATION_FAILED`, `SESSION_UNAVAILABLE`;
- approved core configuration/error codes;
- `CANCELLED`, `CLIENT_DISCONNECTED`, `SERVICE_DISCONNECTED`;
- `CLIENT_BACKPRESSURE`, `PAYLOAD_TOO_LARGE`, `TRANSPORT_FAILURE`.

Unknown safe core errors map to a generic runtime code, never a fabricated configuration reason. Retry classification is transport policy and must be explicit.

## Task ledger

| ID | State | Task |
| --- | --- | --- |
| SR-PROTO-01 | DONE | Confirm module/package names and accepted protocol identity in SR-0. |
| SR-PROTO-02 | DONE | Add protocol constants, feature registry and compatibility evaluator. |
| SR-PROTO-03 | DONE | Add explicit parcel DTOs and structural/size validation. |
| SR-PROTO-04 | DONE | Add AIDL service, lifecycle and operation callbacks. |
| SR-PROTO-05 | DONE | Map supported core input, options, constraints and requests to/from wire DTOs. |
| SR-PROTO-06 | DONE | Map events, metrics, stop reasons and safe errors without backend leakage. |
| SR-PROTO-07 | DONE | Add ordered delta chunking and terminal reconstruction fixtures. |
| SR-PROTO-08 | DONE | Add backward/minor/major compatibility fixtures and unknown-value coverage. |
| SR-PROTO-09 | DONE | Add public API binary/source review and consumer keep rules if needed. |

## Deterministic coverage

Unit/parcel tests cover:

- minimum and maximum valid request shapes;
- blank, NUL, oversized and invalid variant input;
- every nullable override and seed-policy exclusivity;
- all output constraints and schema bounds;
- round-trip mapping for every event and metric field;
- reasoning versus answer content type;
- chunk boundaries including Unicode surrogate pairs;
- monotonically ordered sequence and exactly one terminal event;
- unknown tags/codes/features;
- compatible minor and rejected major versions;
- payload-size calculation and backpressure terminal behavior;
- error redaction and absence of paths/backend text.

Contract fixtures are checked into the protocol module as small privacy-safe values. They contain no model artifact, real prompt or generated user content.

## Acceptance criteria

- Host and client compile from the same published contract artifact.
- `core/contracts` contains no Binder/Parcelable dependency.
- A fake host and fake client can negotiate, stream, cancel and terminate using only v1 AIDL.
- Every supported core semantic has one reviewed wire mapping or an explicit `FEATURE_UNAVAILABLE` result.
- Invalid/unknown input fails closed before runtime invocation.
- Transactions and callback queues are bounded by tested constants.
- Version compatibility is deterministic and documented independently from SDK/model versions.

## Focused validation

Once the planned module exists, the implementation PR adds exact Gradle commands to this section. At minimum run its formatting, unit/parcel tests, compile, lint and AAR assembly, then repository-wide validation because the protocol is a shared public boundary.
