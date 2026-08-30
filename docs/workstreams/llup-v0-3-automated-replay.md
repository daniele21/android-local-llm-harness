# LLUP v0.3.0 automated replay checkpoint

Status: active — automated work in progress; `REAL_ENVIRONMENT` intentionally deferred
Document type: execution checkpoint / promotion-readiness ledger
Owner: llama-cpp-runtime / runtime-memory
Parent candidate: `f796235f47899314b65a5abc95c998b396883c4b`
Candidate backend: `v0.3.0` / `c1d0e7a004015f23bc0233470b747b596f29b264`
Control remains: `dev@ab5b35519d51ee903e1127b2973ab8cf30407704`
Execution policy: deterministic automated work first; all `REAL_ENVIRONMENT` evidence last

## Purpose

This checkpoint changes execution order, not LLUP acceptance criteria.

The production/control pin remains `b9637` until LLUP-70. The candidate migration in PR #490 stays frozen at the parent commit above so its exact-head FULL evidence remains valid. This stacked branch may add only replay workflow/documentation files and must not change runtime, backend, model-policy or product code.

The checkpoint is also the pre-promotion evidence index. It must make clear which gates are already exact-head automated evidence, which are replayed here, and which are still blocked on representative-device execution. `WAITING_REAL_ENVIRONMENT` is a valid readiness boundary; it is not a promotion decision.

## Frozen identities

| Role | Harness/runtime source | llama.cpp |
|---|---|---|
| Control | `ab5b35519d51ee903e1127b2973ab8cf30407704` | `b9637` / `aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3` |
| Candidate | `f796235f47899314b65a5abc95c998b396883c4b` | `v0.3.0` / `c1d0e7a004015f23bc0233470b747b596f29b264` |

Evidence-only branches must record their own harness SHA separately and must not replace either frozen runtime identity.

## Current automated state

The parent candidate has repository-owned FULL evidence on exact head `f796235f47899314b65a5abc95c998b396883c4b` against unchanged control base `ab5b35519d51ee903e1127b2973ab8cf30407704`. Authoritative FULL run: `33324145728`.

Therefore:

- **LLUP-20 — candidate native/API migration:** `PASS` / `AUTOMATED_PREFLIGHT_CONFIRMED`.
- **LLUP-30 — runtime correctness, lifecycle and execution identity propagation:** `PASS` / `AUTOMATED_PREFLIGHT_CONFIRMED`.
- **LLUP-40 — Android ARM64 build and package validation:** `PASS` / `AUTOMATED_PREFLIGHT_CONFIRMED`.
- **LLUP-50 — same-device A/B:** `PENDING_REAL_ENVIRONMENT` by deliberate execution order.
- **LLUP-60 — affected evidence replay:** split into automated LLUP-60A and representative-device LLUP-60B; global task remains incomplete until both required parts pass.
- **LLUP-70 — promotion decision:** `BLOCKED` until the deferred real-environment block is complete.
- **MRES-10+:** `BLOCKED` by LLUP-70; do not implement multi-residency against an unpromoted backend baseline.

## LLUP-60A — automated replay

The repository-owned workflow `.github/workflows/llup60-automated-replay.yml` must fail closed unless:

1. the evidence branch descends from the frozen candidate parent;
2. the branch diff contains only this checkpoint and its evidence workflow;
3. authoritative pin manifest, checked-out submodule, backend runtime revision and Q35 runtime capability identity remain consistent;
4. backend host-native ownership/API tests pass on the exact candidate source;
5. Q35/model-profile and runtime-core JVM contracts pass;
6. evaluation contracts that consume runtime execution identity pass, including engine/runtime-adapter/comparison/persistence and dataset/evaluator integrity;
7. observability execution/benchmark contracts preserve compatible evidence and fingerprint semantics;
8. exact Qwen3.5 0.8B and 2B GGUF identities match the existing reviewed digest/size values;
9. candidate `llama-simple` loads/tokenizes/generates against both exact Qwen3.5 artifacts;
10. a machine-readable replay manifest records runtime source SHA separately from evidence-harness SHA and marks promotion disallowed while representative-device evidence is pending.

The evaluation/benchmark lane is deliberately a contract/integrity replay. It does not pretend to replace real output-quality, latency, memory or thermal evidence from representative Android execution.

The first LLUP-60A replay (`33326979912`) and its STRONG validation (`33327011605`) passed before the explicit evaluation/benchmark lane was added. Those runs remain historical provenance, but exact-head acceptance for the expanded replay must come from the newest workflow and STRONG run after this checkpoint update.

## Deferred REAL_ENVIRONMENT block

Keep all of these lanes for the final physical phase:

- LLUP-50 same-device `b9637` versus `v0.3.0` A/B;
- LLRT KV-cache evidence implemented through ADB/instrumentation;
- LLRT evaluation-batch evidence implemented through ADB/instrumentation;
- device-only Q35 performance, model-load latency, memory/PSS, thermal, cancellation/recovery and lifecycle evidence;
- representative-device resident-count, warm-idle and prepare-after-release recovery evidence required by the LLUP acceptance contract;
- any OMBRA/evaluation output replay whose canonical execution path genuinely requires representative-device/runtime evidence.

The paired LLUP-50 APK artifacts and exact-APK physical runner are prepared separately and remain frozen. They are not executed during LLUP-60A.

## Promotion-readiness boundary

Before entering the deferred physical block, the expected automated state is:

- frozen control and candidate runtime identities still unchanged;
- PR #490 runtime diff exact-head FULL green and unmerged;
- LLUP-50 runner/tooling preflight green and unmerged;
- LLUP-60A replay manifest green on its exact evidence HEAD;
- STRONG preflight green on that same evidence HEAD;
- exact candidate package identity available from repository-owned automation;
- no unresolved deterministic validation failure or automation-capability gap that can be fixed before using a device.

When all bullets above hold, classify the workstream as `WAITING_REAL_ENVIRONMENT`, not complete and not promoted.

## Final sequence after automated exhaustion

1. Execute LLUP-50 same-device control/candidate A/B using the frozen APK identities.
2. Execute deferred device-only LLRT/Q35/memory/thermal/lifecycle and required output-quality evidence.
3. Consolidate LLUP-60B and verify all evidence still refers to the frozen candidate runtime SHA/backend revision.
4. Re-check target/base freshness and promotion FULL requirements.
5. Make the explicit LLUP-70 promotion/reject decision.
6. Only after a promotion decision authorizes it, merge/promote PR #490 and then unblock MRES-10.

No earlier automated success authorizes production promotion by itself.
