# Consumer results, streaming and metrics

Status: active
Document type: feature-specification
Owner: shared-runtime-consumer-api
Canonical scope: shared-runtime.consumer-api.results-metrics
Read when: defining reasoning/answer streaming, terminal results, token counts or public inference metrics
Last reviewed: 2026-08-13

## Goal

Define what a calling app receives from Harness during and after inference, and separate stable consumer metrics from deeper Harness diagnostics.

The core contracts already distinguish `REASONING` and `ANSWER` deltas and already capture a rich `GenerationMetrics` superset. This workstream projects those capabilities into a smaller, precisely defined application-facing contract.

## Accepted CA-3 contract

CA-3 uses the internal `GenerationMetrics` as the source of truth and adds no parallel telemetry path. The consumer projection exposes Tier 1 (`outputTokens`, `timeToFirstTokenMs`, `totalMs`, `decodeTokensPerSecond`) plus privacy-safe Tier 2 (`inputTokens`, surfaced `reasoningTokens`, `answerTokens`, `queueMs`, stable `stopReason`). Model load, prompt planning, context creation, phase timing, memory, thermal, cache and backend counters remain Harness diagnostics.

`ConsumerExecutionIdentity` reports only effective logical configuration: use case, capability revision, preset, surfaced reasoning mode, output constraint and session kind. ADR 0013 keeps exact model/artifact identity host-owned, so CA-3 does not expose a consumer model ID or artifact digest in terminal results.

Internal `GenerationEvent.Prepared` is not forwarded verbatim. CA-3 projects it to a new public `Prepared` event containing only `ConsumerExecutionIdentity`. Exactly one public terminal event is emitted; events received after a terminal event are ignored by the public projector.

## Result model

The consumer should not receive one ambiguous output string when the model/profile supports a surfaced reasoning channel.

Conceptually:

```kotlin
data class ConsumerInferenceResult(
    val answer: String,
    val reasoning: String? = null,
    val metrics: ConsumerInferenceMetrics,
    val execution: ConsumerExecutionIdentity,
)
```

`answer` is always the final application-facing answer for a successful text generation. `reasoning` is present only when intentionally surfaced and requested/allowed.

## Streaming channels

Public streaming should make the content type explicit rather than forcing client parsing.

Recommended semantic events:

```text
Queued?                    optional public lifecycle event
Prepared                   effective public execution identity is known
ReasoningDelta             only when surfaced
AnswerDelta                application-facing generated content
Completed                  terminal aggregate result + metrics
Failed                     terminal typed error
```

The implementation may reuse `GenerationEvent.TextDelta(contentType=...)` directly if that remains a clear public SDK contract. Binder sequence numbers, chunk reassembly bookkeeping and callback backpressure internals remain transport-private.

## Ordering rules

The public contract must define deterministic ordering:

1. no content before the request is accepted/prepared;
2. reasoning and answer deltas preserve host generation order;
3. if a model/profile separates phases, reasoning deltas precede answer deltas unless the backend contract explicitly supports interleaving;
4. exactly one terminal `Completed` or `Failed` event;
5. no content after terminal;
6. cancellation converges to one terminal cancellation outcome;
7. aggregate terminal output matches the content delivered for the corresponding surfaced channels.

If supported models have different channel behavior, Harness normalizes only what it can guarantee and advertises the capability accordingly.

## Reasoning semantics

Use the term **surfaced reasoning** for a model/backend channel intentionally made available to the client.

Do not assume every chain-of-thought-like token sequence is safe or intended for exposure. The public API should carry only the reasoning representation explicitly supported by the model integration and use-case policy.

Possible outcomes:

| Capability/request | Public behavior |
| --- | --- |
| Reasoning unsupported | Answer only. |
| Supported but not surfaced by policy | Answer only; capability says not surfaced. |
| Surfaced but consumer uses default disabled | Answer only. |
| Surfaced and requested | `ReasoningDelta` + optional terminal reasoning aggregate + answer. |
| Explicit reasoning requested but unavailable | Typed capability/configuration failure before generation when possible. |

