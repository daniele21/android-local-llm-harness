# Model evaluation validation and evidence workstream

Status: active
Document type: evidence-runbook
Owner: model-evaluation
Canonical scope: model-evaluation.validation
Read when: validating model-evaluation correctness, Android integration, reproducibility, privacy or physical-device evidence
Last reviewed: 2026-08-15

## Goal

Prove that model evaluation is deterministic where it claims to be deterministic, lifecycle-safe on Android, privacy-safe in persistence/diagnostics and honest about which comparisons are supported by the collected evidence.

Host/JVM tests establish calculation and orchestration correctness. They do not establish representative local-LLM performance. Physical-device results remain device/model/configuration evidence rather than universal model rankings.

## Evidence layers

### Layer 1 — deterministic host/JVM

Covers:

- contract validation and fingerprint stability;
- dataset schema/digest/sampling;
- evaluator golden behavior;
- runner lifecycle with fakes;
- repository parity and retention;
- comparison compatibility;
- UI state/reducer behavior.

This layer is required for ordinary merge readiness.

### Layer 2 — Android integration

Covers production Android boundaries:

- app-private dataset storage/import;
- Room persistence and migration;
- connected ViewModel/effect wiring;
- runtime orchestration through the embedded harness;
- request/telemetry correlation;
- cancellation and recreation behavior.

A developer-injected validation model may be used only through the existing validation-plane policy; it must not create a consumer arbitrary-model path.

### Layer 3 — representative physical-device evidence

Covers exact supported Qwen3.5 reference artifacts on representative `arm64-v8a` devices:

- General Purpose v1 Smoke/Standard execution;
- semantic scores by category;
- model preparation and per-case latency/throughput;
- memory and thermal evidence where available;
- runtime failure/timeout/invalid-output rates;
- comparison compatibility on identical device/runtime conditions.

Candidate runtime profiles may be used for development evidence but must not be presented as certified defaults. Final performance comparison evidence should use the measured Q35-6 profiles when available.

## Reproducibility evidence

A reproducibility fixture must prove that repeated clean executions with the same:

```text
model artifact digest
dataset digest
sample preset/count
sampling policy/version
execution profile/version
evaluator versions
backend/runtime identity required by policy
```

resolve the same sample IDs and comparison identity.

`EvaluationIdentityGoldenTest` now freezes v1 golden values for ordered sample-set digest, evaluator-set digest, case-execution semantics digest, semantic-execution fingerprint and full run fingerprint. It also proves parameter-map construction order does not alter evaluator-set identity and equivalent clean run construction yields the same run identity.

Model generation may still vary only where the selected execution profile explicitly allows stochasticity; v1 default General Purpose execution should avoid this by using deterministic/fixed settings.

## Privacy evidence

Automated inspection must demonstrate that normal telemetry, structured logs, persisted evaluation summaries and default diagnostic export do not contain fixture prompt text, expected answer text or generated answer text.

Use sentinel fixture strings that are easy to search across Room/export/log evidence. A privacy test fails if a sentinel appears outside dataset storage or explicitly ephemeral in-memory output handling.

## Failure/lifecycle matrix

The validation matrix includes at least:

| Failure point | Required behavior |
| --- | --- |
| invalid manifest/case | fail before model execution; no dataset publication |
| unsupported evaluator | fail preflight before model execution |
| unsupported/uninstalled model | fail preflight without binding mutation |
| runtime generate failure | typed case failure; close case resources; continue only per policy |
| malformed output | deterministic evaluator outcome; no parser crash |
| case timeout | cancel active generation and close resources |
| user cancellation | cancel active case; remaining cases become skipped/not-attempted |
| persistence failure | surface typed partial/failure state; runtime remains reusable |
| process recreation | restore persisted state; do not resume decode implicitly |
| dataset deletion during active run | reject/defer deletion deterministically |
| missing telemetry metric | preserve `Unavailable`; never synthesize zero |

## Comparison validation

Fixtures must test independently:

- same dataset and sample set, different model: quality-compatible;
- different dataset version: quality-incompatible;
- same dataset but different preset/sample-set digest: quality-incompatible;
- different execution profile/evaluator version: quality-incompatible;
- same quality identity on different devices: quality-compatible but runtime-incompatible;
- same device but different backend/runtime tuning identity: runtime-incompatible;
- fully matching runtime identity: calculated runtime deltas allowed.

The UI tests must prove that raw summaries may still be shown while invalid deltas are suppressed.

## Physical-device protocol

For each selected supported model tier and exact artifact:

1. record device model, Android version, ABI, available memory and current thermal state;
2. record harness build/commit identity, pinned backend revision and runtime profile identity;
3. verify installed model SHA-256;
4. verify General Purpose dataset digest and sample-set digest;
5. prepare model according to the evaluation run's explicit load/warm-up policy;
6. execute the selected preset with isolated per-case contexts;
7. retain privacy-safe run/result/telemetry evidence;
8. verify runtime cleanup and reusability after completion;
9. compare only runs satisfying the compatibility service;
10. label results as Harness subset/device evidence, never as official full upstream benchmark scores.

