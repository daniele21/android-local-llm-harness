# Public Consumer API v1 surface

Status: active
Document type: feature-specification
Owner: shared-runtime-consumer-api
Canonical scope: shared-runtime.consumer-api.surface-v1
Read when: designing Kotlin client methods, Binder mappings or consumer request/session contracts
Last reviewed: 2026-08-13

## Goal

Define the small application-facing inference surface introduced by CA-2. The surface is additive: the existing `LocalLlmClient` remains the internal/embedded superset, while `ConsumerLocalLlmClient` is the constrained consumer boundary.

Capability contents belong to [`capabilities-and-policy.md`](capabilities-and-policy.md); public result metrics are deferred to CA-3 in [`results-and-metrics.md`](results-and-metrics.md).

## Accepted CA-2 shape

The consumer surface does not expose or accept:

- `ApplicationId` as caller-controlled authorization input;
- model IDs/selectors, `ModelDigest`, GGUF paths or URLs;
- `GenerationOverrides` or raw sampling/runtime tuning;
- manual context sizing;
- host-wide runtime snapshots or diagnostics;
- internal `Prepared` configuration metadata.

The host-side facade is constructed with an already authenticated `ApplicationId`, reuses the CA-1 policy service and delegates execution to the existing `LocalLlmClient` only after consumer policy has been validated.

## Public lifecycle

```text
capabilities(useCase)
  -> prepare(useCase, optional constrained selection)
  -> createSession(preparedId)
  -> generate(sessionId, bounded input, output constraint)
  -> cancel or terminal event
  -> closeSession
```

A consumer can omit preset, reasoning, output kind and session kind during preparation. Harness resolves the advertised host defaults.

## Capability discovery

`capabilities(useCaseId)` returns only the authenticated application/use-case-scoped `UseCaseCapabilities` from CA-1.

Discovery is informational and side-effect free with respect to model verification, loading and download. The capability revision is revalidated at preparation time and is never treated as an authorization token by itself.

## Preparation

`ConsumerPrepareRequest` contains:

- required `UseCaseId`;
- optional `ConsumerSelectionRequest` containing capability revision, allowed preset, reasoning preference, output kind and session kind.

Preparation performs two policy checks:

1. validate the caller/use-case/selection before touching the legacy runtime;
2. after normal host preparation, rediscover and revalidate the same explicit choices against the current capability revision.

The second pass allows an expected `AVAILABLE_REQUIRES_PREPARATION -> READY` transition without trusting a stale discovery response or silently widening policy.

A successful result returns `ConsumerPreparedSelection` with an opaque `ConsumerPreparedId`, effective preset, reasoning mode, output kind, session kind and current capability revision. Exact model/artifact identity remains host-private.

## Session binding

`createSession(preparedId)` revalidates the prepared selection and creates the legacy session with only the host-authorized `SessionKind`. Manual context policy is not consumer-controlled.

The effective selection is immutable for the created session. Policy changes apply to future preparation rather than silently mutating an active session.

Prepared IDs are consumed after successful session creation in CA-2 v1.

## Generation input

The public request accepts only:

- bounded text;
- bounded user/assistant messages.

Raw-completion input is intentionally absent. Input/message limits come from the prepared CA-1 capability policy and are enforced before delegating to `LocalLlmClient`.

## Output constraints

The generation request carries one concrete consumer output constraint:

- text;
- JSON;
- JSON schema with bounded schema text.

Its kind must match the output kind frozen during preparation. A mismatch fails before legacy generation starts.

## Preset and reasoning mapping

Generation does not repeat mutable selection options. The facade maps the prepared selection to a narrow internal `GenerationOverrides` containing only:

- the already authorized preset reference;
- the already resolved effective reasoning/thinking mode.

Temperature, top-p, top-k, token budget, penalties, seed and other raw tuning controls remain host/preset-owned.

## Event projection

The facade projects the internal event superset into `ConsumerGenerationEvent`:

```text
Queued
Started
ContentDelta(ANSWER)
ContentDelta(REASONING) only when surfaced reasoning was authorized
Completed(answer, optional surfacedReasoning)
Failed(coarse typed consumer failure)
```

Internal `GenerationEvent.Prepared`, model digests, effective raw generation metadata and backend error details are never forwarded. Public performance metrics are intentionally not added in CA-2 because CA-3 owns their stable projection.

## Failure model

Consumer failures are coarse and actionable. Categories include capability/policy rejection, invalid bounded input, missing/stale prepared selection, missing session, preparation failure, cancellation and runtime failure.

Host-private paths, native exception text and backend diagnostics are not used as public failure messages.

## Cancellation and close

`ConsumerGenerationHandle.cancel()` delegates cancellation for the caller-owned request while preserving its request ID. `closeSession` only delegates for a session owned by this facade and removes that binding.

## Compatibility

CA-2 is SDK/runtime-core additive. It does not alter AIDL or Binder wire semantics; Binder mapping remains CA-4. The legacy `LocalLlmClient` is unchanged for existing embedded and internal consumers.

## CA-2 exit gate

CA-2 is complete when focused tests prove that a consumer can execute:

```text
discover -> prepare -> session -> generate -> close
```

using only public consumer types, while tests also prove stale selections and invalid input/output fail before delegation and no caller/model/artifact/raw-tuning authority appears in the consumer surface.

### Current validation evidence

`ConsumerLocalLlmFacadeTest` covers the lifecycle exit gate, stale selection rejection, pre-delegation input/output validation and public-surface authority checks. The focused CA-2 gate passes repository formatting, Detekt and `:core:runtime-core:testDebugUnitTest`; repository-wide CI remains the integration gate before the slice can advance beyond `IN PROGRESS`.