## Answer semantics

`answer` is the primary successful output consumed by the app.

The consumer should not need to strip model-specific thinking tags, chat-template markers, stop tokens or backend framing. Harness owns that normalization.

For JSON/JSON-schema output, the result should preserve the constrained payload while exposing output type/constraint metadata through the execution identity or result contract.

## Public metric tiers

Use progressive disclosure in the contract and reference UI.

### Tier 1 — essentials

Recommended always-returned metrics:

- `outputTokens`;
- `timeToFirstTokenMs`;
- `totalMs` with stable public semantics;
- `decodeTokensPerSecond`.

These answer the normal consumer questions: how much was generated, how quickly did it start, how long did it take, and at what decode rate.

### Tier 2 — request details

Recommended privacy-safe optional/additional fields:

- `inputTokens`;
- `reasoningTokens` when surfaced/known;
- `answerTokens` when known;
- `stopReason`;
- possibly queue time if consumers need to distinguish contention from model speed.

### Tier 3 — Harness diagnostics

Keep out of the ordinary consumer result unless a separate diagnostics API explicitly exposes them:

- model load timing/kind;
- prompt planning;
- context creation;
- prefill/decode phase timings;
- memory/PSS;
- thermal state;
- KV/cache state;
- scheduler/client ledger details;
- native/backend counters;
- global request history and benchmarks.

The existing internal `GenerationMetrics` remains the richer source of truth.

## Metric definitions

Public metrics need exact measurement anchors before implementation. Names must not be accepted based only on intuition.

### `outputTokens`

Count of generated output tokens attributable to the request according to the runtime tokenizer/accounting contract.

If reasoning and answer are both generated, document whether `outputTokens = reasoningTokens + answerTokens` for every supported profile. If backend accounting includes hidden/control tokens, either normalize them or document a separate total; do not silently mix definitions.

### `inputTokens`

Token count of the effective model input after Harness-owned prompting/template construction, unless the accepted contract chooses a different anchor.

This must be distinguished from raw user-text token count.

### `timeToFirstTokenMs`

Define one host-side start timestamp and one first generated-token event. The chosen anchor must remain stable across in-process and Binder deployment.

The contract should state whether queue/model-load/preparation time is included. Recommended direction: keep TTFT as a generation/request-runtime metric with clear inclusion semantics and provide queue/load detail only in diagnostics.

### `totalMs`

Must have one public meaning across all clients.

Candidate direction: host-observed elapsed time from accepted generation execution boundary to terminal generation outcome. Do not call this “inference time” if it includes queue/model-load without documenting that fact.

If product UI needs pure model execution time, introduce a distinct metric rather than changing `totalMs` semantics later.

### `decodeTokensPerSecond`

Host-calculated decode throughput using a stable token/time denominator. Do not calculate it from client-observed Binder wall time.

If reasoning and answer share one decode stream, the rate represents the whole generated stream unless explicitly separated in a future version.

### `reasoningTokens` / `answerTokens`

Optional counts tied to the surfaced channels. `reasoningTokens` should not imply visibility when reasoning is not surfaced to the caller.

### `stopReason`

Project stable core stop reasons into a consumer enum/code. Avoid backend-specific stop strings.

## Host time versus client wall time

Harness metrics and consumer-observed latency answer different questions.

```text
client wall time
= client SDK/IPC overhead
+ host queue/preparation/execution
+ callback delivery
```

The public Harness metrics should be host-authoritative. A reference consumer may additionally measure `clientObservedTotalMs` locally for transport/E2E diagnostics, but that is not the same field as host `totalMs`.

For SR-6/transport benchmarking, compare the two explicitly rather than contaminating inference metrics with IPC overhead.

## Execution identity

A terminal result should include privacy-safe identity sufficient to reproduce/understand the request at the product level:

