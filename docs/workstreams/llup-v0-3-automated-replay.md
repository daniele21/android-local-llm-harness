# LLUP v0.3.0 automated replay checkpoint

Status: active
Document type: workstream-state
Owner: llama-cpp-runtime / runtime-memory
Canonical scope: workstream.llup-v0-3-automated-replay
Read when: refreshing LLUP-60 evidence, preparing LLUP-50 device qualification, or evaluating LLUP-70 promotion readiness
Last reviewed: 2026-08-30

Repository integrated state and blockers remain owned by [`../current-state.md`](../current-state.md); this file owns only the bounded LLUP qualification/replay sequence.

## Frozen qualification identities

- Control: `dev@80164329bbc41a00b75721e3d0524294c03fdb56`, llama.cpp `b9637` / `aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3`.
- Candidate: `59af48313b450d9cff13c7f43458c2e5e6560374`, llama.cpp `v0.3.0` / `c1d0e7a004015f23bc0233470b747b596f29b264`.
- Control LLUP-50 evidence ref: `evidence/llup50-control@fcbefc7cd9af84de570da96d039582175dd1700b`.
- Candidate LLUP-50 evidence ref: `evidence/llup50-candidate@a2a050d9551db541bb4c6b152cba8623c782164d`.
- LLUP-50 runner branch: `agent/llup50-physical-runner@cefb893ae95cfd95339de4a24a955a652f0011e6`.

The previous candidate `f796235f47899314b65a5abc95c998b396883c4b` was refreshed only because `dev` gained the canonical Android AAB packaging command. The llama.cpp runtime behavior did not change, but packaging identity is part of qualification, so relevant exact-head evidence is refreshed rather than reused. The automated work is refreshed for this candidate/base pair while `REAL_ENVIRONMENT` evidence remains intentionally deferred.

## Current automated state

PR #490 has repository-owned FULL evidence on exact candidate head `59af48313b450d9cff13c7f43458c2e5e6560374` against `dev@80164329bbc41a00b75721e3d0524294c03fdb56`. Authoritative run: `33332939707`.

- LLUP-20: `PASS` / `AUTOMATED_PREFLIGHT_CONFIRMED`.
- LLUP-30: `PASS` / `AUTOMATED_PREFLIGHT_CONFIRMED`.
- LLUP-40: `PASS` / `AUTOMATED_PREFLIGHT_CONFIRMED`.
- LLUP-50: `PENDING_REAL_ENVIRONMENT`.
- LLUP-60: split into automated LLUP-60A and representative-device LLUP-60B; globally incomplete until both required parts pass.
- LLUP-70: `BLOCKED` until the physical block completes.
- MRES-10+: `BLOCKED` by LLUP-70.

Previous LLUP-60A run `33332064271` and STRONG preflight `33332084155` remain historical provenance but are stale for the refreshed candidate/base pair.

## LLUP-60A acceptance

The repository workflow `.github/workflows/llup60-automated-replay.yml` must prove on the refreshed exact candidate:

1. evidence-only branch scope;
2. authoritative pin/submodule/backend/Q35 identity consistency;
3. native backend ownership/API contracts;
4. Q35 model-profile and runtime-core JVM contracts;
5. evaluation and observability/benchmark contracts;
6. exact Qwen3.5 0.8B and 2B GGUF identity;
7. host load/tokenize/generate compatibility for both curated GGUFs;
8. machine-readable provenance separating runtime source SHA from evidence-harness SHA;
9. promotion disabled while representative-device evidence is pending.

Automated replay does not replace output-quality, latency, memory, thermal or lifecycle evidence that genuinely requires representative Android execution.

## LLUP-50 boundary

The exact-APK runner remains isolated in draft PR #491. It consumes CI-built APKs, verifies package source revision and SHA-256, captures model-load/tuning/memory/cancellation/LOW_MEMORY/switch evidence and compares paired control/candidate results without inventing promotion thresholds.

Before using a phone, repository-owned package automation must build exact-ref APK/manifests for the refreshed control and candidate evidence refs. Packaging is `REMOTE_AUTOMATED`; executing those artifacts on the representative phone is `REAL_ENVIRONMENT`.

## Deferred REAL_ENVIRONMENT block

- LLUP-50 same-device `b9637` versus `v0.3.0` A/B;
- LLRT KV-cache and evaluation-batch physical evidence;
- device-only Q35 load/performance/PSS/memory/thermal/cancellation/recovery/lifecycle evidence;
- representative-device resident-count, warm-idle and prepare-after-release recovery;
- output-quality evidence whose canonical execution path requires representative-device runtime execution.

## Promotion-readiness boundary

Classify the workstream as `WAITING_REAL_ENVIRONMENT` only when:

- PR #490 remains unmerged and exact-head FULL green;
- PR #491 tooling validation is green on its refreshed head;
- LLUP-60A replay and STRONG preflight are green on the same refreshed evidence head;
- exact control/candidate package identities are available from repository-owned automation;
- no deterministic automation gap remains.

Then execute LLUP-50 and LLUP-60B, re-check promotion FULL requirements, make the explicit LLUP-70 decision, and only after promotion authorization merge PR #490 and unblock MRES-10.
