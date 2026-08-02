# Android device end-to-end testing

This procedure validates the Phase 1 runtime on a physical Android `arm64-v8a` device with a real GGUF model.

The test model is never committed to the repository or packaged in an APK. The host runner streams it into the private data directory of a debuggable test application through `adb run-as`.

## What the runner validates

The `apps/device-test-runner` module exercises the production implementations of:

- native GGUF inspection without full model loading;
- `FileSystemModelStore` import and SHA-256 verification;
- explicit application/use-case/model binding;
- `RuntimeOrchestrator` preparation and session lifecycle;
- `LlamaCppInferenceBackend` JNI initialization;
- model load and unload;
- context creation and release;
- aggregated streaming generation;
- generation metrics;
- cooperative active-generation cancellation;
- optional repeated load/generate/unload cycles with a configurable PSS-growth budget.

The main lifecycle is:

```text
copy model into app-private storage
        ↓
inspect GGUF metadata
        ↓
import and verify SHA-256
        ↓
initialize llama.cpp
        ↓
load model
        ↓
create context
        ↓
generate and stream
        ↓
release context
        ↓
unload model
        ↓
shutdown runtime
```

## Prerequisites

- JDK 17 and the repository Android toolchain;
- Android SDK platform-tools with `adb` available;
- one connected physical Android `arm64-v8a` device;
- USB debugging enabled;
- a readable GGUF file compatible with the pinned `llama.cpp` build;
- enough free device storage for both the staged source and content-addressed copy.

The initial Phase 1 device gate is CPU-only and uses `gpuLayers = 0`.

## Run the standard lifecycle and cancellation suite

```bash
bash scripts/run-device-e2e.sh \
  --model /absolute/path/to/model.gguf \
  --architecture qwen2 \
  --quantization Q4_K_M
```

The architecture and quantization arguments are diagnostic profile metadata. They do not replace GGUF compatibility checks performed by the native backend.

The script:

1. verifies that the connected device reports an `arm64-v8a` ABI;
2. builds the target and instrumentation APKs;
3. installs both APKs;
4. computes the model SHA-256 on the host;
5. streams the model into `files/e2e/model.gguf` inside the app sandbox;
6. verifies the transferred byte count;
7. discovers the installed `AndroidJUnitRunner` component;
8. runs inspection, generation, cancellation and any enabled memory test;
9. returns a non-zero exit code when instrumentation fails.

## Run repeated lifecycle and memory validation

```bash
bash scripts/run-device-e2e.sh \
  --model /absolute/path/to/model.gguf \
  --architecture qwen2 \
  --quantization Q4_K_M \
  --memory-repeat 5 \
  --max-pss-growth-kb 131072
```

The memory check records process proportional set size after each complete runtime cycle. It is a regression guard, not a substitute for Android Studio Profiler, Perfetto or native heap inspection.

Use a device-specific budget derived from a stable baseline. A single successful run does not prove the absence of native leaks.

## Useful options

```text
--prompt VALUE
--cpu-threads VALUE
--timeout-seconds VALUE
--cancellation-prompt VALUE
--skip-cancellation
--memory-repeat COUNT
--max-pss-growth-kb VALUE
```

Cancellation validation is enabled by default. The cancellation prompt should be long enough that the selected model is still decoding when the first streamed delta is received.

## Result markers

Successful tests print privacy-safe markers without prompt or generated content:

```text
LOCAL_LLM_E2E inspection version=... architecture=... tensorCount=... fileType=...
LOCAL_LLM_E2E generation inputTokens=... outputTokens=... ttftMs=... totalMs=...
LOCAL_LLM_E2E cancellation terminal=cancelled
LOCAL_LLM_E2E memory pssSamplesKb=[...] growthKb=...
```

Capture the full instrumentation output, device model, Android version, ABI, GGUF identity and runtime build metadata when recording Phase 1 evidence.

## Completion evidence

Do not mark the Phase 1 device gate complete until the selected representative device/model matrix has evidence for:

- successful inspect/import/verify/load/generate/stream/release/unload/shutdown;
- active cancellation;
- repeated lifecycle without unbounded growth;
- correct JNI library packaging in the generated APK;
- no native crash or unrecoverable runtime state after a failed or cancelled request.