```kotlin
data class ConsumerExecutionIdentity(
    val useCaseId: UseCaseId,
    val capabilityRevision: String,
    val preset: InferencePresetRef?,
    val reasoningMode: EffectiveConsumerReasoningMode,
    val outputConstraint: ConsumerOutputConstraintKind,
    val sessionKind: SessionKind,
)
```

Request/session ID and protocol information may belong in request details rather than the primary result object.

Exact model and artifact identity remain host-private in the ordinary consumer API. Engineering evidence may use separate Harness diagnostics, but CA-3 does not add model IDs or artifact digests to consumer results.

## Effective versus requested values

The terminal/prepared metadata should report **effective** logical configuration after defaults are resolved.

Example:

```text
requested preset: omitted

result identity:
useCase = document-pii-detection
preset = balanced@3
reasoning = disabled
output = json-schema
```

This lets consumer logs/UI explain what actually ran without exposing raw internal tuning.

## Error result behavior

Failed requests must not return a misleading partial success result.

A failure may have already emitted surfaced deltas. The contract should specify that:

- `Failed` is terminal;
- partial output is not promoted to a successful final answer automatically;
- the consumer may discard or display partial text according to its UX;
- normal telemetry does not persist that partial content;
- privacy-safe elapsed/count metadata on failures is included only if the error contract deliberately supports it.

## Cancellation

For cancellation:

- terminal status is `CANCELLED`/typed equivalent;
- no further deltas follow;
- output accumulated before cancellation may exist transiently in the consumer UI but is not a successful `Completed` result;
- cancellation latency is diagnostic/evidence data unless promoted through a future public requirement.

## Empty reasoning and answer handling

Avoid using empty strings as the only semantic distinction in the public API.

Prefer nullable/typed presence where possible:

```text
reasoning = null -> no surfaced reasoning channel/result
reasoning = ""   -> only valid if the channel was surfaced but produced no content and that distinction matters
```

A successful text request should normally require a valid answer according to the resolved generation/output contract.

## UI projection for the reference consumer

The reference app must consume and make Tier 1 metrics available through an appropriate product projection:

```text
TTFT       412 ms
Speed      48.2 tok/s
Total      3.21 s
Tokens     154
```

A generic inference playground may show Tier 1 beside the answer. The OMBRA product flow defined in [`pii-redactor/`](pii-redactor/) keeps them in a secondary `Request details` surface so document review remains primary. That surface may also reveal Tier 2 fields and effective logical model/preset identity; it must not recreate Harness-wide diagnostics.

If surfaced reasoning is enabled, show it as a visually separate collapsible/secondary section so the final answer remains primary.

## Privacy requirements

Normal logs/evidence must not contain prompt, reasoning or answer text.

Metrics and identities should avoid exposing:

- raw private file paths;
- model URLs or credentials;
- Binder tokens;
- signing secrets;
- native handles;
- full device-private diagnostics unrelated to the request.

## Deterministic tests

Required coverage:

- answer-only streaming reconstructs the terminal answer;
- surfaced reasoning and answer remain correctly typed and ordered;
- consumer ignoring reasoning still receives the same final answer contract;
- unsupported explicit reasoning request fails according to policy;
- terminal uniqueness under success, failure and cancellation;
- token accounting relationships are tested for supported profiles;
- public TTFT/total/rate anchors are deterministic in fake-clock/unit tests where possible;
- public projection excludes diagnostic-only fields;
- Binder round-trip preserves metric precision and nullability;
- no prompt/reasoning/answer sentinel leaks to normal telemetry/evidence.

## Acceptance criteria

This workstream is ready to implement when:

- surfaced reasoning semantics are unambiguous;
- answer normalization is Harness-owned;
- Tier 1 and Tier 2 metric fields are agreed;
- every public timing metric has a written start/end anchor;
- host versus client-observed timing is explicitly separated;
- token-count relationships are specified;
- stop reasons are stable and backend-neutral;
- the consumer can render useful performance information without accessing Harness diagnostics.
