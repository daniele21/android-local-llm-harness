# Generation configuration and model-aware prompting

Status: active
Document type: feature-specification
Owner: core/runtime-core
Last reviewed: 2026-08-06

## Purpose

This document defines the durable generation-configuration and prompt-planning behavior shared by the embedded runtime and connected Playground. Current release evidence belongs in [`releases/harness-0.5.md`](releases/harness-0.5.md), not in this specification.

The design separates:

1. user-requested configuration;
2. application-reviewed versioned presets;
3. use-case and model-profile policy;
4. model-aware prompt rendering;
5. the effective configuration of one request;
6. native context creation and decode ownership.

No preset or override may change the model digest or silently substitute the application/use-case binding.

## Resolution flow

```text
GenerationRequest + logical session
  -> resolve application/use-case/model binding
  -> resolve preset and per-field overrides
  -> materialize random or fixed seed
  -> resolve system prompt and output constraint
  -> render model chat template
  -> tokenize with the loaded model vocabulary
  -> select effective context from exact prompt tokens and output budget
  -> create or reuse one compatible native context
  -> generate with sampling, grammar and stop policy
  -> emit events and privacy-safe effective metadata
```

Configuration errors fail before unsafe decode. Values are not silently clamped or corrected.

## Public configuration

Request-scoped configuration belongs in `GenerationOverrides`. Session context ownership belongs in `SessionOptions` and `ContextPolicy`.

Supported request controls include:

- versioned preset reference;
- maximum output tokens;
- temperature;
- top-p;
- top-k;
- random or fixed seed policy;
- repeat penalty;
- repeat window;
- output constraint.

Supported session context policy:

- `Auto`: select a reviewed context size from the exact prompt and output budget;
- `Manual(tokens)`: require the explicit size to satisfy model/profile limits and the request budget.

A logical session does not allocate a native context until the prompt has been rendered and tokenized. Stateless sessions may materialize a compatible context lazily. Conversational resize must not silently discard KV state.

## Presets

Presets are application-owned, reviewed and versioned. A remote catalog may select only an application-approved profile or preset identity; it may not provide arbitrary system prompts, templates, schemas or sampler values.

Per-request overrides resolve by field over the selected preset and then the use-case defaults. The effective preset ID/version remains observable even when one or more fields are customized.

Changing a preset definition requires a version increment. Telemetry and diagnostics report the effective version rather than assuming the current code definition.

## Seed policy

`SeedPolicy.Random` materializes one unsigned 32-bit seed per request plan. The generated value is reused through planning, backend invocation, events and telemetry for that request.

`SeedPolicy.Fixed(value)` validates the exact `0..4_294_967_295` range. A missing seed is never coerced to zero.

The effective seed is privacy-safe numeric configuration and may be recorded.

## Sampling validation

The runtime validates configuration before backend invocation.

- maximum output tokens: positive and within the runtime bound;
- temperature: finite and within `0..2`;
- top-p: finite and in `(0, 1]`;
- top-k: bounded and non-negative;
- repeat penalty: finite and in `[1, 2]`;
- repeat window: bounded and non-negative;
- enabled repeat penalty requires a positive repeat window;
- fixed seed: unsigned 32-bit range.

A repeat penalty of `1` disables repetition protection. Temperature zero uses the greedy path; inactive probabilistic controls remain visible as requested/effective metadata but do not create a second decode implementation.

The native sampler order is:

```text
optional grammar
-> repetition penalties
-> greedy
```

or, for positive temperature:

```text
optional grammar
-> repetition penalties
-> top-k
-> top-p
-> temperature
-> distribution
```

Streaming and aggregate output must share this same sampler and decode path.

## Prompt and template policy

Structured messages are rendered through an explicit trust chain:

1. supported template metadata from the installed GGUF;
2. application-reviewed template override;
3. application-reviewed model-family fallback;
4. raw completion only when explicitly requested and allowed.

The application owns system-prompt text and version. The catalog may reference an approved profile but cannot inject arbitrary prompt text.

Prompt planning produces a rendered prompt, exact token count, stop-token IDs and bounded stop sequences. Callers never need to supply model special tokens manually.

Unsupported or ambiguous template behavior fails with a typed configuration error rather than silently applying a generic format.

## Context planning

