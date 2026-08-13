# CA-4 Binder protocol integration

Status: active
Document type: feature-specification
Owner: shared-runtime-consumer-api
Canonical scope: shared-runtime.consumer-api.binder
Read when: changing Binder mapping for the public consumer API
Last reviewed: 2026-08-13

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

## Compatibility behavior

`consumer-api-v1` is introduced at protocol minor `1`. Hosts supporting only minor `0` do not advertise the feature, so a consumer client requiring it fails during registration with `FEATURE_UNAVAILABLE` before any model or runtime access.

Hosts at minor `1` keep all legacy methods operational. Consumer methods are appended to `ILocalLlmService`; existing transaction meanings are not repurposed.

## Ordering and terminal rules

Consumer generation keeps the existing Binder transport invariants:

- monotonically increasing per-request sequence;
- exactly one terminal `COMPLETED` or `FAILED` event;
- bounded delta size and aggregate reconstruction;
- cancellation is idempotent and never replayed after reconnect;
- post-terminal, duplicate or gapped callbacks fail closed;
- completed output is reconstructed from deltas rather than duplicated in the terminal parcel.

## Exit evidence

CA-4 is complete only when contract, host adapter, client adapter, compatibility fixtures and packaged-AAR consumer validation all pass. Physical two-APK execution remains CA-6 evidence and is not implied by JVM/AAR validation.
