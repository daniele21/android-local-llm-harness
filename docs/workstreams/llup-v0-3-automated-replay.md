# LLUP v0.3.0 automated replay checkpoint

Status: active
Document type: execution checkpoint
Owner: llama-cpp-runtime / runtime-memory
Parent candidate: `f796235f47899314b65a5abc95c998b396883c4b`
Candidate backend: `v0.3.0` / `c1d0e7a004015f23bc0233470b747b596f29b264`
Control remains: `dev@ab5b35519d51ee903e1127b2973ab8cf30407704`
Execution policy: deterministic automated work first; all `REAL_ENVIRONMENT` evidence last

## Purpose

This checkpoint changes execution order, not LLUP acceptance criteria.

The production/control pin remains `b9637` until LLUP-70. The candidate migration in PR #490 stays frozen at the parent commit above so its exact-head FULL evidence remains valid. This stacked branch may add only replay workflow/documentation files and must not change runtime, backend, model-policy or product code.

## Current automated state

The parent candidate has repository-owned FULL evidence on exact head and unchanged control base. LLUP-20, LLUP-30 and LLUP-40 are therefore automated-preflight confirmed.

LLUP-60 is split operationally into two evidence phases:

- **LLUP-60A — automated replay now.** Re-run backend identity, host-native ownership/API behavior, Q35/runtime JVM contracts and exact Qwen3.5 host load/tokenize/generate compatibility against the candidate revision.
- **LLUP-60B — representative-device dependent replay later.** Consolidate evidence that actually depends on mobile memory, thermal state, device lifecycle or other representative-device behavior.

This split does not weaken LLUP-60. Global LLUP-60 remains incomplete until both phases required by canonical policy are complete.

## LLUP-60A lanes

The repository-owned workflow `.github/workflows/llup60-automated-replay.yml` must fail closed unless:

1. the evidence branch descends from the frozen candidate parent;
2. the branch diff contains only this checkpoint and its evidence workflow;
3. authoritative pin manifest, checked-out submodule, backend runtime revision and Q35 runtime capability identity remain consistent;
4. backend host-native tests pass on the exact candidate source;
5. Q35/model-profile and runtime-core JVM contracts pass;
6. exact Qwen3.5 0.8B and 2B GGUF identities match pre-existing reviewed digest/size values;
7. candidate `llama-simple` loads/tokenizes/generates against both exact Qwen3.5 artifacts;
8. a machine-readable evidence manifest records runtime source SHA separately from evidence-harness SHA.

No result from this host/CI replay is treated as representative-device performance or memory certification.

## Deferred REAL_ENVIRONMENT block

Keep these lanes for the final physical phase:

- LLUP-50 same-device `b9637` versus `v0.3.0` A/B;
- LLRT KV-cache evidence that is implemented through ADB/instrumentation;
- LLRT evaluation-batch evidence that is implemented through ADB/instrumentation;
- device-only Q35 performance, memory, thermal, cancellation/recovery and lifecycle evidence;
- any OMBRA/evaluation output replay whose canonical execution path genuinely requires representative-device/runtime evidence.

The paired LLUP-50 APK artifacts and physical runner are already prepared; they remain frozen and are not executed during LLUP-60A.

## Promotion and residency gates

LLUP-70 remains blocked. Passing LLUP-60A does **not** authorize merging PR #490 or changing the production pin.

MRES-10 remains blocked by LLUP-70. Bounded multi-residency implementation must not be started against an unpromoted backend baseline merely to keep work moving.

After all deterministic work is exhausted, run the deferred REAL_ENVIRONMENT block, consolidate LLUP-60B, then make the explicit LLUP-70 decision.
