# Phone inference playground

Status: active
Document type: feature-specification
Owner: apps/local-llm-phone-test
Canonical scope: phone.playground
Read when: changing connected-app prompt entry, generation controls, streaming or cancellation
Last reviewed: 2026-08-06

## Scope

`apps/local-llm-phone-test` is the first installable Android application that exposes the embedded local runtime as a manual inference playground on a physical phone.

The application continues to support the existing privacy-safe validation workflow. The playground adds a separate interactive path over the same imported content-addressed GGUF store.

## User flow

1. Install the phone-test application.
2. Select a GGUF through Android's Storage Access Framework.
3. Confirm the architecture and quantization labels.
4. Wait for the model to be copied into app-private storage and verified by SHA-256.
5. Enter a prompt in the Local inference playground section.
6. Optionally change maximum output tokens, temperature and seed.
7. Run the prompt and inspect streamed output and terminal metrics.
8. Cancel an active generation when a generation handle is available.
9. Run another prompt without restarting the application.

## Runtime lifecycle

The playground owns a dedicated `RuntimeOrchestrator` configured through an explicit application/use-case binding:

```text
applicationId = play-internal-phone-test
useCaseId = manual-inference-playground
```

The runtime is created lazily after the first prompt. For the same imported model it is retained between requests, allowing the UI to expose cold and warm model-load behavior.

Each prompt still receives its own session:

```text
verify model
prepare runtime
create session
generate and stream
complete, fail or cancel
close session
retain compatible runtime for the next prompt
```

Before model replacement, model removal or the full physical-device validation suite, the playground runtime is released on its worker executor. This prevents the interactive and validation paths from owning the same model concurrently.

## Controls

The playground accepts:

- a non-empty prompt up to 32,768 characters;
- maximum output tokens from 1 to 512;
- temperature from 0.0 to 2.0;
- a signed 64-bit seed.

The registered playground profile uses:

- context size: 2,048;
- CPU-only execution;
- up to four CPU threads;
- default maximum output tokens: 128;
- default temperature: 0.0, overridden by the UI request;
- default deterministic seed: 42, overridden by the UI request.

## Output and metrics

Text deltas are appended to bounded in-memory state. Visible output is capped at 131,072 characters and explicitly marked when truncated.

A completed request displays:

- cold or warm load classification;
- model-load duration;
- time to first token;
- total latency;
- input and output tokens;
- decode tokens per second.

## Cancellation and cleanup

Cancellation is cooperative and becomes available only after the runtime returns a `GenerationHandle`.

Completed, failed and cancelled requests close their session. Session-cleanup failure is converted into a fixed privacy-safe failure state. The runtime itself remains available for the next compatible prompt until the model changes, validation starts or the Activity is destroyed.

## Privacy boundary

The prompt and generated output are held only in Activity and playground memory. They are not added to the validation report, preferences, saved instance state, Room telemetry or structured logs.

The existing validation report remains privacy safe and continues to exclude prompts and generated output.

## Current limitations

- the playground is one-shot and does not maintain multi-turn conversation history;
- it does not edit system prompts or low-level model profile parameters;
- it supports only the imported model owned by the phone-test application;
- physical-device evidence must still be collected through Google Play internal testing;
- the standalone developer console remains a separate control-plane application until a protected diagnostics bridge is introduced.
