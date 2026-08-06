# ADR 0009: Model-aware generation planning and lazy context materialization

- Status: Accepted
- Date: 2026-08-06

## Context

The runtime currently creates a native context from the fixed `contextSize` in the resolved
model profile before it receives a generation request. It then forwards the request input
directly to the backend and resolves a nullable seed to zero. This prevents exact prompt-token
planning, model-specific chat-template rendering, user-selectable context policy and faithful
recording of the effective generation configuration.

The Playground and future SDK adapters need versioned presets plus explicit temperature,
top-p, top-k, seed and output-token controls. Applications must not need to know model special
tokens, and the remote catalog must not gain authority to inject prompts or unrestricted
runtime configuration.

## Decision

Generation is resolved into a model-aware execution plan before prefill.

- `GenerationOverrides` owns request-scoped sampling, preset and output controls.
- `SessionOptions` owns `Auto` or `Manual` context policy.
- `createSession` creates a logical session; a native context is materialized lazily after the
  prompt is rendered and tokenized with the loaded model.
- Auto context selection uses the exact prompt token count, requested output budget and a
  bounded policy reserve. It selects the smallest approved size that fits, never silently
  truncates content and never silently changes a manual selection.
- A stateless session may grow its context when idle. It does not shrink during its lifetime.
  Conversational context recreation must be explicit because native KV state cannot be
  discarded silently.
- Presets, system prompts, template overrides and family fallbacks are application-owned,
  versioned and fail closed. Remote catalog entries continue to select only an approved
  `profileKey` under ADR 0005.
- The backend exposes neutral prompt planning and model-capability results; native handles,
  vocabularies and `llama.cpp` template structures remain private to the backend.
- Structured output constraints are separate from presets. A preset cannot promise JSON
  validity without a supported grammar or schema constraint.
- The effective seed is materialized once through an injected source, constrained to the
  native unsigned 32-bit range and included in privacy-safe execution metadata.
- The native backend owns one streaming decode path for template rendering, tokenization,
  sampling, grammar, stop handling and terminal-reason semantics. Aggregation, when needed,
  derives from that stream above the native boundary rather than running a second decode loop.
- Normal telemetry may persist bounded IDs, versions, numeric configuration, token counts,
  context size, template source and stop reason. It never persists prompt, output, messages,
  system-prompt text, template text, schema, grammar or stop sequences.

Template resolution is:

```text
supported GGUF template
        -> application-reviewed explicit override
        -> application-reviewed family fallback
        -> explicitly authorized raw completion
        -> typed failure
```

Generation precedence is resolved per field:

```text
valid request override
        -> explicitly selected versioned preset
        -> use-case default
        -> application-reviewed model recommendation
        -> bounded runtime fallback
```

Unknown or incompatible explicit selections fail rather than falling through silently.

## Consequences

Benefits:

- callers provide structured content instead of backend-specific prompt strings;
- Auto context can minimize memory without guessing token counts;
- effective settings are reproducible and diagnosable;
- prompt/template trust remains under application review;
- the same neutral contracts can serve Android-native, Capacitor and later transports.

Costs:

- session creation and native-context creation become separate lifecycle stages;
- public contracts, runtime fakes, telemetry schema and all consumers require coordinated
  migration;
- model-specific rendering and grammar add JNI/native test surface;
- context recommendations require representative-device evidence before support claims.

## Alternatives considered

### Keep context size in per-request overrides

Rejected because the context is an owned session resource, not a sampler value, and changing it
requires native resource recreation.

### Estimate tokens in Kotlin before loading the model

Rejected because an estimate is unsafe at context boundaries and may not match the exact model
vocabulary or applied chat template.

### Let the catalog deliver complete prompts and templates

Rejected under ADR 0005 because a compromised remote control plane could change application
behavior beyond selecting approved model bytes and profiles.

### Use one universal chat template

Rejected because GGUF model families require different roles, markers and special tokens.

### Treat a null seed as zero

Rejected because absence of a fixed seed is a policy choice and must not accidentally become a
deterministic seed.
