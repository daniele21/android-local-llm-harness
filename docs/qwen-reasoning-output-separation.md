# Qwen reasoning output separation

## Objective

Qwen3.5 thinking-capable profiles must be able to use internal reasoning without mixing that reasoning with the final answer delivered to a consumer application.

The runtime therefore treats generated text as two explicit channels:

- `REASONING`: model thinking output;
- `ANSWER`: the final answer intended for the consuming application.

The raw generated output remains available for backward compatibility and diagnostics, but integrations should use the structured answer channel for user-facing output.

## Problem addressed

With Qwen3.5 thinking enabled, the chat template pre-fills the assistant `<think>` opener. The model stream can therefore begin directly with reasoning text and later emit `</think>` before the final answer.

A single undifferentiated output stream caused two problems:

1. reasoning could be rendered as if it were the final answer;
2. a small model could spend the complete output budget reasoning and never leave capacity for an answer.

The implementation solves both problems in the runtime/backend boundary rather than with UI-side regular expressions.

## Public generation behavior

`GenerationEvent.TextDelta` includes a `GenerationContentType` value:

```kotlin
GenerationContentType.REASONING
GenerationContentType.ANSWER
```

`GenerationEvent.Completed` exposes:

```kotlin
reasoningOutput
answerOutput
output
```

`output` preserves the legacy raw-output behavior. `answerOutput` is the structured final answer. `reasoningOutput` is diagnostic reasoning content and must not be treated as end-user output by consumer applications.

The metrics contract also exposes, when the backend can measure them accurately:

```kotlin
timeToFirstAnswerMs
reasoningTokens
answerTokens
```

The existing aggregate token and latency metrics remain available.

## Model-profile configuration

Reasoning delimiters are model-profile data rather than runtime or UI assumptions.

`GenerationDefaults.reasoningStreamProtocol` selects the stream protocol. Qwen3.5 uses:

```kotlin
ReasoningStreamProtocol.QWEN35_THINK_TAGS
```

The protocol defines the generated close marker and the text used by a backend that supports a controlled transition out of reasoning.

`GenerationGuardPolicy.answerReserveTokens` defines the amount of output capacity reserved for the final answer.

Current Qwen3.5 mobile defaults are:

| Tier | Total thinking-profile output | Reasoning budget | Answer reserve |
| --- | ---: | ---: | ---: |
| 0.8B | 512 | 192 | 256 |
| 2B | 1024 | 384 | 512 |

The remaining capacity also accounts for the reasoning close transition and normal generation overhead.

## Streaming parser

`ReasoningStreamParser` lives in `core/runtime-core` and is independent of Compose and llama.cpp types.

For Qwen3.5 with thinking enabled it:

1. starts in the `REASONING` state because the opening `<think>` marker is pre-filled by the chat template;
2. accepts arbitrary backend streaming chunks;
3. retains only the suffix that could still be a prefix of `<think>` or `</think>`;
4. detects `</think>` even when the marker is split across chunks;
5. removes protocol markers from structured deltas;
6. transitions once to the `ANSWER` channel;
7. removes the Qwen transition newlines before the first answer text;
8. flushes unterminated pending text as reasoning rather than mislabelling it as an answer.

Thinking-disabled requests and profiles without a reasoning protocol continue to emit answer text directly.

## Runtime orchestration

`RuntimeOrchestrator` owns channel separation because it is shared by all higher-level integrations.

The backend still streams raw generated text. The orchestrator:

- appends raw chunks to the compatibility `output` buffer;
- passes chunks through `ReasoningStreamParser`;
- emits typed `TextDelta` events;
- builds independent reasoning and answer buffers;
- records time to first non-blank answer text;
- passes a `BackendReasoningControl` only when the backend advertises support for a controlled reasoning transition.

This keeps consumer UIs and transports free from Qwen-specific parsing logic.

## Native llama.cpp reasoning transition

The direct llama.cpp backend advertises `supportsReasoningTransition = true`.

When a thinking-enabled request reaches its reasoning budget before Qwen has emitted the close marker, the native generation path does not terminate the whole request. Instead it:

1. tokenizes the configured forced close text (`</think>\n\n` for Qwen3.5);
2. appends those tokens to the current decode context;
3. feeds the tokens through the sampler/context as generated history;
4. records the reasoning boundary;
5. continues sampling with the remaining output budget for the final answer.

The forced transition is rejected before generation when its configuration is incomplete or would leave no output capacity for an answer.

The older Kotlin generation guard remains the fallback for backends that do not support controlled reasoning transitions. It does not pre-empt the native transition when native reasoning control is active.

## Threading and lifecycle

Reasoning parsing happens inside the existing serialized generation callback for a request. It does not introduce an additional worker, scheduler, or global state.

Cancellation retains the normal request lifecycle. A cancelled request does not flush additional parser output after cancellation.

Reasoning state is request-local and is discarded when generation completes.

## Console and phone playground

The developer surfaces keep reasoning separate from the final answer:

- the console state stores `reasoningOutput` and `answerOutput` independently;
- the phone playground renders a dedicated collapsible Thinking section;
- once final-answer text starts, the Thinking section collapses automatically;
- the final answer remains the primary response surface;
- `timeToFirstAnswerMs` is shown separately from TTFT when available.

The Thinking surface belongs to the developer playground and observability experience. Consumer applications should render only the answer channel unless they intentionally implement a diagnostic developer view.

## Compatibility

The change is additive at the public contract boundary:

- existing raw `output` remains available;
- aggregate output-token metrics remain available;
- thinking-disabled generation preserves the previous answer-stream behavior;
- model-specific reasoning behavior is opt-in through model profiles and backend capabilities.

A backend that does not support reasoning transitions can still implement `InferenceBackend`; the capability defaults to `false`.

## Relevant files

```text
core/contracts/.../Generation.kt
core/runtime-core/.../InferenceBackend.kt
core/runtime-core/.../ReasoningStreamParser.kt
core/runtime-core/.../GenerationGuard.kt
core/runtime-core/.../RuntimeOrchestrator.kt
models/model-profile/.../Profiles.kt
models/model-profile/.../Qwen35GenerationProfiles.kt
backends/llama-cpp/.../LlamaCppGeneration.kt
backends/llama-cpp/.../LlamaCppStreaming.kt
backends/llama-cpp/src/main/cpp/llama_jni_entry.cpp
backends/llama-cpp/src/main/cpp/reasoning_transition.h
apps/local-llm-console/...
apps/local-llm-phone-test/...
```

## Tests

The feature is covered at multiple boundaries:

- parser tests for complete and split markers, echoed opening markers, Unicode, disabled thinking and unterminated reasoning;
- runtime integration tests for typed deltas, structured completion output, reasoning control and metrics;
- generation-guard tests for controlled-transition ownership;
- llama.cpp Kotlin/JNI bridge validation tests;
- native reasoning-transition tracker tests;
- Qwen profile budget tests;
- console output-routing tests;
- phone playground presentation tests.

Repository validation must still pass formatting, Detekt, unit tests, lint, Android packaging and native host tests before the PR is considered ready to merge.

## Limitations

- Reasoning/answer token counts are only published when the backend can identify the boundary in token space; the runtime does not estimate token counts from characters.
- The controlled native transition currently applies only to profiles that explicitly configure a supported reasoning protocol.
- The implementation does not expose a generic server-style reasoning API; it remains compatible with the project's direct llama.cpp JNI backend architecture.
- A real-device run with the target Qwen3.5 GGUF is still recommended before treating the behavior as device-validated.