`Auto` selects the smallest approved context tier that satisfies:

```text
prompt tokens + maximum output tokens + policy reserve
```

Selection is constrained by:

- the model-reported or application-reviewed maximum context;
- use-case preferred and recommended context values;
- explicit runtime upper bounds;
- available model/profile compatibility.

A manual context that is too small fails; it is not increased silently. A requested size above the supported maximum fails before native context creation.

Context creation is cancellable and occurs after exact planning. Planning and context-creation timings are separately observable.

## Output constraints and stop handling

Supported output constraints include:

- plain text;
- JSON;
- reviewed JSON Schema converted to native grammar.

Schema and grammar preparation occurs before prefill. Invalid constraints map to typed configuration errors and do not start decode.

Application stop sequences are bounded in count and UTF-8 bytes. The native output buffer:

- preserves UTF-8 boundaries;
- withholds enough suffix bytes to detect split stop sequences;
- chooses the earliest stop position independently of policy order;
- does not emit the matched stop text;
- returns a typed stop reason.

Prompt text, schema, grammar and stop-sequence text are never persisted in normal telemetry.

## Events and effective metadata

`GenerationEvent.Prepared` exposes only privacy-safe effective metadata:

- preset ID and version;
- temperature, top-p and top-k;
- repeat penalty and window;
- requested seed policy and effective seed;
- maximum output tokens;
- effective context size;
- prompt token count;
- chat-template ID and source;
- system-prompt version;
- prompt-planning and context-creation timing where available.

Terminal events include metrics and stop reason. They do not include prompt, generated output, system-prompt text, template text, schema, grammar, stop-sequence text, private paths or arbitrary backend messages.

Room migrations preserve historical run rows by adding new effective-configuration fields as nullable. In-memory and Room implementations must remain contract-equivalent.

## Cancellation and cleanup

Cancellation is valid while:

- queued;
- prompt planning;
- tokenization;
- context creation;
- prefill;
- decode.

Cancellation must release temporary planning, grammar, sampler and context resources and leave the runtime reusable. A late callback or generation handle must not overwrite a terminal cancelled, failed or completed state.

Synchronous and streaming generation share planning, sampler construction, decode, stop handling, metric collection and cleanup. Only output delivery differs.

## Connected Playground

The phone Playground exposes reviewed presets and custom controls without taking ownership of runtime policy.

Required UI behavior:

- distinguish selected preset, customized-from-preset and effective configuration;
- show Auto/manual context explicitly;
- disable or explain inactive probabilistic controls on the greedy path;
- validate input through shared contract mapping rather than duplicating backend rules;
- retain effective metadata after terminal completion;
- keep prompt and generated output in process memory only;
- provide accessible labels, numeric input and clear range feedback.

The current phone presets use conservative repetition protection where enabled. Other consumers remain disabled by default until they explicitly opt into reviewed values.

## Failure mapping

Caller-correctable failures use typed configuration codes, including:

- invalid generation configuration;
- unsupported or invalid prompt template;
- context limit exceeded;
- invalid output constraint;
- unsupported model capability.

Backend/native failures remain distinct from configuration failures. Arbitrary exception messages are not exposed to UI or persisted telemetry.

## Testing

Required deterministic coverage includes:

- preset and per-field override precedence;
- random and fixed seed behavior and bounds;
- temperature, top-p, top-k, repeat penalty and repeat-window validation;
- greedy and probabilistic sampler chains;
- template source precedence and unsupported-template failure;
- exact token and context-tier boundaries;
- manual context rejection;
- cancellation during planning and context creation;
- grammar validation and native cleanup;
- UTF-8 output buffering and split stop sequences;
- earliest-position stop matching;
- Room migrations and mapper parity;
- prompt/output/schema/stop privacy exclusions;
- Playground ViewModel and Compose behavior.

Representative physical-device evidence must validate real GGUF template behavior, output quality, context memory, cancellation, throughput and thermal behavior before production claims.

## Deferred extensions

Deferred until the current controls and evidence are stable:

- min-p;
- presence and frequency penalties;
- DRY or other sampler families;
- conversational KV-state resizing;
- prefix snapshots and deterministic result caching;
- automatic thread or GPU-layer selection from a preset;
- remote prompt or template injection.
