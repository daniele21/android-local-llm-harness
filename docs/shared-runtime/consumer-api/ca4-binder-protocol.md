# CA-4 Binder protocol integration

Status: active
Document type: feature-specification
Owner: shared-runtime-consumer-api
Canonical scope: shared-runtime.consumer-api.binder
Read when: changing Binder mapping for the public consumer API
Last reviewed: 2026-08-14

## Classification

CA-4 is an append-only Binder protocol minor evolution, not a new runtime and not a protocol-major change.

- protocol major remains `1`;
- protocol minor advances from `0` to `1`;
- the new behavior is advertised by `consumer-api-v1`;
- existing v1.0 methods, parcel fields, tags and transaction meanings remain unchanged;
- a new client must require `consumer-api-v1` before invoking the consumer RPCs;
- an old client may continue negotiating minor `0` and using the legacy `LocalLlmClient` projection.

The legacy wire surface is retained for compatibility even though it exposes model digest and low-level generation overrides. The public consumer SDK must use only the consumer RPCs below and must not project those legacy fields.

## Consumer RPC projection

The Binder extension mirrors `ConsumerLocalLlmClient`:

```text
capabilities(useCaseId)
prepare(useCaseId, constrained selection)
createSession(preparedId)
generate(sessionId, bounded input, selected output constraint)
cancel(requestId)
closeSession(sessionId)
```

Application identity never appears in a request. The host derives it from the authenticated Binder connection and supplies the corresponding `ConsumerLocalLlmClient` instance.

## Wire privacy boundary

Consumer parcels may contain:

- use-case ID and opaque capability revision;
- allowlisted preset ID/version;
- reasoning/output/session enum tags;
- bounded text/messages/JSON schema;
- opaque prepared/session/request correlation IDs;
- Tier 1/Tier 2 public result metrics and execution identity defined by CA-3;
- stable consumer failure code plus fixed safe message.

They must not contain:

- model ID, digest, path, download URL or artifact identity;
- application ID or signing data;
- raw sampling/backend/runtime overrides;
- runtime snapshot or diagnostic telemetry;
- backend exception text.

This boundary is protected by deterministic wire-DTO tests in addition to the public Kotlin-surface checks from CA-2/CA-3.

## Compatibility behavior

`consumer-api-v1` is introduced at protocol minor `1`. Hosts supporting only minor `0` do not advertise the feature, so a consumer client requiring it fails negotiation before registration, model access or runtime access.

Hosts at minor `1` keep all legacy methods operational. Consumer methods are appended to `ILocalLlmService`; existing transaction meanings are not repurposed.

Deterministic compatibility coverage proves both directions:

- a legacy client with no consumer feature requirement can still negotiate minor `0` against a v1.0 host;
- a consumer client requiring `consumer-api-v1` fails closed against that same host;
- the consumer client negotiates minor `1` only when the host advertises the feature.

## Ordering and terminal rules

Consumer generation keeps the existing Binder transport invariants:

- monotonically increasing per-request sequence;
- exactly one terminal `COMPLETED` or `FAILED` event;
- bounded delta size and aggregate reconstruction;
- cancellation is idempotent and never replayed after reconnect;
- post-terminal, duplicate or gapped callbacks fail closed;
- completed output is reconstructed from deltas rather than duplicated in the terminal parcel.

The client adapter and wire reconstructor now have focused deterministic coverage for ordered reconstruction, public metric projection, sequence gaps, duplicate callbacks and idempotent cancellation.

## Host ownership

The host owns application identity, exact model/artifact resolution and runtime configuration.

On registration, the authenticated caller maps to one host-created `ConsumerLocalLlmClient`. Consumer RPCs then operate only through that client and the caller's allowlisted use cases. A request for a non-authorized use case is rejected before the public consumer client is invoked.

Prepared selections, sessions and generation requests remain opaque across the process boundary. Host-side resource ledgers retain ownership and cleanup semantics already established by SR-2/SR-5.

## Packaged SDK boundary

`apps/shared-runtime-client-consumer-fixture` includes a public Consumer API compilation fixture that constructs `BinderConsumerLocalLlmClient` from the packaged release client/contract AARs.

The fixture intentionally has no:

- trusted caller-supplied `ApplicationId`;
- model ID/digest/path/URL;
- arbitrary generation overrides;
- dependency on host/runtime implementation modules.

This keeps packaged-AAR usability part of the repository validation gate rather than an assumption from project-source compilation.

## Exit evidence

CA-4 is complete only when all of the following are true on the exact branch head:

1. consumer contract/AIDL and wire mappings compile and pass unit tests;
2. host consumer authorization, caller-derived identity and privacy behavior pass deterministic tests;
3. Binder consumer lifecycle/generation adapters pass ordering, cancellation, stale-epoch and protocol-failure tests;
4. v1.0/v1.1 compatibility behavior passes deterministic tests;
5. the packaged release AAR consumer fixture compiles against the public consumer client;
6. repository and documentation validation are green.

The current branch contains evidence for items 1-5. Item 6 remains the final merge gate until GitHub Actions is green on the exact head.

Physical two-APK Consumer API scenarios remain CA-6 evidence and are not fabricated or substituted by deterministic repository tests.