For performance evidence intended to choose defaults, run enough repetitions to characterize variance; one single suite execution is not a production baseline.

## Task ledger — deterministic validation

| ID | State | Depends on | Task |
| --- | --- | --- | --- |
| EVAL-V-01 | DONE | EVAL-C-08 | Add identity/hash golden fixtures and cross-run deterministic serialization tests. |
| EVAL-V-02 | PLANNED | EVAL-D-09 | Add dataset parser limits, malformed-input and atomic-install/rollback tests. |
| EVAL-V-03 | READY | EVAL-E-09 | Add evaluator golden corpus including ambiguous, malformed and edge outputs. |
| EVAL-V-04 | PLANNED | EVAL-R-12 | Add runner lifecycle matrix using fake runtime/telemetry failure injection. |
| EVAL-V-05 | PLANNED | EVAL-P-10 | Add persistence/restart/retention/comparison compatibility matrix. |
| EVAL-V-06 | PLANNED | EVAL-U-25,EVAL-U-33,EVAL-U-44 | Add connected UI state tests for run, import, history, comparison and failure surfaces. |
| EVAL-V-07 | PLANNED | EVAL-D-05,EVAL-P-07 | Add privacy sentinel tests across telemetry, logs, evaluation persistence and default diagnostics export. |

EVAL-V-01 is satisfied by `EvaluationIdentityGoldenTest`. EVAL-V-03 can now extend the scorer-local E-09 golden/adversarial tests into a reusable evaluator corpus without blocking other feature lanes.

## Task ledger — Android integration

| ID | State | Depends on | Task |
| --- | --- | --- | --- |
| EVAL-V-10 | PLANNED | EVAL-D-10,EVAL-P-04 | Instrument canonical dataset import/storage and Room persistence on Android. |
| EVAL-V-11 | PLANNED | EVAL-R-12,EVAL-V-10 | Execute fixture evaluation end-to-end through production runtime orchestration on Android. |
| EVAL-V-12 | PLANNED | EVAL-R-09,EVAL-V-11 | Validate active cancellation, timeout and subsequent runtime reuse on device. |
| EVAL-V-13 | PLANNED | EVAL-U-17,EVAL-V-11 | Validate Activity/process recreation presentation without implicit decode resume. |
| EVAL-V-14 | PLANNED | EVAL-V-11,EVAL-V-07 | Verify request/telemetry/resource correlation and privacy sentinel absence on Android. |

## Task ledger — General Purpose and physical evidence

| ID | State | Depends on | Task |
| --- | --- | --- | --- |
| EVAL-V-20 | PLANNED | EVAL-GP-12,EVAL-R-12 | Run General Purpose v1 Smoke against controlled supported-model development configuration and verify scoring pipeline. |
| EVAL-V-21 | PLANNED | EVAL-V-20,Q35-6 measured profiles | Execute Standard 100-case suite for Qwen3.5 0.8B reference artifact on representative physical devices. |
| EVAL-V-22 | PLANNED | EVAL-V-20,Q35-6 measured profiles | Execute Standard 100-case suite for Qwen3.5 2B reference artifact on the same representative device classes. |
| EVAL-V-23 | PLANNED | EVAL-V-21,EVAL-V-22 | Produce quality-compatible and runtime-compatible comparison evidence with exact identities. |
| EVAL-V-24 | PLANNED | EVAL-V-23 | Repeat selected suites enough to inspect latency/throughput/memory/thermal variance and reliability. |
| EVAL-V-25 | PLANNED | EVAL-V-24 | Validate no false official-benchmark or certification claims appear in UI/docs/export. |
| EVAL-V-26 | PLANNED | EVAL-V-01..EVAL-V-25 | Run repository-wide gates, update final feature/current-state docs and close EVAL-8. |

`Q35-6 measured profiles` is an external repository dependency, not a model-evaluation task ID. It blocks final representative performance claims but does not block EVAL-V-20 or earlier feature implementation.

## Parallel execution guidance

- EVAL-V-03 is ready independently of dataset/runner work.
- EVAL-V-02 unlocks after dataset fixture/install completion.
- EVAL-V-05 can proceed independently of Android instrumentation once persistence is complete.
- EVAL-V-10 and UI deterministic validation may proceed in parallel.
- EVAL-V-21 and V-22 are independent after the same device/profile prerequisites are available and may run in parallel on separate representative devices.

## Repository gates

Before closing EVAL-8, run the applicable repository gates including documentation validation, formatting/static analysis, JVM tests, Android lint/build, native tests and model-artifact guards. Physical evidence supplements these gates; it does not replace them.

## Completion criteria

EVAL-8 is complete only when:

- all deterministic correctness/privacy gates are green;
- Android integration covers real evaluation orchestration and cleanup;
- exact General Purpose pack/sample identities are retained with evidence;
- both supported reference tiers have representative physical evidence using approved measured runtime profiles;
- comparison UI/reporting suppresses invalid deltas;
- no content leaks into normal telemetry/export;
- repository and feature current-state ledgers reflect the measured outcome.
