# Console manual inference playground

## Scope

The manual inference playground adds an explicit one-shot generation surface to `apps/local-llm-console`.

It is a console adapter over the public `LocalLlmClient` contract. It does not depend on `RuntimeOrchestrator`, `llama.cpp`, JNI handles, Room implementation types or a backend-specific registry.

An embedding application supplies:

- a real `LocalLlmClient`;
- one or more explicit `ConsoleInferenceTarget` values;
- the application and use-case identities that are already registered in its model-profile registry.

The standalone console intentionally uses `DisconnectedConsoleInferenceControl`. It owns a model-store sandbox but does not own a configured inference runtime or application/use-case registry, so it does not fabricate a runnable target.

## User flow

The Playground view exposes `Run local prompt` only when a connected source has at least one registered target.

The request dialog collects:

- application/use-case target;
- prompt;
- maximum output tokens;
- temperature;
- seed.

Starting a request performs this lifecycle:

```text
prepare application/use case
create session
submit generation request
observe queued/started/text-delta events
complete, fail or cancel
close session
```

Only one playground generation may own a session at a time. The Start action remains visible but disabled while work is active. Cancel is available only after a `GenerationHandle` exists and is disabled after cancellation has been requested.

Refresh, tab selection and ordinary snapshot loading never prepare a model, create a session or start generation.

## Public runtime boundary

`LocalLlmConsoleInferenceControl` uses only:

- `LocalLlmClient.prepare()`;
- `LocalLlmClient.createSession()`;
- `LocalLlmClient.generate()`;
- `GenerationHandle.cancel()`;
- `LocalLlmClient.closeSession()`.

Targets are explicit application/use-case pairs. The playground does not accept an arbitrary model digest because the runtime contract resolves the correct model and profile through the existing application/use-case binding.

Generation overrides are limited to values already supported by `GenerationOverrides`:

- maximum output tokens;
- temperature;
- seed.

System prompts, context size, batching, thread counts, GPU layers and cache policy remain owned by the registered use-case and model profiles.

## Streaming and terminal state

The control maps runtime events into these phases:

- `PREPARING`;
- `QUEUED`;
- `GENERATING`;
- `COMPLETED`;
- `FAILED`;
- `CANCELLED`.

`TextDelta` events append to bounded in-memory output. The visible output is limited to 131,072 characters. Additional text is discarded from the console state and `outputTruncated` is set rather than allowing unbounded UI memory growth.

A `Completed` event replaces the accumulated display value with the terminal output, subject to the same bound, and exposes terminal metrics:

- queue duration;
- model-load duration and cold/warm classification;
- time to first token;
- prefill and decode duration;
- total duration;
- input and output tokens;
- decode tokens per second.

The adapter accepts terminal events that arrive synchronously before `generate()` returns its handle. A late handle cannot overwrite a completed, failed or cancelled state.

## Cancellation

Cancellation is cooperative and uses the runtime-provided `GenerationHandle`.

Calling Cancel:

1. invokes `GenerationHandle.cancel()`;
2. records that cancellation was requested;
3. waits for the runtime's terminal `GenerationEvent.Failed` with `LocalLlmError.Cancelled`;
4. maps the result to `CANCELLED`;
5. closes the session.

A cancellation request is not itself treated as terminal. If the runtime does not emit a terminal event, the playground continues to show the active session and does not claim successful cancellation.

The state update is race-safe when `cancel()` synchronously triggers the terminal callback: `CANCELLED` is not overwritten by the intermediate cancellation-requested state.

## Session cleanup

Every completed, failed or cancelled terminal event triggers `closeSession()`.

Successful cleanup clears the active session and request identity while preserving the terminal output and metrics for inspection. Cleanup failure changes the terminal result to:

```text
phase=FAILED
errorCode=SESSION_CLEANUP_FAILED
detail=Inference session cleanup failed
```

The backend exception message is not exposed.

If generation cannot be started after session creation, the adapter attempts to close the session before returning the start failure. Destroying the Activity calls `ConsoleInferenceControl.close()`, which best-effort cancels the handle and closes any active session.

## Privacy boundary

The prompt exists only in the request object passed to `LocalLlmClient.generate()`. It is not copied into:

- `ConsoleInferenceState`;
- `ConsoleSnapshot`;
- presenter cards;
- console telemetry;
- saved instance state;
- Room storage.

Generated output is held only in bounded in-memory playground state. It is not written by the console to normal telemetry, structured logs or saved instance state.

The runtime may still record its existing privacy-safe generation metrics and lifecycle events. Those contracts exclude prompts and generated output.

Arbitrary preparation, generation, cancellation and cleanup exception messages are replaced with fixed console-safe details.

## Failure isolation

Inference state is loaded independently by `TelemetryConsoleDataSource`. Failure in the inference source produces:

```text
Inference playground unavailable
```

It does not suppress runtime state, model inventory, telemetry, health controls, cache diagnostics, resource history or benchmarks.

The standalone disconnected state is different from a source failure:

- disconnected means no inference capability was supplied;
- unavailable means a supplied capability failed while producing its snapshot.

## Testing

Pure JVM tests cover:

- explicit target registration;
- preparation and session creation;
- queued, started and text-delta streaming;
- completed output and metric mapping;
- output bounding and truncation;
- explicit cancellation;
- cancellation-terminal race behavior;
- session closure after completion and cancellation;
- cleanup failure overriding an otherwise successful terminal result;
- fixed privacy-safe preparation and cleanup failures;
- prompt exclusion from console state;
- disconnected, idle, generating and completed presentation;
- disabled Start and enabled Cancel while generation is active;
- inference-source failure isolation.

The repository gate additionally runs Spotless, ktlint, Detekt, Android Lint, Android compilation and packaging verification.

## Deferred behavior

This slice does not provide:

- persisted chat history;
- multi-turn conversation state;
- arbitrary model/profile construction;
- automatic model import or loading;
- prompt templates or system-prompt editing;
- cross-application runtime access;
- persistence or export of generated output.

Cross-application playground execution requires the future signature-protected diagnostics bridge.
