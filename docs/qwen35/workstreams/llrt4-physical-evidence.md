# LLRT-4 physical recurrent-state evidence

Status: active
Document type: feature-specification
Owner: llama-cpp-runtime / qwen35
Canonical scope: qwen35.llrt4-physical-evidence
Read when: running or changing Qwen3.5 recurrent/session-state correctness evidence on physical Android
Last reviewed: 2026-08-20

## Purpose

LLRT-4 asks a correctness question before any recurrent/prefix reuse optimization is allowed for Qwen3.5: can the pinned llama.cpp sequence-state operations reproduce clean-context behavior for the exact curated mobile artifacts?

The native probe is deliberately allowed to return a negative evidence verdict. `KEEP_DISABLED` is a valid engineering outcome and must not be converted into a passing capability by weakening the checks.

## Native physical probe

Run one tier at a time on a physical arm64-v8a Android device:

```bash
bash scripts/run-llrt4-recurrent-state-android.sh \
  --model /path/Qwen3.5-0.8B-Q4_K_M.gguf \
  --tier 0.8b

bash scripts/run-llrt4-recurrent-state-android.sh \
  --model /path/Qwen3.5-2B-Q4_K_M.gguf \
  --tier 2b
```

The runner:

- verifies the exact curated artifact SHA-256;
- verifies the pinned llama.cpp revision;
- cross-compiles the native probe with the repository NDK version for arm64-v8a;
- runs the executable on the physical device;
- records artifact/backend/Harness/device identity without persisting user prompt/output content;
- writes a machine-readable `nativeVerdict` of `NATIVE_STATE_COMPATIBLE` or `KEEP_DISABLED`.

## Native checks

The probe compares full next-step logits against clean-context execution and covers:

- append-only restore from a captured prefix state;
- divergent restore from the same captured prefix state;
- clear-then-restore behavior;
- repeated restore cycles to detect residual-state contamination;
- full sequence removal followed by restore;
- partial rollback/removal followed by divergent continuation.

Any logit mismatch beyond the fixed tolerance or any required unsupported state operation produces `KEEP_DISABLED`.

## Boundary

`NATIVE_STATE_COMPATIBLE` is not permission to enable production reuse. Runtime-level evidence is still required for cancellation, memory pressure/warm-idle unload, model/context close and switch, structured/reasoning modes and repeated product lifecycle use. Until that evidence is reviewed, all Qwen3.5 prefix/session reuse capabilities remain false.

A probe/build/device infrastructure error exits non-zero. A completed probe with `KEEP_DISABLED` exits successfully because it is valid evidence rather than an infrastructure failure.
