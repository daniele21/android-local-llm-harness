# ARM64 emulator end-to-end results

This file records real-GGUF preflight runs on an Android ARM64 emulator. Emulator results validate the Android packaging, JNI and runtime path in an AVD, but they are not physical-device evidence and do not close the production-readiness gate in [`roadmap.md`](roadmap.md).

## 2026-08-03 — Qwen3 0.6B Q4_K_M

| Field | Value |
| --- | --- |
| Branch | `agent/phase-2-sanity-rules-and-doc-sync` |
| Tested commit | `69ade81959c1e7ce7745e2c7c8736db61b642aeb` |
| Repository state | Clean |
| Emulator | Google `sdk_gphone64_arm64` (`emu64a`) |
| Android | Android 16, API 36 |
| ABI | `arm64-v8a` |
| Model | `Qwen3-0.6B-Q4_K_M.gguf` |
| Model profile | `qwen3`, `Q4_K_M` |
| Model bytes | `484219808` |
| Model SHA-256 | `cd47557a67d7e8f2891d98b5e1dbf2988544569fdf4f1bdb30e92b71aa61b548` |
| Runtime | 4 CPU threads, 180-second operation timeout |
| Memory validation | 5 cycles, 131072 KB maximum configured PSS growth |
| Instrumentation | `OK (4 tests)`, `INSTRUMENTATION_CODE: -1` |
| Instrumentation time | 197.236 seconds |
| CI | Repository validation run `30841401711` passed on the tested commit |

Privacy-safe result markers:

```text
LOCAL_LLM_E2E inspection version=3 architecture=qwen3 tensorCount=311 fileType=15
LOCAL_LLM_E2E cancellation terminal=cancelled
LOCAL_LLM_E2E memory pssSamplesKb=[78099, 78026, 78030, 78042, 78074] growthKb=-25
LOCAL_LLM_E2E generation inputTokens=7 outputTokens=32 ttftMs=10172 totalMs=31811 decodeTokensPerSecond=1.1598405219282348
```

The run validated:

- APK and instrumentation installation;
- extracted `arm64-v8a` JNI and runtime-selected CPU backend libraries;
- GGUF inspection, streaming import and SHA-256 verification;
- model and context creation;
- generation and aggregated streaming;
- cooperative active-generation cancellation;
- five load/generate/unload cycles inside the configured PSS budget;
- final context release, model unload and runtime shutdown.

The emulator run exposed and verified fixes for ADB 37 model streaming, native backend extraction and cancellation-test context sizing. Those fixes are committed in `69ade81`.

## Interpretation

The recorded latency, throughput and PSS values describe only this AVD run. They are not representative Android device performance or thermal baselines. A negative PSS growth value means the final sample was lower than the first sample; it does not prove that native leaks are impossible. Prompts, generated text and model bytes are not included in this record.

Physical Android validation must still follow [`device-e2e-evidence.md`](device-e2e-evidence.md) before production readiness, downstream distribution or device compatibility and performance claims.
